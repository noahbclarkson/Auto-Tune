"""
Generate 3 shops.yml files for Auto-Tune plugin from market_prices.json.

Price transformation: price = (buy_price ^ 1.1) / 2
This widens the gap between cheap and expensive items, then halves everything.

Outputs:
  - shops.yml          (Default tier, ~120 items, for 10-50 player servers)
  - shops-basic.yml    (Basic tier, ~15 items, for <10 player servers)
  - shops-all.yml      (All tier, every valid item, for 100+ player servers)
"""

import json
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESOURCES_DIR = os.path.join(SCRIPT_DIR, "..", "src", "main", "resources")

# ── Load market data ──────────────────────────────────────────────────────────
with open(os.path.join(SCRIPT_DIR, "market_prices.json"), "r") as f:
    raw_data = json.load(f)


# ── Section mapping (raw JSON section → output YAML section) ──────────────────
SECTION_MAP = {
    "ores": "ores",
    "stone": "building",
    "brick": "building",
    "copper": "building",
    "earth": "building",
    "sand": "building",
    "wood": "wood",
    "drops": "drops",
    "food": "food",
    "utility": "utility",
    "transport": "transport",
    "light": "building",
    "plants": "farming",
    "redstone": "redstone",
    "tools": "tools",
    "weapons": "weapons",
    "armor": "armor",
    "enchantments": "enchantments",
    "brewing": "brewing",
    "ocean": "ocean",
    "nether": "nether",
    "end": "end",
    "deep dark": "deep_dark",
    "archaeology": "archaeology",
    "ice": "building",
    "dyed": "dyed",
    "discs": "discs",
}

# ── Items to SKIP entirely (need NBT data, can't be simple materials) ─────────
SKIP_PREFIXES = [
    "potion_of_",
    "splash_potion_of_",
    "lingering_potion_of_",
    "arrow_of_",
    "enchanted_book_",
]

SKIP_EXACT = {
    # Base potions need PotionMeta
    "water_bottle", "awkward_potion", "mundane_potion", "thick_potion",
    "lingering_water_bottle", "splash_water_bottle",
    "awkward_lingering_potion", "awkward_splash_potion",
    # Suspicious stew needs effect NBT
    "suspicious_stew",
    # Remove dragon egg entirely per user request
    "dragon_egg",
}


# ── Manual price overrides (applied AFTER the base transformation) ────────────
# These fix cases where the market data produces clearly wrong relative pricing.
PRICE_OVERRIDES = {
    # Regular golden apple is craftable (8 gold + apple) - should be much less
    # than enchanted golden apple which is uncraftable loot-only
    "GOLDEN_APPLE": 200.0,
    # Enchanted golden apple: extremely rare, uncraftable dungeon loot
    "ENCHANTED_GOLDEN_APPLE": 2500.0,
    # Sea lantern: common in ocean monuments, shouldn't cost more than diamond
    "SEA_LANTERN": 80.0,
    # Magma block: extremely common on nether surface, not a rare block
    "MAGMA_BLOCK": 8.0,
    # Netherite block: should be ~9x netherite ingot (823.5 * 9 = 7411)
    "NETHERITE_BLOCK": 7500.0,
}


def transform_price(buy_price):
    """Apply price transformation: (buy^1.1) / 2"""
    return (buy_price ** 1.1) / 2


def format_price(price):
    """Format price to a clean number."""
    if price >= 100:
        return f"{round(price, 1):.1f}"
    elif price >= 10:
        return f"{round(price, 1):.1f}"
    elif price >= 1:
        return f"{round(price, 2):.2f}"
    else:
        return f"{round(price, 2):.2f}"


def should_skip(item_id):
    """Check if an item should be skipped (needs NBT data)."""
    if item_id in SKIP_EXACT:
        return True
    for prefix in SKIP_PREFIXES:
        if item_id.startswith(prefix):
            return True
    return False


# ── Process all items ─────────────────────────────────────────────────────────
all_items = []
skipped_nbt = 0

