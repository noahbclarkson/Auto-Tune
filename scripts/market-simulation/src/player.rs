use std::cell::RefCell;
use std::collections::HashMap;

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
    pub credit_score: i32,
    pub total_traded: f64,
    pub online: bool,
    pub total_trades: u32,
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
        };
        agent.init_perceived_values(item_count, base_prices);
        // AFK farmers prefer cheap gathered items (building blocks, ores, drops)
        agent.init_afk_preferences(item_count);
        agent
    }

    /// Guild Buyer: maintains a target inventory for guild members.
    /// Buys heavily when stock is low, rarely sells (guild benefit).
    pub fn new_guild_buyer(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = SeededRng;
        let budget = rng.random(50000.0..200000.0);

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
        };
        agent.init_perceived_values(item_count, base_prices);
        agent.init_preferences(item_count);
        // Pre-fill some inventory to represent guild stock
        for i in 0..item_count {
            agent.inventory.insert(i, rng.random(10..50));
        }
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
        } else {
            Self::new_guild_buyer(index, item_count, base_prices)
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
}
