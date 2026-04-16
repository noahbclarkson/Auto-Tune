package com.noahblclarkson.autotune.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.AuctionRepository;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.database.MarketEventRepository;
import com.noahblclarkson.autotune.database.TransactionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Periodically prunes old data from the database to prevent unbounded growth.
 *
 * Cleans nine table groups:
 * - {@code at_transactions}      — trade audit log (used for price calculations)
 * - {@code at_market_history}   — price/volume snapshots (used for web charts)
 * - {@code at_economy_snapshots}— economy-wide statistics (used for dashboards)
 * - {@code at_auction_orders}   — expired/cancelled/filled auction orders
 * - {@code at_auction_fills}    — auction fill history
 * - {@code at_market_events}    — ended/cancelled market events
 *
 * Retention periods are configurable via {@code cleanup:} in config.yml.
 * On SQLite, runs {@code VACUUM} after deletions to reclaim disk space.
 */
@Singleton
@SuppressWarnings("PMD")
public class DatabaseCleanupManager {

    private static final String LOG_DELETED_PREFIX = "[cleanup] Deleted ";
    private static final String LOG_SUFFIX_DAYS = " days";

    private final AutoTune plugin;
    private final ConfigManager configManager;
    private final TransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final EconomySnapshotRepository snapshotRepository;
    private final AuctionRepository auctionRepository;
    private final MarketEventRepository marketEventRepository;
    private final DatabaseManager databaseManager;

    @Inject
    public DatabaseCleanupManager(
            AutoTune plugin,
            ConfigManager configManager,
            TransactionRepository transactionRepository,
            ItemRepository itemRepository,
            EconomySnapshotRepository snapshotRepository,
            AuctionRepository auctionRepository,
            MarketEventRepository marketEventRepository,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.transactionRepository = transactionRepository;
        this.itemRepository = itemRepository;
        this.snapshotRepository = snapshotRepository;
        this.auctionRepository = auctionRepository;
        this.marketEventRepository = marketEventRepository;
        this.databaseManager = databaseManager;
    }

    /**
     * Run cleanup for all enabled retention policies.
     * Logs the number of rows deleted from each table and runs VACUUM on SQLite.
     */
    public void runCleanup() {
        AutoTuneConfig config = configManager.getConfig();
        AutoTuneConfig.CleanupConfig cleanup = config.cleanup();
        boolean isDebug = config.debug().enabled();

        long start = System.currentTimeMillis();
        int totalDeleted = 0;

        // --- Transactions ---
        if (cleanup.transactions().enabled()) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(cleanup.transactions().retentionDays()));
            int deleted = transactionRepository.deleteOlderThan(cutoff);
            totalDeleted += deleted;
            if (isDebug) {
                plugin.getLogger().info(LOG_DELETED_PREFIX + deleted + " transactions older than "
                        + cleanup.transactions().retentionDays() + LOG_SUFFIX_DAYS);
            }
        }

        // --- Market history ---
        if (cleanup.marketHistory().enabled()) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(cleanup.marketHistory().retentionDays()));
            int deleted = itemRepository.deleteMarketHistoryOlderThan(cutoff);
            totalDeleted += deleted;
            if (isDebug) {
                plugin.getLogger().info(LOG_DELETED_PREFIX + deleted + " market history rows older than "
                        + cleanup.marketHistory().retentionDays() + LOG_SUFFIX_DAYS);
            }
        }

        // --- Economy snapshots (keep by count, not just age, to ensure chart continuity) ---
        if (cleanup.economySnapshots().enabled()) {
            // Keep enough snapshots to cover 2x the retention period at 5-min intervals
            int maxSnapshots = cleanup.economySnapshots().retentionDays() * 24 * 60 / 5;
            // But always keep at least 100 (≈8 hours)
            maxSnapshots = Math.max(maxSnapshots, 100);
            int deleted = snapshotRepository.keepMostRecentN(maxSnapshots);
            totalDeleted += deleted;
            if (isDebug) {
                plugin.getLogger().info(LOG_DELETED_PREFIX + deleted + " economy snapshots beyond "
                        + "most recent " + maxSnapshots);
            }
        }

        // --- Auction orders: delete terminal orders older than retention threshold ---
        if (cleanup.auctionOrders().enabled()) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(cleanup.auctionOrders().retentionDays()));
            int deleted = auctionRepository.deleteOrdersOlderThan(cutoff);
            totalDeleted += deleted;
            if (isDebug) {
                plugin.getLogger().info(LOG_DELETED_PREFIX + deleted + " auction orders older than "
                        + cleanup.auctionOrders().retentionDays() + LOG_SUFFIX_DAYS);
            }
        }

        // --- Auction fills: delete old fill records ---
        if (cleanup.auctionFills().enabled()) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(cleanup.auctionFills().retentionDays()));
            int deleted = auctionRepository.deleteFillsOlderThan(cutoff);
            totalDeleted += deleted;
            if (isDebug) {
                plugin.getLogger().info(LOG_DELETED_PREFIX + deleted + " auction fills older than "
                        + cleanup.auctionFills().retentionDays() + LOG_SUFFIX_DAYS);
            }
        }

        // --- Market events: delete ended/cancelled events older than retention threshold ---
        if (cleanup.marketEvents().enabled()) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(cleanup.marketEvents().retentionDays()));
            int deleted = marketEventRepository.deleteEndedOrCancelledOlderThan(cutoff);
            totalDeleted += deleted;
            if (isDebug) {
                plugin.getLogger().info(LOG_DELETED_PREFIX + deleted + " market events older than "
                        + cleanup.marketEvents().retentionDays() + LOG_SUFFIX_DAYS);
            }
        }

        // --- SQLite VACUUM ---
        // VACUUM cannot run inside a transaction, so we use a raw handle with autocommit
        if (isStorageSqlite()) {
            try {
                databaseManager.getJdbi().withHandle(handle ->
                        handle.createUpdate("VACUUM").execute());
                if (isDebug) {
                    plugin.getLogger().info("[cleanup] SQLite VACUUM completed");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[cleanup] SQLite VACUUM failed: " + e.getMessage());
            }
        }

        if (totalDeleted > 0 || isDebug) {
            plugin.getLogger().info("[cleanup] Database cleanup complete — " + totalDeleted
                    + " rows deleted in " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    /**
     * Returns current row counts for each cleanup-managed table.
     * Useful for {@code /autotune admin stats} or debug output.
     */
    public CleanupStats getStats() {
        return new CleanupStats(
                transactionRepository.count(),
                itemRepository.countMarketHistory(),
                snapshotRepository.count(),
                auctionRepository.countOrders(),
                auctionRepository.countFills(),
                marketEventRepository.count()
        );
    }

    private boolean isStorageSqlite() {
        return configManager.getConfig().storage().type()
                == AutoTuneConfig.StorageConfig.StorageType.SQLITE;
    }

    public record CleanupStats(
            long transactionCount,
            long marketHistoryCount,
            long snapshotCount,
            long auctionOrderCount,
            long auctionFillCount,
            long marketEventCount
    ) {}
}
