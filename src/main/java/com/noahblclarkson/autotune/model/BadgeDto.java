package com.noahblclarkson.autotune.model;

import java.time.Instant;

/**
 * A badge earned by a player, enriched with display metadata for the web dashboard.
 *
 * @param badgeType     the badge identifier
 * @param displayName   human-readable name
 * @param description   how this badge is earned
 * @param material      Minecraft material name for the icon
 * @param color         hex color string
 * @param rarity        badge rarity tier
 * @param earnedAt      when the player earned this badge
 */
public record BadgeDto(
        String badgeType,
        String displayName,
        String description,
        String material,
        String color,
        String rarity,
        Instant earnedAt
) {
    private static String rarity(BadgeType type) {
        return switch (type) {
            case FIRST_SALE, FIRST_BUYER, LOAN_TAKER -> "common";
            case CENTURION, LOAN_SHARK -> "uncommon";
            case BIG_SPENDER, MARKET_MAKER -> "rare";
            case HOARDER, DIVERSIFIED, TREND_SPOTTER -> "epic";
            case STABLE_HAND -> "legendary";
            case HOT_STREAK_3 -> "uncommon";
            case HOT_STREAK_7 -> "rare";
            case HOT_STREAK_14 -> "epic";
            case HOT_STREAK_30 -> "legendary";
        };
    }

    private static String color(BadgeType type) {
        return type.getColor().asHexString();
    }

    /**
     * Map a PlayerBadge (persistence model) to a BadgeDto (API model).
     */
    public static BadgeDto from(PlayerBadge badge) {
        return new BadgeDto(
                badge.badgeType().name(),
                badge.badgeType().getDisplayName(),
                badge.badgeType().getDescription(),
                badge.badgeType().getMaterial(),
                color(badge.badgeType()),
                rarity(badge.badgeType()),
                badge.earnedAt()
        );
    }
}
