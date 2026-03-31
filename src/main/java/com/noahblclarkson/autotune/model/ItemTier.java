package com.noahblclarkson.autotune.model;

/**
 * Item rarity tier for spread and price-change multipliers.
 * Higher tiers get wider spreads and more volatile price changes —
 * rare items are inherently harder to trade fairly.
 *
 * Default classification:
 * - COMMON:    raw materials (stone, dirt, wood, sand, gravel, etc.)
 * - UNCOMMON:  processed materials (iron ingot, gold ingot, brick, etc.)
 * - RARE:      valuable materials (diamond, emerald, netherite, quartz, etc.)
 * - EPIC:      crafted valuable items (enchanted gear, potions, etc.)
 * - LEGENDARY: end-game items (nether star, dragon breath, elytra, shulker boxes, etc.)
 */
public enum ItemTier {
    COMMON(1.0, 1.0),
    UNCOMMON(1.2, 1.1),
    RARE(1.5, 1.25),
    EPIC(1.8, 1.4),
    LEGENDARY(2.2, 1.6);

    /** Multiplier applied to base spread for items of this tier. */
    public final double spreadMultiplier;

    /** Multiplier applied to max price change percent for items of this tier. */
    public final double maxPriceChangeMultiplier;

    ItemTier(double spreadMultiplier, double maxPriceChangeMultiplier) {
        this.spreadMultiplier = spreadMultiplier;
        this.maxPriceChangeMultiplier = maxPriceChangeMultiplier;
    }

