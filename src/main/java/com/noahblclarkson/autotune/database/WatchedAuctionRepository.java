package com.noahblclarkson.autotune.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for watched auction orders.
 *
 * Players can "watch" an order via the web dashboard or in-game UI and receive
 * notifications when it is filled or partially filled.
 */
@Singleton
public class WatchedAuctionRepository {

    private static final Logger LOGGER = Logger.getLogger(WatchedAuctionRepository.class.getName());

    private final Jdbi jdbi;

    @Inject
    public WatchedAuctionRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    /**
     * Add a watch entry for an order. Idempotent — safe to call multiple times.
     *
     * @param playerUuid the player watching the order
     * @param orderId    the auction order UUID
     * @param category   notification category: "FILLED" or "PARTIAL"
     */
    public void watch(UUID playerUuid, UUID orderId, String category) {
        try {
            jdbi.withHandle(handle ->
                    handle.execute("""
                            INSERT OR IGNORE INTO at_watched_auctions
                            (player_uuid, order_id, category) VALUES (?, ?, ?)
                            """,
                            playerUuid.toString(), orderId.toString(), category)
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to watch auction " + orderId + " for " + playerUuid, e);
        }
    }

    /**
     * Remove a watch entry.
     */
    public void unwatch(UUID playerUuid, UUID orderId) {
        try {
            jdbi.withHandle(handle ->
                    handle.execute("""
                            DELETE FROM at_watched_auctions
                            WHERE player_uuid = ? AND order_id = ?
                            """,
                            playerUuid.toString(), orderId.toString())
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to unwatch auction " + orderId + " for " + playerUuid, e);
        }
    }

    /**
     * Remove all watch entries for a player (e.g., when order is fully filled).
     *
     * @param orderId the auction order UUID
     * @return list of player UUIDs that were watching this order
     */
    public List<String> fetchAndClearByOrder(UUID orderId) {
        try {
            return jdbi.withHandle(handle -> {
                List<String> players = handle.createQuery("""
                        SELECT player_uuid FROM at_watched_auctions
                        WHERE order_id = ?
                        """)
                        .bind(0, orderId.toString())
                        .map((rs, ctx) -> rs.getString("player_uuid"))
                        .list();

                if (!players.isEmpty()) {
                    handle.execute("DELETE FROM at_watched_auctions WHERE order_id = ?",
                            orderId.toString());
                }
                return players;
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to fetch watched auction players for " + orderId, e);
            return List.of();
        }
    }

    /**
     * Remove all watch entries for an order (e.g., on cancellation).
     */
    public void clearAllForOrder(UUID orderId) {
        try {
            jdbi.withHandle(handle ->
                    handle.execute("DELETE FROM at_watched_auctions WHERE order_id = ?",
                            orderId.toString())
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to clear watched auctions for order " + orderId, e);
        }
    }

    /**
     * Remove all watch entries for a player (e.g., on order cancellation).
     */
    public void clearAllForPlayer(UUID playerUuid) {
        try {
            jdbi.withHandle(handle ->
                    handle.execute("DELETE FROM at_watched_auctions WHERE player_uuid = ?",
                            playerUuid.toString())
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to clear watched auctions for " + playerUuid, e);
        }
    }

    /**
     * Check whether a player is already watching an order.
     */
    public boolean isWatching(UUID playerUuid, UUID orderId) {
        try {
            return jdbi.withHandle(handle ->
                    handle.createQuery("""
                            SELECT 1 FROM at_watched_auctions
                            WHERE player_uuid = ? AND order_id = ?
                            """)
                            .bind(0, playerUuid.toString())
                            .bind(1, orderId.toString())
                            .map((rs, ctx) -> 1)
                            .findFirst()
                            .isPresent()
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to check watched status for " + orderId + " / " + playerUuid, e);
            return false;
        }
    }
}
