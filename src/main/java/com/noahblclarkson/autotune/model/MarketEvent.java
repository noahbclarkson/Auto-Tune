package com.noahblclarkson.autotune.model;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Represents a scheduled or active market event that modifies price behavior
 * for matching items during its duration.
 *
 * Events affect price velocity — they amplify or dampen price changes for
 * affected items, making the economy feel dynamic and alive.
 */
public record MarketEvent(
        /** Unique event ID */
        UUID id,
        /** Human-readable name */
        String name,
        /** Event type — determines the direction of the price effect */
        EventType type,
        /** Materials affected (exact names or wildcard patterns) */
        List<String> materials,
        /** Price change multiplier (e.g., 2.0 = 2× price change velocity) */
        double priceMultiplier,
        /** When the event starts */
        Instant startsAt,
        /** When the event ends */
        Instant endsAt,
        /** Message broadcast to players when event starts */
        String startMessage,
        /** Message broadcast when event ends */
        String endMessage,
        /** Who created this event */
        String createdBy,
        /** Event status */
        Status status,
        /** Cron expression for repeating events (null = one-shot) */
        String cronExpression,
        /** Items created/removed counter (for tracking scope) */
        int tickCount
) {
    public enum EventType {
        /** Buy prices are boosted — good time for players to sell */
        DEMAND_SURGE,
        /** Sell prices are boosted — players get more for their items */
        SUPPLY_GLUT,
        /** All prices drift upward — inflation event */
        INFLATION_BOOST,
        /** All prices drift downward — deflation event */
        DEFLATION_DROP,
        /** Specific items become more valuable to buy (buy price up) */
        GOLD_RUSH,
        /** Custom — admin sets arbitrary multiplier */
        CUSTOM
    }

    public enum Status {
        SCHEDULED,
        ACTIVE,
        ENDED,
        CANCELLED
    }

    /**
     * Returns the effective multiplier for this event's type.
     * For CUSTOM events, returns the configured priceMultiplier field.
     */
    public double effectiveMultiplier() {
        return priceMultiplier;
    }

    /**
     * Returns true if this event is currently active (within its time window).
     */
    public boolean isActive(Instant now) {
        return status == Status.ACTIVE
                && !now.isBefore(startsAt)
                && now.isBefore(endsAt);
    }

    /**
     * Returns true if the given material matches any of this event's material patterns.
     */
    public boolean matchesMaterial(String material) {
        if (materials == null || materials.isEmpty()) {
            return false;
        }
        for (String pattern : materials) {
            if (matchesPattern(material, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPattern(String material, String pattern) {
        // Exact match
        if (material.equalsIgnoreCase(pattern)) {
            return true;
        }
        // Wildcard suffix: "GOLD_*"
        if (pattern.endsWith("_*")) {
            String prefix = pattern.substring(0, pattern.length() - 2).toUpperCase(Locale.ROOT);
            return material.toUpperCase(Locale.ROOT).startsWith(prefix + "_");
        }
        // Wildcard prefix: "*_INGOT"
        if (pattern.startsWith("*_")) {
            String suffix = pattern.substring(2).toUpperCase(Locale.ROOT);
            return material.toUpperCase(Locale.ROOT).endsWith("_" + suffix);
        }
        // Contains: "*GOLD*" or exact
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            String mid = pattern.substring(1, pattern.length() - 1).toUpperCase(Locale.ROOT);
            return material.toUpperCase(Locale.ROOT).contains(mid);
        }
        return false;
    }

    /**
     * Returns the multiplier to apply to a price change delta.
     * For DEMAND_SURGE: amplifies upward (buy pressure)
     * For SUPPLY_GLUT: amplifies downward (sell pressure)  
     * For GOLD_RUSH: amplifies upward
     * For INFLATION_BOOST: always positive (upward drift)
     * For DEFLATION_DROP: always negative (downward drift)
     */
    public double priceChangeMultiplier() {
        return priceMultiplier;
    }

    public MarketEvent withStatus(Status newStatus) {
        return new MarketEvent(
                id, name, type, materials, priceMultiplier,
                startsAt, endsAt, startMessage, endMessage,
                createdBy, newStatus, cronExpression, tickCount
        );
    }

    public MarketEvent withTickCount(int count) {
        return new MarketEvent(
                id, name, type, materials, priceMultiplier,
                startsAt, endsAt, startMessage, endMessage,
                createdBy, status, cronExpression, count
        );
    }
}
