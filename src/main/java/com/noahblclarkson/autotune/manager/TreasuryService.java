package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Manages the server treasury — accumulated transaction taxes.
 *
 * Tax is collected from buy, sell, auction, and loan-interest transactions
 * and accumulated in the treasury. Admins can view and withdraw from it.
 *
 * The treasury balance is kept in memory (AtomicReference for thread-safety)
 * and persisted to the database every 5 minutes and on plugin shutdown.
 *
 * Tax collection is best-effort: if the DB write fails, the in-memory balance
 * is still updated. On the next successful persist, the discrepancy resolves.
 */
@Singleton
public class TreasuryService {

    private final AutoTune plugin;
    private final Economy economy;
    private final ConfigManager configManager;
    private final AtomicReference<BigDecimal> treasuryBalance = new AtomicReference<>(BigDecimal.ZERO);
    private ScheduledTask persistTask;

    @Inject
    public TreasuryService(AutoTune plugin, Economy economy, ConfigManager configManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
    }

    public void start() {
        loadFromDb();
        // Persist every 5 minutes
        persistTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> persistToDb(),
                5 * 60 * 1000L,  // 5 minutes in ms
                5 * 60 * 1000L,
                TimeUnit.MILLISECONDS
        );
        plugin.getLogger().info("Treasury service started. Initial balance: "
                + configManager.formatCurrency(treasuryBalance.get()));
    }

    public void shutdown() {
        if (persistTask != null) {
            persistTask.cancel();
        }
        persistToDb();
        plugin.getLogger().info("Treasury service stopped. Final balance: "
                + configManager.formatCurrency(treasuryBalance.get()));
    }

    /**
     * Returns the current treasury balance.
     */
    public BigDecimal getBalance() {
        return treasuryBalance.get();
    }

    /**
     * Returns true if the tax system is enabled in config.
     */
    public boolean isEnabled() {
        return configManager.getConfig().tax().enabled();
    }

    // ── Tax collection ───────────────────────────────────────────────────────

    /**
     * Collect tax on a buy transaction. The tax is deducted from the player's
     * already-withdrawn cost before the transaction is recorded.
     * Returns the tax amount collected (0 if disabled).
     */
    public BigDecimal collectBuyTax(BigDecimal totalCost) {
        if (!isEnabled()) return BigDecimal.ZERO;
        AutoTuneConfig.TaxConfig tax = configManager.getConfig().tax();
        if (tax.buyTaxPercent() <= 0) return BigDecimal.ZERO;

        BigDecimal taxAmount = totalCost
                .multiply(BigDecimal.valueOf(tax.buyTaxPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (taxAmount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        treasuryBalance.updateAndGet(current -> current.add(taxAmount));
        return taxAmount;
    }

    /**
     * Collect tax on a sell transaction. The tax is deducted from the player's
     * sale proceeds before they are deposited. Returns the tax amount (0 if disabled).
     */
    public BigDecimal collectSellTax(BigDecimal totalProceeds) {
        if (!isEnabled()) return BigDecimal.ZERO;
        AutoTuneConfig.TaxConfig tax = configManager.getConfig().tax();
        if (tax.sellTaxPercent() <= 0) return BigDecimal.ZERO;

        BigDecimal taxAmount = totalProceeds
                .multiply(BigDecimal.valueOf(tax.sellTaxPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (taxAmount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        treasuryBalance.updateAndGet(current -> current.add(taxAmount));
        return taxAmount;
    }

    /**
     * Collect tax on an auction fill. Applied to the seller's proceeds
     * (the buyer already paid in full, seller gets net after tax).
     * Returns the tax amount (0 if disabled).
     */
    public BigDecimal collectAuctionTax(BigDecimal sellerProceeds) {
        if (!isEnabled()) return BigDecimal.ZERO;
        AutoTuneConfig.TaxConfig tax = configManager.getConfig().tax();
        if (tax.auctionTaxPercent() <= 0) return BigDecimal.ZERO;

        BigDecimal taxAmount = sellerProceeds
                .multiply(BigDecimal.valueOf(tax.auctionTaxPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (taxAmount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        treasuryBalance.updateAndGet(current -> current.add(taxAmount));
        return taxAmount;
    }

    /**
     * Collect tax on loan interest accrued. Applied when interest is applied
     * to a loan balance. Returns the tax amount (0 if disabled).
     */
    public BigDecimal collectLoanInterestTax(BigDecimal interestAmount) {
        if (!isEnabled()) return BigDecimal.ZERO;
        AutoTuneConfig.TaxConfig tax = configManager.getConfig().tax();
        if (tax.loanInterestTaxPercent() <= 0) return BigDecimal.ZERO;

        BigDecimal taxAmount = interestAmount
                .multiply(BigDecimal.valueOf(tax.loanInterestTaxPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (taxAmount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        treasuryBalance.updateAndGet(current -> current.add(taxAmount));
        return taxAmount;
    }

    // ── Treasury withdrawals ────────────────────────────────────────────────

    /**
     * Withdraw from the treasury. Returns the amount actually withdrawn
     * (may be less than requested if balance is insufficient).
     */
    public BigDecimal withdraw(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal current = treasuryBalance.get();
        BigDecimal actual = amount.min(current);

        if (actual.compareTo(BigDecimal.ZERO) > 0) {
            treasuryBalance.updateAndGet(balance -> balance.subtract(actual));
            persistToDb();
        }

        return actual;
    }

    /**
     * Add to the treasury (e.g., admin seed or external income).
     */
    public void deposit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        treasuryBalance.updateAndGet(current -> current.add(amount));
        persistToDb();
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private void loadFromDb() {
        try {
            var jdbi = plugin.getDatabaseManager().getJdbi();
            BigDecimal balance = jdbi.withHandle(handle ->
                    handle.createQuery("SELECT COALESCE(balance, 0) FROM at_treasury WHERE id = 1")
                            .mapTo(BigDecimal.class)
                            .findOne()
                            .orElse(BigDecimal.ZERO)
            );
            treasuryBalance.set(balance);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load treasury from DB, starting at 0: " + e.getMessage());
            treasuryBalance.set(BigDecimal.ZERO);
        }
    }

    private void persistToDb() {
        try {
            BigDecimal balance = treasuryBalance.get();
            var jdbi = plugin.getDatabaseManager().getJdbi();
            jdbi.useHandle(handle ->
                    handle.createUpdate(
                            "INSERT INTO at_treasury (id, balance, last_updated) VALUES (1, :balance, CURRENT_TIMESTAMP) "
                                    + "ON CONFLICT(id) DO UPDATE SET balance = :balance, last_updated = CURRENT_TIMESTAMP")
                            .bind("balance", balance)
                            .execute()
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to persist treasury to DB: " + e.getMessage());
        }
    }
}
