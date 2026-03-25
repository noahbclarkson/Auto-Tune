package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionFill(
        @NotNull UUID id,
        @NotNull UUID buyOrderId,
        @NotNull UUID sellOrderId,
        int quantity,
        @NotNull BigDecimal price,
        @NotNull Instant filledAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private UUID buyOrderId;
        private UUID sellOrderId;
        private int quantity = 1;
        private BigDecimal price = BigDecimal.ZERO;
        private Instant filledAt = Instant.now();

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder buyOrderId(UUID id) { this.buyOrderId = id; return this; }
        public Builder sellOrderId(UUID id) { this.sellOrderId = id; return this; }
        public Builder quantity(int qty) { this.quantity = qty; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder filledAt(Instant ts) { this.filledAt = ts; return this; }

        public AuctionFill build() {
            return new AuctionFill(id, buyOrderId, sellOrderId, quantity, price, filledAt);
        }
    }
}
