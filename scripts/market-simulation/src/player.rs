use std::cell::RefCell;
use std::collections::{HashMap, VecDeque};

use rand::Rng;
use rand::RngExt;
use rand::SeedableRng;
use rand::rngs::StdRng;
use rand_distr::{Distribution, Normal};

// Implement TryRng<Error=Infallible> for SeededRng so Normal::sample() works.
// The blanket impl `impl<R> Rng for R where R: TryRng<Error=Infallible>` then
// automatically provides the Rng trait (next_u32, next_u64, fill_bytes).
impl rand::TryRng for SeededRng {
    type Error = std::convert::Infallible;

    fn try_next_u32(&mut self) -> Result<u32, Self::Error> {
        Ok(Self::with(|rng| rng.next_u32()))
    }

    fn try_next_u64(&mut self) -> Result<u64, Self::Error> {
        Ok(Self::with(|rng| rng.next_u64()))
    }

    fn try_fill_bytes(&mut self, dest: &mut [u8]) -> Result<(), Self::Error> {
        Self::with(|rng| rng.fill_bytes(dest));
        Ok(())
    }
}

// Thread-local RNG for deterministic regression testing.
// This covers BOTH rng_next() calls AND rand::rng() calls made by player factories.
thread_local! {
    static GLOBAL_SEEDED_RNG: RefCell<Option<StdRng>> = const { RefCell::new(None) };
    /// If set, ALL GuildBuyers will use this exact threshold instead of randomizing.
    /// Set via --fixed-guild-threshold CLI flag for threshold sweep runs.
    pub static FIXED_GUILD_THRESHOLD: RefCell<Option<f64>> = const { RefCell::new(None) };
}

/// Set the fixed GuildBuyer price-dip threshold. None = use random per-player.
pub fn set_fixed_guild_threshold(t: Option<f64>) {
    FIXED_GUILD_THRESHOLD.with(|cell| *cell.borrow_mut() = t);
}

/// Get the fixed GuildBuyer threshold, if one is set.
pub fn get_fixed_guild_threshold() -> Option<f64> {
    FIXED_GUILD_THRESHOLD.with(|cell| *cell.borrow())
}

/// Set the thread-local seeded RNG for deterministic runs.
/// Also seeds the rand crate's thread-rng so player factory rand::rng() calls are deterministic.
pub fn set_global_seeded_rng(seed: u64) {
    // Seed our own RNG
    GLOBAL_SEEDED_RNG.with(|cell| {
        *cell.borrow_mut() = Some(StdRng::seed_from_u64(seed));
    });
}

/// Clear the seeded RNG (restore normal randomness).
#[allow(dead_code)]
pub fn clear_global_seeded_rng() {
    GLOBAL_SEEDED_RNG.with(|cell| *cell.borrow_mut() = None);
}

/// Get next random f64.
pub fn rng_next() -> f64 {
    GLOBAL_SEEDED_RNG.with(|cell| {
        let mut cell = cell.borrow_mut();
        if let Some(ref mut rng) = *cell {
            rng.random()
        } else {
            rand::rng().random()
        }
    })
}

/// Get next random value from range.
pub fn rng_range<
    R: rand::distr::uniform::SampleRange<T>,
    T: rand::distr::uniform::SampleUniform,
>(
    range: R,
) -> T {
    GLOBAL_SEEDED_RNG.with(|cell| {
        let mut cell = cell.borrow_mut();
        if let Some(ref mut rng) = *cell {
            rng.random_range(range)
        } else {
            rand::rng().random_range(range)
        }
    })
}

use crate::engine::{ItemState, PriceTrendDirection};

/// Wrapper that routes all RNG calls through the seeded thread-local RNG.
/// Replacing `let mut rng = rand::rng()` with `let mut rng = SeededRng` ensures
/// player factory parameters are deterministic in regression runs.
struct SeededRng;

impl SeededRng {
    /// Access the thread-local RNG, or fall back to the global `rand::rng()`.
    fn with<R, F>(f: F) -> R
    where
        F: for<'a> FnOnce(&'a mut StdRng) -> R,
    {
        GLOBAL_SEEDED_RNG.with(|cell| {
            let mut cell = cell.borrow_mut();
            match &mut *cell {
                Some(rng) => f(rng),
                None => {
                    // Not seeded — fall back to real randomness
                    let mut fallback = rand::rng();
                    let mut fallback_std = StdRng::from_rng(&mut fallback);
                    f(&mut fallback_std)
                }
            }
        })
    }

    #[inline]
    fn random<T: rand::distr::uniform::SampleUniform + PartialOrd>(
        &mut self,
        range: std::ops::Range<T>,
    ) -> T {
        Self::with(|rng| rng.random_range(range))
    }

    /// Sample from a RangeInclusive.
    #[inline]
    fn random_inclusive(&mut self, range: std::ops::RangeInclusive<i32>) -> i32 {
        Self::with(|rng| rng.random_range(range))
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Archetype {
    Casual,
    Farmer,
    Trader,
    Hoarder,
    Exploiter,
    /// Brand new player — buys lots of cheap basics, sells almost nothing.
    /// High variance behavior; represents fresh server population.
    Newbie,
    /// Mostly offline (low online_prob) but when online dumps huge quantities
    /// of gathered items. Represents passive resource generators.
    AFKFarmer,
    /// Guild bulk buyer — wants to maintain target inventory for members,
    /// buys heavily when stock is low, rarely sells.
    GuildBuyer,
    /// Market Maker — posts two-sided limit orders around fair value,
    /// earns from the spread. Provides liquidity to both sides, reducing
    /// systemic underselling from Farmer-dominated economies.
    MarketMaker,
    /// Insider Trader — mean-reversion player. Tracks rolling price history,
    /// buys when price is significantly below recent average, sells when
    /// significantly above. Counteracts momentum-driven overshoot in both
    /// directions. Distinct from Exploiter (momentum-following).
    InsiderTrader,
    /// Guild Seller — mirror of GuildBuyer. Guilds liquidate inventory when
    /// prices spike above perceived value, preventing bubble inflation.
    /// Sells proactively when sell_price > perceived * (1 + threshold),
    /// then liquidates excess above guild target. Counteracts price bubbles.
    GuildSeller,
    /// Volume Trader — contrarian liquidity provider. Tracks rolling spread
    /// and price history per item. Buys when spreads are wide AND prices are
    /// below average (volume drought = buy opportunity). Sells when spreads
    /// are tight AND prices are above average (volume surge = sell signal).
    /// Counteracts volume extremes that distort prices in both directions.
    VolumeTrader,
}

impl Archetype {
    pub fn label(&self) -> &'static str {
        match self {
            Self::Casual => "Casual",
            Self::Farmer => "Farmer",
            Self::Trader => "Trader",
            Self::Hoarder => "Hoarder",
            Self::Exploiter => "Exploiter",
            Self::Newbie => "Newbie",
            Self::AFKFarmer => "AFKFarmer",
            Self::GuildBuyer => "GuildBuyer",
            Self::MarketMaker => "MarketMaker",
            Self::InsiderTrader => "InsiderTrader",
            Self::GuildSeller => "GuildSeller",
            Self::VolumeTrader => "VolumeTrader",
        }
    }
}

#[derive(Clone, Debug)]
pub struct PlayerDecision {
    pub item_index: usize,
    pub is_buy: bool,
    pub amount: i32,
}

#[derive(Clone, Debug)]
pub struct DecisionLog {
    pub player_id: usize,
    pub item_index: usize,
    pub is_buy: bool,
    pub amount: i32,
    pub price_per_unit: f64,
    pub total_cost: f64,
    pub perceived_value: f64,
    pub effective_perceived: f64,
    pub buy_threshold: f64,
    pub sell_threshold: f64,
    pub balance_before: f64,
    pub inventory_before: i32,
    pub reasoning: String,
}

pub struct DecisionResult {
    pub decisions: Vec<PlayerDecision>,
    pub logs: Vec<DecisionLog>,
}

