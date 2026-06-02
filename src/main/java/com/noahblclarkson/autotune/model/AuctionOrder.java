package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public record AuctionOrder(
        @NotNull UUID id,
        @NotNull UUID playerUuid,
        @NotNull String material,
        String itemData,
        @NotNull BigDecimal price,
        int originalQuantity,
        int remainingQuantity,
        @NotNull OrderSide side,
        @NotNull OrderStatus status,
        @NotNull Instant createdAt,
        Instant filledAt,
        @NotNull Instant expiresAt
) {

    public enum OrderSide {
        BUY, SELL
    }

    public enum OrderStatus {
        OPEN,
        PARTIALLY_FILLED,
        FILLED,
        CANCELLED,
        EXPIRED,
        RECLAIMED
    }

    public boolean isActive() {
        return (status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED)
                && remainingQuantity > 0;
    }

    /** Short order identifier — first 8 characters of the full UUID. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public int filledQuantity() {
        return originalQuantity - remainingQuantity;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt) && isActive();
    }

    public AuctionOrder withRemainingQuantity(int remaining) {
        OrderStatus newStatus = remaining == 0 ? OrderStatus.FILLED
                : remaining < originalQuantity ? OrderStatus.PARTIALLY_FILLED
                : status;
        return new AuctionOrder(
                id, playerUuid, material, itemData, price,
                originalQuantity, remaining, side, newStatus,
                createdAt, remaining == 0 ? Instant.now() : filledAt,
                expiresAt
        );
    }

    public AuctionOrder withStatusCancelled() {
        return new AuctionOrder(
                id, playerUuid, material, itemData, price,
                originalQuantity, remainingQuantity, side,
                OrderStatus.CANCELLED, createdAt, filledAt,
                expiresAt
        );
    }

    public AuctionOrder withStatusFilled() {
        return new AuctionOrder(
                id, playerUuid, material, itemData, price,
                originalQuantity, remainingQuantity, side,
                OrderStatus.FILLED, createdAt, Instant.now(),
                expiresAt
        );
    }

    public AuctionOrder withStatusExpired() {
        return new AuctionOrder(
                id, playerUuid, material, itemData, price,
                originalQuantity, remainingQuantity, side,
                OrderStatus.EXPIRED, createdAt, filledAt,
                expiresAt
        );
    }

    public AuctionOrder withStatusReclaimed() {
        return new AuctionOrder(
                id, playerUuid, material, itemData, price,
                originalQuantity, remainingQuantity, side,
                OrderStatus.RECLAIMED, createdAt, filledAt,
                expiresAt
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private UUID playerUuid;
        private String material;
        private String itemData;
        private BigDecimal price = BigDecimal.ZERO;
        private int originalQuantity = 1;
        private int remainingQuantity = 1;
        private OrderSide side = OrderSide.SELL;
        private OrderStatus status = OrderStatus.OPEN;
        private Instant createdAt = Instant.now();
        private Instant filledAt = null;
        private Instant expiresAt = Instant.now().plusSeconds(72 * 3600); // default 72h

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder playerUuid(UUID playerUuid) { this.playerUuid = playerUuid; return this; }
        public Builder material(String material) { this.material = material; return this; }
        public Builder itemData(String itemData) { this.itemData = itemData; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder originalQuantity(int qty) { this.originalQuantity = qty; this.remainingQuantity = qty; return this; }
        public Builder remainingQuantity(int qty) { this.remainingQuantity = qty; return this; }
        public Builder side(OrderSide side) { this.side = side; return this; }
        public Builder status(OrderStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant ts) { this.createdAt = ts; return this; }
        public Builder filledAt(Instant ts) { this.filledAt = ts; return this; }
        public Builder expiresAt(Instant ts) { this.expiresAt = ts; return this; }

        public AuctionOrder build() {
            return new AuctionOrder(id, playerUuid, material, itemData, price,
                    originalQuantity, remainingQuantity, side, status, createdAt, filledAt, expiresAt);
        }
    }
}
