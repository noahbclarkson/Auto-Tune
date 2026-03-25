package com.noahblclarkson.autotune.auction;

import com.noahblclarkson.autotune.model.AuctionFill;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Price-time priority limit order book.
 * Buy orders: highest price first, then oldest first.
 * Sell orders: lowest price first, then oldest first.
 * Matching: buy.price >= sell.price (spread crosses).
 * Execution price: the maker's price (the order already in the book).
 */
public class AuctionMatchingEngine {

    private final Supplier<Instant> timeSource;

    public AuctionMatchingEngine() {
        this(Instant::now);
    }

    public AuctionMatchingEngine(Supplier<Instant> timeSource) {
        this.timeSource = timeSource;
    }

    /**
     * Attempt to match a new order against the existing order book.
     * Returns fills for any matches that occurred.
     *
     * @param newOrder The order to place and match.
     * @param existingOrders All active orders for the same material.
     * @return List of fills from any matches.
     */
    public MatchResult matchOrder(AuctionOrder newOrder, List<AuctionOrder> existingOrders) {
        List<AuctionFill> fills = new ArrayList<>();
        AuctionOrder workingOrder = newOrder;

        // Separate existing orders by side
        List<AuctionOrder> existingBuys = existingOrders.stream()
                .filter(o -> o.side() == OrderSide.BUY && o.isActive() && !o.playerUuid().equals(newOrder.playerUuid()))
                .sorted(buyPriceTimeComparator())
                .toList();

        List<AuctionOrder> existingSells = existingOrders.stream()
                .filter(o -> o.side() == OrderSide.SELL && o.isActive() && !o.playerUuid().equals(newOrder.playerUuid()))
                .sorted(sellPriceTimeComparator())
                .toList();

        if (workingOrder.side() == OrderSide.SELL) {
            // Match against buy orders (highest bid first)
            for (AuctionOrder buyOrder : existingBuys) {
                if (!workingOrder.isActive()) break;

                // Prices cross: sell.price <= buy.price
                if (workingOrder.price().compareTo(buyOrder.price()) > 0) {
                    break; // No more buys will accept this price
                }

                MatchResult result = tryMatch(workingOrder, buyOrder);
                if (result.hasMatch()) {
                    fills.addAll(result.fills());
                    workingOrder = result.matchedOrder();
                }
            }
        } else {
            // Match against sell orders (lowest ask first)
            for (AuctionOrder sellOrder : existingSells) {
                if (!workingOrder.isActive()) break;

                // Prices cross: buy.price >= sell.price
                if (workingOrder.price().compareTo(sellOrder.price()) < 0) {
                    break; // No more sells can fill at this price
                }

                MatchResult result = tryMatch(workingOrder, sellOrder);
                if (result.hasMatch()) {
                    fills.addAll(result.fills());
                    workingOrder = result.matchedOrder();
                }
            }
        }

        return new MatchResult(workingOrder, fills);
    }

    private MatchResult tryMatch(AuctionOrder takerOrder, AuctionOrder makerOrder) {
        if (!takerOrder.isActive() || !makerOrder.isActive()) {
            return new MatchResult(takerOrder, List.of());
        }

        // Can't match against your own order
        if (takerOrder.playerUuid().equals(makerOrder.playerUuid())) {
            return new MatchResult(takerOrder, List.of());
        }

        int matchQty = Math.min(takerOrder.remainingQuantity(), makerOrder.remainingQuantity());
        if (matchQty <= 0) {
            return new MatchResult(takerOrder, List.of());
        }

        // Execution price is the maker's price
        BigDecimal execPrice = makerOrder.price();

        AuctionFill fill = AuctionFill.builder()
                .id(UUID.randomUUID())
                .buyOrderId(takerOrder.side() == OrderSide.BUY ? takerOrder.id() : makerOrder.id())
                .sellOrderId(takerOrder.side() == OrderSide.SELL ? takerOrder.id() : makerOrder.id())
                .quantity(matchQty)
                .price(execPrice)
                .filledAt(timeSource.get())
                .build();

        int newRemaining = takerOrder.remainingQuantity() - matchQty;
        AuctionOrder updatedTaker = takerOrder.withRemainingQuantity(newRemaining);

        return new MatchResult(updatedTaker, List.of(fill));
    }

    private Comparator<AuctionOrder> buyPriceTimeComparator() {
        return Comparator
                .comparing(AuctionOrder::price, Comparator.reverseOrder())  // Highest first
                .thenComparing(AuctionOrder::createdAt);                      // Oldest first
    }

    private Comparator<AuctionOrder> sellPriceTimeComparator() {
        return Comparator
                .comparing(AuctionOrder::price)       // Lowest first
                .thenComparing(AuctionOrder::createdAt); // Oldest first
    }

    public record MatchResult(AuctionOrder matchedOrder, List<AuctionFill> fills) {
        public boolean hasMatch() { return !fills.isEmpty(); }
    }
}
