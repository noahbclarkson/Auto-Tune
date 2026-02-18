package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceHistory(
        long id,
        int itemId,
        @NotNull BigDecimal price,
        int buyVolume,
        int sellVolume,
        @NotNull BigDecimal bpd,
        @NotNull BigDecimal spd,
        @NotNull Instant timestamp
) {

    public static Builder builder() {
        return new Builder();
    }

    public int totalVolume() {
        return buyVolume + sellVolume;
    }

    public static class Builder {
        private long id;
        private int itemId;
        private BigDecimal price = BigDecimal.ZERO;
        private int buyVolume;
        private int sellVolume;
        private BigDecimal bpd = BigDecimal.ZERO;
        private BigDecimal spd = BigDecimal.ZERO;
        private Instant timestamp = Instant.now();

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder itemId(int itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder buyVolume(int buyVolume) {
            this.buyVolume = buyVolume;
            return this;
        }

        public Builder sellVolume(int sellVolume) {
            this.sellVolume = sellVolume;
            return this;
        }

        public Builder bpd(BigDecimal bpd) {
            this.bpd = bpd;
            return this;
        }

        public Builder spd(BigDecimal spd) {
            this.spd = spd;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public PriceHistory build() {
            return new PriceHistory(id, itemId, price, buyVolume, sellVolume, bpd, spd, timestamp);
        }
    }
}
