package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Player portfolio — shows a player's holdings, P&L, and net worth
 * derived from their transaction history, vault balance, and active loans.
 */
public record PortfolioDto(
        @NotNull String playerName,
        @Nullable String uuid,
        double vaultBalance,
        double holdingsValue,
        double totalDebt,
        double netWorth,
        double totalRealizedPnl,
        int creditScore,
        int transactionCount,
        @NotNull List<HoldingDto> holdings,
        @NotNull List<ActiveLoanDto> activeLoans
) {

    public record HoldingDto(
            int itemId,
            @NotNull String material,
            @NotNull String displayName,
            @NotNull String section,
            int netQuantity,          // positive = net bought, negative = net sold
            double avgBuyPrice,
            double currentPrice,
            double currentValue,
            double unrealizedPnl,
            double pnlPct,
            double realizedPnl        // profit/loss from completed sell transactions
    ) {}

    public record ActiveLoanDto(
            @NotNull String loanId,
            double principal,
            double currentBalance,
            double interestRate,
            @NotNull Instant createdAt,
            @NotNull Instant dueDate,
            @NotNull String status
    ) {}
}
