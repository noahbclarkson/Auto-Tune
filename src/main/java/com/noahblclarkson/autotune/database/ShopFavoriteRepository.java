package com.noahblclarkson.autotune.database;

import org.jdbi.v3.core.Jdbi;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("PMD")
public class ShopFavoriteRepository {

    private final Jdbi jdbi;
    private final boolean sqlite;

    public ShopFavoriteRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
        this.sqlite = databaseManager.isSqlite();
    }

    public Set<Integer> getFavoriteItemIds(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                new HashSet<>(handle.createQuery("""
                                SELECT item_id FROM at_shop_favorites
                                WHERE player_uuid = :playerUuid
                                ORDER BY created_at DESC
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .list()));
    }

    public boolean isFavorite(UUID playerUuid, int itemId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*) > 0 FROM at_shop_favorites
                                WHERE player_uuid = :playerUuid AND item_id = :itemId
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .mapTo(Boolean.class)
                        .one());
    }

    public void addFavorite(UUID playerUuid, int itemId) {
        String sql = sqlite
                ? """
                                INSERT OR IGNORE INTO at_shop_favorites (player_uuid, item_id)
                                VALUES (:playerUuid, :itemId)
                                """
                : """
                                INSERT IGNORE INTO at_shop_favorites (player_uuid, item_id)
                                VALUES (:playerUuid, :itemId)
                                """;
        jdbi.useHandle(handle ->
                handle.createUpdate(sql)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .execute());
    }

    public void removeFavorite(UUID playerUuid, int itemId) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                DELETE FROM at_shop_favorites
                                WHERE player_uuid = :playerUuid AND item_id = :itemId
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("itemId", itemId)
                        .execute());
    }

    public int countFavorites(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COUNT(*) FROM at_shop_favorites
                                WHERE player_uuid = :playerUuid
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .one());
    }
}