    /**
     * Returns the default tier for a material based on its name.
     * Materials not explicitly classified default to COMMON.
     * Admin overrides (stored in DB) take precedence over this.
     */
    public static ItemTier defaultTierFor(String materialName) {
        return switch (materialName) {
            // LEGENDARY — end-game / raid / boss loot
            case "NETHER_STAR", "DRAGON_BREATH", "ELYTRA", "SHULKER_BOX", "SHULKER_BOX_ITEM",
                 "ENDER_PEARL", "ENDER_EYE", "NETHER_WART", "GHAST_TEAR", "BLAZE_ROD",
                 "MAGMA_CREAM", "BEACON", "CONDUIT", "HEART_OF_THE_SEA",
                 "NAUTIL_SHELL", "PHANTOM_MEMBRANE", "FOX_SPAWN_EGG",
                 "ALLAY_SPAWN_EGG", "WARDEN_SPAWN_EGG",
                 // Boss-related
                 "WITHER_SKELETON_SKULL", "WITHER_SKELETON_SKULL_ITEM",
                 "DRAGON_HEAD", "DRAGON_EGG",
                 // Collections that are genuinely rare
                 "RABBIT_HIDE", "RABBIT_FOOT", "NAUSEA_APPLE" -> ItemTier.LEGENDARY;

            // EPIC — enchanted gear, potions, expensive crafted items
            case "DIAMOND_SWORD", "DIAMOND_PICKAXE", "DIAMOND_AXE", "DIAMOND_SHOVEL",
                 "DIAMOND_HOE", "DIAMOND_HELMET", "DIAMOND_CHESTPLATE",
                 "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
                 "NETHERITE_SWORD", "NETHERITE_PICKAXE", "NETHERITE_AXE",
                 "NETHERITE_SHOVEL", "NETHERITE_HOE",
                 "NETHERITE_HELMET", "NETHERITE_CHESTPLATE",
                 "NETHERITE_LEGGINGS", "NETHERITE_BOOTS",
                 "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE",
                 "FIRE_CHARGE", "FIREWORK_ROCKET", "BOOK_AND_QUILL",
                 "WRITTEN_BOOK", "NAME_TAG", "LEAD", "SADDLE",
                 "SNOWBALL", "EGG", "BOW", "CROSSBOW", "TRIDENT",
                 "SHIELD", "TOTEM_OF_UNDYING", "LINGERING_POTION",
                 "SPLASH_POTION", "POTION", "EXPERIENCE_BOTTLE",
                 "BLUE_BUNDLE", "BUNDLE",
                 "NETHERITE_INGOT", "NETHERITE_SCRAP",
                 // Cosmetic / functional end-game
                 "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE",
                 "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS" -> ItemTier.EPIC;

            // RARE — valuable raw materials (mined or mob-dropped)
            case "DIAMOND", "EMERALD", "NETHER_QUARTZ", "GLOWSTONE_DUST",
                 "BLAZE_POWDER", "PRISMARINE_SHARD",
                 "PRISMARINE_CRYSTALS", "NAUTILUS_SHELL",
                 "AMETHYST_SHARD", "CALCITE", "TUFF", "DEEPSLATE",
                 "RAW_GOLD", "RAW_GOLD_BLOCK", "RAW_IRON", "RAW_IRON_BLOCK",
                 "ANCIENT_DEBRIS", "DEBRIS", "CRYING_OBSIDIAN",
                 "GLOW_INK_SAC", "GLOW_ITEM_FRAME", "ITEM_FRAME",
                 "HONEYCOMB", "HONEYCOMB_BLOCK", "HONEY_BLOCK",
                 "SLIME_BALL", "SLIME_BLOCK", "FERMENTED_SPIDER_EYE",
                 "MOSS_BLOCK", "ROOTED_DIRT", "DRIPSTONE_BLOCK" -> ItemTier.RARE;

            // UNCOMMON — processed materials, intermediate crafts, dyed items
            case "IRON_INGOT", "GOLD_INGOT", "BRICK", "NETHER_BRICK",
                 "BRICK_ITEM", "NETHER_BRICK_ITEM",
                 "PAPER", "BOOK", "BOOKSHELF", "FURNACE", "CHEST",
                 "ENDER_CHEST", "TRAPPED_CHEST", "HOPPER", "DROPPER",
                 "DISPENSER", "PISTON", "STICKY_PISTON", "OBSERVER",
                 "HOPPER_MINECART", "RAIL", "POWERED_RAIL", "DETECTOR_RAIL",
                 "ACTIVATOR_RAIL", "MINECART", "CHEST_MINECART",
                 "COMPARATOR", "REPEATER", "DAYLIGHT_DETECTOR",
                 "LECTERN", "CAULDRON", "BREWING_STAND",
                 "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL",
                 "GRINDSTONE", "STONECUTTER", "LOOM", "CARTOGRAPHY_TABLE",
                 "SMITHING_TABLE", "FLETCHING_TABLE",
                 "LAPIS_LAZULI", "LAPIS_BLOCK", "IRON_BLOCK", "GOLD_BLOCK",
                 "DIAMOND_BLOCK", "EMERALD_BLOCK", "NETHERITE_BLOCK",
                 "COAL_BLOCK", "REDSTONE_BLOCK", "COBBLESTONE_BLOCK",
                 "STONE_BRICKS", "STONE_BRICK_SLAB", "STONE_BRICK_STAIRS",
                 "IRON_DOOR", "GOLDEN_RAIL", "LIGHT",
                 "SPYGLASS", "GOLDEN_HELMET", "GOLDEN_CHESTPLATE",
                 "GOLDEN_LEGGINGS", "GOLDEN_BOOTS",
                 "LEATHER_HELMET", "LEATHER_CHESTPLATE",
                 "LEATHER_LEGGINGS", "LEATHER_BOOTS",
                 "IRON_HELMET", "IRON_CHESTPLATE",
                 "IRON_LEGGINGS", "IRON_BOOTS",
                 "TURTLE_HELMET", "SCUTE",
                 "FEATHER", "FLINT", "STRING", "WOOL", "CARPET",
                 "PAINTING", "ARROW", "SPECTRAL_ARROW",
                 "COOKIE", "CAKE", "BREAD", "GOLDEN_CARROT",
                 "BEETROOT_SOUP", "RABBIT_STEW", "MUSHROOM_STEW",
                 "SUSPICIOUS_STEW", "PUMPKIN_PIE", "SWEET_BERRIES",
                 "GLOW_BERRIES", "DRIED_KELP", "DRIED_KELP_BLOCK",
                 "ROTTEN_FLESH", "PORKCHOP", "COOKED_PORKCHOP",
                 "BEEF", "COOKED_BEEF", "CHICKEN", "COOKED_CHICKEN",
                 "MUTTON", "COOKED_MUTTON", "RABBIT", "COOKED_RABBIT",
                 "COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH",
                 "COOKED_COD", "COOKED_SALMON",
                 "MELON_SEEDS", "PUMPKIN_SEEDS", "WHEAT_SEEDS" -> ItemTier.UNCOMMON;

            // Everything else → COMMON
            default -> ItemTier.COMMON;
        };
    }
}
