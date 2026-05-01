package com.noahblclarkson.autotune.database;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD")
class AuctionRepositoryTest {

    private AuctionRepository repository;
    private Jdbi jdbi;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:sqlite:" + tempDir.resolve("auction-test.db"));
        jdbi.useHandle(handle -> handle.execute("""
                CREATE TABLE at_auction_orders (
                    id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    item_data TEXT DEFAULT NULL,
                    price DECIMAL(20, 2) NOT NULL,
                    original_quantity INTEGER NOT NULL,
                    remaining_quantity INTEGER NOT NULL,
                    side VARCHAR(4) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    filled_at DATETIME DEFAULT NULL,
                    expires_at DATETIME NOT NULL
                )
                """));
        jdbi.useHandle(handle -> handle.execute("""
                CREATE TABLE at_auction_fills (
                    id VARCHAR(36) PRIMARY KEY,
                    buy_order_id VARCHAR(36) NOT NULL,
                    sell_order_id VARCHAR(36) NOT NULL,
                    quantity INTEGER NOT NULL,
                    price DECIMAL(20, 2) NOT NULL,
                    filled_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """));

        DatabaseManager databaseManager = mock(DatabaseManager.class);
        when(databaseManager.getJdbi()).thenReturn(jdbi);
        when(databaseManager.isSqlite()).thenReturn(true);
        repository = new AuctionRepository(databaseManager);
    }

    @Test
    @DisplayName("Daily fill counts aggregate multiple fills on the same calendar day")
    void findFillsByDayAggregatesByCalendarDay() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);

        insertFill(yesterday, LocalTime.of(9, 15));
        insertFill(yesterday, LocalTime.of(17, 45));
        insertFill(today, LocalTime.of(1, 30));

        List<AuctionRepository.DayFillCount> result = repository.findFillsByDay(2);

        assertEquals(2, result.size());
        assertEquals(yesterday.toString(), result.get(0).date());
        assertEquals(2, result.get(0).count());
        assertEquals(today.toString(), result.get(1).date());
        assertEquals(1, result.get(1).count());
    }

    @Test
    @DisplayName("Daily fill counts include zero-fill days so dashboard sparklines show gaps")
    void findFillsByDayPadsZeroFillDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate threeDaysAgo = today.minusDays(3);

        insertFill(threeDaysAgo, LocalTime.NOON);
        insertFill(today, LocalTime.NOON);

        List<AuctionRepository.DayFillCount> result = repository.findFillsByDay(4);

        assertEquals(4, result.size());
        assertEquals(threeDaysAgo.toString(), result.get(0).date());
        assertEquals(1, result.get(0).count());
        assertEquals(threeDaysAgo.plusDays(1).toString(), result.get(1).date());
        assertEquals(0, result.get(1).count());
        assertEquals(threeDaysAgo.plusDays(2).toString(), result.get(2).date());
        assertEquals(0, result.get(2).count());
        assertEquals(today.toString(), result.get(3).date());
        assertEquals(1, result.get(3).count());
    }

    @Test
    @DisplayName("Order churn counts status outcomes inside the requested window")
    void findOrderChurnCountsRecentStatusOutcomes() {
        insertOrder(UUID.randomUUID(), UUID.randomUUID(), "DIAMOND", "BUY", "OPEN", 16, 100, 1);
        insertOrder(UUID.randomUUID(), UUID.randomUUID(), "DIAMOND", "SELL", "CANCELLED", 16, 120, 1);
        insertOrder(UUID.randomUUID(), UUID.randomUUID(), "EMERALD", "SELL", "FILLED", 32, 10, 1);
        insertOrder(UUID.randomUUID(), UUID.randomUUID(), "IRON_INGOT", "SELL", "EXPIRED", 32, 5, 10);

        var statusCounts = repository.countOrdersByStatus();
        assertEquals(1L, statusCounts.get(com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus.OPEN));
        assertEquals(1L, statusCounts.get(com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus.CANCELLED));
        assertEquals(1L, statusCounts.get(com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus.EXPIRED));

        var churn = repository.findOrderChurn(7);
        assertEquals(3, churn.totalOrders());
        assertEquals(1, churn.activeOrders());
        assertEquals(1, churn.cancelledOrders());
        assertEquals(1, churn.filledOrders());
        assertEquals(0, churn.expiredOrders());
        assertEquals(1.0 / 3.0, churn.cancellationRate(), 0.0001);
    }

    @Test
    @DisplayName("Material book health detects thin books and large sell walls using SQLite timestamps")
    void findMaterialBookHealthUsesBoundExpiryTimestamp() {
        insertOrder(UUID.randomUUID(), UUID.randomUUID(), "DIAMOND", "BUY", "OPEN", 16, 100, 1);
        insertOrder(UUID.randomUUID(), UUID.randomUUID(), "DIAMOND", "SELL", "OPEN", 16, 120, 1);
        insertOrder(UUID.randomUUID(), UUID.randomUUID(), "EMERALD", "SELL", "OPEN", 128, 10, 1);

        List<AuctionRepository.MaterialBookHealth> result = repository.findMaterialBookHealth(10);

        assertEquals(2, result.size());
        var diamond = result.stream().filter(h -> h.material().equals("DIAMOND")).findFirst().orElseThrow();
        assertEquals(1, diamond.bidCount());
        assertEquals(1, diamond.askCount());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(diamond.bestBid()));
        assertEquals(0, BigDecimal.valueOf(120).compareTo(diamond.bestAsk()));
        assertEquals(true, diamond.isThinBook());

        var emerald = result.stream().filter(h -> h.material().equals("EMERALD")).findFirst().orElseThrow();
        assertEquals(true, emerald.hasLargeSellWall());
    }

    @Test
    @DisplayName("Self-trade fill audit catches fills where buy and sell orders share a player")
    void countSelfTradeFillsFindsSamePlayerFills() {
        UUID player = UUID.randomUUID();
        UUID buyOrder = UUID.randomUUID();
        UUID sellOrder = UUID.randomUUID();
        insertOrder(buyOrder, player, "DIAMOND", "BUY", "FILLED", 1, 100, 1);
        insertOrder(sellOrder, player, "DIAMOND", "SELL", "FILLED", 1, 100, 1);
        insertFill(buyOrder, sellOrder, LocalDate.now(ZoneOffset.UTC), LocalTime.NOON);

        assertEquals(1, repository.countSelfTradeFills(7));
    }

    private void insertFill(LocalDate date, LocalTime time) {
        insertFill(UUID.randomUUID(), UUID.randomUUID(), date, time);
    }

    private void insertFill(UUID buyOrderId, UUID sellOrderId, LocalDate date, LocalTime time) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO at_auction_fills (id, buy_order_id, sell_order_id, quantity, price, filled_at)
                VALUES (:id, :buyOrderId, :sellOrderId, :quantity, :price, :filledAt)
                """)
                .bind("id", UUID.randomUUID().toString())
                .bind("buyOrderId", buyOrderId.toString())
                .bind("sellOrderId", sellOrderId.toString())
                .bind("quantity", 1)
                .bind("price", BigDecimal.TEN)
                .bind("filledAt", Timestamp.from(date.atTime(time).toInstant(ZoneOffset.UTC)))
                .execute());
    }

    private void insertOrder(UUID id, UUID playerUuid, String material, String side, String status,
                             int quantity, int price, int createdDaysAgo) {
        Instant createdAt = Instant.now().minusSeconds(createdDaysAgo * 24L * 60L * 60L);
        Instant expiresAt = Instant.now().plusSeconds(24L * 60L * 60L);
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO at_auction_orders (
                    id, player_uuid, material, price, original_quantity, remaining_quantity,
                    side, status, created_at, updated_at, filled_at, expires_at
                ) VALUES (
                    :id, :playerUuid, :material, :price, :originalQuantity, :remainingQuantity,
                    :side, :status, :createdAt, :createdAt, :filledAt, :expiresAt
                )
                """)
                .bind("id", id.toString())
                .bind("playerUuid", playerUuid.toString())
                .bind("material", material)
                .bind("price", BigDecimal.valueOf(price))
                .bind("originalQuantity", quantity)
                .bind("remainingQuantity", quantity)
                .bind("side", side)
                .bind("status", status)
                .bind("createdAt", Timestamp.from(createdAt))
                .bind("filledAt", "FILLED".equals(status) ? Timestamp.from(Instant.now()) : null)
                .bind("expiresAt", Timestamp.from(expiresAt))
                .execute());
    }
}
