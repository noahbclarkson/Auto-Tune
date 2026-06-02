use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SimConfig {
    pub economy: EconomyConfig,
    pub spread: SpreadConfig,
    pub player_scaling: PlayerScalingConfig,
    pub loans: LoanConfig,
    pub items: Vec<ItemConfig>,
    /// Tick at which a fraction of players quit the server (simulates mass exodus).
    /// None = no exodus (players stay for entire simulation).
    pub player_exodus_tick: Option<u64>,
    /// Fraction of players that quit when exodus_tick is reached (0.0 to 1.0).
    /// Players with highest outstanding debt quit first (most realistic).
    pub player_exodus_fraction: f64,
    /// Multiplier applied to base_spread for `spread_shock_duration` ticks after exodus.
    /// Models reduced liquidity and market panic when players quit. Default 2.0x.
    pub exodus_spread_multiplier: f64,
    /// Number of ticks the spread shock lasts before decaying (spread decay: 5%/tick).
    /// Default 288 (1 day). Set to 0 to disable shock.
    pub exodus_shock_duration_ticks: u64,
    /// If set, only players of this archetype quit during exodus.
    /// Overrides exodus_fraction — all players of this archetype quit.
    /// Examples: "MarketMaker", "GuildBuyer", "Casual".
    pub exodus_target_archetype: Option<String>,
    /// MarketMaker initial capital range. If set, overrides the default $50-200K.
    /// Recommended: $200-300K so MMs don't need opening loans.
    pub mm_initial_capital_min: Option<f64>,
    pub mm_initial_capital_max: Option<f64>,
    /// GuildSeller Phase 2 dip threshold. When set, GuildSellers use price-dip detection
    /// in Phase 2: sell when price < perceived * (1 - threshold).
    /// This enables the redesigned Phase 2 (anti-oversupply mechanism).
    /// None = use random per-instance value (legacy behavior).
    pub guild_phase2_dip_threshold: Option<f64>,
    /// If true, GuildBuyers use rolling VWAP as their price target instead of
    /// subjective perceived_value. VWAP tracks actual transaction prices, making
    /// the price-dip trigger more grounded in real market activity rather than
    /// drifting perceived values. May reduce D/G oscillation in stable economies.
    pub guild_vwap_targets: bool,
    /// Trigger: sell-side volume exceeds this fraction of circulating supply in a tick.
    /// For example, 0.10 means a sell-wall exceeding 10% of total item supply triggers
    /// spread shock. Applied per-item. None = disable (no automatic shock trigger).
    /// Maps to auto-triggering spread shock when whale-like exodus events occur.
    #[serde(default = "default_whale_spread_shock_trigger_bps")]
    pub whale_spread_shock_trigger_bps: f64,
    /// Multiplier applied to base_spread during whale-triggered spread shock.
    /// Stacks on top of any existing exodus spread shock. Default 2.5x.
    #[serde(default = "default_whale_spread_shock_multiplier")]
    pub whale_spread_shock_multiplier: f64,
    /// Duration in ticks for whale-triggered spread shock (decays 5%/tick).
    /// Default 288 ticks = 1 day. Stacks with exodus_shock_duration_ticks.
    #[serde(default = "default_whale_spread_shock_duration_ticks")]
    pub whale_spread_shock_duration_ticks: u64,
    /// High-value item sell cooldown: minimum ticks between Whale sells of Epic+
    /// items (Tier >= Rare). Prevents continuous dumping of Diamond/Netherite.
    /// None = no cooldown (Whale can dump high-value items every tick).
    /// Default: Some(12) — at least 12 ticks (6h) between high-value sells.
    #[serde(default)]
    pub whale_high_value_sell_cooldown_ticks: Option<u64>,
    /// Maximum quantity a Whale can sell in a single tick per item.
    /// None = no cap (whale dumps all inventory in one tick, causing sell-wall shocks).
    /// Setting a cap (e.g. Some(500)) spreads whale dumps across multiple ticks,
    /// reducing market shocks. Maps directly to WhaleConfig::max_dump_per_item.
    #[serde(default)]
    pub whale_max_dump_per_item: Option<i32>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct EconomyConfig {
    pub max_price_change_percent: f64,
    pub trade_window_days: i32,
    pub slippage_coeff: f64,
    pub sell_pressure_multiplier: f64,
    pub sector_correlation: f64,
    pub player_rate_limit_multiplier: f64,
    pub trend_dampening: f64,
    pub trend_streak_threshold_percent: f64,
    pub trend_dampening_floor: f64,
    pub adaptive_window: bool,
    pub min_window_days: i32,
    pub max_window_days: i32,
    pub max_sector_correlation_group_size: usize,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SpreadConfig {
    pub base_spread: f64,
    pub volume_impact: f64,
    pub player_impact: f64,
    pub liquidity_coeff: f64,
    pub liquidity_full_effect_traders: i32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct PlayerScalingConfig {
    pub full_effect_players: i32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LoanConfig {
    pub enabled: bool,
    pub base_interest_rate: f64,
    pub credit_score_modifier: bool,
    pub max_loan_multiplier: f64,
    pub min_credit_score: i32,
    pub default_duration_days: i32,
    pub compound_interval_hours: i32,
    pub default_penalty: i32,
    /// Tiered debt/GDP circuit breaker.
    /// Above tier1 → interest capped at tier1_cap (50%).
    /// Above tier2 → interest capped at tier2_cap (25%).
    /// Above tier3 → interest fully paused.
    /// Set tier3 to 0.0 to disable all circuit breaking.
    pub debt_gdp_tier1_ratio: f64,
    pub debt_gdp_tier2_ratio: f64,
    pub debt_gdp_tier3_ratio: f64,
    pub tier1_interest_cap: f64,
    pub tier2_interest_cap: f64,
    /// Per-loan GDP cap: no single loan can exceed economy GDP × this factor.
    /// Set to 0.0 to disable. Default 1.0 (matches Java LoanManager.singleLoanGdpCap).
    pub single_loan_gdp_cap: f64,
    /// Post-default cooldown: players cannot take new loans within this many hours
    /// of a loan default. Prevents immediate re-borrowing after defaulting.
    /// Set to 0 to disable. Default 168 (7 days, matches Java LoanManager).
    pub post_default_cooldown_hours: i32,
    /// Whether MarketMaker archetype players can take opening loans.
    /// When false, MM players start with initial capital only and cannot borrow.
    /// Rationale: MM's critical role in economy stability means their opening loans
    /// can cascade catastrophically. Bounding MM loans (single_loan_gdp_cap=0.10) backfires —
    /// it worsens D/G by preventing MM's two-sided liquidity provision.
    /// Instead, simply prohibit MM from taking opening loans (MM has $20-100K initial capital).
    /// Default: true (MM can take opening loans, matching historical behavior).
    pub mm_opening_loan_allowed: bool,
    /// Counter-cyclical interest: continuous taper instead of discrete tiered circuit breaker.
    /// When enabled (default, matching Java LoanManager): interestMultiplier = max(MIN, max(0, min(1, 1 - D/G/tier3Ratio))).
    /// Interest falls smoothly from 100% at D/G=0 to MIN at D/G=tier3Ratio.
    /// This prevents the pre-circuit-breaker debt accumulation spiral better than tiered caps.
    /// When disabled: falls back to legacy tiered circuit breaker (TIER1/TIER2/TIER3 caps).
    /// Default: true (matches Java LoanManager.counterCyclical default).
    pub counter_cyclical: bool,
    /// Minimum interest multiplier during counter-cyclical mode.
    /// When the counter-cyclical multiplier would reach 0 (D/G >= tier3Ratio), this floor
    /// prevents total interest pause and the associated D/G oscillation trap.
    /// Set to 0.0 to disable (matches pure counter-cyclical: 0% interest at D/G=tier3Ratio).
    /// Recommended: 0.005 (0.5%) — allows deleveraging to continue even at D/G >= tier3Ratio.
    /// This prevents the economy from getting stuck at D/G ~= tier3Ratio boundary.
    /// Default: 0.0 (matches pure counter-cyclical behavior).
    pub min_interest_multiplier: f64,
    /// TIER3 hysteresis band width: fraction of tier3_ratio.
    /// Once TIER3 fires, the circuit stays locked until D/G drops below
    /// (1 - hysteresis_band) × tier3_ratio. Default 0.5 (50%) means circuit unlocks
    /// when D/G < 50% of tier3_ratio.
    ///
    /// Example: tier3=30, hysteresis=0.5 → unlock at D/G < 15. This gives 50% headroom
    /// above normal D/G~7-10x before circuit re-engages. Prevents the narrow 10% band
    /// (unlock at 27) from allowing debt accumulation during TIER3 lock.
    /// Default: 0.5 (50% of tier3_ratio). Set to 0.0 to disable hysteresis (match tier3).
    pub tier3_hysteresis_band: f64,
    /// GuildBuyer debt cap: each GuildBuyer player's active debt is capped at
    /// economy GDP × this factor. Mirrors Java LoanManager.guildbuyerTotalDebtCap.
    ///
    /// This cap prevents one GB from accumulating disproportionate debt by rejecting
    /// new GB loans when that player's projected active debt would exceed the cap.
    /// It rejects the whole loan request; it does not partially fill to remaining room.
    ///
    /// Set to 0.0 to disable. Default: 3.0 for Java config parity. At the current
    /// total_debt_gdp_cap default (2.0), this 3× per-player cap is usually a guardrail
    /// rather than a binding stabilizer.
    pub guildbuyer_total_debt_cap: f64,
    /// Economy-wide total debt cap: cumulative active debt across all players
    /// is capped at economy GDP × this factor. Mirrors Java LoanManager's
    /// totalDebtGdpCap check and prevents the simulation from allowing system-wide
    /// borrowing levels that production servers reject.
    /// Set to 0.0 to disable. Default: 2.0.
    #[serde(default = "default_total_debt_gdp_cap")]
    pub total_debt_gdp_cap: f64,
    /// Block new MM/GB loans during TIER3 circuit lock.
    ///
    /// When TIER3 fires (D/G >= tier3_ratio), the circuit locks at 0% interest.
    /// New MM/GB loans issued during lock accumulate at 0%, then cascade catastrophically
    /// when the circuit re-enables. This flag prevents MM/GB from taking new loans
    /// while the circuit is locked.
    ///
    /// Rationale: MM and GB players are the primary loan requesters. Blocking them
    /// during lock prevents zero-interest debt accumulation. Casual/Farmer players
    /// continue to service their loans normally, allowing D/G to deleverage.
    ///
    /// Default: false (MM/GB loans allowed during lock — legacy behavior).
    pub block_mm_gb_loans_during_tier3: bool,
    /// TIER3 exit multiplier cap: limits the counter-cyclical multiplier after TIER3 unlock.
    ///
    /// When the TIER3 circuit unlocks (D/G dropped below hysteresis threshold), the
    /// counter-cyclical formula immediately jumps to 53% interest at D/G=14 (tier3=30).
    /// This is too high — debt grows faster than GDP can deleverage, causing immediate
    /// re-trigger within days. This cap prevents that cascade by keeping interest
    /// artificially suppressed during the graduated exit window.
    ///
    /// Example: tier3=30, D/G=14 post-unlock. Raw multiplier = 0.53 (53% daily).
    /// With cap=0.10: multiplier clamped to 0.10. Debt grows 10%/day vs 1% GDP growth
    /// → D/G stabilizes and gradually deleverages. After `tier3_exit_delay_ticks`
    /// (default 4 days), cap expires and normal multiplier resumes.
    ///
    /// Set to 1.0 to disable (returns to raw counter-cyclical formula).
    /// Recommended: 0.10 to 0.20 for production economies.
    /// Default: 0.10 (10% max interest during graduated TIER3 exit).
    pub tier3_exit_multiplier_cap: f64,
    /// Duration (in ticks) of the graduated TIER3 exit cap.
    ///
    /// After TIER3 circuit unlocks, the `tier3_exit_multiplier_cap` applies for this many
    /// ticks before the normal counter-cyclical multiplier resumes.
    ///
    /// Default: 1152 ticks (4 days at 288 ticks/day). This gives 4 days of suppressed
    /// interest to allow the economy to deleverage before normal rates resume.
    pub tier3_exit_delay_ticks: u32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ArchetypeConfig {
    pub archetype: String,
    pub count: usize,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ItemConfig {
    pub name: String,
    pub base_price: f64,
    pub section: String,
    #[serde(default)]
    pub max_price_change_override: Option<f64>,
    #[serde(default)]
    pub base_spread_override: Option<f64>,
    /// Per-item price floor: minimum price (market support floor).
    /// If set, price cannot fall below this value.
    #[serde(default)]
    pub price_floor_override: Option<f64>,
    /// Per-item price ceiling: maximum price (player affordability cap).
    /// If set, price cannot exceed this value.
    #[serde(default)]
    pub price_ceiling_override: Option<f64>,
    /// Per-item price freeze: if true, price discovery is paused for this item.
    /// Spreads still compute normally. Mirrors Java ShopItem.priceFrozen.
    #[serde(default)]
    pub price_frozen: bool,
    /// Item rarity tier for spread and price-change multipliers.
    /// Higher tiers get wider spreads and more volatile price changes —
    /// rare items are inherently harder to trade fairly.
    /// Mirrors Java ItemTier + ShopItem.effectiveTier()/effectiveSpreadMultiplier()/effectiveMaxPriceChangeMultiplier().
    /// Admin overrides (Java DB overrides) take precedence over this default.
    /// Default (None): inferred from item name via default_tier_for().
    #[serde(default)]
    pub tier: Option<ItemTier>,
}

/// Item rarity tier for spread and price-change multipliers.
/// Higher tiers get wider spreads and more volatile price changes.
/// Mirrors Java ItemTier. Default classification mirrors ItemTier.defaultTierFor().
#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ItemTier {
    Common,
    Uncommon,
    Rare,
    Epic,
    Legendary,
}

impl ItemTier {
    pub fn spread_multiplier(self) -> f64 {
        match self {
            ItemTier::Common => 1.0,
            ItemTier::Uncommon => 1.2,
            ItemTier::Rare => 1.5,
            ItemTier::Epic => 1.8,
            ItemTier::Legendary => 2.2,
        }
    }

    pub fn max_price_change_multiplier(self) -> f64 {
        match self {
            ItemTier::Common => 1.0,
            ItemTier::Uncommon => 1.1,
            ItemTier::Rare => 1.25,
            ItemTier::Epic => 1.4,
            ItemTier::Legendary => 1.6,
        }
    }
}

/// Returns the default tier for an item name.
/// Mirrors Java ItemTier.defaultTierFor().
pub fn default_tier_for(name: &str) -> ItemTier {
    let upper = normalize_material_name(name);
    if matches_legendary(&upper) {
        ItemTier::Legendary
    } else if matches_epic(&upper) {
        ItemTier::Epic
    } else if matches_rare(&upper) {
        ItemTier::Rare
    } else if matches_uncommon(&upper) {
        ItemTier::Uncommon
    } else {
        ItemTier::Common
    }
}

fn normalize_material_name(name: &str) -> String {
    name.trim()
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() {
                ch.to_ascii_uppercase()
            } else {
                '_'
            }
        })
        .collect::<String>()
        .split('_')
        .filter(|part| !part.is_empty())
        .collect::<Vec<_>>()
        .join("_")
}

fn matches_legendary(upper: &str) -> bool {
    matches!(
        upper,
        "NETHER_STAR"
            | "DRAGON_BREATH"
            | "ELYTRA"
            | "SHULKER_BOX"
            | "SHULKER_BOX_ITEM"
            | "ENDER_PEARL"
            | "ENDER_EYE"
            | "NETHER_WART"
            | "GHAST_TEAR"
            | "BLAZE_ROD"
            | "MAGMA_CREAM"
            | "BEACON"
            | "CONDUIT"
            | "HEART_OF_THE_SEA"
            | "NAUTIL_SHELL"
            | "PHANTOM_MEMBRANE"
            | "FOX_SPAWN_EGG"
            | "ALLAY_SPAWN_EGG"
            | "WARDEN_SPAWN_EGG"
            | "WITHER_SKELETON_SKULL"
            | "WITHER_SKELETON_SKULL_ITEM"
            | "DRAGON_HEAD"
            | "DRAGON_EGG"
            | "RABBIT_HIDE"
            | "RABBIT_FOOT"
            | "NAUSEA_APPLE"
    )
}

fn matches_epic(upper: &str) -> bool {
    matches!(
        upper,
        "DIAMOND_SWORD"
            | "DIAMOND_PICKAXE"
            | "DIAMOND_AXE"
            | "DIAMOND_SHOVEL"
            | "DIAMOND_HOE"
            | "DIAMOND_HELMET"
            | "DIAMOND_CHESTPLATE"
            | "DIAMOND_LEGGINGS"
            | "DIAMOND_BOOTS"
            | "NETHERITE_SWORD"
            | "NETHERITE_PICKAXE"
            | "NETHERITE_AXE"
            | "NETHERITE_SHOVEL"
            | "NETHERITE_HOE"
            | "NETHERITE_HELMET"
            | "NETHERITE_CHESTPLATE"
            | "NETHERITE_LEGGINGS"
            | "NETHERITE_BOOTS"
            | "GOLDEN_APPLE"
            | "ENCHANTED_GOLDEN_APPLE"
            | "FIRE_CHARGE"
            | "FIREWORK_ROCKET"
            | "BOOK_AND_QUILL"
            | "WRITTEN_BOOK"
            | "NAME_TAG"
            | "LEAD"
            | "SADDLE"
            | "SNOWBALL"
            | "EGG"
            | "BOW"
            | "CROSSBOW"
            | "TRIDENT"
            | "SHIELD"
            | "TOTEM_OF_UNDYING"
            | "LINGERING_POTION"
            | "SPLASH_POTION"
            | "POTION"
            | "EXPERIENCE_BOTTLE"
            | "BLUE_BUNDLE"
            | "BUNDLE"
            | "NETHERITE_INGOT"
            | "NETHERITE_SCRAP"
            | "CHAINMAIL_HELMET"
            | "CHAINMAIL_CHESTPLATE"
            | "CHAINMAIL_LEGGINGS"
            | "CHAINMAIL_BOOTS"
    )
}

fn matches_rare(upper: &str) -> bool {
    matches!(
        upper,
        "DIAMOND"
            | "EMERALD"
            | "NETHER_QUARTZ"
            | "GLOWSTONE_DUST"
            | "BLAZE_POWDER"
            | "PRISMARINE_SHARD"
            | "PRISMARINE_CRYSTALS"
            | "NAUTILUS_SHELL"
            | "AMETHYST_SHARD"
            | "CALCITE"
            | "TUFF"
            | "DEEPSLATE"
            | "RAW_GOLD"
            | "RAW_GOLD_BLOCK"
            | "RAW_IRON"
            | "RAW_IRON_BLOCK"
            | "ANCIENT_DEBRIS"
            | "DEBRIS"
            | "CRYING_OBSIDIAN"
            | "GLOW_INK_SAC"
            | "GLOW_ITEM_FRAME"
            | "ITEM_FRAME"
            | "HONEYCOMB"
            | "HONEYCOMB_BLOCK"
            | "HONEY_BLOCK"
            | "SLIME_BALL"
            | "SLIME_BLOCK"
            | "FERMENTED_SPIDER_EYE"
            | "MOSS_BLOCK"
            | "ROOTED_DIRT"
            | "DRIPSTONE_BLOCK"
    )
}

fn matches_uncommon(upper: &str) -> bool {
    matches!(
        upper,
        "IRON_INGOT"
            | "GOLD_INGOT"
            | "BRICK"
            | "NETHER_BRICK"
            | "BRICK_ITEM"
            | "NETHER_BRICK_ITEM"
            | "PAPER"
            | "BOOK"
            | "BOOKSHELF"
            | "FURNACE"
            | "CHEST"
            | "ENDER_CHEST"
            | "TRAPPED_CHEST"
            | "HOPPER"
            | "DROPPER"
            | "DISPENSER"
            | "PISTON"
            | "STICKY_PISTON"
            | "OBSERVER"
            | "HOPPER_MINECART"
            | "RAIL"
            | "POWERED_RAIL"
            | "DETECTOR_RAIL"
            | "ACTIVATOR_RAIL"
            | "MINECART"
            | "CHEST_MINECART"
            | "COMPARATOR"
            | "REPEATER"
            | "DAYLIGHT_DETECTOR"
            | "LECTERN"
            | "CAULDRON"
            | "BREWING_STAND"
            | "ANVIL"
            | "CHIPPED_ANVIL"
            | "DAMAGED_ANVIL"
            | "GRINDSTONE"
            | "STONECUTTER"
            | "LOOM"
            | "CARTOGRAPHY_TABLE"
            | "SMITHING_TABLE"
            | "FLETCHING_TABLE"
            | "LAPIS_LAZULI"
            | "LAPIS_BLOCK"
            | "IRON_BLOCK"
            | "GOLD_BLOCK"
            | "DIAMOND_BLOCK"
            | "EMERALD_BLOCK"
            | "NETHERITE_BLOCK"
            | "COAL_BLOCK"
            | "REDSTONE_BLOCK"
            | "COBBLESTONE_BLOCK"
            | "STONE_BRICKS"
            | "STONE_BRICK_SLAB"
            | "STONE_BRICK_STAIRS"
            | "IRON_DOOR"
            | "GOLDEN_RAIL"
            | "LIGHT"
            | "SPYGLASS"
            | "GOLDEN_HELMET"
            | "GOLDEN_CHESTPLATE"
            | "GOLDEN_LEGGINGS"
            | "GOLDEN_BOOTS"
            | "LEATHER_HELMET"
            | "LEATHER_CHESTPLATE"
            | "LEATHER_LEGGINGS"
            | "LEATHER_BOOTS"
            | "IRON_HELMET"
            | "IRON_CHESTPLATE"
            | "IRON_LEGGINGS"
            | "IRON_BOOTS"
            | "TURTLE_HELMET"
            | "SCUTE"
            | "FEATHER"
            | "FLINT"
            | "STRING"
            | "WOOL"
            | "CARPET"
            | "PAINTING"
            | "ARROW"
            | "SPECTRAL_ARROW"
            | "COOKIE"
            | "CAKE"
            | "BREAD"
            | "GOLDEN_CARROT"
            | "BEETROOT_SOUP"
            | "RABBIT_STEW"
            | "MUSHROOM_STEW"
            | "SUSPICIOUS_STEW"
            | "PUMPKIN_PIE"
            | "SWEET_BERRIES"
            | "GLOW_BERRIES"
            | "DRIED_KELP"
            | "DRIED_KELP_BLOCK"
            | "ROTTEN_FLESH"
            | "PORKCHOP"
            | "COOKED_PORKCHOP"
            | "BEEF"
            | "COOKED_BEEF"
            | "CHICKEN"
            | "COOKED_CHICKEN"
            | "MUTTON"
            | "COOKED_MUTTON"
            | "RABBIT"
            | "COOKED_RABBIT"
            | "COD"
            | "SALMON"
            | "TROPICAL_FISH"
            | "PUFFERFISH"
            | "COOKED_COD"
            | "COOKED_SALMON"
            | "MELON_SEEDS"
            | "PUMPKIN_SEEDS"
            | "WHEAT_SEEDS"
    )
}

impl Default for SimConfig {
    fn default() -> Self {
        Self {
            economy: EconomyConfig::default(),
            spread: SpreadConfig::default(),
            player_scaling: PlayerScalingConfig::default(),
            loans: LoanConfig::default(),
            items: default_items(),
            player_exodus_tick: None,
            player_exodus_fraction: 0.5,
            exodus_spread_multiplier: 2.0,
            exodus_shock_duration_ticks: 288,
            exodus_target_archetype: None,
            mm_initial_capital_min: None,
            mm_initial_capital_max: None,
            guild_phase2_dip_threshold: None,
            guild_vwap_targets: false,
            whale_spread_shock_trigger_bps: 0.10,
            whale_spread_shock_multiplier: 2.5,
            whale_spread_shock_duration_ticks: 288,
            whale_high_value_sell_cooldown_ticks: Some(12),
            whale_max_dump_per_item: None,
        }
    }
}

impl Default for EconomyConfig {
    fn default() -> Self {
        Self {
            max_price_change_percent: 1.5,
            trade_window_days: 7,
            slippage_coeff: 0.01,
            sell_pressure_multiplier: 1.0,
            sector_correlation: 0.05,
            player_rate_limit_multiplier: 3.0,
            trend_dampening: 0.10,
            trend_streak_threshold_percent: 0.1,
            trend_dampening_floor: 0.25,
            adaptive_window: true,
            min_window_days: 2,
            max_window_days: 7,
            max_sector_correlation_group_size: 20,
        }
    }
}

impl Default for SpreadConfig {
    fn default() -> Self {
        Self {
            base_spread: 0.20,
            volume_impact: 0.8,
            player_impact: 0.6,
            liquidity_coeff: 0.01,
            liquidity_full_effect_traders: 10,
        }
    }
}

impl Default for PlayerScalingConfig {
    fn default() -> Self {
        Self {
            full_effect_players: 10,
        }
    }
}

impl Default for LoanConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            base_interest_rate: 0.05,
            credit_score_modifier: true,
            max_loan_multiplier: 2.0,
            min_credit_score: 200,
            default_duration_days: 14,
            compound_interval_hours: 24,
            default_penalty: 50,
            debt_gdp_tier1_ratio: 3.0,
            debt_gdp_tier2_ratio: 5.0,
            debt_gdp_tier3_ratio: 30.0, // RAISED from 15.0 (2026-04-15): counter-cyclical TIER3 lock causes D/G doom loop at 60d. tier3=30 gives 3-4× headroom above normal D/G~7-10x. TIER3 only fires in genuine catastrophe.
            tier1_interest_cap: 0.5,
            tier2_interest_cap: 0.25,
            single_loan_gdp_cap: 1.0,
            post_default_cooldown_hours: 168, // 7 days, matches Java LoanManager
            mm_opening_loan_allowed: true,    // MM can take opening loans by default
            counter_cyclical: true, // continuous taper, matches Java LoanManager (default: true)
            min_interest_multiplier: 0.0, // pure counter-cyclical: 0% at D/G=tier3Ratio (30.0 by default)
            tier3_hysteresis_band: 0.5, // 50% band: unlock at D/G < 50% of tier3 (15 when tier3=30)
            guildbuyer_total_debt_cap: 3.0, // cap GB debt at 3× GDP during TIER3 lock — prevents zero-interest loan accumulation
            total_debt_gdp_cap: 2.0, // cap active economy-wide debt at 2× GDP, matching Java LoanManager/config.yml
            block_mm_gb_loans_during_tier3: false, // MM/GB loans allowed during TIER3 lock by default
            tier3_exit_multiplier_cap: 0.10, // 10% cap during graduated TIER3 exit — prevents multiplier jump cascade
            tier3_exit_delay_ticks: 1152, // 4 days at 288 ticks/day — gives economy time to deleverage
        }
    }
}

