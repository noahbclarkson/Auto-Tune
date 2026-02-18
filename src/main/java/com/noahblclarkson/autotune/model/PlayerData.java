package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlayerData(
        @NotNull UUID uuid,
        @Nullable String username,
        int creditScore,
        @NotNull BigDecimal totalTraded,
        @NotNull BigDecimal totalBought,
        @NotNull BigDecimal totalSold,
        int transactionCount,
        @NotNull Instant firstSeen,
        @NotNull Instant lastSeen
) {

    public static final int DEFAULT_CREDIT_SCORE = 500;
    public static final int MIN_CREDIT_SCORE = 0;
    public static final int MAX_CREDIT_SCORE = 1000;

    public static PlayerData createNew(UUID uuid, String username) {
        Instant now = Instant.now();
        return new PlayerData(
                uuid,
                username,
                DEFAULT_CREDIT_SCORE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                now,
                now
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .uuid(uuid)
                .username(username)
                .creditScore(creditScore)
                .totalTraded(totalTraded)
                .totalBought(totalBought)
                .totalSold(totalSold)
                .transactionCount(transactionCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen);
    }

    public PlayerData withCreditScore(int newScore) {
        int clampedScore = Math.max(MIN_CREDIT_SCORE, Math.min(MAX_CREDIT_SCORE, newScore));
        return toBuilder().creditScore(clampedScore).build();
    }

    public PlayerData addTransaction(BigDecimal amount, boolean isBuy) {
        Builder builder = toBuilder()
                .totalTraded(totalTraded.add(amount.abs()))
                .transactionCount(transactionCount + 1)
                .lastSeen(Instant.now());

        if (isBuy) {
            builder.totalBought(totalBought.add(amount.abs()));
        } else {
            builder.totalSold(totalSold.add(amount.abs()));
        }

        return builder.build();
    }

    public static class Builder {
        private UUID uuid;
        private String username;
        private int creditScore = DEFAULT_CREDIT_SCORE;
        private BigDecimal totalTraded = BigDecimal.ZERO;
        private BigDecimal totalBought = BigDecimal.ZERO;
        private BigDecimal totalSold = BigDecimal.ZERO;
        private int transactionCount;
        private Instant firstSeen = Instant.now();
        private Instant lastSeen = Instant.now();

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder creditScore(int creditScore) {
            this.creditScore = creditScore;
            return this;
        }

        public Builder totalTraded(BigDecimal totalTraded) {
            this.totalTraded = totalTraded;
            return this;
        }

        public Builder totalBought(BigDecimal totalBought) {
            this.totalBought = totalBought;
            return this;
        }

        public Builder totalSold(BigDecimal totalSold) {
            this.totalSold = totalSold;
            return this;
        }

        public Builder transactionCount(int transactionCount) {
            this.transactionCount = transactionCount;
            return this;
        }

        public Builder firstSeen(Instant firstSeen) {
            this.firstSeen = firstSeen;
            return this;
        }

        public Builder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public PlayerData build() {
            return new PlayerData(
                    uuid, username, creditScore, totalTraded, totalBought,
                    totalSold, transactionCount, firstSeen, lastSeen
            );
        }
    }
}
