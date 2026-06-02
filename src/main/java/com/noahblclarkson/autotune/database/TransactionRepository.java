package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.Transaction;
import com.noahblclarkson.autotune.model.Transaction.TransactionType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("PMD")
public class TransactionRepository {

    private final Jdbi jdbi;

    public TransactionRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
    }

    public void insert(@NotNull Transaction transaction) {
        jdbi.useHandle(handle -> insert(handle, transaction));
    }

    public void insert(@NotNull Handle handle, @NotNull Transaction transaction) {
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
                .execute();
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

    public List<Transaction> findByPlayerSince(UUID playerUuid, Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, item_id, transaction_type, amount,
                                       price_per_unit, total_price, timestamp
                                FROM at_transactions
                                WHERE player_uuid = :playerUuid AND timestamp >= :since
                                ORDER BY timestamp ASC
                                """)
                        .bind("playerUuid", playerUuid.toString())
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

    public List<Transaction> findByPlayerRange(UUID playerUuid, Instant from, Instant to, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                SELECT id, player_uuid, item_id, transaction_type, amount,
                                       price_per_unit, total_price, timestamp
                                FROM at_transactions
                                WHERE player_uuid = :playerUuid
                                  AND timestamp >= :from
                                  AND timestamp <= :to
                                ORDER BY timestamp ASC
                                LIMIT :limit
                                """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("from", Timestamp.from(from))
                        .bind("to", Timestamp.from(to))
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

    /**
     * Delete transactions older than the given cutoff.
     * Returns the number of rows deleted.
     */
    public int deleteOlderThan(Instant cutoff) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("DELETE FROM at_transactions WHERE timestamp < :cutoff")
                        .bind("cutoff", Timestamp.from(cutoff))
                        .execute());
    }

    public long count() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM at_transactions")
                        .mapTo(Long.class)
                        .one());
    }

    /**
     * Returns the number of distinct items a player has traded.
     */
    public int countDistinctItemsTraded(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(DISTINCT item_id) FROM at_transactions
                        WHERE player_uuid = :playerUuid
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .findOne()
                        .orElse(0));
    }

    /**
     * Returns the number of distinct item sections a player has traded in.
     * Joins transactions with items to get section information.
     */
    public int countDistinctSectionsTraded(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(DISTINCT i.section) FROM at_transactions t
                        JOIN at_items i ON t.item_id = i.id
                        WHERE t.player_uuid = :playerUuid AND i.section IS NOT NULL
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .mapTo(Integer.class)
                        .findOne()
                        .orElse(0));
    }

    /**
     * Aggregates transaction history for the given time period.
     * Used for leaderboard period filtering (day / week / month).
     * Returns aggregated transaction counts and volumes per player,
     * ranked by total volume (buys + sells) descending.
     *
     * @param period "day" (24h), "week" (7d), "month" (30d), or "all" (returns empty list)
     * @param limit  max rows to return
     * @return list of period aggregates, never null
     */
    public List<TransactionPeriodAggregate> findTopTradersByPeriod(String period, int limit) {
        return jdbi.withHandle(handle -> {
            String since = switch (period) {
                case "day" -> "datetime('now', '-1 day')";
                case "week" -> "datetime('now', '-7 days')";
                case "month" -> "datetime('now', '-30 days')";
                default -> null;
            };

            if (since == null) {
                return List.of();
            }

            String sql = """
                SELECT
                    t.player_uuid,
                    COALESCE(p.username, SUBSTR(t.player_uuid, 1, 8)) AS username,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'BUY' THEN t.amount ELSE 0 END), 0) AS total_bought,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'SELL' THEN t.amount ELSE 0 END), 0) AS total_sold,
                    COUNT(*) AS transaction_count,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'BUY' THEN t.total_price ELSE 0 END), 0) AS total_spent,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'SELL' THEN t.total_price ELSE 0 END), 0) AS total_earned
                FROM at_transactions t
                LEFT JOIN at_players p ON t.player_uuid = p.uuid
                WHERE t.timestamp >= """ + since + """
                GROUP BY t.player_uuid
                ORDER BY (total_bought + total_sold) DESC
                LIMIT :limit
                """;

            return handle.createQuery(sql)
                .bind("limit", limit)
                .map((rs, ctx) -> new TransactionPeriodAggregate(
                    rs.getString("player_uuid"),
                    rs.getString("username"),
                    rs.getLong("total_bought"),
                    rs.getLong("total_sold"),
                    rs.getLong("transaction_count"),
                    rs.getBigDecimal("total_spent"),
                    rs.getBigDecimal("total_earned")
                ))
                .list();
        });
    }

    /**
     * Returns aggregated transaction stats for a single player in a given time period.
     * Returns null if the player has no transactions in that period.
     *
     * @param period "day" (24h), "week" (7d), "month" (30d), or "all" (returns null)
     * @param uuid   the player UUID
     * @return period aggregate for the player, or null if none
     */
    public TransactionPeriodAggregate findPlayerPeriodStats(String period, UUID uuid) {
        return jdbi.withHandle(handle -> {
            String since = switch (period) {
                case "day" -> "datetime('now', '-1 day')";
                case "week" -> "datetime('now', '-7 days')";
                case "month" -> "datetime('now', '-30 days')";
                default -> null;
            };

            if (since == null) {
                return null;
            }

            String sql = """
                SELECT
                    t.player_uuid,
                    COALESCE(p.username, SUBSTR(t.player_uuid, 1, 8)) AS username,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'BUY' THEN t.amount ELSE 0 END), 0) AS total_bought,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'SELL' THEN t.amount ELSE 0 END), 0) AS total_sold,
                    COUNT(*) AS transaction_count,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'BUY' THEN t.total_price ELSE 0 END), 0) AS total_spent,
                    COALESCE(SUM(CASE WHEN t.transaction_type = 'SELL' THEN t.total_price ELSE 0 END), 0) AS total_earned
                FROM at_transactions t
                LEFT JOIN at_players p ON t.player_uuid = p.uuid
                WHERE t.timestamp >= """ + since + """
                  AND t.player_uuid = :uuid
                GROUP BY t.player_uuid
                LIMIT 1
                """;

            return handle.createQuery(sql)
                .bind("uuid", uuid.toString())
                .map((rs, ctx) -> new TransactionPeriodAggregate(
                    rs.getString("player_uuid"),
                    rs.getString("username"),
                    rs.getLong("total_bought"),
                    rs.getLong("total_sold"),
                    rs.getLong("transaction_count"),
                    rs.getBigDecimal("total_spent"),
                    rs.getBigDecimal("total_earned")
                ))
                .findFirst()
                .orElse(null);
        });
    }

    public record TransactionPeriodAggregate(
        String playerUuid,
        String username,
        long totalBought,
        long totalSold,
        long transactionCount,
        BigDecimal totalSpent,
        BigDecimal totalEarned
    ) {}

    /**
     * Returns per-item volume totals for a player within the given time window.
     * Used for the market impact calculation.
     *
     * @param uuid  player UUID
     * @param since start of the time window
     * @return list of item volumes, never null
     */
    public List<ItemVolume> findPlayerItemVolumesSince(UUID uuid, Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT item_id,
                               SUM(amount)            AS total_amount,
                               SUM(total_price)        AS total_value
                        FROM   at_transactions
                        WHERE  player_uuid   = :uuid
                          AND  timestamp     >= :since
                        GROUP BY item_id
                        ORDER BY total_value DESC
                        """)
                        .bind("uuid", uuid.toString())
                        .bind("since", Timestamp.from(since))
                        .map((rs, ctx) -> new ItemVolume(
                                rs.getInt("item_id"),
                                rs.getLong("total_amount"),
                                rs.getBigDecimal("total_value")
                        ))
                        .list());
    }

    /**
     * Returns per-item volume totals for the entire market within the given time window.
     * Used for the market impact calculation.
     *
     * @param since start of the time window
     * @return list of item volumes, never null
     */
    public List<ItemVolume> findGlobalItemVolumesSince(Instant since) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT item_id,
                               SUM(amount)     AS total_amount,
                               SUM(total_price) AS total_value
                        FROM   at_transactions
                        WHERE  timestamp >= :since
                        GROUP BY item_id
                        """)
                        .bind("since", Timestamp.from(since))
                        .map((rs, ctx) -> new ItemVolume(
                                rs.getInt("item_id"),
                                rs.getLong("total_amount"),
                                rs.getBigDecimal("total_value")
                        ))
                        .list());
    }

    /**
     * Returns all distinct player UUIDs who traded in the given time window,
     * ordered by their total trading value descending.
     * Used for the weekly market impact rank.
     *
     * @param since start of the time window
     * @param limit max number of players to return
     * @return list of player-impact pairs, never null
     */
    public List<PlayerImpact> findTopPlayersByImpact(Instant since, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT player_uuid, SUM(total_price) AS total_value
                        FROM   at_transactions
                        WHERE  timestamp >= :since
                        GROUP BY player_uuid
                        ORDER BY total_value DESC
                        LIMIT  :limit
                        """)
                        .bind("since", Timestamp.from(since))
                        .bind("limit", limit)
                        .map((rs, ctx) -> new PlayerImpact(
                                rs.getString("player_uuid"),
                                rs.getBigDecimal("total_value")
                        ))
                        .list());
    }

    public record ItemVolume(int itemId, long amount, BigDecimal value) {}

    public record PlayerImpact(String playerUuid, BigDecimal totalValue) {}

    /**
     * Returns all transactions for the top traders in a given period, grouped by player.
     * Used for computing realized P&L via average-cost FIFO accounting.
     *
     * @param period "day" (24h), "week" (7d), "month" (30d)
     * @param limit  max number of players to consider
     * @return map of playerUuid → list of transactions (sorted by timestamp ASC per player)
     */
    public java.util.Map<String, List<Transaction>> findTransactionsForTopTraders(String period, int limit) {
        String since = switch (period) {
            case "day" -> "datetime('now', '-1 day')";
            case "week" -> "datetime('now', '-7 days')";
            case "month" -> "datetime('now', '-30 days')";
            default -> "datetime('now', '-100 years')";
        };

        // Get top trader UUIDs first — use regular strings to avoid text-block/single-quote conflict
        String uuidSql = "SELECT DISTINCT player_uuid FROM at_transactions WHERE timestamp >= "
                + since
                + " ORDER BY (SELECT SUM(total_price) FROM at_transactions t2 WHERE t2.player_uuid = at_transactions.player_uuid AND t2.timestamp >= "
                + since + ") DESC LIMIT :limit";

        List<String> topUuids = jdbi.withHandle(handle ->
                handle.createQuery(uuidSql)
                        .bind("limit", limit)
                        .mapTo(String.class)
                        .list());

        if (topUuids.isEmpty()) {
            return java.util.Map.of();
        }

        // Bind UUIDs as a list for IN clause
        java.util.List<java.util.UUID> uuidList = topUuids.stream()
                .map(UUID::fromString)
                .toList();

        String txSql = "SELECT id, player_uuid, item_id, transaction_type, amount, "
                + "price_per_unit, total_price, timestamp FROM at_transactions "
                + "WHERE player_uuid IN (<uuids>) AND timestamp >= " + since
                + " ORDER BY player_uuid, timestamp ASC";

        return jdbi.withHandle(handle ->
                handle.createQuery(txSql)
                        .bindList("uuids", uuidList)
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
                        .list())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.playerUuid().toString(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }
}
