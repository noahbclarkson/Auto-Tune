package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.PriceOverride;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PriceOverrideRepository {

    private final Jdbi jdbi;

    public PriceOverrideRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    private static PriceOverride mapOverride(ResultSet rs) throws SQLException {
        int itemId = rs.getInt("item_id");
        BigDecimal price = rs.getBigDecimal("price");
        Timestamp expiresTs = rs.getTimestamp("expires_at");
        Instant expiresAt = rs.wasNull() ? null : expiresTs.toInstant();

        String setByStr = rs.getString("set_by");
        UUID setBy = (setByStr != null && !rs.wasNull()) ? UUID.fromString(setByStr) : null;

        Timestamp setAtTs = rs.getTimestamp("set_at");
        Instant setAt = setAtTs.toInstant();

        return new PriceOverride(itemId, price, expiresAt, setBy, setAt);
    }

    /**
     * Returns all non-expired price overrides, cached by item ID.
     * Call this on startup and after any modification.
     */
    public Map<Integer, PriceOverride> getActiveOverrides() {
        Map<Integer, PriceOverride> overrides = new HashMap<>();
        jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT item_id, price, expires_at, set_by, set_at
                                FROM at_price_overrides
                                WHERE expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP
                                """)
                        .map((rs, ctx) -> {
                            PriceOverride o = mapOverride(rs);
                            overrides.put(o.itemId(), o);
                            return o;
                        })
                        .list()
        );
        return overrides;
    }

    /**
     * Get a single active override for an item.
     */
    public Optional<PriceOverride> getActiveOverride(int itemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT item_id, price, expires_at, set_by, set_at
                                FROM at_price_overrides
                                WHERE item_id = :itemId
                                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                                """)
                        .bind("itemId", itemId)
                        .map((rs, ctx) -> mapOverride(rs))
                        .findFirst());
    }

    /**
     * Set or replace a price override for an item.
     */
    public void setOverride(int itemId, BigDecimal price, @Nullable Instant expiresAt, @Nullable UUID setBy) {
        jdbi.useHandle(handle -> {
            int updated = handle.createUpdate("""
                            UPDATE at_price_overrides
                            SET price = :price, expires_at = :expiresAt, set_by = :setBy, set_at = :setAt
                            WHERE item_id = :itemId
                            """)
                    .bind("itemId", itemId)
                    .bind("price", price)
                    .bind("expiresAt", expiresAt != null ? Timestamp.from(expiresAt) : null)
                    .bind("setBy", setBy != null ? setBy.toString() : null)
                    .bind("setAt", Timestamp.from(Instant.now()))
                    .execute();

            if (updated == 0) {
                handle.createUpdate("""
                                INSERT INTO at_price_overrides (item_id, price, expires_at, set_by, set_at)
                                VALUES (:itemId, :price, :expiresAt, :setBy, :setAt)
                                """)
                        .bind("itemId", itemId)
                        .bind("price", price)
                        .bind("expiresAt", expiresAt != null ? Timestamp.from(expiresAt) : null)
                        .bind("setBy", setBy != null ? setBy.toString() : null)
                        .bind("setAt", Timestamp.from(Instant.now()))
                        .execute();
            }
        });
    }

    /**
     * Remove the price override for an item.
     */
    public void removeOverride(int itemId) {
        jdbi.useHandle(handle ->
                handle.createUpdate("DELETE FROM at_price_overrides WHERE item_id = :itemId")
                        .bind("itemId", itemId)
                        .execute());
    }

    /**
     * Remove all expired overrides. Call periodically.
     */
    public int deleteExpired() {
        return jdbi.withHandle(handle ->
                handle.createUpdate("DELETE FROM at_price_overrides WHERE expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP")
                        .execute());
    }

    /**
     * List all overrides including expired ones (for admin display).
     */
    public Map<Integer, PriceOverride> getAllOverrides() {
        Map<Integer, PriceOverride> overrides = new HashMap<>();
        jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT item_id, price, expires_at, set_by, set_at
                                FROM at_price_overrides
                                ORDER BY set_at DESC
                                """)
                        .map((rs, ctx) -> {
                            PriceOverride o = mapOverride(rs);
                            overrides.put(o.itemId(), o);
                            return o;
                        })
                        .list()
        );
        return overrides;
    }
}
