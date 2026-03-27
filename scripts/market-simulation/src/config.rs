use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SimConfig {
    pub economy: EconomyConfig,
    pub spread: SpreadConfig,
    pub player_scaling: PlayerScalingConfig,
    pub loans: LoanConfig,
    pub items: Vec<ItemConfig>,
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
}

impl Default for SimConfig {
    fn default() -> Self {
        Self {
            economy: EconomyConfig::default(),
            spread: SpreadConfig::default(),
            player_scaling: PlayerScalingConfig::default(),
            loans: LoanConfig::default(),
            items: default_items(),
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
            debt_gdp_tier3_ratio: 10.0,
            tier1_interest_cap: 0.5,
            tier2_interest_cap: 0.25,
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
        },
        ItemConfig {
            name: "Rotten Flesh".into(),
            base_price: 2.0,
            section: "drops".into(),
            max_price_change_override: None,
            base_spread_override: None,
        },
        ItemConfig {
            name: "Redstone".into(),
            base_price: 20.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
        },
        ItemConfig {
            name: "Iron Ingot".into(),
            base_price: 50.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
        },
        ItemConfig {
            name: "Blaze Rod".into(),
            base_price: 75.0,
            section: "drops".into(),
            max_price_change_override: None,
            base_spread_override: None,
        },
        ItemConfig {
            name: "Diamond".into(),
            base_price: 500.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
        },
        ItemConfig {
            name: "Golden Apple".into(),
            base_price: 500.0,
            section: "food".into(),
            max_price_change_override: None,
            base_spread_override: None,
        },
        ItemConfig {
            name: "Netherite Ingot".into(),
            base_price: 2500.0,
            section: "ores".into(),
            max_price_change_override: None,
            base_spread_override: None,
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
