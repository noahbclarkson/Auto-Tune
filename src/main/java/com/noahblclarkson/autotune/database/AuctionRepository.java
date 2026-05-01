package com.noahblclarkson.autotune.database;

import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("PMD")
public class AuctionRepository {

    private final Jdbi jdbi;
    private final boolean sqlite;

    public AuctionRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
        this.sqlite = databaseManager.isSqlite();
    }

    public Optional<AuctionOrder> findById(UUID id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, player_uuid, material, item_data, price,
                               original_quantity, remaining_quantity, side, status,
                               created_at, filled_at, expires_at
                        FROM at_auction_orders WHERE id = :id
                        """)
                        .bind("id", id.toString())
                        .map((rs, ctx) -> mapOrder(rs))
                        .findFirst());
    }

    /**
     * Returns top-N active orders for a material grouped by side, sorted by price.
     * BUY orders: highest price first → best bid at index 0.
     * SELL orders: lowest price first → best ask at index 0.
     * Used for auction depth-chart rendering.
     */
    public Map<String, List<AuctionOrder>> findDepthByMaterial(String material, int depth) {
        return jdbi.withHandle(handle -> {
            Timestamp now = Timestamp.from(Instant.now());
            List<AuctionOrder> buyOrders = handle.createQuery("""
                    SELECT id, player_uuid, material, item_data, price,
                           original_quantity, remaining_quantity, side, status,
                           created_at, filled_at, expires_at
                    FROM at_auction_orders
                    WHERE material = :material
                      AND side = 'BUY'
                      AND status IN ('OPEN', 'PARTIALLY_FILLED')
                      AND remaining_quantity > 0
                      AND expires_at > :now
                    ORDER BY price DESC
                    LIMIT :limit
                    """)
                    .bind("material", material)
                    .bind("limit", depth)
                    .bind("now", now)
                    .map((rs, ctx) -> mapOrder(rs))
                    .list();
            List<AuctionOrder> sellOrders = handle.createQuery("""
                    SELECT id, player_uuid, material, item_data, price,
                           original_quantity, remaining_quantity, side, status,
                           created_at, filled_at, expires_at
                    FROM at_auction_orders
                    WHERE material = :material
                      AND side = 'SELL'
                      AND status IN ('OPEN', 'PARTIALLY_FILLED')
                      AND remaining_quantity > 0
                      AND expires_at > :now
                    ORDER BY price ASC
                    LIMIT :limit
                    """)
                    .bind("material", material)
                    .bind("limit", depth)
                    .bind("now", now)
                    .map((rs, ctx) -> mapOrder(rs))
                    .list();
            Map<String, List<AuctionOrder>> result = new java.util.HashMap<>();
            result.put("bids", buyOrders);
            result.put("asks", sellOrders);
            return result;
        });
    }

    public List<AuctionOrder> findActiveByMaterial(String material) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, player_uuid, material, item_data, price,
                               original_quantity, remaining_quantity, side, status,
                               created_at, filled_at, expires_at
                        FROM at_auction_orders
                        WHERE material = :material
                          AND status IN ('OPEN', 'PARTIALLY_FILLED')
                          AND remaining_quantity > 0
                          AND expires_at > :now
                        ORDER BY side ASC, price DESC, created_at ASC
                        """)
                        .bind("material", material)
                        .bind("now", Timestamp.from(Instant.now()))
                        .map((rs, ctx) -> mapOrder(rs))
                        .list());
    }

    public List<AuctionOrder> findActiveByPlayer(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, player_uuid, material, item_data, price,
                               original_quantity, remaining_quantity, side, status,
                               created_at, filled_at, expires_at
                        FROM at_auction_orders
                        WHERE player_uuid = :playerUuid
                          AND status IN ('OPEN', 'PARTIALLY_FILLED')
                          AND remaining_quantity > 0
                          AND expires_at > :now
                        ORDER BY created_at DESC
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("now", Timestamp.from(Instant.now()))
                        .map((rs, ctx) -> mapOrder(rs))
                        .list());
    }

    public List<AuctionOrder> findAllActive() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, player_uuid, material, item_data, price,
                               original_quantity, remaining_quantity, side, status,
                               created_at, filled_at, expires_at
                        FROM at_auction_orders
                        WHERE status IN ('OPEN', 'PARTIALLY_FILLED')
                          AND remaining_quantity > 0
                          AND expires_at > :now
                        ORDER BY created_at DESC
                        LIMIT 200
                        """)
                        .bind("now", Timestamp.from(Instant.now()))
                        .map((rs, ctx) -> mapOrder(rs))
                        .list());
    }

    public List<AuctionOrder> findByPlayerHistory(UUID playerUuid, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, player_uuid, material, item_data, price,
                               original_quantity, remaining_quantity, side, status,
                               created_at, filled_at, expires_at
                        FROM at_auction_orders
                        WHERE player_uuid = :playerUuid
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .bind("limit", limit)
                        .map((rs, ctx) -> mapOrder(rs))
                        .list());
    }

    /**
     * Find all active orders that have passed their expiration time.
     * These orders should be cancelled and their owners refunded/notified.
     */
    public List<AuctionOrder> findExpiredOrders() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, player_uuid, material, item_data, price,
                               original_quantity, remaining_quantity, side, status,
                               created_at, filled_at, expires_at
                        FROM at_auction_orders
                        WHERE status IN ('OPEN', 'PARTIALLY_FILLED')
                          AND remaining_quantity > 0
                          AND expires_at <= :now
                        ORDER BY expires_at ASC
                        """)
                        .bind("now", Timestamp.from(Instant.now()))
                        .map((rs, ctx) -> mapOrder(rs))
                        .list());
    }

    /**
     * Find expired sell orders for a specific player.
     * Used by /auction reclaim to return items to players who were offline when orders expired.
     */
    public List<AuctionOrder> findExpiredSellOrdersByPlayer(UUID playerUuid) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, player_uuid, material, item_data, price,
                               original_quantity, remaining_quantity, side, status,
                               created_at, filled_at, expires_at
                        FROM at_auction_orders
                        WHERE player_uuid = :playerUuid
                          AND side = 'SELL'
                          AND status = 'EXPIRED'
                          AND remaining_quantity > 0
                        ORDER BY expires_at ASC
                        """)
                        .bind("playerUuid", playerUuid.toString())
                        .map((rs, ctx) -> mapOrder(rs))
                        .list());
    }

    public List<AuctionFill> findFillsByOrder(UUID orderId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, buy_order_id, sell_order_id, quantity, price, filled_at
                        FROM at_auction_fills
                        WHERE buy_order_id = :id OR sell_order_id = :id
                        ORDER BY filled_at DESC
                        """)
                        .bind("id", orderId.toString())
                        .map((rs, ctx) -> AuctionFill.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .buyOrderId(UUID.fromString(rs.getString("buy_order_id")))
                                .sellOrderId(UUID.fromString(rs.getString("sell_order_id")))
                                .quantity(rs.getInt("quantity"))
                                .price(rs.getBigDecimal("price"))
                                .filledAt(rs.getTimestamp("filled_at").toInstant())
                                .build())
                        .list());
    }

    public List<AuctionFill> findRecentFills(int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT id, buy_order_id, sell_order_id, quantity, price, filled_at
                        FROM at_auction_fills
                        ORDER BY filled_at DESC
                        LIMIT :limit
                        """)
                        .bind("limit", limit)
                        .map((rs, ctx) -> AuctionFill.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .buyOrderId(UUID.fromString(rs.getString("buy_order_id")))
                                .sellOrderId(UUID.fromString(rs.getString("sell_order_id")))
                                .quantity(rs.getInt("quantity"))
                                .price(rs.getBigDecimal("price"))
                                .filledAt(rs.getTimestamp("filled_at").toInstant())
                                .build())
                        .list());
    }

    public List<AuctionFill> findRecentFillsByMaterial(String material, int limit) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                        SELECT af.id, af.buy_order_id, af.sell_order_id, af.quantity, af.price, af.filled_at
                        FROM at_auction_fills af
                        JOIN at_auction_orders ao ON ao.id = af.sell_order_id
                        WHERE ao.material = :material
                        ORDER BY af.filled_at DESC
                        LIMIT :limit
                        """)
                        .bind("material", material)
                        .bind("limit", limit)
                        .map((rs, ctx) -> AuctionFill.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .buyOrderId(UUID.fromString(rs.getString("buy_order_id")))
                                .sellOrderId(UUID.fromString(rs.getString("sell_order_id")))
                                .quantity(rs.getInt("quantity"))
                                .price(rs.getBigDecimal("price"))
                                .filledAt(rs.getTimestamp("filled_at").toInstant())
                                .build())
                        .list());
    }

    public void insert(@NotNull AuctionOrder order) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                        INSERT INTO at_auction_orders
                          (id, player_uuid, material, item_data, price,
                           original_quantity, remaining_quantity, side, status,
                           created_at, filled_at, expires_at)
                        VALUES
                          (:id, :playerUuid, :material, :itemData, :price,
                           :originalQty, :remainingQty, :side, :status,
                           :createdAt, :filledAt, :expiresAt)
                        """)
                        .bind("id", order.id().toString())
                        .bind("playerUuid", order.playerUuid().toString())
                        .bind("material", order.material())
                        .bind("itemData", order.itemData())
                        .bind("price", order.price())
                        .bind("originalQty", order.originalQuantity())
                        .bind("remainingQty", order.remainingQuantity())
                        .bind("side", order.side().name())
                        .bind("status", order.status().name())
                        .bind("createdAt", Timestamp.from(order.createdAt()))
                        .bind("filledAt", order.filledAt() != null ? Timestamp.from(order.filledAt()) : null)
                        .bind("expiresAt", Timestamp.from(order.expiresAt()))
                        .execute());
    }

    public void update(@NotNull AuctionOrder order) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                        UPDATE at_auction_orders SET
                            remaining_quantity = :remainingQty,
                            status = :status,
                            filled_at = :filledAt,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """)
                        .bind("id", order.id().toString())
                        .bind("remainingQty", order.remainingQuantity())
                        .bind("status", order.status().name())
                        .bind("filledAt", order.filledAt() != null ? Timestamp.from(order.filledAt()) : null)
                        .execute());
    }

    public void insertFill(@NotNull AuctionFill fill) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                        INSERT INTO at_auction_fills
                          (id, buy_order_id, sell_order_id, quantity, price, filled_at)
                        VALUES
                          (:id, :buyOrderId, :sellOrderId, :qty, :price, :filledAt)
                        """)
                        .bind("id", fill.id().toString())
                        .bind("buyOrderId", fill.buyOrderId().toString())
                        .bind("sellOrderId", fill.sellOrderId().toString())
                        .bind("qty", fill.quantity())
                        .bind("price", fill.price())
                        .bind("filledAt", Timestamp.from(fill.filledAt()))
                        .execute());
    }

    /**
     * Delete auction orders whose status is terminal (FILLED, EXPIRED, CANCELLED, RECLAIMED)
     * and whose last status update is older than the given cutoff.
     * Active OPEN/PARTIALLY_FILLED orders are never deleted.
     *
     * @param cutoff Orders updated before this instant are deleted.
     * @return Number of rows deleted.
     */
    public int deleteOrdersOlderThan(Instant cutoff) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                        DELETE FROM at_auction_orders
                        WHERE status IN ('FILLED', 'EXPIRED', 'CANCELLED', 'RECLAIMED')
                          AND updated_at < :cutoff
                        """)
                        .bind("cutoff", Timestamp.from(cutoff))
                        .execute());
    }

    /**
     * Delete auction fills whose fill timestamp is older than the given cutoff.
     *
     * @param cutoff Fills filled before this instant are deleted.
     * @return Number of rows deleted.
     */
    public int deleteFillsOlderThan(Instant cutoff) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                        DELETE FROM at_auction_fills
                        WHERE filled_at < :cutoff
                        """)
                        .bind("cutoff", Timestamp.from(cutoff))
                        .execute());
    }

    /** @return Total count of auction orders (all statuses). */
    public long countOrders() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM at_auction_orders")
                        .map((rs, ctx) -> rs.getLong(1))
                        .findOnly());
    }

    /** @return Total count of auction fills. */
    public long countFills() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM at_auction_fills")
                        .map((rs, ctx) -> rs.getLong(1))
                        .findOnly());
    }

    /** @return Order counts grouped by auction status. */
    public Map<OrderStatus, Long> countOrdersByStatus() {
        return jdbi.withHandle(handle -> {
            Map<OrderStatus, Long> counts = new HashMap<>();
            handle.createQuery("""
                    SELECT status, COUNT(*) AS cnt
                    FROM at_auction_orders
                    GROUP BY status
                    """)
                    .map((rs, ctx) -> Map.entry(
                            OrderStatus.valueOf(rs.getString("status")),
                            rs.getLong("cnt")))
                    .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
            for (OrderStatus status : OrderStatus.values()) {
                counts.putIfAbsent(status, 0L);
            }
            return counts;
        });
    }

    /**
     * Recent order churn summary. Uses created_at as the analysis window so admins can see
     * whether newly-created orders are filling, cancelling, or expiring.
     */
    public AuctionChurnSummary findOrderChurn(int days) {
        int windowDays = Math.max(1, days);
        Instant cutoff = Instant.now().minusSeconds(windowDays * 24L * 60L * 60L);
        Map<OrderStatus, Long> counts = jdbi.withHandle(handle -> {
            Map<OrderStatus, Long> result = new HashMap<>();
            handle.createQuery("""
                    SELECT status, COUNT(*) AS cnt
                    FROM at_auction_orders
                    WHERE created_at >= :cutoff
                    GROUP BY status
                    """)
                    .bind("cutoff", Timestamp.from(cutoff))
                    .map((rs, ctx) -> Map.entry(
                            OrderStatus.valueOf(rs.getString("status")),
                            rs.getLong("cnt")))
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
            return result;
        });

        long open = counts.getOrDefault(OrderStatus.OPEN, 0L);
        long partial = counts.getOrDefault(OrderStatus.PARTIALLY_FILLED, 0L);
        long filled = counts.getOrDefault(OrderStatus.FILLED, 0L);
        long cancelled = counts.getOrDefault(OrderStatus.CANCELLED, 0L);
        long expired = counts.getOrDefault(OrderStatus.EXPIRED, 0L);
        long reclaimed = counts.getOrDefault(OrderStatus.RECLAIMED, 0L);
        long total = open + partial + filled + cancelled + expired + reclaimed;
        return new AuctionChurnSummary(windowDays, total, open + partial, filled,
                cancelled, expired, reclaimed, rate(cancelled, total), rate(filled, total), rate(expired, total));
    }

    /**
     * Active material-level liquidity summary for audit dashboards.
     * BUY side: best bid is max price. SELL side: best ask is min price.
     */
    public List<MaterialBookHealth> findMaterialBookHealth(int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT material,
                       SUM(CASE WHEN side = 'BUY' THEN 1 ELSE 0 END) AS bid_count,
                       SUM(CASE WHEN side = 'SELL' THEN 1 ELSE 0 END) AS ask_count,
                       SUM(CASE WHEN side = 'BUY' THEN remaining_quantity ELSE 0 END) AS bid_quantity,
                       SUM(CASE WHEN side = 'SELL' THEN remaining_quantity ELSE 0 END) AS ask_quantity,
                       MAX(CASE WHEN side = 'BUY' THEN price ELSE NULL END) AS best_bid,
                       MIN(CASE WHEN side = 'SELL' THEN price ELSE NULL END) AS best_ask,
                       MAX(CASE WHEN side = 'SELL' THEN remaining_quantity ELSE 0 END) AS largest_sell_quantity
                FROM at_auction_orders
                WHERE status IN ('OPEN', 'PARTIALLY_FILLED')
                  AND remaining_quantity > 0
                  AND expires_at > :now
                GROUP BY material
                ORDER BY (bid_count + ask_count) DESC, material ASC
                LIMIT :limit
                """)
                .bind("limit", cappedLimit)
                .bind("now", Timestamp.from(Instant.now()))
                .map((rs, ctx) -> new MaterialBookHealth(
                        rs.getString("material"),
                        rs.getInt("bid_count"),
                        rs.getInt("ask_count"),
                        rs.getInt("bid_quantity"),
                        rs.getInt("ask_quantity"),
                        rs.getBigDecimal("best_bid"),
                        rs.getBigDecimal("best_ask"),
                        rs.getInt("largest_sell_quantity")))
                .list());
    }

    /** @return Count of recent fills where both sides belonged to the same player. */
    public long countSelfTradeFills(int days) {
        int windowDays = Math.max(1, days);
        Instant cutoff = Instant.now().minusSeconds(windowDays * 24L * 60L * 60L);
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT COUNT(*)
                FROM at_auction_fills af
                JOIN at_auction_orders buy_order ON buy_order.id = af.buy_order_id
                JOIN at_auction_orders sell_order ON sell_order.id = af.sell_order_id
                WHERE af.filled_at >= :cutoff
                  AND buy_order.player_uuid = sell_order.player_uuid
                """)
                .bind("cutoff", Timestamp.from(cutoff))
                .map((rs, ctx) -> rs.getLong(1))
                .findOnly());
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
    }

    /**
     * Fill count per day for the last N days.
     * @return List of {date (YYYY-MM-DD), count} sorted oldest→newest.
     */
    public List<DayFillCount> findFillsByDay(int days) {
        int windowDays = Math.max(1, days);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = today.minusDays(windowDays - 1L);
        Instant cutoff = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        String fillDateExpression = sqlite
                ? "DATE(filled_at / 1000, 'unixepoch')"
                : "DATE(filled_at)";
        String sql = """
                SELECT %s AS fill_date, COUNT(*) AS cnt
                FROM at_auction_fills
                WHERE filled_at >= :cutoff
                GROUP BY %s
                ORDER BY fill_date ASC
                """.formatted(fillDateExpression, fillDateExpression);

        List<DayFillCount> rows = jdbi.withHandle(handle ->
                handle.createQuery(sql)
                        .bind("cutoff", Timestamp.from(cutoff))
                        .map((rs, ctx) -> new DayFillCount(
                                rs.getString("fill_date"),
                                rs.getInt("cnt")))
                        .list());

        Map<String, Integer> countsByDate = new HashMap<>();
        for (DayFillCount row : rows) {
            countsByDate.put(row.date(), row.count());
        }

        List<DayFillCount> result = new ArrayList<>(windowDays);
        for (int i = 0; i < windowDays; i++) {
            String date = startDate.plusDays(i).toString();
            result.add(new DayFillCount(date, countsByDate.getOrDefault(date, 0)));
        }
        return result;
    }

    public record DayFillCount(String date, int count) {}

    public record AuctionChurnSummary(
            int days,
            long totalOrders,
            long activeOrders,
            long filledOrders,
            long cancelledOrders,
            long expiredOrders,
            long reclaimedOrders,
            double cancellationRate,
            double fillRate,
            double expirationRate
    ) {}

    public record MaterialBookHealth(
            String material,
            int bidCount,
            int askCount,
            int bidQuantity,
            int askQuantity,
            BigDecimal bestBid,
            BigDecimal bestAsk,
            int largestSellQuantity
    ) {
        public boolean isThinBook() {
            return bidCount < 2 || askCount < 2;
        }

        public boolean hasLargeSellWall() {
            return askQuantity > 0 && largestSellQuantity >= Math.max(64, askQuantity / 2);
        }
    }

    private AuctionOrder mapOrder(java.sql.ResultSet rs) {
        try {
            return AuctionOrder.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                    .material(rs.getString("material"))
                    .itemData(rs.getString("item_data"))
                    .price(rs.getBigDecimal("price"))
                    .originalQuantity(rs.getInt("original_quantity"))
                    .remainingQuantity(rs.getInt("remaining_quantity"))
                    .side(OrderSide.valueOf(rs.getString("side")))
                    .status(OrderStatus.valueOf(rs.getString("status")))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .filledAt(rs.getTimestamp("filled_at") != null
                            ? rs.getTimestamp("filled_at").toInstant() : null)
                    .expiresAt(rs.getTimestamp("expires_at").toInstant())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to map auction order", e);
        }
    }
}
