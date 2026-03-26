package com.noahblclarkson.autotune.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A player-set price alert that fires once when the market price crosses
 * the target threshold in the specified direction.
 *
 * @param id           unique alert ID
 * @param playerUuid   the player who owns this alert
 * @param itemId       the shop item this alert monitors
 * @param alertType    ABOVE (price rises above target) or BELOW (price falls below target)
 * @param targetPrice  the threshold price
 * @param createdAt    when the alert was created
 * @param triggeredAt  when the alert was triggered (null = still active)
 * @param enabled      whether the alert is active
 */
public record PriceAlert(
        String id,
        UUID playerUuid,
        int itemId,
        AlertType alertType,
        BigDecimal targetPrice,
        Instant createdAt,
        Instant triggeredAt,
        boolean enabled
) {

    public enum AlertType {
        ABOVE,  // Trigger when price rises above targetPrice
        BELOW   // Trigger when price falls below targetPrice
    }

    public boolean isTriggered() {
        return triggeredAt != null;
    }

    public boolean isActive() {
        return enabled && !isTriggered();
    }

    /**
     * Returns true if the given market price crosses the threshold
     * for this alert type (and the alert hasn't already triggered).
     */
    public boolean isCrossed(BigDecimal currentPrice) {
        if (!enabled || isTriggered()) {
            return false;
        }
        return switch (alertType) {
            case ABOVE -> currentPrice.compareTo(targetPrice) > 0;
            case BELOW -> currentPrice.compareTo(targetPrice) < 0;
        };
    }

    /**
     * Builder-style copy to mark this alert as triggered.
     */
    public PriceAlert withTriggered() {
        return new PriceAlert(id, playerUuid, itemId, alertType, targetPrice, createdAt, Instant.now(), enabled);
    }

    /**
     * Builder-style copy to re-arm a triggered alert.
     */
    public PriceAlert rearm() {
        return new PriceAlert(id, playerUuid, itemId, alertType, targetPrice, createdAt, null, enabled);
    }

    /**
     * Builder-style copy to toggle enabled state.
     */
    public PriceAlert withEnabled(boolean enabled) {
        return new PriceAlert(id, playerUuid, itemId, alertType, targetPrice, createdAt, triggeredAt, enabled);
    }
}