fn default_whale_spread_shock_trigger_bps() -> f64 {
    0.10
}
fn default_whale_spread_shock_multiplier() -> f64 {
    2.5
}
fn default_whale_spread_shock_duration_ticks() -> u64 {
    288
}

pub fn default_items() -> Vec<ItemConfig> {
    vec![
        ItemConfig {
            name: "Cobblestone".into(),
            base_price: 1.0,
            section: "building".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
        ItemConfig {
            name: "Rotten Flesh".into(),
            base_price: 2.0,
            section: "drops".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
        ItemConfig {
            name: "Redstone".into(),
            base_price: 20.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
        ItemConfig {
            name: "Iron Ingot".into(),
            base_price: 50.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
        ItemConfig {
            name: "Blaze Rod".into(),
            base_price: 75.0,
            section: "drops".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
        ItemConfig {
            name: "Diamond".into(),
            base_price: 500.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
        ItemConfig {
            name: "Golden Apple".into(),
            base_price: 500.0,
            section: "food".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
        ItemConfig {
            name: "Netherite Ingot".into(),
            base_price: 2500.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
            price_floor_override: None,
            price_ceiling_override: None,
            price_frozen: false,
            tier: None,
        },
    ]
}

pub const TICKS_PER_DAY: u64 = 288;
pub const TICKS_PER_HOUR: u64 = 12;

fn default_total_debt_gdp_cap() -> f64 {
    2.0
}

impl SimConfig {
    pub fn trade_window_ticks(&self) -> u64 {
        self.economy.trade_window_days as u64 * TICKS_PER_DAY
    }

    pub fn min_window_ticks(&self) -> u64 {
        self.economy.min_window_days as u64 * TICKS_PER_DAY
    }

    pub fn max_window_ticks(&self) -> u64 {
        self.economy.max_window_days as u64 * TICKS_PER_DAY
    }

    pub fn compound_interval_ticks(&self) -> u64 {
        self.loans.compound_interval_hours as u64 * TICKS_PER_HOUR
    }

    pub fn loan_duration_ticks(&self) -> u64 {
        self.loans.default_duration_days as u64 * TICKS_PER_DAY
    }
}

#[cfg(test)]
mod tests {
    use super::{ItemTier, SimConfig, default_tier_for};

    #[test]
    fn missing_total_debt_gdp_cap_deserializes_to_java_default() {
        let mut value = serde_json::to_value(SimConfig::default()).unwrap();
        value
            .get_mut("loans")
            .unwrap()
            .as_object_mut()
            .unwrap()
            .remove("total_debt_gdp_cap");

        let config: SimConfig = serde_json::from_value(value).unwrap();

        assert_eq!(2.0, config.loans.total_debt_gdp_cap);
    }

    #[test]
    fn missing_whale_cap_deserializes_to_uncapped_default() {
        let mut value = serde_json::to_value(SimConfig::default()).unwrap();
        value
            .as_object_mut()
            .unwrap()
            .remove("whale_max_dump_per_item");

        let config: SimConfig = serde_json::from_value(value).unwrap();

        assert_eq!(None, config.whale_max_dump_per_item);
    }

    #[test]
    fn item_tier_defaults_match_java_material_names() {
        assert_eq!(ItemTier::Common, default_tier_for("Cobblestone"));
        assert_eq!(ItemTier::Uncommon, default_tier_for("Iron Ingot"));
        assert_eq!(ItemTier::Rare, default_tier_for("Diamond"));
        assert_eq!(ItemTier::Epic, default_tier_for("Golden Apple"));
        assert_eq!(ItemTier::Legendary, default_tier_for("Blaze Rod"));
        assert_eq!(ItemTier::Uncommon, default_tier_for("Diamond Block"));
        assert_eq!(ItemTier::Uncommon, default_tier_for("Netherite Block"));
        assert_eq!(ItemTier::Epic, default_tier_for("Netherite Ingot"));
    }

    #[test]
    fn item_tier_multipliers_match_java_item_tier() {
        assert_eq!(1.0, ItemTier::Common.spread_multiplier());
        assert_eq!(1.0, ItemTier::Common.max_price_change_multiplier());
        assert_eq!(1.2, ItemTier::Uncommon.spread_multiplier());
        assert_eq!(1.1, ItemTier::Uncommon.max_price_change_multiplier());
        assert_eq!(1.5, ItemTier::Rare.spread_multiplier());
        assert_eq!(1.25, ItemTier::Rare.max_price_change_multiplier());
        assert_eq!(1.8, ItemTier::Epic.spread_multiplier());
        assert_eq!(1.4, ItemTier::Epic.max_price_change_multiplier());
        assert_eq!(2.2, ItemTier::Legendary.spread_multiplier());
        assert_eq!(1.6, ItemTier::Legendary.max_price_change_multiplier());
    }
}
