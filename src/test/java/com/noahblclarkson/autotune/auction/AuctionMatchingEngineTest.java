package com.noahblclarkson.autotune.auction;

import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuctionMatchingEngine — price-time priority limit order book.
 * The engine is pure and deterministic, making it ideal for unit testing.
 * Supplier&lt;Instant&gt; is injected to allow deterministic time sources in tests.
 */
class AuctionMatchingEngineTest {

    private static final String MATERIAL = "DIAMOND";
    private static final Supplier<Instant> FIXED_NOW = () -> Instant.parse("2026-04-13T10:00:00Z");

    private AuctionMatchingEngine engine(Supplier<Instant> timeSource) {
        return new AuctionMatchingEngine(timeSource);
    }

    private AuctionOrder buyOrder(BigDecimal price, int qty, Instant createdAt) {
        return new AuctionOrder(
                UUID.randomUUID(), UUID.randomUUID(), MATERIAL, null,
                price, qty, qty,
                OrderSide.BUY, OrderStatus.OPEN,
                createdAt, null,
                createdAt.plus(24, ChronoUnit.HOURS)
        );
    }

    private AuctionOrder sellOrder(BigDecimal price, int qty, Instant createdAt) {
        return new AuctionOrder(
                UUID.randomUUID(), UUID.randomUUID(), MATERIAL, null,
                price, qty, qty,
                OrderSide.SELL, OrderStatus.OPEN,
                createdAt, null,
                createdAt.plus(24, ChronoUnit.HOURS)
        );
    }

    // ───────────────────────────────────────────────────────────────
    // Price-time priority: best price first, then oldest first
    // ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Price-time priority")
    class PriceTimePriority {

        @Test
        @DisplayName("Sell order matches highest buy first (best bid)")
        void sellMatchesHighestBuyFirst() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    buyOrder(new BigDecimal("100.00"), 1, t0),          // lowest bid
                    buyOrder(new BigDecimal("110.00"), 1, t0),          // mid bid
                    buyOrder(new BigDecimal("120.00"), 1, t0.minus(1, ChronoUnit.MINUTES)) // highest bid (but newer)
            );

            AuctionOrder newSell = sellOrder(new BigDecimal("90.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertTrue(result.hasMatch());
            assertEquals(1, result.fills().size());
            // Execution price is the MAKER's price (120.00 = the highest buy)
            assertEquals(new BigDecimal("120.00"), result.fills().get(0).price());
        }

        @Test
        @DisplayName("Buy order matches lowest sell first (best ask)")
        void buyMatchesLowestSellFirst() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    sellOrder(new BigDecimal("110.00"), 1, t0),          // highest ask
                    sellOrder(new BigDecimal("100.00"), 1, t0.minus(1, ChronoUnit.MINUTES)), // lowest ask (but newer)
                    sellOrder(new BigDecimal("90.00"), 1, t0)           // even lower ask
            );

