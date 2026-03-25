use std::collections::{HashMap, VecDeque};

use crate::config::{SimConfig, TICKS_PER_DAY};

const ATANH_099: f64 = 2.6466524123622457;
const VOLUME_BUCKETS: usize = 10;
const TARGET_TX_DENSITY_PER_DAY: f64 = 100.0;

fn round2(x: f64) -> f64 {
    (x * 100.0).round() / 100.0
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TransactionType {
    Buy,
    Sell,
}

#[derive(Clone, Debug)]
pub struct Transaction {
    pub item_index: usize,
    pub tx_type: TransactionType,
    pub amount: i32,
    pub total_price: f64,
    pub tick: u64,
    pub player_id: usize,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PriceTrendDirection {
    Up,
    Down,
    Stable,
}

#[derive(Clone, Debug)]
pub struct PriceTrend {
    pub direction: PriceTrendDirection,
    pub percent_change: f64,
}

#[derive(Clone, Debug)]
pub struct SpreadResult {
    pub bpd: f64,
    pub spd: f64,
}

impl Default for SpreadResult {
    fn default() -> Self {
        Self {
            bpd: 0.15,
            spd: 0.15,
        }
    }
}

#[derive(Clone, Debug)]
pub struct ItemState {
    pub name: String,
    pub base_price: f64,
    pub price: f64,
    pub spread: SpreadResult,
    pub trend: PriceTrend,
    pub trend_direction: PriceTrendDirection,
    pub trend_streak: u32,
    pub price_history: Vec<f64>,
    pub buy_price_history: Vec<f64>,
    pub sell_price_history: Vec<f64>,
    pub bpd_history: Vec<f64>,
    pub spd_history: Vec<f64>,
    pub buy_volume_history: Vec<i32>,
    pub sell_volume_history: Vec<i32>,
    pub tick_buy_volume: i32,
    pub tick_sell_volume: i32,
}

impl ItemState {
    pub fn buy_price(&self) -> f64 {
        round2(self.price * (1.0 + self.spread.bpd))
    }

    pub fn sell_price(&self) -> f64 {
        round2((self.price * (1.0 - self.spread.spd)).max(0.0))
    }
}

struct TradeMetrics {
    weighted_buys: f64,
    weighted_sells: f64,
    distinct_traders: usize,
}

pub struct MarketEngine {
    pub items: Vec<ItemState>,
    pub global_volume_multiplier: f64,
    pub global_volume_history: Vec<f64>,
    pub effective_window_ticks: u64,
}

impl MarketEngine {
    pub fn new(config: &SimConfig) -> Self {
        let items = config
            .items
            .iter()
            .map(|ic| ItemState {
                name: ic.name.clone(),
                base_price: ic.base_price,
                price: ic.base_price,
                spread: SpreadResult::default(),
                trend: PriceTrend {
                    direction: PriceTrendDirection::Stable,
                    percent_change: 0.0,
                },
                trend_direction: PriceTrendDirection::Stable,
                trend_streak: 0,
                price_history: vec![ic.base_price],
                buy_price_history: Vec::new(),
                sell_price_history: Vec::new(),
                bpd_history: Vec::new(),
                spd_history: Vec::new(),
                buy_volume_history: vec![0],
                sell_volume_history: vec![0],
                tick_buy_volume: 0,
                tick_sell_volume: 0,
            })
            .collect();

        Self {
            items,
            global_volume_multiplier: 1.0,
            global_volume_history: vec![1.0],
            effective_window_ticks: config.trade_window_ticks(),
        }
    }

    pub fn tick(
        &mut self,
        online_count: i32,
        config: &SimConfig,
        current_tick: u64,
        transactions: &VecDeque<Transaction>,
    ) {
        let base_window_ticks = config.trade_window_ticks();
        let trade_window_ticks = if config.economy.adaptive_window {
            calculate_adaptive_window(
                base_window_ticks,
                config.min_window_ticks(),
                config.max_window_ticks(),
                current_tick,
                transactions,
            )
        } else {
            base_window_ticks
        };
        self.effective_window_ticks = trade_window_ticks;

        let global_vol_mult =
            self.calculate_global_volume_multiplier(current_tick, trade_window_ticks, transactions);
        self.global_volume_multiplier = global_vol_mult;
        self.global_volume_history.push(global_vol_mult);
        if self.global_volume_history.len() > 5000 {
            self.global_volume_history.remove(0);
        }

        let old_prices: Vec<f64> = self.items.iter().map(|item| item.price).collect();
        let mut new_prices = Vec::with_capacity(self.items.len());
        let mut new_spreads = Vec::with_capacity(self.items.len());

        for item_idx in 0..self.items.len() {
            let metrics = calculate_trade_metrics(
                item_idx,
                current_tick,
                trade_window_ticks,
                transactions,
                config.economy.player_rate_limit_multiplier,
            );

            let price = self.calculate_new_price(item_idx, &metrics, online_count, config);
            let spread =
                self.calculate_spread(item_idx, &metrics, online_count, global_vol_mult, config);

            new_prices.push(price);
            new_spreads.push(spread);
        }

        let sector_correlation = config.economy.sector_correlation;
        if sector_correlation > 0.0001 && self.items.len() > 1 {
            let mut section_changes: HashMap<&str, Vec<(usize, f64)>> = HashMap::new();
            for (i, item_config) in config.items.iter().enumerate() {
                if i >= old_prices.len() {
                    break;
                }
                let old = old_prices[i];
                if old > 0.0 {
                    let pct_change = (new_prices[i] - old) / old;
                    section_changes
                        .entry(&item_config.section)
                        .or_default()
                        .push((i, pct_change));
                }
            }

            let max_group_size = config.economy.max_sector_correlation_group_size;
            for entries in section_changes.values() {
                if entries.len() < 2 || entries.len() > max_group_size {
                    continue;
                }
                let total: f64 = entries.iter().map(|(_, c)| c).sum();
                for &(idx, own_change) in entries {
                    let others_sum = total - own_change;
                    let others_avg = others_sum / (entries.len() - 1) as f64;
                    let nudge = sector_correlation * others_avg;
                    new_prices[idx] *= 1.0 + nudge;
                }
            }
        }

        for item_idx in 0..self.items.len() {
            let new_price = new_prices[item_idx].max(0.01);
            let new_price = round2(new_price);
            let spread = &new_spreads[item_idx];

            self.items[item_idx].price = new_price;
            self.items[item_idx].spread = spread.clone();

            let buy_price = self.items[item_idx].buy_price();
            let sell_price = self.items[item_idx].sell_price();

            self.items[item_idx].price_history.push(new_price);
            self.items[item_idx].buy_price_history.push(buy_price);
            self.items[item_idx].sell_price_history.push(sell_price);
            self.items[item_idx].bpd_history.push(spread.bpd);
            self.items[item_idx].spd_history.push(spread.spd);
            let bv = self.items[item_idx].tick_buy_volume;
            let sv = self.items[item_idx].tick_sell_volume;
            self.items[item_idx].buy_volume_history.push(bv);
            self.items[item_idx].sell_volume_history.push(sv);

            let old_price = old_prices[item_idx];
            let threshold = config.economy.trend_streak_threshold_percent / 100.0;
            let tick_dir = if old_price > 0.0 {
                let pct = (new_price - old_price) / old_price;
                if pct > threshold {
                    PriceTrendDirection::Up
                } else if pct < -threshold {
                    PriceTrendDirection::Down
                } else {
                    PriceTrendDirection::Stable
                }
            } else {
                PriceTrendDirection::Stable
            };

            let prev_dir = self.items[item_idx].trend_direction;
            if tick_dir == prev_dir && tick_dir != PriceTrendDirection::Stable {
                self.items[item_idx].trend_streak += 1;
            } else if tick_dir != PriceTrendDirection::Stable && tick_dir != prev_dir {
                self.items[item_idx].trend_streak = 1;
            } else {
                self.items[item_idx].trend_streak = 0;
            }

            if tick_dir != PriceTrendDirection::Stable {
                self.items[item_idx].trend_direction = tick_dir;
            } else {
                self.items[item_idx].trend_direction = PriceTrendDirection::Stable;
            }

            let display_trend = self.calculate_price_trend(item_idx);
            self.items[item_idx].trend = display_trend;

            self.items[item_idx].tick_buy_volume = 0;
            self.items[item_idx].tick_sell_volume = 0;

            let item = &mut self.items[item_idx];
            if item.price_history.len() > 5000 {
                item.price_history.remove(0);
            }
            if item.buy_price_history.len() > 5000 {
                item.buy_price_history.remove(0);
            }
            if item.sell_price_history.len() > 5000 {
                item.sell_price_history.remove(0);
            }
            if item.bpd_history.len() > 5000 {
                item.bpd_history.remove(0);
            }
            if item.spd_history.len() > 5000 {
                item.spd_history.remove(0);
            }
            if item.buy_volume_history.len() > 5000 {
                item.buy_volume_history.remove(0);
            }
            if item.sell_volume_history.len() > 5000 {
                item.sell_volume_history.remove(0);
            }
        }
    }

    pub fn record_buy(&mut self, item_index: usize, amount: i32) {
        if let Some(item) = self.items.get_mut(item_index) {
            item.tick_buy_volume += amount;
        }
    }

    pub fn record_sell(&mut self, item_index: usize, amount: i32) {
        if let Some(item) = self.items.get_mut(item_index) {
            item.tick_sell_volume += amount;
        }
    }

    fn calculate_player_scaling(online_count: i32, config: &SimConfig) -> f64 {
        if online_count == 0 {
            return 0.0;
        }
        let full_effect = config.player_scaling.full_effect_players as f64;
        let coefficient = ATANH_099 / full_effect;
        (online_count as f64 * coefficient).tanh()
    }

    fn calculate_new_price(
        &self,
        item_idx: usize,
        metrics: &TradeMetrics,
        online_count: i32,
        config: &SimConfig,
    ) -> f64 {
        let current_price = self.items[item_idx].price;

        let total_weighted = metrics.weighted_buys + metrics.weighted_sells;
        if total_weighted < 0.001 {
            return current_price;
        }

        let trade_ratio = (metrics.weighted_buys - metrics.weighted_sells) / total_weighted;
        let player_scaling = Self::calculate_player_scaling(online_count, config);
        let scaled_ratio = trade_ratio * player_scaling;

        let max_change_percent = config
            .items
            .get(item_idx)
            .and_then(|ic| ic.max_price_change_override)
            .unwrap_or(config.economy.max_price_change_percent)
            / 100.0;

        let mut price_change_percent = scaled_ratio * max_change_percent;

        // Sell pressure applied before dampening
        if price_change_percent < 0.0 {
            price_change_percent *= config.economy.sell_pressure_multiplier;
        }

        // Directional dampening: only dampen when continuing the streak direction
        let streak = self.items[item_idx].trend_streak;
        if streak > 0 && config.economy.trend_dampening > 0.0 {
            let streak_dir = self.items[item_idx].trend_direction;
            let continuing = (price_change_percent > 0.0 && streak_dir == PriceTrendDirection::Up)
                || (price_change_percent < 0.0 && streak_dir == PriceTrendDirection::Down);
            if continuing {
                let raw_dampening = 1.0 / (1.0 + streak as f64 * config.economy.trend_dampening);
                let dampening = raw_dampening.max(config.economy.trend_dampening_floor);
                price_change_percent *= dampening;
            }
        }

        let price_change = current_price * price_change_percent;

        current_price + price_change
    }

    fn calculate_spread(
        &self,
        item_idx: usize,
        metrics: &TradeMetrics,
        online_count: i32,
        global_volume_multiplier: f64,
        config: &SimConfig,
    ) -> SpreadResult {
        let base_spread = config
            .items
            .get(item_idx)
            .and_then(|ic| ic.base_spread_override)
            .unwrap_or(config.spread.base_spread);
        let half_spread = base_spread / 2.0;

        let mut bpd = half_spread;
        let mut spd = half_spread;

        let total_weighted = metrics.weighted_buys + metrics.weighted_sells;
        if total_weighted > 0.001 {
            let buy_ratio = metrics.weighted_buys / total_weighted;
            let imbalance = (buy_ratio - 0.5) * 2.0;

            bpd += imbalance.max(0.0) * half_spread * config.spread.volume_impact;
            spd += (-imbalance).max(0.0) * half_spread * config.spread.volume_impact;
        }

        let full_effect_traders = config.spread.liquidity_full_effect_traders.max(1) as f64;
        let clamped_traders = (metrics.distinct_traders as f64).min(full_effect_traders);
        let effective_coeff =
            (config.spread.liquidity_coeff / full_effect_traders) * clamped_traders;
        let liquidity_reduction = 1.0 / (1.0 + total_weighted * effective_coeff);
        bpd *= liquidity_reduction;
        spd *= liquidity_reduction;

        let player_scaling = Self::calculate_player_scaling(online_count, config);
        let player_reduction = 1.0 - config.spread.player_impact * player_scaling;
        bpd *= player_reduction;
        spd *= player_reduction;

        bpd *= global_volume_multiplier;
        spd *= global_volume_multiplier;

        SpreadResult { bpd, spd }
    }

    fn calculate_global_volume_multiplier(
        &self,
        current_tick: u64,
        trade_window_ticks: u64,
        transactions: &VecDeque<Transaction>,
    ) -> f64 {
        let window_start = current_tick.saturating_sub(trade_window_ticks);
        let bucket_size = trade_window_ticks / VOLUME_BUCKETS as u64;

        if bucket_size == 0 {
            return 1.0;
        }

        let mut bucket_volumes = [0.0_f64; VOLUME_BUCKETS];

        for tx in transactions.iter() {
            if tx.tick < window_start {
                continue;
            }
            let bucket_idx =
                ((tx.tick - window_start) / bucket_size).min(VOLUME_BUCKETS as u64 - 1) as usize;
            bucket_volumes[bucket_idx] += tx.amount as f64;
        }

        let mean: f64 = bucket_volumes.iter().sum::<f64>() / VOLUME_BUCKETS as f64;

        let variance: f64 = bucket_volumes
            .iter()
            .map(|v| {
                let diff = v - mean;
                diff * diff
            })
            .sum::<f64>()
            / VOLUME_BUCKETS as f64;
        let stddev = variance.sqrt();

        if stddev < 0.001 {
            return 1.0;
        }

        let recent_volume = bucket_volumes[VOLUME_BUCKETS - 1];
        let z = (recent_volume - mean) / stddev;

        if (-1.0..=1.0).contains(&z) {
            return 1.0;
        }

        if z > 1.0 {
            let t = (z - 1.0).min(1.0);
            return 1.0 - 0.2 * t;
        }

        let t = (-z - 1.0).min(1.0);
        1.0 + 0.3 * t
    }

    fn calculate_price_trend(&self, item_idx: usize) -> PriceTrend {
        let history = &self.items[item_idx].price_history;
        if history.len() < 2 {
            return PriceTrend {
                direction: PriceTrendDirection::Stable,
                percent_change: 0.0,
            };
        }

        let count = history.len().min(10);
        let newest = history[history.len() - 1];
        let oldest = history[history.len() - count];

        if oldest == 0.0 {
            return PriceTrend {
                direction: PriceTrendDirection::Stable,
                percent_change: 0.0,
            };
        }

        let percent_change = ((newest - oldest) / oldest) * 100.0;
        let percent_change = round2(percent_change);

        let direction = if percent_change > 0.5 {
            PriceTrendDirection::Up
        } else if percent_change < -0.5 {
            PriceTrendDirection::Down
        } else {
            PriceTrendDirection::Stable
        };

        PriceTrend {
            direction,
            percent_change,
        }
    }

    pub fn reset(&mut self, config: &SimConfig) {
        *self = Self::new(config);
    }
}

fn calculate_adaptive_window(
    base_window_ticks: u64,
    min_ticks: u64,
    max_ticks: u64,
    current_tick: u64,
    transactions: &VecDeque<Transaction>,
) -> u64 {
    let window_start = current_tick.saturating_sub(base_window_ticks);
    let tx_count = transactions
        .iter()
        .filter(|tx| tx.tick >= window_start)
        .count() as f64;
    let base_days = base_window_ticks as f64 / TICKS_PER_DAY as f64;

    if base_days < 0.001 {
        return base_window_ticks;
    }

    let actual_density = tx_count / base_days;

    if actual_density < 0.001 {
        return max_ticks;
    }

    let scale = (TARGET_TX_DENSITY_PER_DAY / actual_density).clamp(
        min_ticks as f64 / base_window_ticks as f64,
        max_ticks as f64 / base_window_ticks as f64,
    );
    let effective = (base_window_ticks as f64 * scale) as u64;
    effective.clamp(min_ticks, max_ticks)
}

fn calculate_trade_metrics(
    item_index: usize,
    current_tick: u64,
    trade_window_ticks: u64,
    transactions: &VecDeque<Transaction>,
    rate_limit_multiplier: f64,
) -> TradeMetrics {
    let window_start = current_tick.saturating_sub(trade_window_ticks);

    let mut per_player: HashMap<usize, (f64, f64)> = HashMap::new();

    for tx in transactions.iter() {
        if tx.item_index != item_index || tx.tick < window_start {
            continue;
        }

        let age = current_tick.saturating_sub(tx.tick) as f64;
        let weight = (1.0 - age / trade_window_ticks as f64).max(0.0);
        let weighted_amount = tx.amount as f64 * weight;

        let entry = per_player.entry(tx.player_id).or_insert((0.0, 0.0));
        match tx.tx_type {
            TransactionType::Buy => entry.0 += weighted_amount,
            TransactionType::Sell => entry.1 += weighted_amount,
        }
    }

    let distinct_traders = per_player.len();

    if distinct_traders == 0 {
        return TradeMetrics {
            weighted_buys: 0.0,
            weighted_sells: 0.0,
            distinct_traders: 0,
        };
    }

    let total_all: f64 = per_player.values().map(|(b, s)| b + s).sum();
    let mean_per_player = total_all / distinct_traders as f64;
    let cap = mean_per_player * rate_limit_multiplier;

    let mut weighted_buys = 0.0;
    let mut weighted_sells = 0.0;

    for &(buys, sells) in per_player.values() {
        let player_total = buys + sells;
        if player_total <= cap {
            weighted_buys += buys;
            weighted_sells += sells;
        } else {
            let scale = cap / player_total;
            weighted_buys += buys * scale;
            weighted_sells += sells * scale;
        }
    }

    TradeMetrics {
        weighted_buys,
        weighted_sells,
        distinct_traders,
    }
}
