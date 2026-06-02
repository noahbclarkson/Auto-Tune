package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.PriceAlert;
import com.noahblclarkson.autotune.model.PriceAlert.AlertType;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBI repository for price alert persistence.
 */
public class PriceAlertRepository {

    private final Jdbi jdbi;

    public PriceAlertRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    private static PriceAlert mapAlert(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        int itemId = rs.getInt("item_id");
        AlertType alertType = AlertType.valueOf(rs.getString("alert_type"));
        BigDecimal targetPrice = rs.getBigDecimal("target_price");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();
        Timestamp triggeredTs = rs.getTimestamp("triggered_at");
        Instant triggeredAt = rs.wasNull() ? null : triggeredTs.toInstant();
        boolean enabled = rs.getBoolean("enabled");

        return new PriceAlert(id, playerUuid, itemId, alertType, targetPrice, createdAt, triggeredAt, enabled);
    }

    /**
     * Insert a new alert.
     */
    public void insert(PriceAlert alert) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                        INSERT INTO at_price_alerts (id, player_uuid, item_id, alert_type, target_price, created_at, triggered_at, enabled)
                        VALUES (:id, :playerUuid, :itemId, :alertType, :targetPrice, :createdAt, :triggeredAt, :enabled)
                        """)
                        .bind("id", alert.id())
                        .bind("playerUuid", alert.playerUuid().toString())
                        .bind("itemId", alert.itemId())
                        .bind("alertType", alert.alertType().name())
                        .bind("targetPrice", alert.targetPrice())
                        .bind("createdAt", Timestamp.from(alert.createdAt()))
                        .bind("triggeredAt", alert.triggeredAt() != null ? Timestamp.from(alert.triggeredAt()) : null)
                        .bind("enabled", alert.enabled())
                        .execute()
        );
    }

    /**
     * Update an alert (used to mark triggered or toggle enabled state).
     */
    public void update(PriceAlert alert) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                        UPDATE at_price_alerts
                        SET alert_type = :alertType, target_price = :targetPrice,
                            triggered_at = :triggeredAt, enabled = :enabled
                        WHERE id = :id
                        """)
                        .bind("id", alert.id())
                        .bind("alertType", alert.alertType().name())
                        .bind("targetPrice", alert.targetPrice())
                        .bind("triggeredAt", alert.triggeredAt() != null ? Timestamp.from(alert.triggeredAt()) : null)
                        .bind("enabled", alert.enabled())
                        .execute()
        );
    }

    /**
     * Delete an alert by ID.
     */
    public void delete(String alertId) {
        jdbi.useHandle(handle ->
                handle.createUpdate("DELETE FROM at_price_alerts WHERE id = :id")
                        .bind("id", alertId)
                        .execute()
        );
    }

    /**
     * Get a single alert by ID.
     */
    public Optional<PriceAlert> findById(String alertId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM at_price_alerts WHERE id = :id")
                        .bind("id", alertId)
                        .map((rs, ctx) -> mapAlert(rs))
                        .findFirst()
        );
    }

    /**
     * Get all active (enabled, not yet triggered) alerts.
     */
    public List<PriceAlert> findActiveAlerts() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT * FROM at_price_alerts
                        WHERE enabled = TRUE AND triggered_at IS NULL
                        """)
                        .map((rs, ctx) -> mapAlert(rs))
                        .list()
        );
    }

    /**
     * Get all alerts for a player, ordered by creation date descending.
     */
    public List<PriceAlert> findByPlayer(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT * FROM at_price_alerts
                        WHERE player_uuid = :playerUuid
                        ORDER BY created_at DESC
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .map((rs, ctx) -> mapAlert(rs))
                        .list()
        );
    }

    /**
     * Get active alerts for a specific item. Used during market tick to check
     * which alerts are relevant for a price change.
     */
    public List<PriceAlert> findActiveByItem(int itemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT * FROM at_price_alerts
                        WHERE item_id = :itemId AND enabled = TRUE AND triggered_at IS NULL
                        """)
                        .bind("itemId", itemId)
                        .map((rs, ctx) -> mapAlert(rs))
                        .list()
        );
    }

    /**
     * Count how many active alerts a player has.
     */
    public int countActiveByPlayer(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(*) FROM at_price_alerts
                        WHERE player_uuid = :playerUuid AND enabled = TRUE AND triggered_at IS NULL
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .one()
        );
    }
}