for section, items in raw_data.items():
    if section == "enchantments":
        skipped_nbt += len(items)
        continue

    for item in items:
        item_id = item["id"]

        if should_skip(item_id):
            skipped_nbt += 1
            continue

        buy_price = item["prices"]["unit"]["buy"]
        price = transform_price(buy_price)
        material = item_id.upper()

        # Apply manual price overrides
        if material in PRICE_OVERRIDES:
            price = PRICE_OVERRIDES[material]

        yaml_section = SECTION_MAP.get(section, "misc")

        all_items.append({
            "material": material,
            "price": price,
            "section": yaml_section,
            "raw_section": section,
            "original_price": buy_price,
        })

print(f"Processed: {len(all_items)} items")
print(f"Skipped (NBT required): {skipped_nbt}")

# Create lookup by material
item_lookup = {item["material"]: item for item in all_items}


# ── Define tier item lists ────────────────────────────────────────────────────

# BASIC: Core trading resources for small servers (<10 players)
# These are the fundamental materials that drive a Minecraft economy
BASIC_MATERIALS = [
    "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT", "COPPER_INGOT",
    "COAL", "NETHERITE_INGOT", "NETHERITE_SCRAP",
    "LAPIS_LAZULI", "REDSTONE", "QUARTZ", "AMETHYST_SHARD",
    "RAW_GOLD", "RAW_IRON", "RAW_COPPER",
]

# DEFAULT: Curated selection for medium servers (10-50 players)
# Target: ~120 items with good coverage across gameplay styles.
DEFAULT_MATERIALS = BASIC_MATERIALS + [
    # Additional ores & minerals
    "GOLD_NUGGET", "IRON_NUGGET", "ANCIENT_DEBRIS",

    # Building materials
    "COBBLESTONE", "STONE", "DEEPSLATE", "GRANITE", "DIORITE", "ANDESITE",
    "SAND", "RED_SAND", "GRAVEL", "CLAY_BALL", "CLAY",
    "OBSIDIAN", "GLASS", "BRICK", "TUFF", "CALCITE",
    "SANDSTONE", "TERRACOTTA", "MOSSY_COBBLESTONE",

    # Wood (all overworld log types)
    "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "DARK_OAK_LOG",
    "JUNGLE_LOG", "ACACIA_LOG", "MANGROVE_LOG", "CHERRY_LOG",

    # Food & cooking
    "WHEAT", "BREAD", "APPLE", "GOLDEN_APPLE", "GOLDEN_CARROT",
    "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN",
    "COOKED_SALMON", "COOKED_COD", "CARROT", "POTATO", "BEETROOT",
    "MELON_SLICE", "SWEET_BERRIES", "SUGAR_CANE", "COCOA_BEANS",
    "EGG", "PUMPKIN", "SUGAR", "HONEY_BOTTLE",
    "GLISTERING_MELON_SLICE", "DRIED_KELP",

    # Mob drops (core drops only)
    "STRING", "SPIDER_EYE", "GUNPOWDER", "ROTTEN_FLESH", "BONE",
    "ENDER_PEARL", "BLAZE_ROD", "GHAST_TEAR",
    "SLIME_BALL", "LEATHER", "FEATHER", "INK_SAC",
    "PHANTOM_MEMBRANE", "SHULKER_SHELL", "WITHER_SKELETON_SKULL",

    # Farming & plants
    "BONE_MEAL", "WHEAT_SEEDS", "PUMPKIN_SEEDS", "MELON_SEEDS",
    "CACTUS", "BAMBOO",

    # Redstone components
    "PISTON", "STICKY_PISTON", "OBSERVER", "HOPPER",
    "DROPPER", "DISPENSER", "REPEATER", "COMPARATOR",

    # Nether materials
    "NETHER_WART", "GLOWSTONE", "GLOWSTONE_DUST", "SOUL_SAND",
    "CRYING_OBSIDIAN", "BLACKSTONE", "BASALT",

    # Ocean materials
    "NAUTILUS_SHELL", "HEART_OF_THE_SEA", "SPONGE",
    "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS",

    # End items
    "END_STONE", "CHORUS_FRUIT", "SHULKER_BOX", "ENDER_CHEST",

    # Utility items
    "BOOK", "PAPER", "NAME_TAG", "SADDLE", "LEAD",
    "EXPERIENCE_BOTTLE", "TNT", "CHARCOAL", "TORCH", "LANTERN",
    "BOOKSHELF", "CHEST", "FURNACE", "BUCKET", "IRON_BARS",
]


