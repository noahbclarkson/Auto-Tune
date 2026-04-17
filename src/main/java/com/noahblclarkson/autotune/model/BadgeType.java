package com.noahblclarkson.autotune.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Achievement badges earned through market activity.
 * Each badge has a description, a Minecraft material icon, and a color.
 */
public enum BadgeType {

    FIRST_SALE(
            "First Sale",
            "Sold your first item on the market",
            "PAPER",
            NamedTextColor.GREEN,
            0
    ),
    LOAN_SHARK(
            "Loan Shark",
            "Fully repaid a loan of 100,000 or more",
            "GOLD_INGOT",
            NamedTextColor.YELLOW,
            100_000
    ),
    MARKET_MAKER(
            "Market Maker",
            "Traded across 10 or more different items",
            "EMERALD",
            NamedTextColor.DARK_GREEN,
            10
    ),
    HOARDER(
            "Hoarder",
            "Held 50 or more items in your autosell inventory at once",
            "CHEST",
            NamedTextColor.GOLD,
            50
    ),
    TREND_SPOTTER(
            "Trend Spotter",
            "Had a price alert fire with the price moving as predicted",
            "CLOCK",
            NamedTextColor.AQUA,
            0
    ),
    STABLE_HAND(
            "Stable Hand",
            "Remained active for 7 days without ever defaulting on a loan",
            "FLOWER_BANNER_PATTERN",
            NamedTextColor.LIGHT_PURPLE,
            7
    ),
    BIG_SPENDER(
            "Big Spender",
            "Completed a single transaction worth 1,000,000 or more",
            "GOLD_BLOCK",
            TextColor.fromHexString("#FFA500"),
            1_000_000
    ),
    DIVERSIFIED(
            "Diversified",
            "Held positions in 5 or more different item sections simultaneously",
            "RAINBOW_BANNER_PATTERN",
            NamedTextColor.DARK_AQUA,
            5
    ),
    CENTURION(
            "Centurion",
            "Completed 100 or more transactions",
            "NETHER_STAR",
            NamedTextColor.WHITE,
            100
    ),
    FIRST_BUYER(
            "First Buyer",
            "Bought your first item from the market",
            "SHOP_USE",
            NamedTextColor.BLUE,
            0
    ),
    LOAN_TAKER(
            "Loan Taker",
            "Took out your first loan",
            "WRITABLE_BOOK",
            NamedTextColor.DARK_BLUE,
            1
    ),
    HOT_STREAK_3(
            "Hot Streak",
            "Traded for 3 consecutive days",
            "BLAZE_POWDER",
            NamedTextColor.GOLD,
            3
    ),
    HOT_STREAK_7(
            "On Fire",
            "Traded for 7 consecutive days",
            "BLAZE_ROD",
            TextColor.fromHexString("#FF6600"),
            7
    ),
    HOT_STREAK_14(
            "Unstoppable",
            "Traded for 14 consecutive days",
            "MAGMA_BLOCK",
            TextColor.fromHexString("#FF3300"),
            14
    ),
    HOT_STREAK_30(
            "Legendary Trader",
            "Traded for 30 consecutive days",
            "NETHER_STAR",
            TextColor.fromHexString("#FF0000"),
            30
    );

    private final String displayName;
    private final String description;
    private final String material;
    private final TextColor color;
    private final double threshold;

    BadgeType(String displayName, String description, String material, TextColor color, double threshold) {
        this.displayName = displayName;
        this.description = description;
        this.material = material;
        this.color = color;
        this.threshold = threshold;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getMaterial() {
        return material;
    }

    public TextColor getColor() {
        return color;
    }

    /** For numeric badges, the threshold that must be met or exceeded. Zero = no threshold. */
    public double getThreshold() {
        return threshold;
    }

    /**
     * Returns a colored display Component for this badge's name.
     */
    public Component displayNameComponent() {
        return Component.text(displayName, color);
    }

    /**
     * Returns the material name used to render this badge's icon in GUIs.
     */
    public String iconMaterial() {
        return material;
    }
}
