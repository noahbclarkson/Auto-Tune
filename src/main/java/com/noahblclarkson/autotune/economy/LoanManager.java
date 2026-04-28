package com.noahblclarkson.autotune.economy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.LoanConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.LoanRepository;
import com.noahblclarkson.autotune.database.PlayerRepository;
import com.noahblclarkson.autotune.manager.TreasuryService;
import com.noahblclarkson.autotune.service.BadgeService;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.Loan;
import com.noahblclarkson.autotune.model.Loan.LoanStatus;
import com.noahblclarkson.autotune.model.PlayerData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Singleton
@SuppressWarnings("PMD")
public class LoanManager {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final Economy economy;
    private final DatabaseManager databaseManager;
    private final LoanRepository loanRepository;
    private final PlayerRepository playerRepository;
    private final EconomySnapshotRepository snapshotRepository;
    private final TreasuryService treasuryService;
    private final BadgeService badgeService;
    private volatile boolean interestCircuitOpen = false;
    /**
     * Hysteresis lock for TIER3 circuit breaker (legacy non-counter-cyclical path).
     * Once TIER3 fires (D/G >= tier3Ratio), the circuit stays locked (0% interest)
     * until D/G drops below 90% of tier3Ratio (a 10% hysteresis band).
     * This prevents rapid open/close cycling when D/G hovers near the boundary.
     */
    private volatile boolean tier3CircuitLocked = false;
    /**
     * Admin-triggered recovery mode: hard-freezes loan interest at 0% and blocks
     * all new loan issuance until an admin disables it.
     */
    private volatile boolean manualRecoveryMode = false;
    /**
     * Remaining ticks for the graduated TIER3 exit cap.
     * After TIER3 circuit unlocks (D/G dropped below hysteresis threshold), the
     * interest multiplier is capped at tier3ExitMultiplierCap for this many ticks.
     * Prevents multiplier jump cascade: without this, multiplier jumps from 0% → 53%
     * at D/G=14, causing TIER3 re-entry within days.
     * Mirrors Rust's tier3_exit_delay_remaining field.
     */
    private int tier3ExitDelayRemaining = 0;

    public void setTier3CircuitLocked(boolean locked) {
        this.tier3CircuitLocked = locked;
    }

    public void setManualRecoveryMode(boolean enabled) {
        this.manualRecoveryMode = enabled;
        this.tier3CircuitLocked = enabled;
        if (!enabled) {
            this.interestCircuitOpen = false;
        }
    }

    public boolean isManualRecoveryMode() {
        return manualRecoveryMode;
    }

    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    @Inject
    public LoanManager(
            AutoTune plugin,
            ConfigManager configManager,
            Economy economy,
            DatabaseManager databaseManager,
            LoanRepository loanRepository,
            PlayerRepository playerRepository,
            EconomySnapshotRepository snapshotRepository,
            TreasuryService treasuryService,
            BadgeService badgeService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.economy = economy;
        this.databaseManager = databaseManager;
        this.loanRepository = loanRepository;
        this.playerRepository = playerRepository;
        this.snapshotRepository = snapshotRepository;
        this.treasuryService = treasuryService;
        this.badgeService = badgeService;
    }

    public LoanResult requestLoan(@NotNull Player player, @NotNull BigDecimal amount, int termDays) {
        Object lock = playerLocks.computeIfAbsent(player.getUniqueId(), k -> new Object());
        synchronized (lock) {
            try {
                return requestLoanInternal(player.getUniqueId(), player.getName(), amount, termDays);
            } finally {
                playerLocks.remove(player.getUniqueId());
            }
        }
    }