# ── Section display order ────────────────────────────────────────────────────
SECTION_ORDER = [
    "ores", "building", "wood", "food", "drops", "farming",
    "redstone", "nether", "ocean", "end", "deep_dark",
    "utility", "transport", "tools", "weapons", "armor",
    "brewing", "archaeology", "discs", "dyed", "misc",
]

# Section display names for YAML comments
SECTION_DISPLAY = {
    "ores": "Ores & Minerals",
    "building": "Building Blocks",
    "wood": "Wood & Logs",
    "food": "Food & Cooking",
    "drops": "Mob Drops",
    "farming": "Farming & Plants",
    "redstone": "Redstone",
    "nether": "Nether",
    "ocean": "Ocean",
    "end": "End",
    "deep_dark": "Deep Dark",
    "utility": "Utility",
    "transport": "Transport",
    "tools": "Tools",
    "weapons": "Weapons",
    "armor": "Armor",
    "brewing": "Brewing",
    "archaeology": "Archaeology",
    "discs": "Music Discs",
    "dyed": "Dyed Items",
    "misc": "Miscellaneous",
}


def generate_yaml(items_list, header_lines):
    """Generate a shops.yml formatted string from a list of items."""
    lines = []
    for line in header_lines:
        lines.append(line)
    lines.append("")
    lines.append("items:")

    # Group items by section
    sections = {}
    for item in items_list:
        sec = item["section"]
        if sec not in sections:
            sections[sec] = []
        sections[sec].append(item)

    # Sort sections by defined order
    sorted_sections = []
    for sec in SECTION_ORDER:
        if sec in sections:
            sorted_sections.append(sec)
    # Add any remaining sections not in order
    for sec in sections:
        if sec not in sorted_sections:
            sorted_sections.append(sec)

    first_section = True
    for sec in sorted_sections:
        items = sections[sec]
        # Sort items within section by price descending (most valuable first)
        items.sort(key=lambda x: x["price"], reverse=True)

        display_name = SECTION_DISPLAY.get(sec, sec.replace("_", " ").title())
        if not first_section:
            lines.append("")
        lines.append(f"  # === {display_name} ===")
        first_section = False

        for item in items:
            lines.append(f"  - material: {item['material']}")
            lines.append(f"    price: {format_price(item['price'])}")
            lines.append(f"    section: {sec}")

    lines.append("")
    return "\n".join(lines)


# Section overrides for curated tiers (basic/default).
# The market data classifies some items in sections that don't match
# how players expect to find them in a curated shop.
CURATED_SECTION_OVERRIDES = {
    "GLASS": "building",
    "OBSIDIAN": "building",
    "HOPPER": "redstone",
    "GLOWSTONE": "nether",
    "GLOWSTONE_DUST": "nether",
    "SHROOMLIGHT": "nether",
    "NETHER_GOLD_ORE": "ores",
    "COCOA_BEANS": "food",
    "SUGAR_CANE": "food",
    "PUMPKIN": "food",
    "SUGAR": "food",
    "DRIED_KELP": "food",
    "HONEY_BOTTLE": "food",
    "GLISTERING_MELON_SLICE": "food",
    "CACTUS": "farming",
    "BAMBOO": "farming",
    "BONE_MEAL": "farming",
    "WHEAT_SEEDS": "farming",
    "PUMPKIN_SEEDS": "farming",
    "MELON_SEEDS": "farming",
    "NAUTILUS_SHELL": "ocean",
    "SADDLE": "utility",
    "SEA_LANTERN": "ocean",
    "CHORUS_FRUIT": "end",
    "NETHER_BRICK": "nether",
    "QUARTZ": "ores",
    "CHARCOAL": "utility",
    "TORCH": "utility",
    "LANTERN": "utility",
    "MOSSY_COBBLESTONE": "building",
    "TERRACOTTA": "building",
}


