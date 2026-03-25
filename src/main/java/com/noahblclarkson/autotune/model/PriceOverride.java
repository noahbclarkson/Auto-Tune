package com.noahblclarkson.autotune.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceOverride(
        int itemId,
        BigDecimal price,
        Instant expiresAt,
        UUID setBy,
        Instant setAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public String formatExpiry() {
        if (expiresAt == null) {
            return "never";
        }
        long remainingMs = expiresAt.toEpochMilli() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return "expired";
        }
        long hours = remainingMs / 3_600_000;
        long mins = (remainingMs % 3_600_000) / 60_000;
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }
}
