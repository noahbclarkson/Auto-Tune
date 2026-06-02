package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public record PlayerData(
        @NotNull UUID uuid,
        @Nullable String username,
        @Nullable String guildTag,
        @NotNull PlayerType playerType,
        int creditScore,
        @NotNull BigDecimal totalTraded,
        @NotNull BigDecimal totalBought,
        @NotNull BigDecimal totalSold,
        int transactionCount,
        @NotNull Instant firstSeen,
        @NotNull Instant lastSeen,
        @Nullable Instant lastDefaultedAt
) {

    public enum PlayerType {
        OTHER,
        MARKET_MAKER,
        GUILD_BUYER
    }

    public static final int DEFAULT_CREDIT_SCORE = 500;
    public static final int MIN_CREDIT_SCORE = 0;
    public static final int MAX_CREDIT_SCORE = 1000;

    public static PlayerData createNew(UUID uuid, String username) {
        Instant now = Instant.now();
        return new PlayerData(
                uuid,
                username,
                null,
                PlayerType.OTHER,
                DEFAULT_CREDIT_SCORE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                now,
                now,
                null
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .uuid(uuid)
                .username(username)
                .guildTag(guildTag)
                .playerType(playerType)
                .creditScore(creditScore)
                .totalTraded(totalTraded)
                .totalBought(totalBought)
                .totalSold(totalSold)
                .transactionCount(transactionCount)
                .firstSeen(firstSeen)
                .lastSeen(lastSeen)
                .lastDefaultedAt(lastDefaultedAt);
    }

    public PlayerData withCreditScore(int newScore) {
        int clampedScore = Math.max(MIN_CREDIT_SCORE, Math.min(MAX_CREDIT_SCORE, newScore));
        return toBuilder().creditScore(clampedScore).build();
    }

    public PlayerData withPlayerType(@NotNull PlayerType newType) {
        return toBuilder().playerType(newType).build();
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
        private String guildTag;
        private PlayerType playerType = PlayerType.OTHER;
        private int creditScore = DEFAULT_CREDIT_SCORE;
        private BigDecimal totalTraded = BigDecimal.ZERO;
        private BigDecimal totalBought = BigDecimal.ZERO;
        private BigDecimal totalSold = BigDecimal.ZERO;
        private int transactionCount;
        private Instant firstSeen = Instant.now();
        private Instant lastSeen = Instant.now();
        private Instant lastDefaultedAt = null;

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder guildTag(String guildTag) {
            this.guildTag = guildTag;
            return this;
        }

        public Builder playerType(@NotNull PlayerType playerType) {
            this.playerType = playerType;
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

        public Builder lastDefaultedAt(Instant lastDefaultedAt) {
            this.lastDefaultedAt = lastDefaultedAt;
            return this;
        }

        public PlayerData build() {
            return new PlayerData(
                    uuid, username, guildTag, playerType, creditScore, totalTraded,
                    totalBought, totalSold, transactionCount, firstSeen, lastSeen, lastDefaultedAt
            );
        }
    }
}
