package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public record Transaction(
        long id,
        @NotNull UUID playerUuid,
        int itemId,
        @NotNull TransactionType type,
        int amount,
        @NotNull BigDecimal pricePerUnit,
        @NotNull BigDecimal totalPrice,
        @NotNull Instant timestamp
) {

    public enum TransactionType {
        BUY,
        SELL
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long id;
        private UUID playerUuid;
        private int itemId;
        private TransactionType type;
        private int amount;
        private BigDecimal pricePerUnit = BigDecimal.ZERO;
        private BigDecimal totalPrice = BigDecimal.ZERO;
        private Instant timestamp = Instant.now();

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder itemId(int itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder pricePerUnit(BigDecimal pricePerUnit) {
            this.pricePerUnit = pricePerUnit;
            return this;
        }

        public Builder totalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Transaction build() {
            if (totalPrice.compareTo(BigDecimal.ZERO) == 0 && pricePerUnit.compareTo(BigDecimal.ZERO) != 0) {
                totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(amount));
            }
            return new Transaction(
                    id, playerUuid, itemId, type, amount,
                    pricePerUnit, totalPrice, timestamp
            );
        }
    }
}
