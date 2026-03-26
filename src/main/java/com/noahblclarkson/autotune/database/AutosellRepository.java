package com.noahblclarkson.autotune.database;

import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AutosellRepository {

    private final Jdbi jdbi;

    public AutosellRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    public Set<Integer> getEnabledItems(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                new HashSet<>(handle.createQuery("""
                                SELECT item_id FROM at_autosell_items
                                WHERE player_uuid = :playerUuid AND enabled = TRUE
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .list()));
    }

    public boolean isItemEnabled(UUID playerUuid, int itemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*) > 0 FROM at_autosell_items
                                WHERE player_uuid = :playerUuid AND item_id = :itemId AND enabled = TRUE
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .mapTo(Boolean.class)
                        .one());
    }

    public void setItemEnabled(UUID playerUuid, int itemId, boolean enabled) {
        jdbi.useHandle(handle -> {
            int updated = handle.createUpdate("""
                            UPDATE at_autosell_items
                            SET enabled = :enabled
                            WHERE player_uuid = :playerUuid AND item_id = :itemId
                            """)
                    .bind("playerUuid", playerUuid.toString())
                    .bind("itemId", itemId)
                    .bind("enabled", enabled)
                    .execute();

            if (updated == 0) {
                handle.createUpdate("""
                                INSERT INTO at_autosell_items (player_uuid, item_id, enabled)
                                VALUES (:playerUuid, :itemId, :enabled)
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .bind("enabled", enabled)
                        .execute();
            }
        });
    }

    public void toggleItem(UUID playerUuid, int itemId) {
        jdbi.useHandle(handle -> {
            int updated = handle.createUpdate("""
                            UPDATE at_autosell_items
                            SET enabled = NOT enabled
                            WHERE player_uuid = :playerUuid AND item_id = :itemId
                            """)
                    .bind("playerUuid", playerUuid.toString())
                    .bind("itemId", itemId)
                    .execute();

            if (updated == 0) {
                handle.createUpdate("""
                                INSERT INTO at_autosell_items (player_uuid, item_id, enabled)
                                VALUES (:playerUuid, :itemId, TRUE)
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .execute();
            }
        });
    }

    public void enableAllItems(UUID playerUuid, Set<Integer> itemIds) {
        jdbi.useTransaction(handle -> {
            for (int itemId : itemIds) {
                int updated = handle.createUpdate("""
                                UPDATE at_autosell_items
                                SET enabled = TRUE
                                WHERE player_uuid = :playerUuid AND item_id = :itemId
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .execute();

                if (updated == 0) {
                    handle.createUpdate("""
                                    INSERT INTO at_autosell_items (player_uuid, item_id, enabled)
                                    VALUES (:playerUuid, :itemId, TRUE)
                                    """)
                            .bind("playerUuid", playerUuid.toString())
                            .bind("itemId", itemId)
                            .execute();
                }
            }
        });
    }

    public void disableAllItems(UUID playerUuid) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_autosell_items
                                SET enabled = FALSE
                                WHERE player_uuid = :playerUuid
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .execute());
    }

    public int countEnabledItems(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*) FROM at_autosell_items
                                WHERE player_uuid = :playerUuid AND enabled = TRUE
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .one());
    }

    /**
     * Get the per-item minimum price threshold for a player.
     * Returns empty if no custom threshold is set.
     */
    public Optional<BigDecimal> getMinPrice(UUID playerUuid, int itemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT min_price FROM at_autosell_items
                                WHERE player_uuid = :playerUuid AND item_id = :itemId
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .mapTo(BigDecimal.class)
                        .findOne());
    }

    /**
     * Set the per-item minimum price threshold for a player.
     * Setting to null removes the custom threshold (player will use global config min).
     */
    public void setMinPrice(UUID playerUuid, int itemId, BigDecimal minPrice) {
        jdbi.useHandle(handle -> {
            int updated = handle.createUpdate("""
                            UPDATE at_autosell_items
                            SET min_price = :minPrice
                            WHERE player_uuid = :playerUuid AND item_id = :itemId
                            """)
                    .bind("playerUuid", playerUuid.toString())
                    .bind("itemId", itemId)
                    .bind("minPrice", minPrice)
                    .execute();

            if (updated == 0) {
                // Row doesn't exist yet — create it with the min_price set
                handle.createUpdate("""
                                INSERT INTO at_autosell_items (player_uuid, item_id, enabled, min_price)
                                VALUES (:playerUuid, :itemId, TRUE, :minPrice)
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .bind("minPrice", minPrice)
                        .execute();
            }
        });
    }

    /**
     * Remove the per-item minimum price threshold (reverts to global config).
     */
    public void removeMinPrice(UUID playerUuid, int itemId) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_autosell_items
                                SET min_price = NULL
                                WHERE player_uuid = :playerUuid AND item_id = :itemId
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .execute());
    }
}
