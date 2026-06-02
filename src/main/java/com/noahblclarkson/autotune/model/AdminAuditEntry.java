package com.noahblclarkson.autotune.model;

import java.time.Instant;
import java.util.Optional;

/** A single admin action recorded in the audit log. */
public record AdminAuditEntry(
        long id,
        Instant timestamp,
        String adminUuid,
        String adminName,
        ActionType actionType,
        String target,
        String oldValue,
        String newValue,
        String details
) {
    public enum ActionType {
        MARKET_FREEZE,
        MARKET_UNFREEZE,
        PRICE_SET,
        PRICE_REMOVE,
        ITEM_FLOOR,
        ITEM_CEILING,
        CONFIG_RELOAD,
        EVENT_TRIGGER,
        EVENT_CANCEL,
        ITEM_SPREAD,
        ITEM_MAX_CHANGE,
        DATABASE_CLEANUP,
        OTHER
    }

    /** Human-readable one-liner for display in the audit log command. */
    public String toSummary() {
        return switch (actionType) {
            case MARKET_FREEZE -> "❄️ Froze market prices";
            case MARKET_UNFREEZE -> "▶️ Unfroze market prices";
            case PRICE_SET -> String.format("💰 Set %s price to %s (was %s)",
                    target, newValue, Optional.ofNullable(oldValue).orElse("unset"));
            case PRICE_REMOVE -> "🗑️ Removed price override for " + target;
            case ITEM_FLOOR -> String.format("⬇️ Set floor on %s to %s (was %s)",
                    target, newValue, Optional.ofNullable(oldValue).orElse("none"));
            case ITEM_CEILING -> String.format("⬆️ Set ceiling on %s to %s (was %s)",
                    target, newValue, Optional.ofNullable(oldValue).orElse("none"));
            case CONFIG_RELOAD -> "🔄 Reloaded config";
            case EVENT_TRIGGER -> "🎯 Triggered event: " + target;
            case EVENT_CANCEL -> "⏹ Cancelled event: " + target;
            case ITEM_SPREAD -> String.format("📐 Set spread on %s to %s",
                    target, newValue);
            case ITEM_MAX_CHANGE -> String.format("⚡ Set max-price-change on %s to %s",
                    target, newValue);
            case DATABASE_CLEANUP -> "🧹 Ran database cleanup: " + details;
            case OTHER -> details;
        };
    }
}
