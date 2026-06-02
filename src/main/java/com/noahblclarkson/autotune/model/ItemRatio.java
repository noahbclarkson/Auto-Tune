package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a price ratio between two items.
 * ratio = price of itemA / price of itemB
 * Items are normalized so itemA < itemB to avoid duplicate entries.
 */
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public record ItemRatio(
        int itemA,
        int itemB,
        @NotNull BigDecimal ratio,
        @NotNull Instant updatedAt
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int itemA;
        private int itemB;
        private BigDecimal ratio = BigDecimal.ONE;
        private Instant updatedAt = Instant.now();

        public Builder itemA(int itemA) {
            this.itemA = itemA;
            return this;
        }

        public Builder itemB(int itemB) {
            this.itemB = itemB;
            return this;
        }

        public Builder ratio(BigDecimal ratio) {
            this.ratio = ratio;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ItemRatio build() {
            return new ItemRatio(itemA, itemB, ratio, updatedAt);
        }
    }
}
