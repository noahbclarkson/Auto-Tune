package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record EconomySnapshot(
        long id,
        @NotNull BigDecimal gdp,
        @NotNull BigDecimal totalDebt,
        int activeLoans,
        int playerCount,
        @NotNull BigDecimal averagePriceChange,
        @NotNull BigDecimal transactionVolume,
        @NotNull Instant timestamp
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long id;
        private BigDecimal gdp = BigDecimal.ZERO;
        private BigDecimal totalDebt = BigDecimal.ZERO;
        private int activeLoans;
        private int playerCount;
        private BigDecimal averagePriceChange = BigDecimal.ZERO;
        private BigDecimal transactionVolume = BigDecimal.ZERO;
        private Instant timestamp = Instant.now();

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder gdp(BigDecimal gdp) {
            this.gdp = gdp;
            return this;
        }

        public Builder totalDebt(BigDecimal totalDebt) {
            this.totalDebt = totalDebt;
            return this;
        }

        public Builder activeLoans(int activeLoans) {
            this.activeLoans = activeLoans;
            return this;
        }

        public Builder playerCount(int playerCount) {
            this.playerCount = playerCount;
            return this;
        }

        public Builder averagePriceChange(BigDecimal averagePriceChange) {
            this.averagePriceChange = averagePriceChange;
            return this;
        }

        public Builder transactionVolume(BigDecimal transactionVolume) {
            this.transactionVolume = transactionVolume;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public EconomySnapshot build() {
            return new EconomySnapshot(
                    id, gdp, totalDebt, activeLoans, playerCount,
                    averagePriceChange, transactionVolume, timestamp
            );
        }
    }
}
