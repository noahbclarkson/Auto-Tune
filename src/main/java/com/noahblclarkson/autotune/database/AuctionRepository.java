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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("PMD")
public class AuctionRepository {

    private final Jdbi jdbi;

    public AuctionRepository(DatabaseManager databaseManager) {
        this.jdbi = databaseManager.getJdbi();
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
                          AND expires_at > CURRENT_TIMESTAMP
                        ORDER BY side ASC, price DESC, created_at ASC
                        """)
                        .bind("material", material)
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
                          AND expires_at > CURRENT_TIMESTAMP
                        ORDER BY created_at DESC
                        """)
                        .bind("playerUuid", playerUuid.toString())
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
                          AND expires_at > CURRENT_TIMESTAMP
                        ORDER BY created_at DESC
                        LIMIT 200
                        """)
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
                          AND expires_at <= CURRENT_TIMESTAMP
                        ORDER BY expires_at ASC
                        """)
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