#[derive(Clone, Debug)]
pub struct PlayerAgent {
    pub id: usize,
    pub name: String,
    pub archetype: Archetype,
    pub balance: f64,
    pub online_probability: f64,
    pub activity_rate: f64,
    pub buy_threshold: f64,
    pub sell_threshold: f64,
    pub max_trade_amount: i32,
    pub risk_tolerance: f64,
    pub inventory_saturation: f64,
    pub gather_rate: f64,
    pub usage_rate: f64,
    pub perceived_values: HashMap<usize, f64>,
    pub preferences: HashMap<usize, f64>,
    pub inventory: HashMap<usize, i32>,
    /// Target inventory levels for GuildBuyer archetype (item → qty).
    /// Empty for all other archetypes.
    pub guild_target_inventory: HashMap<usize, i32>,
    /// Base inventory for GuildBuyer — fixed reference for price-dip buying.
    /// Equal to guild_target_inventory at construction. Does not change.
    pub guild_base_inventory: HashMap<usize, i32>,
    /// Price-dip threshold: buy when buy_price < perceived * (1.0 - this).
    /// 0.0 = disabled. 0.2 = buy when price is 20%+ below perceived.
    pub guild_price_dip_threshold: f64,
    /// Sell-spike threshold: sell when sell_price > perceived * (1.0 + this).
    /// 0.0 = disabled. 0.2 = sell when price is 20%+ above perceived.
    pub guild_sell_threshold: f64,
    /// Minimum tick cooldown between GuildSeller Phase 1 sells per item.
    /// Prevents sell avalanche when spike condition persists across many ticks.
    pub guild_sell_cooldown_ticks: HashMap<usize, u64>,
    /// Phase 2 price-dip threshold: sell when price < perceived * (1 - this).
    /// Redesigned Phase 2 trigger — detects market oversupply via price depression.
    /// When 0.0, Phase 2 is disabled (legacy behavior: inventory > 2x target only).
    pub guild_phase2_dip_threshold: f64,
    /// If true, GuildBuyer uses rolling VWAP as its price anchor instead of
    /// subjective perceived_value. VWAP is grounded in actual transaction prices.
    pub use_vwap_targets: bool,
    /// Max inventory per item for MarketMaker archetype. Limits position size.
    pub mm_max_inventory: i32,
    /// Target inventory level per item for MarketMaker archetype.
    pub mm_target_inventory: i32,
    /// Rolling price history window size (in ticks) for InsiderTrader.
    /// How many past prices to track for mean-reversion calculation.
    pub insider_history_window: usize,
    /// Per-item rolling price history. Updated after each engine tick.
    /// Used by InsiderTrader to compute moving average for mean-reversion.
    pub insider_price_history: HashMap<usize, VecDeque<f64>>,
    /// How aggressively InsiderTrader widens its threshold during volatile markets.
    /// 0.0 = disabled (fixed threshold). Higher values = more adaptation.
    /// Range: 0.5..2.0 set at construction.
    pub insider_volatility_sensitivity: f64,
    /// Rolling spread history window size (in ticks) for VolumeTrader.
    /// How many past spread observations to track per item.
    pub volume_spread_window: usize,
    /// Per-item rolling spread history. Used by VolumeTrader to detect
    /// spread deviations from normal — wide spread = low volume = buy signal.
    pub volume_spread_history: HashMap<usize, VecDeque<f64>>,
    /// Rolling price history window size (in ticks) for VolumeTrader.
    /// Used alongside spread to confirm volume-driven price signals.
    pub volume_price_window: usize,
    /// Per-item rolling price history for VolumeTrader.
    pub volume_price_history: HashMap<usize, VecDeque<f64>>,
    /// Minimum tick cooldown between VolumeTrader decisions per item.
    /// Prevents over-trading on short-term spread noise.
    pub volume_cooldown_ticks: HashMap<usize, u64>,
    pub credit_score: i32,
    pub total_traded: f64,
    pub online: bool,
    pub total_trades: u32,
    /// Tick when player last defaulted a loan. Used for post-default cooldown.
    /// None = has never defaulted (or cooldown has expired).
    pub last_defaulted_at: Option<u64>,
}

impl PlayerAgent {
    pub fn new_casual(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(500.0..2000.0);

        let mut agent = Self {
            id: index,
            name: format!("Casual-{index}"),
            archetype: Archetype::Casual,
            balance: budget,
            online_probability: rng.random(0.1..0.3),
            activity_rate: rng.random(0.05..0.15),
            buy_threshold: rng.random(0.05..0.15),
            sell_threshold: rng.random(0.1..0.25),
            max_trade_amount: rng.random(1..10),
            risk_tolerance: rng.random(0.3..0.7),
            inventory_saturation: rng.random(0.03..0.07),
            gather_rate: rng.random(0.05..0.15),
            usage_rate: rng.random(0.15..0.30),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 500,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);
        agent
    }

    pub fn new_farmer(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(200.0..1000.0);

        let mut agent = Self {
            id: index,
            name: format!("Farmer-{index}"),
            archetype: Archetype::Farmer,
            balance: budget,
            online_probability: rng.random(0.3..0.6),
            activity_rate: rng.random(0.2..0.4),
            buy_threshold: rng.random(0.2..0.4),
            sell_threshold: rng.random(0.0..0.05),
            max_trade_amount: rng.random(5..30),
            risk_tolerance: rng.random(0.2..0.5),
            inventory_saturation: rng.random(0.02..0.05),
            gather_rate: rng.random(0.3..0.5),
            usage_rate: rng.random(0.05..0.12),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 500,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);

        for i in 0..item_count {
            agent.inventory.insert(i, rng.random(5..20));
        }
        agent
    }

    pub fn new_trader(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(5000.0..20000.0);

        let mut agent = Self {
            id: index,
            name: format!("Trader-{index}"),
            archetype: Archetype::Trader,
            balance: budget,
            online_probability: rng.random(0.5..0.8),
            activity_rate: rng.random(0.5..0.8),
            buy_threshold: rng.random(0.02..0.08),
            sell_threshold: rng.random(0.02..0.08),
            max_trade_amount: rng.random(1..15),
            risk_tolerance: rng.random(0.5..0.9),
            inventory_saturation: rng.random(0.08..0.15),
            gather_rate: rng.random(0.02..0.08),
            usage_rate: rng.random(0.03..0.08),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 500,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);
        agent
    }

    pub fn new_hoarder(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(3000.0..10000.0);

        let mut agent = Self {
            id: index,
            name: format!("Hoarder-{index}"),
            archetype: Archetype::Hoarder,
            balance: budget,
            online_probability: rng.random(0.2..0.4),
            activity_rate: rng.random(0.1..0.3),
            buy_threshold: rng.random(0.0..0.05),
            sell_threshold: rng.random(0.3..0.5),
            max_trade_amount: rng.random(5..25),
            risk_tolerance: rng.random(0.6..0.9),
            inventory_saturation: rng.random(0.005..0.02),
            gather_rate: rng.random(0.08..0.15),
            usage_rate: rng.random(0.02..0.05),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 500,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);
        agent
    }

