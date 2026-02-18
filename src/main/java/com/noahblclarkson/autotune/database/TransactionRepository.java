package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.Transaction;
import com.noahblclarkson.autotune.model.Transaction.TransactionType;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TransactionRepository {

    private final Jdbi jdbi;

    public TransactionRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    public void insert(@NotNull Transaction transaction) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                                INSERT INTO at_transactions (player_uuid, item_id, transaction_type,
                                                             amount, price_per_unit, total_price, timestamp)
                                VALUES (:playerUuid, :itemId, :type, :amount, :pricePerUnit, :totalPrice, :timestamp)
                                """)
                        .bind("playerUuid", transaction.playerUuid().toString())
                        .bind("itemId", transaction.itemId())
                        .bind("type", transaction.type().name())
                        .bind("amount", transaction.amount())
                        .bind("pricePerUnit", transaction.pricePerUnit())
                        .bind("totalPrice", transaction.totalPrice())
                        .bind("timestamp", Timestamp.from(transaction.timestamp()))
                        .execute());
    }

    public List<Transaction> findByPlayer(UUID playerUuid, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, item_id, transaction_type, amount,
                                       price_per_unit, total_price, timestamp
                                FROM at_transactions
                                WHERE player_uuid = :playerUuid
                                ORDER BY timestamp DESC
                                LIMIT :limit
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("limit", limit)
                        .map((rs, ctx) -> Transaction.builder()
                                .id(rs.getLong("id"))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .itemId(rs.getInt("item_id"))
                                .type(TransactionType.valueOf(rs.getString("transaction_type")))
                                .amount(rs.getInt("amount"))
                                .pricePerUnit(rs.getBigDecimal("price_per_unit"))
                                .totalPrice(rs.getBigDecimal("total_price"))
                                .timestamp(rs.getTimestamp("timestamp").toInstant())
                                .build())
                        .list());
    }

    public List<Transaction> findByItem(int itemId, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, item_id, transaction_type, amount,
                                       price_per_unit, total_price, timestamp
                                FROM at_transactions
                                WHERE item_id = :itemId
                                ORDER BY timestamp DESC
                                LIMIT :limit
                                """)
                        .bind("itemId", itemId)
                        .bind("limit", limit)
                        .map((rs, ctx) -> Transaction.builder()
                                .id(rs.getLong("id"))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .itemId(rs.getInt("item_id"))
                                .type(TransactionType.valueOf(rs.getString("transaction_type")))
                                .amount(rs.getInt("amount"))
                                .pricePerUnit(rs.getBigDecimal("price_per_unit"))
                                .totalPrice(rs.getBigDecimal("total_price"))
                                .timestamp(rs.getTimestamp("timestamp").toInstant())
                                .build())
                        .list());
    }

    public List<Transaction> findByItemSince(int itemId, Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, item_id, transaction_type, amount,
                                       price_per_unit, total_price, timestamp
                                FROM at_transactions
                                WHERE item_id = :itemId AND timestamp >= :since
                                ORDER BY timestamp DESC
                                """)
                        .bind("itemId", itemId)
                        .bind("since", Timestamp.from(since))
                        .map((rs, ctx) -> Transaction.builder()
                                .id(rs.getLong("id"))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .itemId(rs.getInt("item_id"))
                                .type(TransactionType.valueOf(rs.getString("transaction_type")))
                                .amount(rs.getInt("amount"))
                                .pricePerUnit(rs.getBigDecimal("price_per_unit"))
                                .totalPrice(rs.getBigDecimal("total_price"))
                                .timestamp(rs.getTimestamp("timestamp").toInstant())
                                .build())
                        .list());
    }

    public List<Transaction> findRecent(int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, item_id, transaction_type, amount,
                                       price_per_unit, total_price, timestamp
                                FROM at_transactions
                                ORDER BY timestamp DESC
                                LIMIT :limit
                                """)
                        .bind("limit", limit)
                        .map((rs, ctx) -> Transaction.builder()
                                .id(rs.getLong("id"))
                                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                .itemId(rs.getInt("item_id"))
                                .type(TransactionType.valueOf(rs.getString("transaction_type")))
                                .amount(rs.getInt("amount"))
                                .pricePerUnit(rs.getBigDecimal("price_per_unit"))
                                .totalPrice(rs.getBigDecimal("total_price"))
                                .timestamp(rs.getTimestamp("timestamp").toInstant())
                                .build())
                        .list());
    }

    public List<Transaction> findByPlayerAndItem(UUID playerUuid, Integer itemId, int limit) {
        if (playerUuid != null && itemId != null) {
            return jdbi.withHandle(handle ->
                    handle.createQuery("""
                                    SELECT id, player_uuid, item_id, transaction_type, amount,
                                           price_per_unit, total_price, timestamp
                                    FROM at_transactions
                                    WHERE player_uuid = :playerUuid AND item_id = :itemId
                                    ORDER BY timestamp DESC
                                    LIMIT :limit
                                    """)
                            .bind("playerUuid", playerUuid.toString())
                            .bind("itemId", itemId)
                            .bind("limit", limit)
                            .map((rs, ctx) -> Transaction.builder()
                                    .id(rs.getLong("id"))
                                    .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                                    .itemId(rs.getInt("item_id"))
                                    .type(TransactionType.valueOf(rs.getString("transaction_type")))
                                    .amount(rs.getInt("amount"))
                                    .pricePerUnit(rs.getBigDecimal("price_per_unit"))
                                    .totalPrice(rs.getBigDecimal("total_price"))
                                    .timestamp(rs.getTimestamp("timestamp").toInstant())
                                    .build())
                            .list());
        } else if (playerUuid != null) {
            return findByPlayer(playerUuid, limit);
        } else if (itemId != null) {
            return findByItem(itemId, limit);
        }
        return findRecent(limit);
    }

    public BigDecimal getTotalVolumeForItem(int itemId, Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COALESCE(SUM(total_price), 0) FROM at_transactions
                                WHERE item_id = :itemId AND timestamp >= :since
                                """)
                        .bind("itemId", itemId)
                        .bind("since", Timestamp.from(since))
                        .mapTo(BigDecimal.class)
                        .one());
    }

    public int getTransactionCountForItem(int itemId, TransactionType type, Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COALESCE(SUM(amount), 0) FROM at_transactions
                                WHERE item_id = :itemId AND transaction_type = :type AND timestamp >= :since
                                """)
                        .bind("itemId", itemId)
                        .bind("type", type.name())
                        .bind("since", Timestamp.from(since))
                        .mapTo(Integer.class)
                        .one());
    }

    public BigDecimal getGlobalVolume(Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COALESCE(SUM(total_price), 0) FROM at_transactions
                                WHERE timestamp >= :since
                                """)
                        .bind("since", Timestamp.from(since))
                        .mapTo(BigDecimal.class)
                        .one());
    }

    public BigDecimal getGlobalBuyVolume(Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COALESCE(SUM(total_price), 0) FROM at_transactions
                                WHERE timestamp >= :since AND transaction_type = 'BUY'
                                """)
                        .bind("since", Timestamp.from(since))
                        .mapTo(BigDecimal.class)
                        .one());
    }

    public int getGlobalTransactionCount(Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COALESCE(COUNT(*), 0) FROM at_transactions
                                WHERE timestamp >= :since
                                """)
                        .bind("since", Timestamp.from(since))
                        .mapTo(Integer.class)
                        .one());
    }

    public long getGlobalTransactionAmountBetween(Instant from, Instant to) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT COALESCE(SUM(amount), 0) FROM at_transactions
                                WHERE timestamp >= :from AND timestamp < :to
                                """)
                        .bind("from", Timestamp.from(from))
                        .bind("to", Timestamp.from(to))
                        .mapTo(Long.class)
                        .one());
    }
}
