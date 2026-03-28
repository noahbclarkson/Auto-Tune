package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public record Loan(
        @NotNull UUID id,
        @NotNull UUID playerUuid,
        @NotNull BigDecimal principal,
        @NotNull BigDecimal currentBalance,
        @NotNull BigDecimal interestRate,
        @NotNull Instant createdAt,
        @NotNull Instant dueDate,
        @NotNull Instant lastInterestAt,
        @NotNull LoanStatus status
) {

    public enum LoanStatus {
        ACTIVE,
        PAID,
        DEFAULTED
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .playerUuid(playerUuid)
                .principal(principal)
                .currentBalance(currentBalance)
                .interestRate(interestRate)
                .createdAt(createdAt)
                .dueDate(dueDate)
                .lastInterestAt(lastInterestAt)
                .status(status);
    }

    public boolean isOverdue() {
        return status == LoanStatus.ACTIVE && Instant.now().isAfter(dueDate);
    }

    public Loan applyInterest(BigDecimal rate) {
        BigDecimal interest = currentBalance.multiply(rate);
        return toBuilder()
                .currentBalance(currentBalance.add(interest))
                .lastInterestAt(Instant.now())
                .build();
    }

    public Loan makePayment(BigDecimal amount) {
        BigDecimal newBalance = currentBalance.subtract(amount);
        LoanStatus newStatus = newBalance.compareTo(BigDecimal.ZERO) <= 0
                ? LoanStatus.PAID
                : status;
        return toBuilder()
                .currentBalance(newBalance.max(BigDecimal.ZERO))
                .status(newStatus)
                .build();
    }

    public Loan markDefaulted() {
        return toBuilder().status(LoanStatus.DEFAULTED).build();
    }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private UUID playerUuid;
        private BigDecimal principal = BigDecimal.ZERO;
        private BigDecimal currentBalance = BigDecimal.ZERO;
        private BigDecimal interestRate = BigDecimal.ZERO;
        private Instant createdAt = Instant.now();
        private Instant dueDate;
        private Instant lastInterestAt = Instant.now();
        private LoanStatus status = LoanStatus.ACTIVE;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder principal(BigDecimal principal) {
            this.principal = principal;
            this.currentBalance = principal;
            return this;
        }

        public Builder currentBalance(BigDecimal currentBalance) {
            this.currentBalance = currentBalance;
            return this;
        }

        public Builder interestRate(BigDecimal interestRate) {
            this.interestRate = interestRate;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder dueDate(Instant dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder lastInterestAt(Instant lastInterestAt) {
            this.lastInterestAt = lastInterestAt;
            return this;
        }

        public Builder status(LoanStatus status) {
            this.status = status;
            return this;
        }

        public Loan build() {
            return new Loan(
                    id, playerUuid, principal, currentBalance, interestRate,
                    createdAt, dueDate, lastInterestAt, status
            );
        }
    }
}
