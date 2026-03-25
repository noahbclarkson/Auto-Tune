use std::cell::RefCell;
use std::collections::HashMap;

use rand::RngExt;
use rand::SeedableRng;
use rand::rngs::StdRng;
use rand_distr::{Distribution, Normal};

/// Thread-local seeded RNG for deterministic regression testing.
/// Set via `set_global_seeded_rng()` and cleared via `clear_global_seeded_rng()`.
thread_local! {
    static GLOBAL_SEEDED_RNG: RefCell<Option<StdRng>> = const { RefCell::new(None) };
}

/// Set the thread-local seeded RNG for deterministic runs.
pub fn set_global_seeded_rng(seed: u64) {
    GLOBAL_SEEDED_RNG.with(|cell| *cell.borrow_mut() = Some(StdRng::seed_from_u64(seed)));
}

/// Clear the thread-local seeded RNG (restore normal randomness).
pub fn clear_global_seeded_rng() {
    GLOBAL_SEEDED_RNG.with(|cell| *cell.borrow_mut() = None);
}

/// Get next random f64: uses seeded RNG if set, else global RNG.
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

/// Get next random value from range: uses seeded RNG if set, else global RNG.
pub fn rng_range<R: rand::distr::uniform::SampleRange<T>, T>(range: R) -> T {
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
        let mut rng = rand::rng();
        let budget = rng.random_range(500.0..2000.0);

        let mut agent = Self {
            id: index,
            name: format!("Casual-{index}"),
            archetype: Archetype::Casual,
            balance: budget,
            online_probability: rng.random_range(0.1..0.3),
            activity_rate: rng.random_range(0.05..0.15),
            buy_threshold: rng.random_range(0.05..0.15),
            sell_threshold: rng.random_range(0.1..0.25),
            max_trade_amount: rng.random_range(1..10),
            risk_tolerance: rng.random_range(0.3..0.7),
            inventory_saturation: rng.random_range(0.03..0.07),
            gather_rate: rng.random_range(0.05..0.15),
            usage_rate: rng.random_range(0.15..0.30),
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
        let mut rng = rand::rng();
        let budget = rng.random_range(200.0..1000.0);

        let mut agent = Self {
            id: index,
            name: format!("Farmer-{index}"),
            archetype: Archetype::Farmer,
            balance: budget,
            online_probability: rng.random_range(0.3..0.6),
            activity_rate: rng.random_range(0.2..0.4),
            buy_threshold: rng.random_range(0.2..0.4),
            sell_threshold: rng.random_range(0.0..0.05),
            max_trade_amount: rng.random_range(5..30),
            risk_tolerance: rng.random_range(0.2..0.5),
            inventory_saturation: rng.random_range(0.02..0.05),
            gather_rate: rng.random_range(0.3..0.5),
            usage_rate: rng.random_range(0.05..0.12),
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
            agent.inventory.insert(i, rng.random_range(5..20));
        }
        agent
    }

    pub fn new_trader(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = rand::rng();
        let budget = rng.random_range(5000.0..20000.0);

        let mut agent = Self {
            id: index,
            name: format!("Trader-{index}"),
            archetype: Archetype::Trader,
            balance: budget,
            online_probability: rng.random_range(0.5..0.8),
            activity_rate: rng.random_range(0.5..0.8),
            buy_threshold: rng.random_range(0.02..0.08),
            sell_threshold: rng.random_range(0.02..0.08),
            max_trade_amount: rng.random_range(1..15),
            risk_tolerance: rng.random_range(0.5..0.9),
            inventory_saturation: rng.random_range(0.08..0.15),
            gather_rate: rng.random_range(0.02..0.08),
            usage_rate: rng.random_range(0.03..0.08),
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
        let mut rng = rand::rng();
        let budget = rng.random_range(3000.0..10000.0);

        let mut agent = Self {
            id: index,
            name: format!("Hoarder-{index}"),
            archetype: Archetype::Hoarder,
            balance: budget,
            online_probability: rng.random_range(0.2..0.4),
            activity_rate: rng.random_range(0.1..0.3),
            buy_threshold: rng.random_range(0.0..0.05),
            sell_threshold: rng.random_range(0.3..0.5),
            max_trade_amount: rng.random_range(5..25),
            risk_tolerance: rng.random_range(0.6..0.9),
            inventory_saturation: rng.random_range(0.005..0.02),
            gather_rate: rng.random_range(0.08..0.15),
            usage_rate: rng.random_range(0.02..0.05),
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
        let mut rng = rand::rng();
        let budget = rng.random_range(20000.0..100000.0);

        let mut agent = Self {
            id: index,
            name: format!("Exploiter-{index}"),
            archetype: Archetype::Exploiter,
            balance: budget,
            online_probability: rng.random_range(0.8..0.95),
            activity_rate: rng.random_range(0.8..1.0),
            buy_threshold: 0.0,
            sell_threshold: 0.0,
            max_trade_amount: rng.random_range(10..50),
            risk_tolerance: 1.0,
            inventory_saturation: rng.random_range(0.04..0.08),
            gather_rate: rng.random_range(0.01..0.05),
            usage_rate: rng.random_range(0.01..0.03),
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
        let mut rng = rand::rng();
        // Newbies start with modest budget
        let budget = rng.random_range(100.0..500.0);

        let mut agent = Self {
            id: index,
            name: format!("Newbie-{index}"),
            archetype: Archetype::Newbie,
            balance: budget,
            // Newbies come and go unpredictably
            online_probability: rng.random_range(0.15..0.35),
            // But when online, quite active
            activity_rate: rng.random_range(0.3..0.6),
            // Will buy even at slight premium — eager to get items
            buy_threshold: rng.random_range(0.0..0.05),
            // Never sells (new players hoard what they get)
            sell_threshold: rng.random_range(0.4..0.7),
            max_trade_amount: rng.random_range(1..8),
            risk_tolerance: rng.random_range(0.2..0.5),
            // Newbies gather some but use even more
            inventory_saturation: rng.random_range(0.02..0.06),
            gather_rate: rng.random_range(0.1..0.2),
            usage_rate: rng.random_range(0.2..0.4),
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
        let mut rng = rand::rng();
        let budget = rng.random_range(50.0..300.0);

        let mut agent = Self {
            id: index,
            name: format!("AFKFarmer-{index}"),
            archetype: Archetype::AFKFarmer,
            balance: budget,
            // Very rarely online
            online_probability: rng.random_range(0.05..0.15),
            // When online, very active — dumps inventory
            activity_rate: rng.random_range(0.8..1.0),
            buy_threshold: rng.random_range(0.3..0.5), // Almost never buys
            sell_threshold: rng.random_range(0.0..0.03), // Sells at tiny margin
            max_trade_amount: rng.random_range(50..200), // Huge dump sizes
            risk_tolerance: rng.random_range(0.1..0.3),
            // Accumulates a LOT of inventory while AFK
            inventory_saturation: rng.random_range(0.3..0.6),
            // Gathers rapidly while online
            gather_rate: rng.random_range(1.0..3.0),
            // Almost no usage
            usage_rate: rng.random_range(0.0..0.02),
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
        let mut rng = rand::rng();
        let budget = rng.random_range(50000.0..200000.0);

        let mut agent = Self {
            id: index,
            name: format!("GuildBuyer-{index}"),
            archetype: Archetype::GuildBuyer,
            balance: budget,
            online_probability: rng.random_range(0.6..0.9),
            activity_rate: rng.random_range(0.6..0.9),
            // Sells only at high premium (guild markup)
            buy_threshold: rng.random_range(0.0..0.03),
            sell_threshold: rng.random_range(0.5..0.8),
            max_trade_amount: rng.random_range(20..100),
            risk_tolerance: rng.random_range(0.4..0.7),
            // Needs to keep substantial stock for members
            inventory_saturation: rng.random_range(0.3..0.6),
            // Gathers moderately
            gather_rate: rng.random_range(0.05..0.15),
            // Provides to guild — low personal use
            usage_rate: rng.random_range(0.0..0.05),
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
            agent.inventory.insert(i, rng.random_range(10..50));
        }
        agent
    }

    fn init_newbie_preferences(&mut self, item_count: usize) {
        // Newbies strongly prefer cheap, basic items (low base_price)
        let mut rng = rand::rng();
        for i in 0..item_count {
            // Higher preference for cheaper items
            let pref = rng.random_range(0.5..1.0);
            self.preferences.insert(i, pref);
        }
    }

    fn init_afk_preferences(&mut self, item_count: usize) {
        // AFK farmers focus on gatherable items: building materials, ores, basic drops
        let mut rng = rand::rng();
        for i in 0..item_count {
            let pref = rng.random_range(0.3..0.8);
            self.preferences.insert(i, pref);
        }
    }

    pub fn new_random(index: usize, item_count: usize, base_prices: &[f64]) -> Self {
        let mut rng = rand::rng();
        let roll: f64 = rng.random();
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
        let mut rng = rand::rng();
        for (i, &base) in base_prices.iter().enumerate().take(item_count) {
            let stddev = base * 0.15;
            let normal =
                Normal::new(base, stddev).unwrap_or_else(|_| Normal::new(base, 1.0).unwrap());
            let perceived: f64 = normal.sample(&mut rng).max(base * 0.5);
            self.perceived_values.insert(i, perceived);
        }
    }

    fn init_preferences(&mut self, item_count: usize) {
        let mut rng = rand::rng();
        for i in 0..item_count {
            self.preferences.insert(i, rng.random_range(0.1..1.0));
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
        let mut rng = rand::rng();

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
        let mut rng = rand::rng();

        for (i, item) in items.iter().enumerate() {
            let preference = self.preferences.get(&i).copied().unwrap_or(0.5);
            if rng.random::<f64>() > preference {
                continue;
            }

            let base_perceived = self.perceived_values.get(&i).copied().unwrap_or(item.price);
            let perceived = self.effective_perceived(i, base_perceived);

            match item.trend.direction {
                PriceTrendDirection::Up => {
                    let buy_price = item.buy_price();
                    if self.balance > buy_price {
                        let max_affordable = (self.balance / buy_price).floor() as i32;
                        let amount =
                            rng.random_range(1..=self.max_trade_amount.min(max_affordable).max(1));
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
                        let amount = rng.random_range(1..=have.min(self.max_trade_amount).max(1));
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
