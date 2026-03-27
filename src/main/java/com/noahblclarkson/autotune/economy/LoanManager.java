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
public class LoanManager {

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final Economy economy;
    private final DatabaseManager databaseManager;
    private final LoanRepository loanRepository;
    private final PlayerRepository playerRepository;
    private final EconomySnapshotRepository snapshotRepository;
    private final TreasuryService treasuryService;
    private volatile boolean interestCircuitOpen = false;

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
            TreasuryService treasuryService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.economy = economy;
        this.databaseManager = databaseManager;
        this.loanRepository = loanRepository;
        this.playerRepository = playerRepository;
        this.snapshotRepository = snapshotRepository;
        this.treasuryService = treasuryService;
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
                        .thenRun(() -> future.complete(result))
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

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return LoanResult.error("Loan amount must be positive");
        }

        int clampedTerm = Math.max(config.minTermDays(), Math.min(termDays, config.maxTermDays()));

        PlayerData playerData = playerRepository.getOrCreate(playerId, playerName);

        if (playerData.creditScore() < config.minCreditScore()) {
            return LoanResult.insufficientCredit(config.minCreditScore());
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

    public List<Loan> getLoanHistory(@NotNull UUID playerUuid) {
        return loanRepository.findByPlayer(playerUuid);
    }

    public void processInterest() {
        LoanConfig config = configManager.getConfig().loans();
        Duration compoundInterval = Duration.ofHours(config.compoundIntervalHours());

        // Circuit breaker: skip interest if system debt exceeds GDP × threshold.
        // Simulation showed debt can grow 1000x when player count drops — this
        // prevents unbounded compound growth from destabilizing the economy.
        Optional<EconomySnapshot> latestSnapshot = snapshotRepository.findLatest();
        if (latestSnapshot.isPresent()) {
            BigDecimal gdp = latestSnapshot.get().gdp();
            BigDecimal totalDebt = BigDecimal.ZERO;
            for (Loan l : loanRepository.findAllActive()) {
                totalDebt = totalDebt.add(l.currentBalance());
            }
            if (gdp.compareTo(BigDecimal.ZERO) > 0) {
                double debtGdpRatio = totalDebt.divide(gdp, MathContext.DECIMAL128).doubleValue();
                if (debtGdpRatio > config.debtGdpCircuitBreakerRatio()) {
                    if (!interestCircuitOpen) {
                        plugin.getLogger().warning("[Auto-Tune] Loan circuit breaker OPEN — debt/gdp ratio "
                                + String.format("%.1f", debtGdpRatio) + " exceeds threshold "
                                + config.debtGdpCircuitBreakerRatio() + ". Interest accrual paused.");
                        interestCircuitOpen = true;
                    }
                    return;
                }
            }
        }
        interestCircuitOpen = false;

        List<Loan> activeLoans = loanRepository.findAllActive();
        Instant now = Instant.now();

        for (Loan loan : activeLoans) {
            try {
                Duration timeSinceInterest = Duration.between(loan.lastInterestAt(), now);
                if (timeSinceInterest.compareTo(compoundInterval) >= 0) {
                    // Calculate interest BEFORE applying so we can collect tax on it
                    BigDecimal interestAmount = loan.currentBalance().multiply(loan.interestRate());
                    treasuryService.collectLoanInterestTax(interestAmount);
                    Loan updated = loan.applyInterest(loan.interestRate());
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
}
