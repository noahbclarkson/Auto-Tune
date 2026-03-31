package com.noahblclarkson.autotune.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A badge earned by a player, with the type and when it was earned.
 *
 * @param playerUuid  the player who earned this badge
 * @param badgeType  which badge was earned
 * @param earnedAt   when it was earned
 */
public record PlayerBadge(
        UUID playerUuid,
        BadgeType badgeType,
        Instant earnedAt
) {
}
