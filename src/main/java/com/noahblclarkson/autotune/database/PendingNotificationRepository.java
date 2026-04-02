package com.noahblclarkson.autotune.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for pending (offline) player notifications.
 *
 * When a player is offline and an event fires (e.g., a price alert), the notification
 * is stored here and delivered on their next login.
 */
@Singleton
public class PendingNotificationRepository {

    private static final Logger LOGGER = Logger.getLogger(PendingNotificationRepository.class.getName());

    private final DatabaseManager databaseManager;

    @Inject
    public PendingNotificationRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Store a pending notification for an offline player.
     *
     * @param playerUuid  the player's UUID
     * @param message     the plain-text message to deliver
     * @param category    category tag (e.g. "PRICE_ALERT", "ECONOMY_NEWS")
     */
    public void insert(UUID playerUuid, String message, String category) {
        databaseManager.runAsync(() -> {
            try {
                Jdbi jdbi = databaseManager.getJdbi();
                jdbi.useHandle(handle ->
                        handle.execute(
                                "INSERT INTO at_pending_notifications (player_uuid, message, category) VALUES (?, ?, ?)",
                                playerUuid.toString(), message, category
                        )
                );
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to insert pending notification for " + playerUuid, e);
            }
        });
    }

    /**
     * Fetch all pending notifications for a player, ordered oldest-first.
     * Returns plain-text messages only.
     */
    public List<PendingNotification> fetchAndClear(UUID playerUuid) {
        try {
            Jdbi jdbi = databaseManager.getJdbi();
            return jdbi.withHandle(handle -> {
                List<PendingNotification> rows = handle.createQuery(
                                "SELECT id, message, category, created_at FROM at_pending_notifications " +
                                        "WHERE player_uuid = ? ORDER BY created_at ASC")
                        .bind(0, playerUuid.toString())
                        .map((rs, ctx) -> new PendingNotification(
                                rs.getInt("id"),
                                rs.getString("message"),
                                rs.getString("category")
                        ))
                        .list();

                if (!rows.isEmpty()) {
                    handle.execute("DELETE FROM at_pending_notifications WHERE player_uuid = ?",
                            playerUuid.toString());
                }
                return rows;
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch pending notifications for " + playerUuid, e);
            return List.of();
        }
    }

    /** Simple DTO for a pending notification row. */
    public record PendingNotification(int id, String message, String category) {}
}