    public CompletableFuture<LoanResult> requestLoanAsync(@NotNull Player player, @NotNull BigDecimal amount, int termDays) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        return databaseManager.supplyAsync(() -> {
            Object lock = playerLocks.computeIfAbsent(playerId, k -> new Object());
            synchronized (lock) {
                try {
                    return requestLoanInternal(playerId, playerName, amount, termDays);
                } finally {
                    playerLocks.remove(playerId);
                }
            }
        }).thenCompose(result -> {
            if (result == null) {
                return CompletableFuture.completedFuture(LoanResult.error("Database error"));
            }
            if (!result.success()) {
                return CompletableFuture.completedFuture(result);
            }

            CompletableFuture<LoanResult> future = new CompletableFuture<>();
            databaseManager.runOnMain(() -> {
                if (!economy.depositPlayer(player, result.loan().principal().doubleValue()).transactionSuccess()) {
                    future.complete(LoanResult.error("Economy transaction failed"));
                    return;
                }
                databaseManager.runAsync(() -> loanRepository.insert(result.loan()))
                        .thenRun(() -> {
                            // Award LOAN_TAKER badge for first loan taken
                            badgeService.onFirstLoan(player.getUniqueId());
                            future.complete(result);
                        })
                        .exceptionally(ex -> {
                            plugin.getLogger().log(Level.WARNING, "Failed to insert loan", ex);
                            future.complete(LoanResult.error("Database error"));
                            return null;
                        });
            });
            return future;
        });
    }

    private LoanResult requestLoanInternal(UUID playerId, String playerName, BigDecimal amount, int termDays) {
        LoanConfig config = configManager.getConfig().loans();

        if (!config.enabled()) {
            return LoanResult.error("Loans are disabled");
        }

        if (manualRecoveryMode) {
            return LoanResult.error("Admin recovery mode is active: new loans are temporarily paused.");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return LoanResult.error("Loan amount must be positive");
        }

        int clampedTerm = Math.max(config.minTermDays(), Math.min(termDays, config.maxTermDays()));

        PlayerData playerData = playerRepository.getOrCreate(playerId, playerName);

        if (playerData.creditScore() < config.minCreditScore()) {
            return LoanResult.insufficientCredit(config.minCreditScore());
        }

        // Post-default cooldown: prevent immediately taking a new loan after defaulting
        if (playerData.lastDefaultedAt() != null && config.postDefaultCooldownHours() > 0) {
            Instant cooldownEnd = playerData.lastDefaultedAt()
                    .plus(Duration.ofHours(config.postDefaultCooldownHours()));
            if (Instant.now().isBefore(cooldownEnd)) {
                long hoursLeft = Duration.between(Instant.now(), cooldownEnd).toHours();
                return LoanResult.error("Cooldown after default: " + hoursLeft + "h remaining");
            }
        }

        Optional<Loan> existingLoan = loanRepository.findActiveByPlayer(playerId);
        if (existingLoan.isPresent()) {
            return LoanResult.alreadyHasLoan(existingLoan.get());
        }

        BigDecimal maxLoan = playerData.totalTraded().multiply(BigDecimal.valueOf(config.maxLoanMultiplier()));
        if (maxLoan.compareTo(BigDecimal.valueOf(100)) < 0) {
            maxLoan = BigDecimal.valueOf(100);
        }

        if (amount.compareTo(maxLoan) > 0) {
            return LoanResult.exceedsMax(maxLoan);
        }

        // Single loan GDP cap: no single loan can exceed economy GDP × singleLoanGdpCap
        if (config.singleLoanGdpCap() > 0) {
            Optional<EconomySnapshot> latestSnapshot = snapshotRepository.findLatest();
            if (latestSnapshot.isPresent()) {
                BigDecimal gdp = latestSnapshot.get().gdp();
                if (gdp.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal gdpCap = gdp.multiply(BigDecimal.valueOf(config.singleLoanGdpCap()));
                    if (amount.compareTo(gdpCap) > 0) {
                        return LoanResult.exceedsGdpCap(gdpCap);
                    }
                }
            }
        }

        // Economy-wide total debt cap: reject if total debt would exceed GDP × totalDebtGdpCap
        if (config.totalDebtGdpCap() > 0) {
            Optional<EconomySnapshot> latestSnapshot = snapshotRepository.findLatest();
            if (latestSnapshot.isPresent()) {
                BigDecimal gdp = latestSnapshot.get().gdp();
                if (gdp.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal totalDebtLimit = gdp.multiply(BigDecimal.valueOf(config.totalDebtGdpCap()));
                    BigDecimal totalCurrentDebt = loanRepository.findAllActive().stream()
                            .map(Loan::currentBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal projectedTotal = totalCurrentDebt.add(amount);
                    if (projectedTotal.compareTo(totalDebtLimit) > 0) {
                        return LoanResult.error("Economy-wide debt cap reached: total debt would exceed "
                                + config.totalDebtGdpCap() + "× GDP (" + configManager.formatCurrency(totalDebtLimit) + "). "
                                + "Current total: " + configManager.formatCurrency(totalCurrentDebt) + ". Repay existing loans to take new ones.");
                    }
                }
            }
        }


        // block_mm_gb_loans_during_tier3: prevent MM/GB from opening new loans while circuit is in
        // TIER3 (locked at 0% interest). This prevents archetype players from accumulating
        // interest-free debt during the circuit lock, which would amplify the doom loop on release.
        if (config.blockMmGbLoansDuringTier3()
                && tier3CircuitLocked
                && (playerData.playerType() == PlayerData.PlayerType.MARKET_MAKER
                    || playerData.playerType() == PlayerData.PlayerType.GUILD_BUYER)) {
            return LoanResult.error("Loan blocked: MarketMakers and GuildBuyers cannot open new loans "
                    + "while the economy circuit breaker is active (TIER3).");
        }

        // guildbuyer_total_debt_cap: per-GuildBuyer debt limit. Prevents a single GB from
        // accumulating disproportionate debt relative to economy size.
        if (playerData.playerType() == PlayerData.PlayerType.GUILD_BUYER
                && config.guildbuyerTotalDebtCap() > 0) {
            Optional<EconomySnapshot> latestSnapshot = snapshotRepository.findLatest();
            if (latestSnapshot.isPresent()) {
                BigDecimal gdp = latestSnapshot.get().gdp();
                if (gdp.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal gbDebtLimit = gdp.multiply(BigDecimal.valueOf(config.guildbuyerTotalDebtCap()));
                    // Sum all active loans for this GB player
                    BigDecimal gbCurrentDebt = loanRepository.findAllActive().stream()
                            .filter(loan -> loan.playerUuid().equals(playerId))
                            .map(Loan::currentBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal gbProjectedTotal = gbCurrentDebt.add(amount);
                    if (gbProjectedTotal.compareTo(gbDebtLimit) > 0) {
                        return LoanResult.error("GuildBuyer debt cap reached: your total debt would exceed "
                                + config.guildbuyerTotalDebtCap() + "× economy GDP (" + configManager.formatCurrency(gbDebtLimit) + "). "
                                + "Your current debt: " + configManager.formatCurrency(gbCurrentDebt) + ". Repay existing loans.");
                    }
                }
            }
        }

        BigDecimal interestRate = calculateInterestRate(playerData.creditScore(), clampedTerm, config);
        Instant dueDate = Instant.now().plus(Duration.ofDays(clampedTerm));

        Loan loan = Loan.builder()
                .playerUuid(playerId)
                .principal(amount)
                .currentBalance(amount)
                .interestRate(interestRate)
                .dueDate(dueDate)
                .build();

        return LoanResult.success(loan);
    }

    public CompletableFuture<LoanResult> repayLoanAsync(@NotNull Player player, @NotNull BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CompletableFuture.completedFuture(LoanResult.error("Repayment amount must be positive"));
        }

        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        return databaseManager.supplyAsync(() -> loanRepository.findActiveByPlayer(playerId))
                .thenCompose(activeLoan -> {
                    if (activeLoan == null) {
                        return CompletableFuture.completedFuture(LoanResult.error("Database error"));
                    }
                    if (activeLoan.isEmpty()) {
                        return CompletableFuture.completedFuture(LoanResult.noActiveLoan());
                    }

                    Loan loan = activeLoan.get();
                    BigDecimal paymentAmount = amount.min(loan.currentBalance());

                    CompletableFuture<LoanResult> future = new CompletableFuture<>();
                    databaseManager.runOnMain(() -> {
                        if (!economy.has(player, paymentAmount.doubleValue())) {
                            future.complete(LoanResult.insufficientFunds(paymentAmount));
                            return;
                        }

                        if (!economy.withdrawPlayer(player, paymentAmount.doubleValue()).transactionSuccess()) {
                            future.complete(LoanResult.error("Economy transaction failed"));
                            return;
                        }

                        Loan updated = loan.makePayment(paymentAmount);
                        databaseManager.runAsync(() -> {
                                    loanRepository.update(updated);
                                    if (updated.status() == LoanStatus.PAID) {
                                        int creditBonus = calculateEarlyRepaymentBonus(loan);
                                        PlayerData playerData = playerRepository.getOrCreate(playerId, playerName);
                                        playerRepository.updateCreditScore(playerId,
                                                Math.min(PlayerData.MAX_CREDIT_SCORE, playerData.creditScore() + creditBonus));
                                        // Award LOAN_SHARK if repaid a large loan
                                        badgeService.onLoanRepaid(playerId, loan.principal());
                                    }
                                })
                                .thenRun(() -> future.complete(LoanResult.repaymentSuccess(updated, paymentAmount)))
                                .exceptionally(ex -> {
                                    plugin.getLogger().log(Level.WARNING,
                                            "Failed to process loan repayment DB update for " + playerId
                                                    + " (amount=" + paymentAmount + "). Restoring funds.", ex);
                                    // DB write failed but money was already withdrawn — refund the player
                                    // to avoid losing their funds. Must run on main thread for Vault ops.
                                    databaseManager.runOnMain(() ->
                                            economy.depositPlayer(player, paymentAmount.doubleValue()));
                                    future.complete(LoanResult.error("Database error — funds restored"));
                                    return null;
                                });
                    });

                    return future;
                });
    }

    private int calculateEarlyRepaymentBonus(Loan loan) {
        LoanConfig config = configManager.getConfig().loans();
        int baseBonus = Math.min(50, (int) (loan.principal().doubleValue() / 100));

        Duration totalTerm = Duration.between(loan.createdAt(), loan.dueDate());
        Duration elapsed = Duration.between(loan.createdAt(), Instant.now());

        if (totalTerm.isZero() || totalTerm.isNegative()) {
            return baseBonus;
        }

        double fractionRemaining = 1.0 - Math.min(1.0, (double) elapsed.toMillis() / totalTerm.toMillis());

        return (int) (baseBonus * (1.0 + fractionRemaining * config.earlyRepaymentBonusMultiplier()));
    }

    public CompletableFuture<Optional<Loan>> getActiveLoanAsync(@NotNull UUID playerUuid) {
        return databaseManager.supplyAsync(() -> loanRepository.findActiveByPlayer(playerUuid));
    }

    public CompletableFuture<BigDecimal> getMaxLoanAmountAsync(@NotNull UUID playerUuid) {
        return databaseManager.supplyAsync(() -> getMaxLoanAmount(playerUuid));
    }

    public Optional<Loan> getActiveLoan(@NotNull UUID playerUuid) {
        return loanRepository.findActiveByPlayer(playerUuid);
    }

    /** Returns all currently overdue loans. Does not modify state. */
    public List<Loan> getOverdueLoans() {
        return loanRepository.findOverdueLoans();
    }

    public List<Loan> getLoanHistory(@NotNull UUID playerUuid) {
        return loanRepository.findByPlayer(playerUuid);
    }

    public void processInterest() {
        LoanConfig config = configManager.getConfig().loans();
        Duration compoundInterval = Duration.ofHours(config.compoundIntervalHours());

        if (manualRecoveryMode) {
            tier3CircuitLocked = true;
            interestCircuitOpen = true;
            return;
        }

        // Counter-cyclical interest: smooth linear reduction of interest rate as debt/GDP rises.
        // multiplier = max(0, min(1.0, 1.0 - debtGdpRatio / tier3Ratio))
        // At D/G=3  → 70% interest  (vs old TIER1 50%)
        // At D/G=5  → 50% interest  (vs old TIER2 25%)
        // At D/G=10 →  0% interest  (vs old TIER3 0%)
        //
        // When counterCyclical=false, falls back to the tiered circuit breaker:
        // TIER1 (>3x): 50% interest — warning zone
        // TIER2 (>5x): 25% interest — danger zone
        // TIER3 (>10x): 0% interest — emergency zone
        //
        // Simulation evidence (2026-03-27): old single-ratio breaker fired at 10x
        // but couldn't prevent runaway compounding before that threshold.
        // FIX (2026-03-30): Circuit breaker now counts ALL unpaid debt (ACTIVE + DEFAULTED).
        double interestMultiplier = 1.0;
        String currentTier = "NORMAL";
        Optional<EconomySnapshot> latestSnapshot = snapshotRepository.findLatest();
        if (latestSnapshot.isPresent() && config.debtGdpTier3Ratio() > 0.0) {
            BigDecimal gdp = latestSnapshot.get().gdp();
            BigDecimal totalDebt = BigDecimal.ZERO;
            for (Loan l : loanRepository.findAllUnpaid()) {
                totalDebt = totalDebt.add(l.currentBalance());
            }
            if (gdp.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = totalDebt.divide(gdp, MathContext.DECIMAL128).doubleValue();

                if (config.counterCyclical()) {
                    // Continuous counter-cyclical taper: interest falls smoothly from 100% at
                    // D/G=0 to 0% at D/G=tier3Ratio. Players get proportional relief as debt
                    // rises, preventing the pre-circuit-breaker debt accumulation spiral.
                    //
                    // HYSTERESIS: Once TIER3 fires (D/G >= tier3Ratio), the circuit stays
                    // locked (0% interest) until D/G drops below the hysteresis threshold.
                    // Without hysteresis, D/G hovering near tier3 causes multiplier to
                    // oscillate between 0.0 (TIER3) and ~0.003 (just below tier3), allowing
                    // debt to compound during brief TIER2 windows — a doom loop.
                    // Configurable via tier3HysteresisBand: 0.1 (10% band = unlock at 90%
                    // of tier3) for backward compat, 0.5 (50% band = unlock at 50% of tier3)
                    // is recommended for deeper hysteresis.
                    double hysteresisThreshold = config.debtGdpTier3Ratio() * (1.0 - config.tier3HysteresisBand());

                    // Check hysteresis unlock: if locked and ratio dropped below band, unlock.
                    // also track whether we just transitioned from locked→unlocked to apply the exit cap.
                    boolean justUnlockedCC = tier3CircuitLocked && ratio < hysteresisThreshold;
                    if (justUnlockedCC) {
                        tier3CircuitLocked = false;
                        plugin.getLogger().info(String.format(
                            "[Auto-Tune] TIER3 hysteresis unlock (counter-cyclical) — D/G %.1fx (below %.1fx threshold). Interest may resume.",
                            ratio, hysteresisThreshold));
                    }

                    if (tier3CircuitLocked) {
                        // Circuit locked in TIER3 — hold at 0% interest until hysteresis threshold.
                        interestMultiplier = 0.0;
                        currentTier = "TIER3";
                    } else if (ratio >= config.debtGdpTier3Ratio()) {
                        // First time crossing TIER3 — engage the lock.
                        tier3CircuitLocked = true;
                        interestMultiplier = 0.0;
                        currentTier = "TIER3";
                    } else {
                        double maxRatio = config.debtGdpTier3Ratio();
                        // Counter-cyclical taper: multiplier = max(minInterestMultiplier, 1 - ratio/maxRatio)
                        // minInterestMultiplier (default 0.0) prevents total 0% interest at D/G=tier3,
                        // which causes D/G to oscillate at the boundary. A small floor (0.005) allows
                        // deleveraging to continue even during TIER3 lock.
                        interestMultiplier = Math.max(config.minInterestMultiplier(),
                                Math.min(1.0, 1.0 - ratio / maxRatio));
                        if (ratio >= config.debtGdpTier2Ratio()) {
                            currentTier = "TIER2";
                        } else if (ratio >= config.debtGdpTier1Ratio()) {
                            currentTier = "TIER1";
                        }
                        // ARCHITECTURAL FIX: When TIER3 unlocks (D/G below hysteresis threshold),
                        // exit to NORMAL instead of TIER2. TIER2's 50% rate compounds debt faster than
                        // GDP grows (~1%/day), causing immediate re-entry. Exiting to NORMAL prevents
                        // the doom-loop oscillation that all 8 prior fix candidates failed to solve.
                        // Also apply graduated exit cap: clamp multiplier to tier3ExitMultiplierCap
                        // during the delay window to prevent the multiplier jump cascade
                        // (0% → ~53% at D/G=14) that re-triggers TIER3 within days.
                        currentTier = "NORMAL";
                        if (tier3ExitDelayRemaining > 0) {
                            tier3ExitDelayRemaining--;
                            interestMultiplier = Math.min(interestMultiplier, config.tier3ExitMultiplierCap());
                        } else if (justUnlockedCC) {
                            // TIER3 just unlocked — start the graduated exit delay window
                            tier3ExitDelayRemaining = config.tier3ExitDelayTicks();
                            interestMultiplier = Math.min(interestMultiplier, config.tier3ExitMultiplierCap());
                        }
                    }
                } else {
                    // Legacy tiered circuit breaker with hysteresis for TIER3:
                    // Once TIER3 fires (D/G >= tier3Ratio), the circuit stays locked (0% interest)
                    // until D/G drops below the hysteresis threshold (configurable via tier3HysteresisBand).
                    double hysteresisThreshold = config.debtGdpTier3Ratio() * (1.0 - config.tier3HysteresisBand());

                    // Check hysteresis unlock: if locked and ratio dropped below band, unlock.
                    // Track whether we just transitioned from locked→unlocked to apply the exit cap.
                    boolean justUnlockedLegacy = tier3CircuitLocked && ratio < hysteresisThreshold;
                    if (justUnlockedLegacy) {
                        tier3CircuitLocked = false;
                        plugin.getLogger().info(String.format(
                            "[Auto-Tune] TIER3 hysteresis unlock — D/G %.1fx (below %.1fx threshold). Interest may resume.",
                            ratio, hysteresisThreshold));
                    }

                    if (tier3CircuitLocked) {
                        // Circuit locked in TIER3 — hold at 0% interest until hysteresis threshold.
                        interestMultiplier = 0.0;
                        currentTier = "TIER3";
                    } else if (ratio >= config.debtGdpTier3Ratio()) {
                        // First time crossing TIER3 threshold — engage the lock.
                        tier3CircuitLocked = true;
                        interestMultiplier = 0.0;
                        currentTier = "TIER3";
                    } else if (ratio >= config.debtGdpTier2Ratio()) {
                        interestMultiplier = config.tier2InterestCap();
                        currentTier = "TIER2";
                    } else if (ratio >= config.debtGdpTier1Ratio()) {
                        interestMultiplier = config.tier1InterestCap();
                        currentTier = "TIER1";
                    }
                    // ARCHITECTURAL FIX: When TIER3 unlocks (D/G below hysteresis threshold),
                    // exit to NORMAL instead of TIER2. Legacy TIER2 (25%) compounds debt too fast vs
                    // GDP growth (~1%/day), causing TIER3 re-entry within days. All 8 fix candidates
                    // failed because they never addressed this exit path. Exiting to NORMAL prevents it.
                    // Also apply graduated exit cap: clamp multiplier to tier3ExitMultiplierCap
                    // during the delay window to prevent the multiplier jump cascade that re-triggers TIER3.
                    currentTier = "NORMAL";
                    if (tier3ExitDelayRemaining > 0) {
                        tier3ExitDelayRemaining--;
                        interestMultiplier = Math.min(interestMultiplier, config.tier3ExitMultiplierCap());
                    } else if (justUnlockedLegacy) {
                        // TIER3 just unlocked — start the graduated exit delay window
                        tier3ExitDelayRemaining = config.tier3ExitDelayTicks();
                        interestMultiplier = Math.min(interestMultiplier, config.tier3ExitMultiplierCap());
                    }
                }

                if (!currentTier.equals("NORMAL")) {
                    String mode = config.counterCyclical() ? "Counter-cyclical" : "Circuit breaker";
                    String msg = String.format("[Auto-Tune] %s %s — debt/gdp %.1fx. Interest at %.0f%%.",
                            mode, currentTier, ratio, interestMultiplier * 100);
                    if (currentTier.equals("TIER3") && !interestCircuitOpen) {
                        plugin.getLogger().warning(msg + " Interest paused.");
                    } else if (!currentTier.equals("TIER3")) {
                        plugin.getLogger().info(msg);
                    }
                }
            }
        }

        if (interestMultiplier <= 0.0) {
            interestCircuitOpen = true;
            return;
        }
        if (currentTier.equals("NORMAL")) {
            interestCircuitOpen = false;
        }

        List<Loan> activeLoans = loanRepository.findAllActive();
        Instant now = Instant.now();

        for (Loan loan : activeLoans) {
            try {
                Duration timeSinceInterest = Duration.between(loan.lastInterestAt(), now);
                if (timeSinceInterest.compareTo(compoundInterval) >= 0) {
                    // Calculate interest BEFORE applying so we can collect tax on it
                    BigDecimal fullInterest = loan.currentBalance().multiply(loan.interestRate());
                    BigDecimal interestAmount = fullInterest.multiply(BigDecimal.valueOf(interestMultiplier));
                    treasuryService.collectLoanInterestTax(interestAmount);
                    // Apply tiered interest: newBalance = current + (fullInterest * multiplier)
                    Loan updated = loan.toBuilder()
                            .currentBalance(loan.currentBalance().add(interestAmount))
                            .lastInterestAt(Instant.now())
                            .build();
                    loanRepository.update(updated);

                    Player player = plugin.getServer().getPlayer(loan.playerUuid());
                    if (player != null && player.isOnline()) {
                        databaseManager.runOnMain(() -> player.sendMessage(configManager.getMessage("loan.interest-applied",
                                Map.of("balance", configManager.formatCurrency(updated.currentBalance())))));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error processing interest for loan " + loan.id(), e);
            }
        }
    }

    public void processOverdueLoans() {
        LoanConfig config = configManager.getConfig().loans();
        List<Loan> overdueLoans = loanRepository.findOverdueLoans();

        for (Loan loan : overdueLoans) {
            try {
                Loan defaulted = loan.markDefaulted();
                loanRepository.update(defaulted);

                // Record when this player defaulted — used for post-default cooldown
                playerRepository.updateLastDefaultedAt(loan.playerUuid(), Instant.now());

                PlayerData playerData = playerRepository.findByUuid(loan.playerUuid()).orElse(null);
                if (playerData != null) {
                    int newScore = Math.max(PlayerData.MIN_CREDIT_SCORE,
                            playerData.creditScore() - config.defaultPenalty());
                    playerRepository.updateCreditScore(loan.playerUuid(), newScore);
                }

                Player player = plugin.getServer().getPlayer(loan.playerUuid());
                if (player != null && player.isOnline()) {
                    databaseManager.runOnMain(() -> player.sendMessage(configManager.getMessage("loan.defaulted",
                            Map.of("penalty", String.valueOf(config.defaultPenalty())))));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error processing overdue loan " + loan.id(), e);
            }
        }
    }

    public void processWarnings() {
        LoanConfig config = configManager.getConfig().loans();
        int warningHours = config.warningBeforeDueHours();
        if (warningHours <= 0) {
            return;
        }

        List<Loan> activeLoans = loanRepository.findAllActive();
        Instant now = Instant.now();
        Instant warningThreshold = now.plus(Duration.ofHours(warningHours));

        for (Loan loan : activeLoans) {
            try {
                if (loan.dueDate().isBefore(warningThreshold) && loan.dueDate().isAfter(now)) {
                    long hoursRemaining = Duration.between(now, loan.dueDate()).toHours();
                    String timeLeft = hoursRemaining >= 24
                            ? (hoursRemaining / 24) + " day" + (hoursRemaining / 24 != 1 ? "s" : "")
                            : hoursRemaining + " hour" + (hoursRemaining != 1 ? "s" : "");

                    Player player = plugin.getServer().getPlayer(loan.playerUuid());
                    if (player != null && player.isOnline()) {
                        databaseManager.runOnMain(() -> player.sendMessage(configManager.getMessage("loan.warning-due",
                                Map.of(
                                        "amount", configManager.formatCurrency(loan.currentBalance()),
                                        "time_left", timeLeft
                                ))));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error processing loan warning for " + loan.id(), e);
            }
        }
    }

    private BigDecimal calculateInterestRate(int creditScore, int termDays, LoanConfig config) {
        BigDecimal baseRate = BigDecimal.valueOf(config.baseInterestRate());

        if (config.creditScoreModifier()) {
            double modifier = 1 + (500.0 - creditScore) / 1000.0;
            baseRate = baseRate.multiply(BigDecimal.valueOf(modifier));
        }

        int extraDays = Math.max(0, termDays - config.minTermDays());
        double termPremium = extraDays * config.termPremiumPerDay();
        baseRate = baseRate.add(BigDecimal.valueOf(termPremium));

        if (config.inflationRateImpact() > 0) {
            Optional<EconomySnapshot> latestSnapshot = snapshotRepository.findLatest();
            if (latestSnapshot.isPresent()) {
                double avgPriceChange = latestSnapshot.get().averagePriceChange().doubleValue();
                double inflationAdjustment = 1.0 + (avgPriceChange / 100.0) * config.inflationRateImpact();
                inflationAdjustment = Math.max(0.5, inflationAdjustment);
                baseRate = baseRate.multiply(BigDecimal.valueOf(inflationAdjustment));
            }
        }

        return baseRate.setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal getMaxLoanAmount(@NotNull UUID playerUuid) {
        LoanConfig config = configManager.getConfig().loans();
        PlayerData playerData = playerRepository.findByUuid(playerUuid).orElse(null);

        if (playerData == null) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal maxLoan = playerData.totalTraded().multiply(BigDecimal.valueOf(config.maxLoanMultiplier()));
        return maxLoan.max(BigDecimal.valueOf(100));
    }

    public BigDecimal getInterestRate(@NotNull UUID playerUuid, int termDays) {
        LoanConfig config = configManager.getConfig().loans();
        PlayerData playerData = playerRepository.findByUuid(playerUuid).orElse(null);

        int creditScore = playerData != null ? playerData.creditScore() : PlayerData.DEFAULT_CREDIT_SCORE;
        int clampedDays = Math.min(termDays, config.maxTermDays());
        return calculateInterestRate(creditScore, clampedDays, config);
    }

    public BigDecimal getAmortizationPayment(Loan loan) {
        LoanConfig config = configManager.getConfig().loans();
        Duration remaining = Duration.between(Instant.now(), loan.dueDate());
        // Use minutes then convert to periods to avoid truncation error from toHours().
        // toHours() truncates nanosecond precision loss (e.g. 30 days - 1ns → 719h not 720h).
        // Using minutes gives us the precision needed for accurate period counting.
        long minutesRemaining = Math.max(1, remaining.toMinutes());
        long periodsRemaining = Math.max(1, (minutesRemaining + config.compoundIntervalHours() * 59L) / (config.compoundIntervalHours() * 60L));

        double rate = loan.interestRate().doubleValue();
        double balance = loan.currentBalance().doubleValue();

        if (rate <= 0 || periodsRemaining <= 0) {
            return loan.currentBalance().divide(BigDecimal.valueOf(periodsRemaining), 2, RoundingMode.HALF_UP);
        }

        double payment = balance * rate * Math.pow(1 + rate, (double) periodsRemaining)
                / (Math.pow(1 + rate, (double) periodsRemaining) - 1);

        return BigDecimal.valueOf(payment).setScale(2, RoundingMode.HALF_UP);
    }

    public record LoanResult(
            boolean success,
            String errorMessage,
            Loan loan,
            BigDecimal amount
    ) {
        public static LoanResult success(Loan loan) {
            return new LoanResult(true, null, loan, loan.principal());
        }

        public static LoanResult repaymentSuccess(Loan loan, BigDecimal amount) {
            return new LoanResult(true, null, loan, amount);
        }

        public static LoanResult insufficientCredit(int required) {
            return new LoanResult(false, "Credit score too low. Required: " + required, null, BigDecimal.ZERO);
        }

        public static LoanResult alreadyHasLoan(Loan existing) {
            return new LoanResult(false, "You already have an active loan", existing, existing.currentBalance());
        }

        public static LoanResult exceedsMax(BigDecimal max) {
            return new LoanResult(false, "Exceeds maximum loan amount: " + max, null, max);
        }

        public static LoanResult exceedsGdpCap(BigDecimal cap) {
            return new LoanResult(false, "Exceeds economy GDP cap: " + cap + " (single loan limit)", null, cap);
        }

        public static LoanResult noActiveLoan() {
            return new LoanResult(false, "No active loan to repay", null, BigDecimal.ZERO);
        }

        public static LoanResult insufficientFunds(BigDecimal required) {
            return new LoanResult(false, "Insufficient funds", null, required);
        }

        public static LoanResult error(String message) {
            return new LoanResult(false, message, null, BigDecimal.ZERO);
        }
    }

    /**
     * Returns the current loan circuit breaker status — tier, ratio, and whether
     * interest is currently paused. Safe to call from admin commands at any time.
     */
    public CircuitBreakerStatus getCircuitBreakerStatus() {
        LoanConfig config = configManager.getConfig().loans();
        Optional<EconomySnapshot> latestSnapshot = snapshotRepository.findLatest();

        if (manualRecoveryMode) {
            double ratio = -1.0;
            if (latestSnapshot.isPresent() && latestSnapshot.get().gdp().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalDebt = BigDecimal.ZERO;
                for (Loan l : loanRepository.findAllUnpaid()) {
                    totalDebt = totalDebt.add(l.currentBalance());
                }
                ratio = totalDebt.divide(latestSnapshot.get().gdp(), MathContext.DECIMAL128).doubleValue();
            }
            return new CircuitBreakerStatus("ADMIN_RECOVERY", ratio, 0.0, true, config.counterCyclical());
        }

        if (latestSnapshot.isEmpty() || config.debtGdpTier3Ratio() <= 0.0) {
            return new CircuitBreakerStatus("NORMAL", -1.0, 1.0, false, config.counterCyclical());
        }

        BigDecimal gdp = latestSnapshot.get().gdp();
        BigDecimal totalDebt = BigDecimal.ZERO;
        for (Loan l : loanRepository.findAllUnpaid()) {
            totalDebt = totalDebt.add(l.currentBalance());
        }

        if (gdp.compareTo(BigDecimal.ZERO) <= 0) {
            return new CircuitBreakerStatus("NORMAL", -1.0, 1.0, false, config.counterCyclical());
        }

        double ratio = totalDebt.divide(gdp, MathContext.DECIMAL128).doubleValue();
        if (ratio >= config.debtGdpTier3Ratio()) {
            return new CircuitBreakerStatus("TIER3", ratio, 0.0, interestCircuitOpen, config.counterCyclical());
        } else if (ratio >= config.debtGdpTier2Ratio()) {
            double mult = config.counterCyclical()
                    ? Math.max(0.0, Math.min(1.0, 1.0 - ratio / config.debtGdpTier3Ratio()))
                    : config.tier2InterestCap();
            return new CircuitBreakerStatus("TIER2", ratio, mult, false, config.counterCyclical());
        } else if (ratio >= config.debtGdpTier1Ratio()) {
            double mult = config.counterCyclical()
                    ? Math.max(0.0, Math.min(1.0, 1.0 - ratio / config.debtGdpTier3Ratio()))
                    : config.tier1InterestCap();
            return new CircuitBreakerStatus("TIER1", ratio, mult, false, config.counterCyclical());
        }
        double mult = config.counterCyclical()
                ? Math.max(0.0, Math.min(1.0, 1.0 - ratio / config.debtGdpTier3Ratio()))
                : 1.0;
        return new CircuitBreakerStatus("NORMAL", ratio, mult, false, config.counterCyclical());
    }

    /** Current state of the loan circuit breaker / counter-cyclical interest system. */
    public record CircuitBreakerStatus(
            String tier,       // NORMAL, TIER1, TIER2, TIER3
            double debtGdpRatio,
            double interestMultiplier,
            boolean circuitOpen,
            boolean counterCyclicalMode
    ) {}
}
