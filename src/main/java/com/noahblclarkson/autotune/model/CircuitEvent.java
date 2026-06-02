package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/** A timestamped transition in the loan circuit breaker / admin recovery state. */
public record CircuitEvent(
        long id,
        @Nullable String previousTier,
        @NotNull String newTier,
        double debtGdpRatio,
        @NotNull BigDecimal gdp,
        @NotNull BigDecimal totalDebt,
        double interestMultiplier,
        boolean adminInitiated,
        @Nullable String details,
        @NotNull Instant timestamp
) {
    public CircuitEvent(
            @Nullable String previousTier,
            @NotNull String newTier,
            double debtGdpRatio,
            @NotNull BigDecimal gdp,
            @NotNull BigDecimal totalDebt,
            double interestMultiplier,
            boolean adminInitiated,
            @Nullable String details
    ) {
        this(0L, previousTier, newTier, debtGdpRatio, gdp, totalDebt, interestMultiplier,
                adminInitiated, details, Instant.now());
    }
}