def build_item_list(material_names, apply_overrides=False):
    """Build item list from material names, only including items that exist in data."""
    result = []
    missing = []
    for mat in material_names:
        if mat in item_lookup:
            item = dict(item_lookup[mat])  # copy so we don't modify the original
            if apply_overrides and mat in CURATED_SECTION_OVERRIDES:
                item["section"] = CURATED_SECTION_OVERRIDES[mat]
            result.append(item)
        else:
            missing.append(mat)
    if missing:
        print(f"  Missing from market data: {missing}")
    return result


# ── Generate BASIC tier ───────────────────────────────────────────────────────
print(f"\n--- BASIC TIER ---")
basic_items = build_item_list(BASIC_MATERIALS, apply_overrides=True)
print(f"  Items: {len(basic_items)}")

basic_header = [
    "# ============================================",
    "# Auto-Tune Shop Items - BASIC",
    "# ============================================",
    "# Minimal economy preset for small servers (<10 players).",
    "# Only core ores and minerals are tradeable.",
    "# This keeps inter-player trading intact for most items",
    "# while providing a dynamic market for the fundamentals.",
    "#",
    "# To use: rename this file to shops.yml and restart.",
    "# Prices are starting values only - the market engine",
    "# adjusts them dynamically based on player trading.",
    "# ============================================",
]

basic_yaml = generate_yaml(basic_items, basic_header)

# ── Generate DEFAULT tier ─────────────────────────────────────────────────────
print(f"\n--- DEFAULT TIER ---")
default_items = build_item_list(DEFAULT_MATERIALS, apply_overrides=True)
print(f"  Items: {len(default_items)}")

default_header = [
    "# ============================================",
    "# Auto-Tune Default Shop Items",
    "# ============================================",
    "# Balanced economy preset for medium servers (10-50 players).",
    "# Covers all major item categories with a curated selection.",
    "# Good for most servers - provides enough variety for",
    "# meaningful price discovery and some arbitrage opportunities.",
    "#",
    "# These items are loaded on first run when the database is empty.",
    "# Prices are starting values only - the market engine",
    "# adjusts them dynamically based on player trading.",
    "# ============================================",
]

default_yaml = generate_yaml(default_items, default_header)

# ── Generate ALL tier ─────────────────────────────────────────────────────────
print(f"\n--- ALL TIER ---")
all_valid_items = all_items  # already filtered
print(f"  Items: {len(all_valid_items)}")

all_header = [
    "# ============================================",
    "# Auto-Tune Shop Items - ALL",
    "# ============================================",
    "# Complete economy preset for large servers (100+ players).",
    "# Contains every tradeable item in Minecraft.",
    "# Use with caution - a fully dynamic economy requires",
    "# high player counts to avoid price manipulation.",
    "#",
    "# Invalid materials are automatically skipped on load.",
    "#",
    "# To use: rename this file to shops.yml and restart.",
    "# Prices are starting values only - the market engine",
    "# adjusts them dynamically based on player trading.",
    "# ============================================",
]

all_yaml = generate_yaml(all_valid_items, all_header)

# ── Write files ───────────────────────────────────────────────────────────────
with open(os.path.join(RESOURCES_DIR, "shops-basic.yml"), "w", newline="\n") as f:
    f.write(basic_yaml)
print(f"\nWrote shops-basic.yml")

with open(os.path.join(RESOURCES_DIR, "shops.yml"), "w", newline="\n") as f:
    f.write(default_yaml)
print(f"Wrote shops.yml")

with open(os.path.join(RESOURCES_DIR, "shops-all.yml"), "w", newline="\n") as f:
    f.write(all_yaml)
print(f"Wrote shops-all.yml")

# ── Print summary ────────────────────────────────────────────────────────────
print(f"\n=== FINAL SUMMARY ===")
print(f"Basic:   {len(basic_items)} items")
print(f"Default: {len(default_items)} items")
print(f"All:     {len(all_valid_items)} items")

# Show applied overrides
print(f"\nManual price overrides applied:")
for mat, price in PRICE_OVERRIDES.items():
    if mat in item_lookup:
        orig = item_lookup[mat]["original_price"]
        auto = transform_price(orig)
        print(f"  {mat}: {format_price(auto)} (auto) -> {format_price(price)} (override)")
