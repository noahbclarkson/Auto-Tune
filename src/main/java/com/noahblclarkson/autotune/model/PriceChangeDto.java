package com.noahblclarkson.autotune.model;

import java.time.Instant;

/**
 * Represents a price history point with its computed attribution —
 * what caused the price to move at this point in time.
 *
 * Attribution is computed by comparing the price change against:
 * - Active market events (event multiplier explains unusual change)
 * - Volume spikes (unusually high trading volume)
 * - Natural market forces (normal trading activity)
 *
 * Used by the web dashboard to show "What moved this price?" on item detail pages.
 */
public record PriceChangeDto(
        /** Unix timestamp (millis) of this price point */
        long timestamp,
        /** Mid price at this point */
        double currentPrice,
        /** Mid price in the previous period */
        double previousPrice,
        /** Percent change from previous to current (e.g., -5.2 or +8.1) */
        double percentChange,
        /** Buy premium (BPD) at this point */
        double bpd,
        /** Sell premium (SPD) at this point */
        double spd,
        /** Total volume (buys + sells) in this period */
        int totalVolume,
        /** Volume as a multiple of the historical average volume (e.g., 2.3 = 2.3× normal) */
        double volumeVsNormal,
        /** Active event multiplier active at this time (1.0 = no event) */
        double eventMultiplier,
        /** Human-readable attribution label */
        String attribution,
        /** Short code for programmatic styling: NORMAL, EVENT, VOLUME, TREND, STABLE */
        String attributionKey,
        /** True if an active market event was affecting this item at this time */
        boolean hasActiveEvent
) {
    /**
     * Returns an emoji representing the attribution type.
     */
    public String emoji() {
        return switch (attributionKey) {
            case "EVENT" -> "🎯";
            case "VOLUME" -> "🔥";
            case "TREND" -> "📈";
            case "STABLE" -> "➖";
            default -> "⚖️";
        };
    }
}