    pub fn new_exploiter(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(20000.0..100000.0);

        let mut agent = Self {
            id: index,
            name: format!("Exploiter-{index}"),
            archetype: Archetype::Exploiter,
            balance: budget,
            online_probability: rng.random(0.8..0.95),
            activity_rate: rng.random(0.8..1.0),
            buy_threshold: 0.0,
            sell_threshold: 0.0,
            max_trade_amount: rng.random(10..50),
            risk_tolerance: 1.0,
            inventory_saturation: rng.random(0.04..0.08),
            gather_rate: rng.random(0.01..0.05),
            usage_rate: rng.random(0.01..0.03),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 500,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent
    }

    /// New players: low budget, buy heavily of basics (high price), sell nothing.
    /// High variance in decisions. Represent fresh server joiners.
    pub fn new_newbie(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        // Newbies start with modest budget
        let budget = rng.random(100.0..500.0);

        let mut agent = Self {
            id: index,
            name: format!("Newbie-{index}"),
            archetype: Archetype::Newbie,
            balance: budget,
            // Newbies come and go unpredictably
            online_probability: rng.random(0.15..0.35),
            // But when online, quite active
            activity_rate: rng.random(0.3..0.6),
            // Will buy even at slight premium — eager to get items
            buy_threshold: rng.random(0.0..0.05),
            // Never sells (new players hoard what they get)
            sell_threshold: rng.random(0.4..0.7),
            max_trade_amount: rng.random(1..8),
            risk_tolerance: rng.random(0.2..0.5),
            // Newbies gather some but use even more
            inventory_saturation: rng.random(0.02..0.06),
            gather_rate: rng.random(0.1..0.2),
            usage_rate: rng.random(0.2..0.4),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 300, // Low credit — new account
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        // Newbies prefer cheap basic items
        agent.init_newbie_preferences(item_count);
        agent
    }

    /// AFK Farmer: mostly offline (low online_prob) but when online dumps huge
    /// quantities of gathered items. Represents passive resource generators.
    pub fn new_afk_farmer(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(50.0..300.0);

        let mut agent = Self {
            id: index,
            name: format!("AFKFarmer-{index}"),
            archetype: Archetype::AFKFarmer,
            balance: budget,
            // Very rarely online
            online_probability: rng.random(0.05..0.15),
            // When online, very active — dumps inventory
            activity_rate: rng.random(0.8..1.0),
            buy_threshold: rng.random(0.3..0.5), // Almost never buys
            sell_threshold: rng.random(0.0..0.03), // Sells at tiny margin
            max_trade_amount: rng.random(50..200), // Huge dump sizes
            risk_tolerance: rng.random(0.1..0.3),
            // Accumulates a LOT of inventory while AFK
            inventory_saturation: rng.random(0.3..0.6),
            // Gathers rapidly while online
            gather_rate: rng.random(1.0..3.0),
            // Almost no usage
            usage_rate: rng.random(0.0..0.02),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 500,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        // AFK farmers prefer cheap gathered items (building blocks, ores, drops)
        agent.init_afk_preferences(item_count);
        agent
    }

    /// Guild Buyer: maintains a target inventory for guild members.
    /// Buys heavily when stock is low, rarely sells (guild benefit).
    pub fn new_guild_buyer(
        index: usize,
        item_count: usize,
        base_prices: &[f64],
        use_vwap_targets: bool,
    ) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(50000.0..200000.0);

        // Fixed threshold set via CLI sweep; otherwise randomize per-player (0.15–0.30).
        // A lower threshold = buys only on large dips; higher = aggressive, buys on small dips.
        let guild_dip_threshold =
            get_fixed_guild_threshold().unwrap_or_else(|| rng.random(0.15..0.30));

        let mut agent = Self {
            id: index,
            name: format!("GuildBuyer-{index}"),
            archetype: Archetype::GuildBuyer,
            balance: budget,
            online_probability: rng.random(0.6..0.9),
            activity_rate: rng.random(0.6..0.9),
            // Sells only at high premium (guild markup)
            buy_threshold: rng.random(0.0..0.03),
            sell_threshold: rng.random(0.5..0.8),
            max_trade_amount: rng.random(20..100),
            risk_tolerance: rng.random(0.4..0.7),
            // Needs to keep substantial stock for members
            inventory_saturation: rng.random(0.3..0.6),
            // Gathers moderately
            gather_rate: rng.random(0.05..0.15),
            // Provides to guild — low personal use
            usage_rate: rng.random(0.0..0.05),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 700, // Good credit — guild backed
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            // Buy when price drops guild_dip_threshold+% below perceived (proactive price stabilizer)
            guild_price_dip_threshold: guild_dip_threshold,
            guild_sell_threshold: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);
        // Pre-fill inventory to represent guild stock
        for i in 0..item_count {
            let qty = rng.random(10..50);
            agent.inventory.insert(i, qty);
            agent.guild_target_inventory.insert(i, qty);
            agent.guild_base_inventory.insert(i, qty);
        }
        agent
    }

    /// Guild Seller: mirror of GuildBuyer. Sells when prices spike above perceived
    /// value, providing downward pressure to prevent bubble inflation.
    /// Phase 1: Proactive sell-spike selling — sells when sell_price > perceived*(1+threshold).
    /// Phase 2: Liquidate excess inventory when above target.
    pub fn new_guild_seller(
        index: usize,
        item_count: usize,
        base_prices: &[f64],
        phase2_dip_threshold: Option<f64>,
    ) -> Self {
        let mut rng = SeededRng;
        // GuildSellers have moderate capital — they sell guild inventory
        let budget = rng.random(10000.0..50000.0);
        // Sell-spike threshold: sell when price > perceived*(1 + this)
        let sell_spike_threshold = rng.random(0.05..0.15);

        let mut agent = Self {
            id: index,
            name: format!("GuildSeller-{index}"),
            archetype: Archetype::GuildSeller,
            balance: budget,
            online_probability: rng.random(0.6..0.9),
            activity_rate: rng.random(0.6..0.9),
            // Willing to sell at modest markup
            buy_threshold: rng.random(0.0..0.05),
            sell_threshold: rng.random(0.3..0.6),
            max_trade_amount: rng.random(10..80),
            risk_tolerance: rng.random(0.4..0.7),
            // Needs to keep substantial stock for liquidation events
            inventory_saturation: rng.random(0.3..0.6),
            // Gathers moderately
            gather_rate: rng.random(0.05..0.15),
            // Provides to guild members — low personal use
            usage_rate: rng.random(0.0..0.05),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 650,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            // Sell when price spikes guild_sell_threshold+% above perceived
            guild_sell_threshold: sell_spike_threshold,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            // Phase 2 redesigned: sell when price dips below perceived*(1 - dip_threshold).
            // This makes GS an active anti-oversupply mechanism, not just excess-liquidator.
            guild_phase2_dip_threshold: phase2_dip_threshold
                .unwrap_or_else(|| rng.random(0.10..0.25)),
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);
        // Pre-fill inventory to represent guild stock for liquidation
        for i in 0..item_count {
            let qty = rng.random(15..60);
            agent.inventory.insert(i, qty);
            agent.guild_target_inventory.insert(i, qty);
            agent.guild_base_inventory.insert(i, qty);
        }
        agent
    }

    /// Market Maker: posts two-sided limit orders around perceived fair value.
    /// Earns from the bid-ask spread. Trades in both directions, providing
    /// liquidity that counteracts Farmer-dominated sell pressure.
    pub fn new_market_maker(
        index: usize,
        item_count: usize,
        base_prices: &[f64],
        mm_capital_min: f64,
        mm_capital_max: f64,
    ) -> Self {
        let mut rng = SeededRng;
        // MarketMakers need substantial capital to maintain two-sided positions
        let budget = rng.random(mm_capital_min..mm_capital_max);
        let max_inv = rng.random(30..80);
        let target_inv = rng.random(15..40);

        let mut agent = Self {
            id: index,
            name: format!("MarketMaker-{index}"),
            archetype: Archetype::MarketMaker,
            balance: budget,
            online_probability: rng.random(0.7..0.95),
            activity_rate: rng.random(0.7..0.95),
            // Very tight thresholds — MarketMakers transact on narrow margins
            buy_threshold: rng.random(0.02..0.05),
            sell_threshold: rng.random(0.02..0.05),
            max_trade_amount: rng.random(50..200),
            risk_tolerance: rng.random(0.5..0.8),
            inventory_saturation: rng.random(0.1..0.3),
            gather_rate: rng.random(0.0..0.05),
            usage_rate: rng.random(0.0..0.02),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 750,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            // MarketMaker-specific
            mm_max_inventory: max_inv,
            mm_target_inventory: target_inv,
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);
        // Start with target inventory
        for i in 0..item_count {
            agent.inventory.insert(i, target_inv);
        }
        agent
    }

    pub fn new_insider_trader(index: usize, item_count: usize, _base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        // InsiderTraders have substantial capital — they take positions
        let budget = rng.random(20000.0..100000.0);
        // Mean-reversion threshold: buy when price < mean*(1-threshold), sell when > mean*(1+threshold)
        let threshold = rng.random(0.08..0.20);
        // History window: number of ticks to average. ~20 ticks = ~4 hours of price history.
        let history_window = rng.random(15..35);
        // Volatility sensitivity: how much to widen threshold during volatile markets.
        // Higher = more adaptation (tighter threshold in calm markets, wider in volatile ones).
        let volatility_sensitivity = rng.random(0.5..2.0);

        let mut agent = Self {
            id: index,
            name: format!("InsiderTrader-{index}"),
            archetype: Archetype::InsiderTrader,
            balance: budget,
            online_probability: rng.random(0.6..0.9),
            activity_rate: rng.random(0.6..0.9),
            // Insider uses threshold symmetrically for buy/sell
            buy_threshold: threshold,
            sell_threshold: threshold,
            max_trade_amount: rng.random(10..50),
            risk_tolerance: rng.random(0.5..0.8),
            inventory_saturation: rng.random(0.05..0.15),
            gather_rate: rng.random(0.02..0.10),
            usage_rate: rng.random(0.02..0.08),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 700,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            // InsiderTrader-specific
            insider_history_window: history_window,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: volatility_sensitivity,
            // VolumeTrader-specific (zeroed for InsiderTrader)
            volume_spread_window: 0,
            volume_spread_history: HashMap::new(),
            volume_price_window: 0,
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, &[]);
        agent.init_preferences(item_count);
        agent
    }

    /// Create a VolumeTrader — contrarian liquidity provider.
    /// Monitors spread and price history per item. Buys when spreads are wide
    /// AND prices are below average (volume drought = buy signal). Sells when
    /// spreads are tight AND prices are above average (volume surge = sell signal).
    ///
    /// Parameters:
    /// - `spread_threshold`: fraction above/below rolling spread mean to trigger
    ///   a signal. E.g., 0.30 means trigger when spread is 30%+ deviation.
    /// - `spread_window`: how many past ticks of spread to track for rolling mean.
    /// - `price_window`: how many past ticks of price to track for rolling mean.
    ///   Must be >= spread_window (price history used alongside spread signal).
    pub fn new_volume_trader(
        index: usize,
        item_count: usize,
        _base_prices: &[f64],
        spread_threshold: f64,
        spread_window: usize,
        price_window: usize,
    ) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(20_000.0..100_000.0);

        let mut agent = Self {
            id: index,
            name: format!("VolumeTrader-{index}"),
            archetype: Archetype::VolumeTrader,
            balance: budget,
            online_probability: rng.random(0.7..0.95),
            activity_rate: rng.random(0.5..0.8),
            // Threshold used for spread deviation — symmetric buy/sell trigger
            buy_threshold: spread_threshold,
            sell_threshold: spread_threshold,
            max_trade_amount: rng.random(15..60),
            risk_tolerance: rng.random(0.4..0.7),
            inventory_saturation: rng.random(0.10..0.25),
            gather_rate: rng.random(0.05..0.15),
            usage_rate: rng.random(0.05..0.12),
            perceived_values: HashMap::new(),
            preferences: HashMap::new(),
            inventory: HashMap::new(),
            credit_score: 750,
            total_traded: 0.0,
            online: false,
            total_trades: 0,
            guild_target_inventory: HashMap::new(),
            guild_base_inventory: HashMap::new(),
            guild_price_dip_threshold: 0.0,
            guild_sell_threshold: 0.0,
            mm_max_inventory: 0,
            mm_target_inventory: 0,
            insider_history_window: 0,
            insider_price_history: HashMap::new(),
            insider_volatility_sensitivity: 0.0,
            // VolumeTrader-specific
            volume_spread_window: spread_window.max(3),
            volume_spread_history: HashMap::new(),
            volume_price_window: price_window.max(spread_window),
            volume_price_history: HashMap::new(),
            volume_cooldown_ticks: HashMap::new(),
            guild_sell_cooldown_ticks: HashMap::new(),
            guild_phase2_dip_threshold: 0.0,
            use_vwap_targets: false,
            last_defaulted_at: None,
        };
        agent.init_perceived_values(item_count, &[]);
        agent.init_preferences(item_count);
        agent
    }

    fn init_newbie_preferences(&mut self, item_count: usize) {
        // Newbies strongly prefer cheap, basic items (low base_price)
        let mut rng = SeededRng;
        for i in 0..item_count {
            // Higher preference for cheaper items
            let pref = rng.random(0.5..1.0);
            self.preferences.insert(i, pref);
        }
    }

    fn init_afk_preferences(&mut self, item_count: usize) {
        // AFK farmers focus on gatherable items: building materials, ores, basic drops
        let mut rng = SeededRng;
        for i in 0..item_count {
            let pref = rng.random(0.3..0.8);
            self.preferences.insert(i, pref);
        }
    }

    pub fn new_random(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let roll: f64 = rng.random(0.0..1.0);
        if roll < 0.25 {
            Self::new_casual(index, item_count, base_prices)
        } else if roll < 0.45 {
            Self::new_farmer(index, item_count, base_prices)
        } else if roll < 0.60 {
            Self::new_trader(index, item_count, base_prices)
        } else if roll < 0.75 {
            Self::new_hoarder(index, item_count, base_prices)
        } else if roll < 0.85 {
            Self::new_exploiter(index, item_count, base_prices)
        } else if roll < 0.92 {
            Self::new_newbie(index, item_count, base_prices)
        } else if roll < 0.97 {
            Self::new_afk_farmer(index, item_count, base_prices)
        } else if roll < 0.985 {
            Self::new_guild_buyer(index, item_count, base_prices, false)
        } else if roll < 0.9925 {
            Self::new_insider_trader(index, item_count, base_prices)
        } else if roll < 0.9975 {
            Self::new_guild_seller(index, item_count, base_prices, None)
        } else {
            Self::new_volume_trader(index, item_count, base_prices, 0.25, 20, 30)
        }
    }

    fn init_perceived_values(&mut self, item_count: usize, base_prices: &[f64]) {
        let mut rng = SeededRng;
        for (i, &base) in base_prices.iter().enumerate().take(item_count) {
            // Normal::new fails if mean <= 0 or stddev is NaN/inf. Base prices are always > 0
            // and stddev = base * 0.15 is always valid. Use expect() to document the invariant.
            let normal = Normal::new(base, base * 0.15)
                .expect("Normal(base, base*0.15) should always be valid for positive base prices");
            let perceived: f64 = normal.sample(&mut rng).max(base * 0.5);
            self.perceived_values.insert(i, perceived);
        }
    }

    fn init_preferences(&mut self, item_count: usize) {
        let mut rng = SeededRng;
        for i in 0..item_count {
            self.preferences.insert(i, rng.random(0.1..1.0));
        }
    }

    pub fn effective_perceived(&self, item_index: usize, base_perceived: f64) -> f64 {
        let qty = self.inventory.get(&item_index).copied().unwrap_or(0);
        base_perceived / (1.0 + qty as f64 * self.inventory_saturation)
    }

    pub fn decide(
        &mut self,
        items: &[ItemState],
        record: bool,
        slippage_coeff: f64,
        current_tick: u64,
    ) -> DecisionResult {
        let mut decisions = Vec::new();
        let mut logs = Vec::new();

        self.online = rng_next() < self.online_probability;
        if !self.online {
            return DecisionResult { decisions, logs };
        }

        if rng_next() > self.activity_rate {
            return DecisionResult { decisions, logs };
        }

        for (i, item) in items.iter().enumerate() {
            let perceived = self.perceived_values.get(&i).copied().unwrap_or(item.price);
            let price_incentive = if perceived > 0.0 {
                item.sell_price() / perceived
            } else {
                1.0
            };
            if rng_next() < self.gather_rate * price_incentive {
                *self.inventory.entry(i).or_insert(0) += 1;
            }
        }

        for i in 0..items.len() {
            let qty = self.inventory.get(&i).copied().unwrap_or(0);
            if qty > 0 {
                let pref = self.preferences.get(&i).copied().unwrap_or(0.5);
                if rng_next() < self.usage_rate * pref {
                    *self.inventory.entry(i).or_insert(0) -= 1;
                }
            }
        }

        match self.archetype {
            Archetype::Exploiter => {
                self.decide_exploiter(items, &mut decisions, record, &mut logs, slippage_coeff);
            }
            Archetype::GuildBuyer => {
                self.decide_guildbuyer(items, &mut decisions, record, &mut logs, slippage_coeff);
            }
            Archetype::MarketMaker => {
                self.decide_marketmaker(items, &mut decisions, record, &mut logs, slippage_coeff);
            }
            Archetype::InsiderTrader => {
                self.decide_insider_trader(
                    items,
                    &mut decisions,
                    record,
                    &mut logs,
                    slippage_coeff,
                );
            }
            Archetype::GuildSeller => {
                self.decide_guildseller(
                    items,
                    &mut decisions,
                    record,
                    &mut logs,
                    slippage_coeff,
                    current_tick,
                );
            }
            Archetype::VolumeTrader => {
                self.decide_volume_trader(
                    items,
                    &mut decisions,
                    record,
                    &mut logs,
                    slippage_coeff,
                    current_tick,
                );
            }
            _ => {
                self.decide_value_based(items, &mut decisions, record, &mut logs, slippage_coeff);
            }
        }

        DecisionResult { decisions, logs }
    }

    fn decide_value_based(
        &mut self,
        items: &[ItemState],
        decisions: &mut Vec<PlayerDecision>,
        record: bool,
        logs: &mut Vec<DecisionLog>,
        slippage_coeff: f64,
    ) {
        let mut item_indices: Vec<usize> = (0..items.len()).collect();
        item_indices.sort_by(|a, b| {
            let pa = self.preferences.get(a).copied().unwrap_or(0.5);
            let pb = self.preferences.get(b).copied().unwrap_or(0.5);
            pb.partial_cmp(&pa).unwrap_or(std::cmp::Ordering::Equal)
        });

        for &i in &item_indices {
            let preference = self.preferences.get(&i).copied().unwrap_or(0.5);
            if rng_next() > preference {
                continue;
            }

            let base_perceived = self
                .perceived_values
                .get(&i)
                .copied()
                .unwrap_or(items[i].price);
            let perceived = self.effective_perceived(i, base_perceived);
            let buy_price = items[i].buy_price();
            let sell_price = items[i].sell_price();

            if buy_price < perceived * (1.0 - self.buy_threshold) && self.balance > buy_price {
                let max_affordable = (self.balance / buy_price).floor() as i32;
                let risk_adjusted_max =
                    ((self.max_trade_amount as f64) * self.risk_tolerance).ceil() as i32;
                let amount = rng_range(1..=risk_adjusted_max.min(max_affordable).max(1));
                let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                let cost = buy_price * slippage * amount as f64;
                if cost <= self.balance {
                    let balance_before = self.balance;
                    let inventory_before = self.inventory.get(&i).copied().unwrap_or(0);
                    self.balance -= cost;
                    *self.inventory.entry(i).or_insert(0) += amount;
                    self.total_traded += cost;
                    self.total_trades += 1;
                    decisions.push(PlayerDecision {
                        item_index: i,
                        is_buy: true,
                        amount,
                    });
                    if record {
                        logs.push(DecisionLog {
                            player_id: self.id,
                            item_index: i,
                            is_buy: true,
                            amount,
                            price_per_unit: buy_price * slippage,
                            total_cost: cost,
                            perceived_value: base_perceived,
                            effective_perceived: perceived,
                            buy_threshold: self.buy_threshold,
                            sell_threshold: self.sell_threshold,
                            balance_before,
                            inventory_before,
                            reasoning: "value_buy".to_string(),
                        });
                    }
                }
            } else if sell_price > perceived * (1.0 + self.sell_threshold) {
                let have = self.inventory.get(&i).copied().unwrap_or(0);
                if have > 0 {
                    let amount = rng_range(1..=have.min(self.max_trade_amount).max(1));
                    let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                    let revenue = sell_price / slippage * amount as f64;
                    let balance_before = self.balance;
                    let inventory_before = have;
                    self.balance += revenue;
                    *self.inventory.entry(i).or_insert(0) -= amount;
                    self.total_traded += revenue;
                    self.total_trades += 1;
                    decisions.push(PlayerDecision {
                        item_index: i,
                        is_buy: false,
                        amount,
                    });
                    if record {
                        logs.push(DecisionLog {
                            player_id: self.id,
                            item_index: i,
                            is_buy: false,
                            amount,
                            price_per_unit: sell_price / slippage,
                            total_cost: revenue,
                            perceived_value: base_perceived,
                            effective_perceived: perceived,
                            buy_threshold: self.buy_threshold,
                            sell_threshold: self.sell_threshold,
                            balance_before,
                            inventory_before,
                            reasoning: "value_sell".to_string(),
                        });
                    }
                }
            }
        }
    }
    fn decide_exploiter(
        &mut self,
        items: &[ItemState],
        decisions: &mut Vec<PlayerDecision>,
        record: bool,
        logs: &mut Vec<DecisionLog>,
        slippage_coeff: f64,
    ) {
        let mut rng = SeededRng;

        for (i, item) in items.iter().enumerate() {
            let preference = self.preferences.get(&i).copied().unwrap_or(0.5);
            if rng.random(0.0..1.0) > preference {
                continue;
            }

            let base_perceived = self.perceived_values.get(&i).copied().unwrap_or(item.price);
            let perceived = self.effective_perceived(i, base_perceived);

            match item.trend.direction {
                PriceTrendDirection::Up => {
                    let buy_price = item.buy_price();
                    if self.balance > buy_price {
                        let max_affordable = (self.balance / buy_price).floor() as i32;
                        let amount = rng
                            .random_inclusive(1..=self.max_trade_amount.min(max_affordable).max(1));
                        let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                        let cost = buy_price * slippage * amount as f64;
                        if cost <= self.balance {
                            let balance_before = self.balance;
                            let inventory_before = self.inventory.get(&i).copied().unwrap_or(0);
                            self.balance -= cost;
                            *self.inventory.entry(i).or_insert(0) += amount;
                            self.total_traded += cost;
                            self.total_trades += 1;
                            decisions.push(PlayerDecision {
                                item_index: i,
                                is_buy: true,
                                amount,
                            });
                            if record {
                                logs.push(DecisionLog {
                                    player_id: self.id,
                                    item_index: i,
                                    is_buy: true,
                                    amount,
                                    price_per_unit: buy_price * slippage,
                                    total_cost: cost,
                                    perceived_value: base_perceived,
                                    effective_perceived: perceived,
                                    buy_threshold: self.buy_threshold,
                                    sell_threshold: self.sell_threshold,
                                    balance_before,
                                    inventory_before,
                                    reasoning: "trend_up".to_string(),
                                });
                            }
                        }
                    }
                }
                PriceTrendDirection::Down | PriceTrendDirection::Stable => {
                    let have = self.inventory.get(&i).copied().unwrap_or(0);
                    if have > 0 {
                        let sell_price = item.sell_price();
                        let amount =
                            rng.random_inclusive(1..=have.min(self.max_trade_amount).max(1));
                        let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                        let revenue = sell_price / slippage * amount as f64;
                        let balance_before = self.balance;
                        let inventory_before = have;
                        self.balance += revenue;
                        *self.inventory.entry(i).or_insert(0) -= amount;
                        self.total_traded += revenue;
                        self.total_trades += 1;
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: false,
                            amount,
                        });
                        if record {
                            let reasoning = match item.trend.direction {
                                PriceTrendDirection::Down => "trend_down",
                                _ => "trend_stable",
                            };
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: false,
                                amount,
                                price_per_unit: sell_price / slippage,
                                total_cost: revenue,
                                perceived_value: base_perceived,
                                effective_perceived: perceived,
                                buy_threshold: self.buy_threshold,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: reasoning.to_string(),
                            });
                        }
                    }
                }
            }
        }
    }

    /// Guild Buyer: maintains target inventory for guild members.
    /// Buys heavily when below target, holds otherwise, sells only at high surplus (> 2x target).
    fn decide_guildbuyer(
        &mut self,
        items: &[ItemState],
        decisions: &mut Vec<PlayerDecision>,
        record: bool,
        logs: &mut Vec<DecisionLog>,
        slippage_coeff: f64,
    ) {
        let mut rng = SeededRng;

        // Low-rate gathering — guilds get resources from members, not farming
        if rng.random(0.0..1.0) < 0.2 {
            for (i, _item) in items.iter().enumerate() {
                let target = self.guild_target_inventory.get(&i).copied().unwrap_or(50);
                let current = self.inventory.get(&i).copied().unwrap_or(0);
                if current < target && rng.random(0.0..1.0) < self.gather_rate {
                    *self.inventory.entry(i).or_insert(0) += 1;
                }
            }
        }

        // Phase 1: Price-dip buying — proactive market stabilization.
        // When price drops significantly below perceived value, GuildBuyer buys
        // regardless of current inventory level. This creates demand when prices fall,
        // acting as an automatic price floor and reducing systemic underselling.
        // BUG FIX (2026-03-27): Removed `&& current < base` from the condition.
        // The original check prevented price-dip buying when inventory >= base,
        // which contradicted the entire purpose of proactive buying. Phase 2
        // handles target replenishment separately — Phase 1 is purely price-driven.
        if self.guild_price_dip_threshold > 0.0 {
            let dip_multiplier = 1.0 - self.guild_price_dip_threshold;
            for (i, _item) in items.iter().enumerate() {
                let perceived = self
                    .perceived_values
                    .get(&i)
                    .copied()
                    .unwrap_or(items[i].price);
                // VWAP-anchored target: if enabled, use rolling volume-weighted average
                // price instead of subjective perceived value. VWAP tracks actual transaction
                // prices, so the price-dip trigger is more grounded in real market activity.
                let price_anchor = if self.use_vwap_targets {
                    items[i].rolling_vwap(100)
                } else {
                    perceived
                };
                let buy_price = items[i].buy_price();
                let current = self.inventory.get(&i).copied().unwrap_or(0);
                let target = self.guild_target_inventory.get(&i).copied().unwrap_or(50);

                // Price dip detected: market price is guild_price_dip_threshold+% below perceived
                // Buy regardless of inventory level (proactive stabilization)
                // Uses price_anchor (VWAP when enabled, perceived otherwise) for the trigger
                if buy_price < price_anchor * dip_multiplier && self.balance > buy_price {
                    // Can buy up to target inventory when seeing a dip (stock up opportunistically)
                    let room = (target - current).max(0) as f64;
                    let max_affordable = (self.balance / buy_price).floor() as i32;
                    let amount = rng.random_inclusive(
                        1..=(room as i32)
                            .min(max_affordable)
                            .min(self.max_trade_amount)
                            .max(1),
                    );
                    if amount <= 0 {
                        continue;
                    }
                    let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                    let cost = buy_price * slippage * amount as f64;
                    if cost <= self.balance {
                        let balance_before = self.balance;
                        let inventory_before = current;
                        self.balance -= cost;
                        *self.inventory.entry(i).or_insert(0) += amount;
                        self.total_traded += cost;
                        self.total_trades += 1;
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: true,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: true,
                                amount,
                                price_per_unit: buy_price * slippage,
                                total_cost: cost,
                                perceived_value: price_anchor,
                                effective_perceived: price_anchor * dip_multiplier,
                                buy_threshold: 0.0,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: "guild_price_dip".to_string(),
                            });
                        }
                    }
                }
            }
        }

        // Phase 2: Replenish target inventory when below target
        for (i, _item) in items.iter().enumerate() {
            let target = self.guild_target_inventory.get(&i).copied().unwrap_or(50);
            let current = self.inventory.get(&i).copied().unwrap_or(0);
            let buy_price = items[i].buy_price();
            let sell_price = items[i].sell_price();

            if current < target {
                // Below target — BUY to replenish. Willing to pay up to 1.5x perceived.
                let perceived = self
                    .perceived_values
                    .get(&i)
                    .copied()
                    .unwrap_or(items[i].price);
                let price_anchor = if self.use_vwap_targets {
                    items[i].rolling_vwap(100)
                } else {
                    perceived
                };
                let max_willing = price_anchor * 1.5;

                if buy_price <= max_willing && self.balance > buy_price {
                    let deficit = (target - current) as f64;
                    let max_affordable = (self.balance / buy_price).floor() as i32;
                    let amount = rng.random_inclusive(
                        1..=(deficit as i32)
                            .min(max_affordable)
                            .min(self.max_trade_amount)
                            .max(1),
                    );
                    let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                    let cost = buy_price * slippage * amount as f64;
                    if cost <= self.balance {
                        let balance_before = self.balance;
                        let inventory_before = current;
                        self.balance -= cost;
                        *self.inventory.entry(i).or_insert(0) += amount;
                        self.total_traded += cost;
                        self.total_trades += 1;
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: true,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: true,
                                amount,
                                price_per_unit: buy_price * slippage,
                                total_cost: cost,
                                perceived_value: price_anchor,
                                effective_perceived: max_willing,
                                buy_threshold: 0.0,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: "guild_replenish".to_string(),
                            });
                        }
                    }
                }
            } else if current > target * 2 {
                // Way above target — sell surplus
                let surplus = current - target;
                if surplus > 0 {
                    let amount =
                        rng.random_inclusive(1..=surplus.min(self.max_trade_amount).max(1));
                    let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                    let revenue = sell_price / slippage * amount as f64;
                    let balance_before = self.balance;
                    let inventory_before = current;
                    self.balance += revenue;
                    *self.inventory.entry(i).or_insert(0) -= amount;
                    self.total_traded += revenue;
                    self.total_trades += 1;
                    decisions.push(PlayerDecision {
                        item_index: i,
                        is_buy: false,
                        amount,
                    });
                    if record {
                        logs.push(DecisionLog {
                            player_id: self.id,
                            item_index: i,
                            is_buy: false,
                            amount,
                            price_per_unit: sell_price / slippage,
                            total_cost: revenue,
                            perceived_value: self
                                .perceived_values
                                .get(&i)
                                .copied()
                                .unwrap_or(items[i].price),
                            effective_perceived: self
                                .perceived_values
                                .get(&i)
                                .copied()
                                .unwrap_or(items[i].price),
                            buy_threshold: self.buy_threshold,
                            sell_threshold: self.sell_threshold,
                            balance_before,
                            inventory_before,
                            reasoning: "guild_surplus".to_string(),
                        });
                    }
                }
            }
            // Between target and 2x: hold — guild maintains stock
        }
    }

    /// Guild Seller: mirror of GuildBuyer. Sells when prices spike above perceived value,
    /// providing downward pressure to prevent bubble inflation.
    ///
    /// Phase 1 — Price-spike selling: when sell_price > perceived*(1+threshold)
    /// AND GuildSeller has inventory available. Creates supply when prices bubble,
    /// acting as an automatic price ceiling and preventing market overheating.
    /// Per-item cooldown prevents sell avalanche during sustained spike conditions.
    ///
    /// Phase 2 — Excess liquidation: sell surplus when > 2x target inventory.
    /// (mirrors GuildBuyer's surplus sell behavior)
    fn decide_guildseller(
        &mut self,
        items: &[ItemState],
        decisions: &mut Vec<PlayerDecision>,
        record: bool,
        logs: &mut Vec<DecisionLog>,
        slippage_coeff: f64,
        current_tick: u64,
    ) {
        let mut rng = SeededRng;

        // Low-rate gathering — guilds get resources from members
        if rng.random(0.0..1.0) < 0.2 {
            for (i, _item) in items.iter().enumerate() {
                let target = self.guild_target_inventory.get(&i).copied().unwrap_or(50);
                let current = self.inventory.get(&i).copied().unwrap_or(0);
                if current < target && rng.random(0.0..1.0) < self.gather_rate {
                    *self.inventory.entry(i).or_insert(0) += 1;
                }
            }
        }

        // Phase 1: Price-spike selling — proactive anti-bubble mechanism.
        // When price rises significantly above perceived value, GuildSeller sells
        // regardless of current inventory level. This creates supply when prices bubble,
        // acting as an automatic price ceiling and preventing market overheating.
        if self.guild_sell_threshold > 0.0 {
            let spike_multiplier = 1.0 + self.guild_sell_threshold;
            for (i, _item) in items.iter().enumerate() {
                let perceived = self
                    .perceived_values
                    .get(&i)
                    .copied()
                    .unwrap_or(items[i].price);
                let sell_price = items[i].sell_price();
                let current = self.inventory.get(&i).copied().unwrap_or(0);

                // Price spike detected: sell if we have inventory (cooldown prevents avalanche)
                if sell_price > perceived * spike_multiplier {
                    // Per-item cooldown: don't re-sell the same item too frequently
                    let last_spike_sold =
                        self.guild_sell_cooldown_ticks.get(&i).copied().unwrap_or(0);
                    if current_tick - last_spike_sold < 5 {
                        continue;
                    }
                    let have = current;
                    if have <= 0 {
                        continue;
                    }
                    // Sell up to available inventory × risk_tolerance (don't dump 100%)
                    let max_sell = (have as f64 * self.risk_tolerance).ceil() as i32;
                    let amount =
                        rng.random_inclusive(1..=max_sell.min(self.max_trade_amount).max(1));
                    if amount <= 0 {
                        continue;
                    }
                    // Record this tick so we don't sell the same item again too soon
                    self.guild_sell_cooldown_ticks.insert(i, current_tick);
                    let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                    let revenue = sell_price / slippage * amount as f64;
                    let balance_before = self.balance;
                    let inventory_before = current;
                    self.balance += revenue;
                    *self.inventory.entry(i).or_insert(0) -= amount;
                    self.total_traded += revenue;
                    self.total_trades += 1;
                    decisions.push(PlayerDecision {
                        item_index: i,
                        is_buy: false,
                        amount,
                    });
                    if record {
                        logs.push(DecisionLog {
                            player_id: self.id,
                            item_index: i,
                            is_buy: false,
                            amount,
                            price_per_unit: sell_price / slippage,
                            total_cost: revenue,
                            perceived_value: perceived,
                            effective_perceived: perceived * spike_multiplier,
                            buy_threshold: self.buy_threshold,
                            sell_threshold: self.sell_threshold,
                            balance_before,
                            inventory_before,
                            reasoning: "guild_price_spike".to_string(),
                        });
                    }
                }
            }
        }

        // Phase 2: Liquidate excess inventory when well above target
        // OR when price dips below perceived*(1-threshold) indicating market oversupply.
        // The price-dip trigger is the redesigned Phase 2 — active anti-oversupply mechanism.
        for (i, _item) in items.iter().enumerate() {
            let target = self.guild_target_inventory.get(&i).copied().unwrap_or(50);
            let current = self.inventory.get(&i).copied().unwrap_or(0);
            let perceived = self
                .perceived_values
                .get(&i)
                .copied()
                .unwrap_or(items[i].price);
            let sell_price = items[i].sell_price();

            // Trigger A: Excess inventory liquidation (legacy behavior)
            let trigger_a = current > target * 2;

            // Trigger B: Price-dip — redesigned Phase 2 active oversupply detection
            // When price < perceived * (1 - dip_threshold), market is oversupplied.
            // Sell proportionally to how severe the dip is.
            let trigger_b = self.guild_phase2_dip_threshold > 0.0
                && sell_price < perceived * (1.0 - self.guild_phase2_dip_threshold);

            if !trigger_a && !trigger_b {
                continue;
            }

            // Compute amount to sell
            let amount = if trigger_a {
                // Legacy: sell surplus above target
                let surplus = current - target;
                rng.random_inclusive(1..=surplus.min(self.max_trade_amount).max(1))
            } else {
                // Redesigned Phase 2: sell proportional to dip severity
                // dip_severity = 1.0 when price=0, approaches 0 as price approaches threshold
                let dip_floor = perceived * (1.0 - self.guild_phase2_dip_threshold);
                let dip_severity = ((dip_floor - sell_price) / dip_floor).clamp(0.0, 1.0);
                // Sell: dip_severity * risk_tolerance * current, min 1
                let base_amount =
                    (dip_severity * self.risk_tolerance * (current as f64)).ceil() as i32;
                rng.random_inclusive(1..=base_amount.min(self.max_trade_amount).max(1))
            };

            if amount <= 0 {
                continue;
            }

            let sell_amount = amount.min(current.max(1));
            let slippage = 1.0 + slippage_coeff * (sell_amount as f64).sqrt();
            let revenue = sell_price / slippage * sell_amount as f64;
            let balance_before = self.balance;
            let inventory_before = current;
            self.balance += revenue;
            *self.inventory.entry(i).or_insert(0) -= sell_amount;
            self.total_traded += revenue;
            self.total_trades += 1;
            decisions.push(PlayerDecision {
                item_index: i,
                is_buy: false,
                amount: sell_amount,
            });
            if record {
                logs.push(DecisionLog {
                    player_id: self.id,
                    item_index: i,
                    is_buy: false,
                    amount: sell_amount,
                    price_per_unit: sell_price / slippage,
                    total_cost: revenue,
                    perceived_value: perceived,
                    effective_perceived: if trigger_b {
                        perceived * (1.0 - self.guild_phase2_dip_threshold)
                    } else {
                        perceived
                    },
                    buy_threshold: self.buy_threshold,
                    sell_threshold: self.sell_threshold,
                    balance_before,
                    inventory_before,
                    reasoning: if trigger_a {
                        "guild_liquidate_excess".to_string()
                    } else {
                        "guild_phase2_oversupply".to_string()
                    },
                });
            }
        }
    }

    /// Market Maker: posts two-sided orders around perceived fair value.
    ///
    /// Strategy:
    /// - Each item has a target inventory level (mm_target_inventory).
    /// - If current < target: buy from market (up to deficit × risk_tolerance).
    /// - If current > target: sell to market (up to surplus × risk_tolerance).
    /// - If near target: post both a buy AND a sell (two-sided liquidity).
    /// - Fair value = midpoint of (perceived, base_price, current_market_price).
    /// - Buy price = fair × (1 - sell_threshold), Sell price = fair × (1 + sell_threshold).
    ///   The spread (2 × sell_threshold) is the MarketMaker's profit margin.
    ///
    /// Effect on economy:
    /// - Provides liquidity on BOTH sides simultaneously.
    /// - Counteracts Farmer sell pressure (absorbs sells when overstocked).
    /// - Counteracts Hoarder undersupply (provides buys when understocked).
    /// - Tight spread = more transactions, more GDP, more stable prices.
    fn decide_marketmaker(
        &mut self,
        items: &[ItemState],
        decisions: &mut Vec<PlayerDecision>,
        record: bool,
        logs: &mut Vec<DecisionLog>,
        slippage_coeff: f64,
    ) {
        let mut rng = SeededRng;
        let max_inv = self.mm_max_inventory.max(1) as f64;
        let target_inv = self.mm_target_inventory.max(1) as f64;

        for (i, _item) in items.iter().enumerate() {
            let perceived = self
                .perceived_values
                .get(&i)
                .copied()
                .unwrap_or(items[i].price);
            let base = items[i].price;
            // Fair value: blend of perceived value and market price
            let fair = (perceived + base) / 2.0;

            let buy_price = items[i].buy_price();
            let sell_price = items[i].sell_price();
            let current = self.inventory.get(&i).copied().unwrap_or(0) as f64;
            let deficit = (target_inv - current).max(0.0);
            let surplus = (current - target_inv).max(0.0);
            let at_target = current >= target_inv * 0.8 && current <= target_inv * 1.2;

            // Post buy order when below target or at target
            // Buy price threshold is self.sell_threshold (narrow margin for MM)
            if buy_price < perceived * (1.0 + self.sell_threshold) && self.balance > buy_price {
                // Scale buy amount by how understocked we are
                let position_fraction = (deficit / max_inv).min(1.0);
                let base_amount =
                    (self.max_trade_amount as f64 * position_fraction * self.risk_tolerance).ceil();
                let amount = rng.random_inclusive(1..=base_amount.max(1.0) as i32);
                if amount > 0 {
                    let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                    let cost = buy_price * slippage * amount as f64;
                    if cost <= self.balance {
                        let balance_before = self.balance;
                        let inventory_before = current as i32;
                        self.balance -= cost;
                        *self.inventory.entry(i).or_insert(0) += amount;
                        self.total_traded += cost;
                        self.total_trades += 1;
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: true,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: true,
                                amount,
                                price_per_unit: buy_price * slippage,
                                total_cost: cost,
                                perceived_value: perceived,
                                effective_perceived: fair,
                                buy_threshold: self.buy_threshold,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: "mm_buy".to_string(),
                            });
                        }
                    }
                }
            }

            // Post sell order when above target or at target
            // Sell price threshold is self.sell_threshold (narrow margin for MM)
            if sell_price > perceived * (1.0 - self.sell_threshold) {
                let have = self.inventory.get(&i).copied().unwrap_or(0);
                if have > 0 {
                    let position_fraction = (surplus / max_inv).min(1.0);
                    let base_amount =
                        (self.max_trade_amount as f64 * position_fraction * self.risk_tolerance)
                            .ceil();
                    let amount = rng
                        .random_inclusive(1..=base_amount.max(1.0) as i32)
                        .min(have);
                    if amount > 0 {
                        let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                        let revenue = sell_price / slippage * amount as f64;
                        let balance_before = self.balance;
                        let inventory_before = have;
                        self.balance += revenue;
                        *self.inventory.entry(i).or_insert(0) -= amount;
                        self.total_traded += revenue;
                        self.total_trades += 1;
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: false,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: false,
                                amount,
                                price_per_unit: sell_price / slippage,
                                total_cost: revenue,
                                perceived_value: perceived,
                                effective_perceived: fair,
                                buy_threshold: self.buy_threshold,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: "mm_sell".to_string(),
                            });
                        }
                    }
                }
            }

            // Two-sided mode when at target: also post passive orders on the other side
            if at_target {
                // Already posted a buy above if needed; now also post a sell if have inventory
                let have = self.inventory.get(&i).copied().unwrap_or(0);
                if have > 0 && sell_price > perceived * (1.0 - self.sell_threshold) {
                    let amount = rng.random_inclusive(1..=have.min(self.max_trade_amount).max(1));
                    if amount > 0 {
                        let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                        let revenue = sell_price / slippage * amount as f64;
                        let balance_before = self.balance;
                        let inventory_before = have;
                        self.balance += revenue;
                        *self.inventory.entry(i).or_insert(0) -= amount;
                        self.total_traded += revenue;
                        self.total_trades += 1;
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: false,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: false,
                                amount,
                                price_per_unit: sell_price / slippage,
                                total_cost: revenue,
                                perceived_value: perceived,
                                effective_perceived: fair,
                                buy_threshold: self.buy_threshold,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: "mm_two_sided".to_string(),
                            });
                        }
                    }
                }
            }
        }
    }

    /// Insider Trader — mean-reversion strategy.
    ///
    /// Tracks a rolling price history for each item. When current price is
    /// significantly BELOW the rolling mean → BUY (price is cheap, expect rebound).
    /// When significantly ABOVE → SELL (price is expensive, expect pullback).
    /// Near the mean → hold/neutral.
    ///
    /// Key distinction from Exploiter (momentum-following):
    ///   Exploiter buys when price is RISING (rides momentum up)
    ///   InsiderTrader buys when price is FALLING (fades momentum, expects bounce)
    ///
    /// Key distinction from MarketMaker (liquidity provision):
    ///   MM posts around perceived fair value regardless of price history
    ///   InsiderTrader specifically exploits deviations from recent price average
    ///
    /// This provides a stabilizing counter-force to price overshoot in both
    /// directions — buying into dips reduces crash depth, selling into spikes
    /// trims rally height. Acts as a "smart money" anchor in the player mix.
    fn decide_insider_trader(
        &mut self,
        items: &[ItemState],
        decisions: &mut Vec<PlayerDecision>,
        record: bool,
        logs: &mut Vec<DecisionLog>,
        slippage_coeff: f64,
    ) {
        let mut rng = SeededRng;

        for (i, item) in items.iter().enumerate() {
            // Update price history: record current price BEFORE making decisions.
            // This ensures decisions are based on history UP TO the start of this tick.
            let current_price = item.sell_price();
            let history = self.insider_price_history.entry(i).or_default();

            // Add current price to history (will be used in NEXT tick's decisions)
            // Skip if history window is 0
            if self.insider_history_window > 0 {
                history.push_back(current_price);
                while history.len() > self.insider_history_window {
                    history.pop_front();
                }
            }

            // Need at least 3 data points before we can meaningfully mean-revert
            if history.len() < 3 {
                continue;
            }

            // Compute rolling mean
            let mean: f64 = history.iter().sum::<f64>() / history.len() as f64;
            if mean <= 0.0 {
                continue;
            }

            // ── Adaptive threshold: widen threshold during volatile markets ──
            // Compute rolling volatility from the engine's price history (last N prices
            // matching the insider's history window). Volatility = coefficient of variation.
            let vol_window = self.insider_history_window.min(item.price_history.len());
            let vol_data: &[f64] =
                &item.price_history[item.price_history.len().saturating_sub(vol_window)..];
            let vol_mean: f64 = vol_data.iter().sum::<f64>() / vol_data.len() as f64;
            let vol_std = if vol_data.len() > 1 {
                let variance = vol_data
                    .iter()
                    .map(|&p| {
                        let d = p - vol_mean;
                        d * d
                    })
                    .sum::<f64>()
                    / vol_data.len() as f64;
                variance.sqrt()
            } else {
                0.0
            };
            let vol_normalized = if vol_mean > 0.0 {
                vol_std / vol_mean
            } else {
                0.0
            };
            // When vol is high → threshold widens → InsiderTrader is more selective (avoids noise).
            // When vol is low → threshold tightens → InsiderTrader reacts to smaller deviations.
            // Sensitivity controls how aggressively the threshold scales with volatility.
            let effective_threshold =
                self.buy_threshold * (1.0 + self.insider_volatility_sensitivity * vol_normalized);

            let deviation = (current_price - mean) / mean;
            let perceived = mean; // Use rolling mean as the insider's fair value estimate
            let buy_price = item.buy_price();
            let sell_price = item.sell_price();

            // BUY when price is significantly below mean (undervalued)
            // deviation is negative → e.g., deviation=-0.15 means price is 15% below mean
            if deviation < -effective_threshold && buy_price <= self.balance {
                // Size scales with how extreme the deviation is
                // At -effective_threshold: min position. At -2x effective_threshold: max position.
                let extremity = (-deviation / effective_threshold).min(2.0);
                let base_amount =
                    (self.max_trade_amount as f64 * extremity * self.risk_tolerance).ceil();
                let amount = rng.random_inclusive(1..=base_amount.max(1.0) as i32);
                let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                let cost = buy_price * slippage * amount as f64;
                if cost <= self.balance {
                    let balance_before = self.balance;
                    let inventory_before = self.inventory.get(&i).copied().unwrap_or(0);
                    self.balance -= cost;
                    *self.inventory.entry(i).or_insert(0) += amount;
                    self.total_traded += cost;
                    self.total_trades += 1;
                    decisions.push(PlayerDecision {
                        item_index: i,
                        is_buy: true,
                        amount,
                    });
                    if record {
                        logs.push(DecisionLog {
                            player_id: self.id,
                            item_index: i,
                            is_buy: true,
                            amount,
                            price_per_unit: buy_price * slippage,
                            total_cost: cost,
                            perceived_value: perceived,
                            effective_perceived: mean,
                            buy_threshold: self.buy_threshold,
                            sell_threshold: self.sell_threshold,
                            balance_before,
                            inventory_before,
                            reasoning: format!("insider_buy_dev={:.2}", deviation),
                        });
                    }
                }
            }
            // SELL when price is significantly above mean (overvalued)
            else if deviation > effective_threshold {
                let have = self.inventory.get(&i).copied().unwrap_or(0);
                if have > 0 {
                    let extremity = (deviation / effective_threshold).min(2.0);
                    let base_amount = (have as f64 * extremity * self.risk_tolerance).ceil();
                    let amount = rng
                        .random_inclusive(1..=base_amount.max(1.0) as i32)
                        .min(have);
                    if amount > 0 {
                        let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                        let revenue = sell_price / slippage * amount as f64;
                        let balance_before = self.balance;
                        let inventory_before = have;
                        self.balance += revenue;
                        *self.inventory.entry(i).or_insert(0) -= amount;
                        self.total_traded += revenue;
                        self.total_trades += 1;
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: false,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: false,
                                amount,
                                price_per_unit: sell_price / slippage,
                                total_cost: revenue,
                                perceived_value: perceived,
                                effective_perceived: mean,
                                buy_threshold: self.buy_threshold,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: format!("insider_sell_dev={:.2}", deviation),
                            });
                        }
                    }
                }
            }
            // NEUTRAL when price is within threshold band — do nothing
        }
    }

    fn decide_volume_trader(
        &mut self,
        items: &[ItemState],
        decisions: &mut Vec<PlayerDecision>,
        record: bool,
        logs: &mut Vec<DecisionLog>,
        slippage_coeff: f64,
        current_tick: u64,
    ) {
        let mut rng = SeededRng;

        for (i, item) in items.iter().enumerate() {
            // Update spread history: record this tick's BPD for use in NEXT tick
            // (mirrors InsiderTrader — decisions use data from PREVIOUS ticks only)
            let current_bpd = items[i].spread.bpd;

            let spread_history = self.volume_spread_history.entry(i).or_default();
            if self.volume_spread_window > 0 {
                spread_history.push_back(current_bpd);
                while spread_history.len() > self.volume_spread_window {
                    spread_history.pop_front();
                }
            }

            // Update price history similarly
            let current_price = item.price;
            let price_history = self.volume_price_history.entry(i).or_default();
            if self.volume_price_window > 0 {
                price_history.push_back(current_price);
                while price_history.len() > self.volume_price_window {
                    price_history.pop_front();
                }
            }

            // Need enough spread data to compute a meaningful rolling mean
            if spread_history.len() < 3 {
                continue;
            }

            // Compute rolling means
            let spread_mean: f64 = spread_history.iter().sum::<f64>() / spread_history.len() as f64;
            let price_mean: f64 = price_history.iter().sum::<f64>() / price_history.len() as f64;

            if spread_mean <= 0.0 || price_mean <= 0.0 {
                continue;
            }

            // Check cooldown: don't re-decide on the same item too frequently
            let last_decided = self.volume_cooldown_ticks.get(&i).copied().unwrap_or(0);
            if current_tick - last_decided < 5 {
                continue;
            }

            // Spread deviation: how much wider/tighter is the current spread vs mean?
            let spread_deviation = (current_bpd - spread_mean) / spread_mean;
            // Price deviation: how much is price below/above its rolling mean?
            let price_deviation = (current_price - price_mean) / price_mean;

            // BUY signal: spread is significantly wider than normal AND price is below mean
            // This indicates a volume drought — prices artificially depressed, good entry
            // Require BOTH conditions to be significant to avoid false signals
            let threshold = self.buy_threshold; // same as sell_threshold (symmetric)
            let price_threshold = threshold * 0.7; // slightly softer on price

            if spread_deviation > threshold
                && price_deviation < -price_threshold
                && items[i].buy_price() <= self.balance
            {
                // How extreme is the volume drought?
                let extremity = (spread_deviation / threshold).min(2.0);
                let base_amount =
                    (self.max_trade_amount as f64 * extremity * self.risk_tolerance).ceil();
                let amount = rng.random_inclusive(1..=base_amount.max(1.0) as i32);
                if amount > 0 {
                    let buy_price = items[i].buy_price();
                    let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                    let cost = buy_price * slippage * amount as f64;
                    if cost <= self.balance {
                        let balance_before = self.balance;
                        let inventory_before = self.inventory.get(&i).copied().unwrap_or(0);
                        self.balance -= cost;
                        *self.inventory.entry(i).or_insert(0) += amount;
                        self.total_traded += cost;
                        self.total_trades += 1;
                        self.volume_cooldown_ticks.insert(i, current_tick);
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: true,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: true,
                                amount,
                                price_per_unit: buy_price * slippage,
                                total_cost: cost,
                                perceived_value: price_mean,
                                effective_perceived: spread_mean,
                                buy_threshold: self.buy_threshold,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: format!(
                                    "vt_buy_spread={:.2}price={:.2}",
                                    spread_deviation, price_deviation
                                ),
                            });
                        }
                    }
                }
            }
            // SELL signal: spread is significantly tighter than normal AND price is above mean
            // This indicates a volume surge — prices elevated, time to liquidate
            else if spread_deviation < -threshold && price_deviation > price_threshold {
                let have = self.inventory.get(&i).copied().unwrap_or(0);
                if have > 0 {
                    let extremity = ((-spread_deviation) / threshold).min(2.0);
                    let base_amount = (have as f64 * extremity * self.risk_tolerance).ceil();
                    let amount = rng
                        .random_inclusive(1..=base_amount.max(1.0) as i32)
                        .min(have);
                    if amount > 0 {
                        let sell_price = items[i].sell_price();
                        let slippage = 1.0 + slippage_coeff * (amount as f64).sqrt();
                        let revenue = sell_price / slippage * amount as f64;
                        let balance_before = self.balance;
                        let inventory_before = have;
                        self.balance += revenue;
                        *self.inventory.entry(i).or_insert(0) -= amount;
                        self.total_traded += revenue;
                        self.total_trades += 1;
                        self.volume_cooldown_ticks.insert(i, current_tick);
                        decisions.push(PlayerDecision {
                            item_index: i,
                            is_buy: false,
                            amount,
                        });
                        if record {
                            logs.push(DecisionLog {
                                player_id: self.id,
                                item_index: i,
                                is_buy: false,
                                amount,
                                price_per_unit: sell_price / slippage,
                                total_cost: revenue,
                                perceived_value: price_mean,
                                effective_perceived: spread_mean,
                                buy_threshold: self.buy_threshold,
                                sell_threshold: self.sell_threshold,
                                balance_before,
                                inventory_before,
                                reasoning: format!(
                                    "vt_sell_spread={:.2}price={:.2}",
                                    spread_deviation, price_deviation
                                ),
                            });
                        }
                    }
                }
            }
            // NEUTRAL: no significant spread/price deviation — do nothing
        }
    }
}
