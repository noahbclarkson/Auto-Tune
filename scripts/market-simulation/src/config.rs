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
            trend_dampening: 0.05,
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
            default_duration_days: 7,
            compound_interval_hours: 24,
            default_penalty: 50,
            debt_gdp_tier1_ratio: 3.0,
            debt_gdp_tier2_ratio: 5.0,
            debt_gdp_tier3_ratio: 15.0,
            tier1_interest_cap: 0.5,
            tier2_interest_cap: 0.25,
            single_loan_gdp_cap: 1.0,
            post_default_cooldown_hours: 168, // 7 days, matches Java LoanManager
            mm_opening_loan_allowed: true,    // MM can take opening loans by default
            counter_cyclical: true, // continuous taper, matches Java LoanManager (default: true)
            min_interest_multiplier: 0.0, // pure counter-cyclical: 0% at D/G=tier3Ratio
        }
    }
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
        },
    ]
}

pub const TICKS_PER_DAY: u64 = 288;
pub const TICKS_PER_HOUR: u64 = 12;

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