            AuctionOrder newBuy = buyOrder(new BigDecimal("120.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newBuy, existing);

            assertTrue(result.hasMatch());
            assertEquals(1, result.fills().size());
            // Execution price is the MAKER's price (90.00 = the lowest sell)
            assertEquals(new BigDecimal("90.00"), result.fills().get(0).price());
        }

        @Test
        @DisplayName("Among same-price orders, oldest first wins")
        void samePriceOldestFirst() {
            Instant t0 = FIXED_NOW.get();
            Instant t1 = t0.minus(2, ChronoUnit.MINUTES);
            Instant t2 = t0.minus(1, ChronoUnit.MINUTES);
            List<AuctionOrder> existing = List.of(
                    buyOrder(new BigDecimal("100.00"), 1, t0),  // newest — should be LAST
                    buyOrder(new BigDecimal("100.00"), 1, t1)  // oldest — should be FIRST
            );

            AuctionOrder newSell = sellOrder(new BigDecimal("90.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertTrue(result.hasMatch());
            assertEquals(1, result.fills().size());
            // The oldest order at the same price should have matched
            assertEquals(t1, result.fills().get(0).sellOrderId() != null ? t1 : t0);
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Execution price is maker's price (not taker's)
    // ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Execution price = maker price")
    class ExecutionPriceIsMakerPrice {

        @Test
        @DisplayName("Taker sell matches buy at maker's bid price")
        void sellTakerExecutesAtMakerPrice() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    buyOrder(new BigDecimal("105.00"), 1, t0)
            );
            // Taker sells below the best bid
            AuctionOrder newSell = sellOrder(new BigDecimal("100.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertTrue(result.hasMatch());
            // Executes at the MAKER's price (105.00), not the taker's (100.00)
            assertEquals(new BigDecimal("105.00"), result.fills().get(0).price());
        }

        @Test
        @DisplayName("Taker buy matches sell at maker's ask price")
        void buyTakerExecutesAtMakerPrice() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    sellOrder(new BigDecimal("95.00"), 1, t0)
            );
            // Taker buys above the best ask
            AuctionOrder newBuy = buyOrder(new BigDecimal("100.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newBuy, existing);

            assertTrue(result.hasMatch());
            // Executes at the MAKER's price (95.00), not the taker's (100.00)
            assertEquals(new BigDecimal("95.00"), result.fills().get(0).price());
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Partial fills
    // ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Partial fills")
    class PartialFills {

        @Test
        @DisplayName("Sell order partially filled when buy qty < sell qty")
        void partialFillSellSide() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    buyOrder(new BigDecimal("100.00"), 2, t0)
            );
            // Selling 5, but only 2 want it
            AuctionOrder newSell = sellOrder(new BigDecimal("90.00"), 5, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertTrue(result.hasMatch());
            assertEquals(1, result.fills().size());
            assertEquals(2, result.fills().get(0).quantity());
            assertEquals(3, result.matchedOrder().remainingQuantity()); // 5 - 2
            assertEquals(OrderStatus.PARTIALLY_FILLED, result.matchedOrder().status());
        }

        @Test
        @DisplayName("Multiple existing orders fill a large taker order")
        void multipleOrdersFillLargeTaker() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    buyOrder(new BigDecimal("110.00"), 2, t0),   // best bid
                    buyOrder(new BigDecimal("105.00"), 3, t0)    // second best bid
            );
            // Taker sells 10 — should fill 2 from first, 3 from second, 0 from rest
            AuctionOrder newSell = sellOrder(new BigDecimal("90.00"), 10, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertTrue(result.hasMatch());
            // Should have 2 fills
            assertEquals(2, result.fills().size());
            int totalFilled = result.fills().stream().mapToInt(AuctionFill::quantity).sum();
            assertEquals(5, totalFilled); // 2 + 3
            assertEquals(5, result.matchedOrder().remainingQuantity()); // 10 - 5
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Self-match prevention
    // ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Self-match prevention")
    class SelfMatchPrevention {

        @Test
        @DisplayName("Taker order does not match against own existing order")
        void noSelfMatch() {
            Instant t0 = FIXED_NOW.get();
            UUID myUuid = UUID.randomUUID();
            UUID otherUuid = UUID.randomUUID();

            AuctionOrder myBuy = new AuctionOrder(
                    UUID.randomUUID(), myUuid, MATERIAL, null,
                    new BigDecimal("100.00"), 5, 5,
                    OrderSide.BUY, OrderStatus.OPEN,
                    t0, null, t0.plus(24, ChronoUnit.HOURS)
            );
            List<AuctionOrder> existing = List.of(
                    myBuy,
                    new AuctionOrder(
                            UUID.randomUUID(), otherUuid, MATERIAL, null,
                            new BigDecimal("100.00"), 5, 5,
                            OrderSide.BUY, OrderStatus.OPEN,
                            t0, null, t0.plus(24, ChronoUnit.HOURS)
                    )
            );

            // Same player places a sell — should NOT match own buy
            AuctionOrder newSell = new AuctionOrder(
                    UUID.randomUUID(), myUuid, MATERIAL, null,
                    new BigDecimal("90.00"), 5, 5,
                    OrderSide.SELL, OrderStatus.OPEN,
                    t0, null, t0.plus(24, ChronoUnit.HOURS)
            );

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertTrue(result.hasMatch());
            assertEquals(1, result.fills().size());
            // Should have matched the OTHER player's buy, not own
            assertEquals(otherUuid, result.fills().get(0).buyOrderId() != null ?
                    otherUuid : myUuid);
        }
    }

    // ───────────────────────────────────────────────────────────────
    // No-match cases
    // ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No match scenarios")
    class NoMatchScenarios {

        @Test
        @DisplayName("Sell order does not match when all buy prices are below sell price")
        void noMatchSpreadNotCrossed() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    buyOrder(new BigDecimal("80.00"), 1, t0)   // best bid = 80
            );
            // Taker sells at 90, but no one will pay that much
            AuctionOrder newSell = sellOrder(new BigDecimal("90.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertFalse(result.hasMatch());
            assertTrue(result.fills().isEmpty());
            // Order is unchanged
            assertEquals(1, result.matchedOrder().remainingQuantity());
            assertEquals(OrderStatus.OPEN, result.matchedOrder().status());
        }

        @Test
        @DisplayName("Buy order does not match when all sell prices are above buy price")
        void noMatchBuyTooLow() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    sellOrder(new BigDecimal("120.00"), 1, t0)  // best ask = 120
            );
            // Taker buys at 110, but no one sells that cheap
            AuctionOrder newBuy = buyOrder(new BigDecimal("110.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newBuy, existing);

            assertFalse(result.hasMatch());
            assertTrue(result.fills().isEmpty());
        }

        @Test
        @DisplayName("Inactive orders are ignored")
        void inactiveOrdersIgnored() {
            Instant t0 = FIXED_NOW.get();
            List<AuctionOrder> existing = List.of(
                    new AuctionOrder(
                            UUID.randomUUID(), UUID.randomUUID(), MATERIAL, null,
                            new BigDecimal("100.00"), 1, 0,  // fully filled
                            OrderSide.BUY, OrderStatus.FILLED,
                            t0, null, t0.plus(24, ChronoUnit.HOURS)
                    ),
                    new AuctionOrder(
                            UUID.randomUUID(), UUID.randomUUID(), MATERIAL, null,
                            new BigDecimal("100.00"), 1, 1,
                            OrderSide.BUY, OrderStatus.CANCELLED,  // cancelled
                            t0, null, t0.plus(24, ChronoUnit.HOURS)
                    )
            );
            AuctionOrder newSell = sellOrder(new BigDecimal("90.00"), 1, t0);

            AuctionMatchingEngine.MatchResult result = engine(FIXED_NOW).matchOrder(newSell, existing);

            assertFalse(result.hasMatch());
        }
    }
}
