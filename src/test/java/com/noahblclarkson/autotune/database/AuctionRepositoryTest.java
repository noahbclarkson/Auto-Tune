package com.noahblclarkson.autotune.database;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
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

    private void insertFill(LocalDate date, LocalTime time) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO at_auction_fills (id, buy_order_id, sell_order_id, quantity, price, filled_at)
                VALUES (:id, :buyOrderId, :sellOrderId, :quantity, :price, :filledAt)
                """)
                .bind("id", UUID.randomUUID().toString())
                .bind("buyOrderId", UUID.randomUUID().toString())
                .bind("sellOrderId", UUID.randomUUID().toString())
                .bind("quantity", 1)
                .bind("price", BigDecimal.TEN)
                .bind("filledAt", Timestamp.from(date.atTime(time).toInstant(ZoneOffset.UTC)))
                .execute());
    }
}
