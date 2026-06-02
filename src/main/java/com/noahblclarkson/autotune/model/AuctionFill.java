package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public record AuctionFill(
        @NotNull UUID id,
        @NotNull UUID buyOrderId,
        @NotNull UUID sellOrderId,
        int quantity,
        @NotNull BigDecimal price,
        @NotNull Instant filledAt,
        @NotNull FillStatus status
) {
    public enum FillStatus {
        PENDING,   // Fill recorded in DB; economy ops not yet run
        COMPLETED, // Economy ops succeeded
        FAILED     // Economy ops failed; needs compensation
    }

    /** Convenience: create a COMPLETED fill (backward-compatible). */
    public static AuctionFill completed(
            UUID id, UUID buyOrderId, UUID sellOrderId,
            int quantity, BigDecimal price, Instant filledAt) {
        return new AuctionFill(id, buyOrderId, sellOrderId, quantity, price, filledAt, FillStatus.COMPLETED);
    }

    public static Builder builder() {
        return new Builder();
    }

    public AuctionFill withStatus(FillStatus newStatus) {
        return new AuctionFill(id, buyOrderId, sellOrderId, quantity, price, filledAt, newStatus);
    }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private UUID buyOrderId;
        private UUID sellOrderId;
        private int quantity = 1;
        private BigDecimal price = BigDecimal.ZERO;
        private Instant filledAt = Instant.now();
        private FillStatus status = FillStatus.PENDING;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder buyOrderId(UUID id) { this.buyOrderId = id; return this; }
        public Builder sellOrderId(UUID id) { this.sellOrderId = id; return this; }
        public Builder quantity(int qty) { this.quantity = qty; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder filledAt(Instant ts) { this.filledAt = ts; return this; }
        public Builder status(FillStatus s) { this.status = s; return this; }

        public AuctionFill build() {
            return new AuctionFill(id, buyOrderId, sellOrderId, quantity, price, filledAt, status);
        }
    }
}
