package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.PlayerData;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerRepository {

    private final Jdbi jdbi;

    public PlayerRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    public Optional<PlayerData> findByName(String name) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT uuid, username, COALESCE(guild_tag, '') as guild_tag,
                                       credit_score, total_traded, total_bought,
                                       total_sold, transaction_count, first_seen, last_seen
                                FROM at_players WHERE LOWER(username) = LOWER(:name)
                                """)
                        .bind("name", name)
                        .map((rs, ctx) -> PlayerData.builder()
                                .uuid(UUID.fromString(rs.getString("uuid")))
                                .username(rs.getString("username"))
                                .guildTag(rs.getString("guild_tag"))
                                .creditScore(rs.getInt("credit_score"))
                                .totalTraded(rs.getBigDecimal("total_traded"))
                                .totalBought(rs.getBigDecimal("total_bought"))
                                .totalSold(rs.getBigDecimal("total_sold"))
                                .transactionCount(rs.getInt("transaction_count"))
                                .firstSeen(rs.getTimestamp("first_seen").toInstant())
                                .lastSeen(rs.getTimestamp("last_seen").toInstant())
                                .build())
                        .findFirst());
    }

    public Optional<PlayerData> findByUuid(UUID uuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT uuid, username, COALESCE(guild_tag, '') as guild_tag,
                                       credit_score, total_traded, total_bought,
                                       total_sold, transaction_count, first_seen, last_seen
                                FROM at_players WHERE uuid = :uuid
                                """)
                        .bind("uuid", uuid.toString())
                        .map((rs, ctx) -> PlayerData.builder()
                                .uuid(UUID.fromString(rs.getString("uuid")))
                                .username(rs.getString("username"))
                                .guildTag(rs.getString("guild_tag"))
                                .creditScore(rs.getInt("credit_score"))
                                .totalTraded(rs.getBigDecimal("total_traded"))
                                .totalBought(rs.getBigDecimal("total_bought"))
                                .totalSold(rs.getBigDecimal("total_sold"))
                                .transactionCount(rs.getInt("transaction_count"))
                                .firstSeen(rs.getTimestamp("first_seen").toInstant())
                                .lastSeen(rs.getTimestamp("last_seen").toInstant())
                                .build())
                        .findFirst());
    }

    public PlayerData getOrCreate(UUID uuid, String username) {
        return findByUuid(uuid).orElseGet(() -> {
            PlayerData newPlayer = PlayerData.createNew(uuid, username);
            insert(newPlayer);
            return newPlayer;
        });
    }

    public void insert(@NotNull PlayerData player) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO at_players (uuid, username, guild_tag, credit_score, total_traded,
                                                        total_bought, total_sold, transaction_count,
                                                        first_seen, last_seen)
                                VALUES (:uuid, :username, :guildTag, :creditScore, :totalTraded,
                                        :totalBought, :totalSold, :transactionCount,
                                        :firstSeen, :lastSeen)
                                """)
                        .bind("uuid", player.uuid().toString())
                        .bind("username", player.username())
                        .bind("guildTag", player.guildTag())
                        .bind("creditScore", player.creditScore())
                        .bind("totalTraded", player.totalTraded())
                        .bind("totalBought", player.totalBought())
                        .bind("totalSold", player.totalSold())
                        .bind("transactionCount", player.transactionCount())
                        .bind("firstSeen", Timestamp.from(player.firstSeen()))
                        .bind("lastSeen", Timestamp.from(player.lastSeen()))
                        .execute());
    }

    public void update(@NotNull PlayerData player) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_players SET
                                    username = :username,
                                    guild_tag = :guildTag,
                                    credit_score = :creditScore,
                                    total_traded = :totalTraded,
                                    total_bought = :totalBought,
                                    total_sold = :totalSold,
                                    transaction_count = :transactionCount,
                                    last_seen = :lastSeen
                                WHERE uuid = :uuid
                                """)
                        .bind("uuid", player.uuid().toString())
                        .bind("username", player.username())
                        .bind("guildTag", player.guildTag())
                        .bind("creditScore", player.creditScore())
                        .bind("totalTraded", player.totalTraded())
                        .bind("totalBought", player.totalBought())
                        .bind("totalSold", player.totalSold())
                        .bind("transactionCount", player.transactionCount())
                        .bind("lastSeen", Timestamp.from(player.lastSeen()))
                        .execute());
    }

    public void updateGuildTag(UUID uuid, String guildTag) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_players SET guild_tag = :guildTag, last_seen = :lastSeen
                                WHERE uuid = :uuid
                                """)
                        .bind("uuid", uuid.toString())
                        .bind("guildTag", guildTag)
                        .bind("lastSeen", Timestamp.from(Instant.now()))
                        .execute());
    }

    public List<PlayerData> findByGuildTag(String guildTag) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT uuid, username, COALESCE(guild_tag, '') as guild_tag,
                                       credit_score, total_traded, total_bought,
                                       total_sold, transaction_count, first_seen, last_seen
                                FROM at_players WHERE guild_tag = :guildTag
                                """)
                        .bind("guildTag", guildTag)
                        .map((rs, ctx) -> PlayerData.builder()
                                .uuid(UUID.fromString(rs.getString("uuid")))
                                .username(rs.getString("username"))
                                .guildTag(rs.getString("guild_tag"))
                                .creditScore(rs.getInt("credit_score"))
                                .totalTraded(rs.getBigDecimal("total_traded"))
                                .totalBought(rs.getBigDecimal("total_bought"))
                                .totalSold(rs.getBigDecimal("total_sold"))
                                .transactionCount(rs.getInt("transaction_count"))
                                .firstSeen(rs.getTimestamp("first_seen").toInstant())
                                .lastSeen(rs.getTimestamp("last_seen").toInstant())
                                .build())
                        .list());
    }

    public void updateCreditScore(UUID uuid, int newScore) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_players SET credit_score = :score, last_seen = :lastSeen
                                WHERE uuid = :uuid
                                """)
                        .bind("uuid", uuid.toString())
                        .bind("score", newScore)
                        .bind("lastSeen", Timestamp.from(Instant.now()))
                        .execute());
    }

    public void addTransaction(UUID uuid, BigDecimal amount, boolean isBuy) {
        String buyColumn = isBuy ? "total_bought = total_bought + :amount," : "";
        String sellColumn = !isBuy ? "total_sold = total_sold + :amount," : "";

        jdbi.useHandle(handle ->
                handle.createUpdate(String.format("""
                                UPDATE at_players SET
                                    total_traded = total_traded + :amount,
                                    %s
                                    %s
                                    transaction_count = transaction_count + 1,
                                    last_seen = :lastSeen
                                WHERE uuid = :uuid
                                """, buyColumn, sellColumn))
                        .bind("uuid", uuid.toString())
                        .bind("amount", amount.abs())
                        .bind("lastSeen", Timestamp.from(Instant.now()))
                        .execute());
    }

    public List<PlayerData> findTopTraders(int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT uuid, username, credit_score, total_traded, total_bought,
                                       total_sold, transaction_count, first_seen, last_seen
                                FROM at_players ORDER BY total_traded DESC LIMIT :limit
                                """)
                        .bind("limit", limit)
                        .map((rs, ctx) -> PlayerData.builder()
                                .uuid(UUID.fromString(rs.getString("uuid")))
                                .username(rs.getString("username"))
                                .creditScore(rs.getInt("credit_score"))
                                .totalTraded(rs.getBigDecimal("total_traded"))
                                .totalBought(rs.getBigDecimal("total_bought"))
                                .totalSold(rs.getBigDecimal("total_sold"))
                                .transactionCount(rs.getInt("transaction_count"))
                                .firstSeen(rs.getTimestamp("first_seen").toInstant())
                                .lastSeen(rs.getTimestamp("last_seen").toInstant())
                                .build())
                        .list());
    }

    public void updateLastSeen(UUID uuid) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                UPDATE at_players SET last_seen = :lastSeen WHERE uuid = :uuid
                                """)
                        .bind("uuid", uuid.toString())
                        .bind("lastSeen", Timestamp.from(Instant.now()))
                        .execute());
    }
}
