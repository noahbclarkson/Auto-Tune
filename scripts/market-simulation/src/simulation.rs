use std::collections::VecDeque;

use crate::config::SimConfig;
use crate::engine::{MarketEngine, Transaction, TransactionType};
use crate::events::MarketEvent;
use crate::loan::{Loan, LoanStatus, calculate_interest_rate};
use crate::player::{Archetype, DecisionLog, PlayerAgent, rng_next, set_global_seeded_rng};
use crate::recorder::{DataRecorder, LoanEventData, TickSnapshot};

const MAX_TRANSACTIONS: usize = 50_000;

/// Records when a loan request is capped by the per-loan GDP limit.
#[derive(Clone, Debug)]
pub struct LoanCapRecord {
    pub tick: u64,
    pub player_id: usize,
    /// Loan amount the player requested (before cap).
    pub raw_amount: f64,
    /// Actual amount issued (after cap).
    pub capped_amount: f64,
    /// Economy GDP at time of loan request.
    pub gdp: f64,
    /// The cap ratio used (single_loan_gdp_cap config).
    pub cap_ratio: f64,
    /// What the cap was (gdp * cap_ratio).
    #[allow(dead_code)]
    pub cap_value: f64,
}

#[derive(Clone, Debug)]
pub struct EconomySnapshot {
    pub tick: u64,
    pub gdp: f64,
    pub total_debt: f64,
    pub avg_price_change: f64,
    pub online_players: i32,
    pub total_players: usize,
}

pub struct Simulation {
    pub engine: MarketEngine,
    pub config: SimConfig,
    pub players: Vec<PlayerAgent>,
    pub loans: Vec<Loan>,
    pub transactions: VecDeque<Transaction>,
    pub economy_snapshots: Vec<EconomySnapshot>,
    pub current_tick: u64,
    pub paused: bool,
    pub speed: f64,
    pub tick_accumulator: f64,
    pub config_dirty: bool,
    pub recorder: Option<DataRecorder>,
    /// Active market events that apply price velocity modifiers.
    pub events: Vec<MarketEvent>,
    /// Tracks whether the loan interest circuit breaker is currently open.
    /// When true, interest accrual is paused until debt/GDP drops below threshold.
    interest_circuit_open: bool,
    next_player_id: usize,
    /// Log of all loans that were capped by the per-loan GDP cap.
    pub loan_cap_log: Vec<LoanCapRecord>,
    /// Total count of opening loans taken by MarketMakers.
    pub mm_opening_loan_count: u32,
    /// Total amount of opening loans taken by MarketMakers.
    pub mm_opening_loan_total: f64,
}

impl Simulation {
    pub fn new(config: SimConfig) -> Self {
        let engine = MarketEngine::new(&config);
        Self {
            engine,
            config,
            players: Vec::new(),
            loans: Vec::new(),
            transactions: VecDeque::new(),
            economy_snapshots: Vec::new(),
            current_tick: 0,
            paused: true,
            speed: 1.0,
            tick_accumulator: 0.0,
            config_dirty: false,
            recorder: None,
            events: Vec::new(),
            interest_circuit_open: false,
            next_player_id: 0,
            loan_cap_log: Vec::new(),
            mm_opening_loan_count: 0,
            mm_opening_loan_total: 0.0,
        }
    }

    /// Create a simulation with a seeded RNG for deterministic regression testing.
    /// The seed is set thread-locally and remains active for the entire simulation run
    /// to ensure fully deterministic behavior (including during simulation ticks).
    pub fn new_seeded(config: SimConfig, seed: u64) -> Self {
        set_global_seeded_rng(seed);
        Self::new(config)
    }

    pub fn add_player(&mut self, archetype: Archetype) {
        let item_count = self.config.items.len();
        let base_prices: Vec<f64> = self.config.items.iter().map(|i| i.base_price).collect();
        self.next_player_id += 1;
        let id = self.next_player_id;

        let player = match archetype {
            Archetype::Casual => PlayerAgent::new_casual(id, item_count, &base_prices),
            Archetype::Farmer => PlayerAgent::new_farmer(id, item_count, &base_prices),
            Archetype::Trader => PlayerAgent::new_trader(id, item_count, &base_prices),
            Archetype::Hoarder => PlayerAgent::new_hoarder(id, item_count, &base_prices),
            Archetype::Exploiter => PlayerAgent::new_exploiter(id, item_count, &base_prices),
            Archetype::Newbie => PlayerAgent::new_newbie(id, item_count, &base_prices),
            Archetype::AFKFarmer => PlayerAgent::new_afk_farmer(id, item_count, &base_prices),
            Archetype::GuildBuyer => PlayerAgent::new_guild_buyer(id, item_count, &base_prices),
            Archetype::MarketMaker => {
                let min_cap = self.config.mm_initial_capital_min.unwrap_or(50_000.0);
                let max_cap = self.config.mm_initial_capital_max.unwrap_or(200_000.0);
                PlayerAgent::new_market_maker(id, item_count, &base_prices, min_cap, max_cap)
            }
            Archetype::InsiderTrader => {
                PlayerAgent::new_insider_trader(id, item_count, &base_prices)
            }
            Archetype::GuildSeller => PlayerAgent::new_guild_seller(id, item_count, &base_prices),
            Archetype::VolumeTrader => {
                // spread_threshold=0.25, spread_window=20, price_window=30
                PlayerAgent::new_volume_trader(id, item_count, &base_prices, 0.25, 20, 30)
            }
        };
        self.players.push(player);
    }

    pub fn add_random_players(&mut self, count: usize) {
        let item_count = self.config.items.len();
        let base_prices: Vec<f64> = self.config.items.iter().map(|i| i.base_price).collect();
        for _ in 0..count {
            self.next_player_id += 1;
            let player = PlayerAgent::new_random(self.next_player_id, item_count, &base_prices);
            self.players.push(player);
        }
    }

    pub fn clear_players(&mut self) {
        self.players.clear();
        self.loans.clear();
    }

    pub fn tick(&mut self) {
        self.current_tick += 1;

        // ── Player Exodus ───────────────────────────────────────────────────────
        // Simulates mass player departure at a specific tick (e.g. half the server quits).
        // Players with highest outstanding debt quit first (most realistic).
        // Also triggers a spread shock (liquidity panic) for the configured duration.
        // If exodus_target_archetype is set, only players of that archetype quit.
        if self.current_tick == self.config.player_exodus_tick.unwrap_or(u64::MAX) {
            let quit_indices: Vec<usize> = if let Some(ref target_arch) =
                self.config.exodus_target_archetype
            {
                // Target specific archetype: find all players of this type, quit all
                self.players
                    .iter()
                    .enumerate()
                    .filter(|(_, p)| {
                        format!("{:?}", p.archetype).to_lowercase() == target_arch.to_lowercase()
                    })
                    .map(|(i, _)| i)
                    .collect()
            } else {
                // Default: highest-debt-first exodus
                let num_to_remove =
                    (self.players.len() as f64 * self.config.player_exodus_fraction) as usize;
                if num_to_remove == 0 {
                    Vec::new()
                } else {
                    let mut player_debts: Vec<(usize, f64)> = (0..self.players.len())
                        .map(|i| {
                            let debt = self
                                .loans
                                .iter()
                                .filter(|l| {
                                    l.player_index == i && l.status != LoanStatus::Defaulted
                                })
                                .map(|l| l.current_balance)
                                .sum::<f64>();
                            (i, debt)
                        })
                        .collect();
                    player_debts
                        .sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
                    player_debts
                        .iter()
                        .take(num_to_remove)
                        .map(|&(i, _)| i)
                        .collect()
                }
            };
            if !quit_indices.is_empty() {
                let archetype_label = self
                    .config
                    .exodus_target_archetype
                    .clone()
                    .unwrap_or_else(|| "highest-debt".to_string());
                for &idx in &quit_indices {
                    self.players[idx].online = false;
                }
                // Fire the spread shock to model liquidity panic
                self.engine.spread_shock = self.config.exodus_spread_multiplier;
                self.engine.shock_remaining_ticks = self.config.exodus_shock_duration_ticks;
                println!(
                    "  [EXODUS] tick {} — {} {} players quit: {:?} | spread shock: {:.1}x for {} ticks",
                    self.current_tick,
                    quit_indices.len(),
                    archetype_label,
                    quit_indices,
                    self.config.exodus_spread_multiplier,
                    self.config.exodus_shock_duration_ticks
                );
            }
        }

        let recording = self.recorder.is_some();
        let slippage_coeff = self.config.economy.slippage_coeff;
        let items_snapshot: Vec<_> = self.engine.items.clone();
        let mut tick_transactions = Vec::new();
        let mut all_decision_logs: Vec<DecisionLog> = Vec::new();

        let mut online_count = 0;

        for player in &mut self.players {
            let result = player.decide(
                &items_snapshot,
                recording,
                slippage_coeff,
                self.current_tick,
            );
            if player.online {
                online_count += 1;
            }

            let player_id = player.id;

            for decision in &result.decisions {
                let item = &items_snapshot[decision.item_index];
                let slippage = 1.0 + slippage_coeff * (decision.amount as f64).sqrt();
                let price_per_unit = if decision.is_buy {
                    item.buy_price() * slippage
                } else {
                    item.sell_price() / slippage
                };
                let total_price = price_per_unit * decision.amount as f64;

                let tx = Transaction {
                    item_index: decision.item_index,
                    tx_type: if decision.is_buy {
                        TransactionType::Buy
                    } else {
                        TransactionType::Sell
                    },
                    amount: decision.amount,
                    total_price,
                    tick: self.current_tick,
                    player_id,
                };

                if decision.is_buy {
                    self.engine.record_buy(decision.item_index, decision.amount);
                } else {
                    self.engine
                        .record_sell(decision.item_index, decision.amount);
                }

                tick_transactions.push(tx);
            }

            if recording {
                all_decision_logs.extend(result.logs);
            }
        }

        for tx in tick_transactions {
            self.transactions.push_back(tx);
        }

        while self.transactions.len() > MAX_TRANSACTIONS {
            self.transactions.pop_front();
        }

        // Filter to only events that are active at this tick
        let active_events: Vec<_> = self
            .events
            .iter()
            .filter(|e| e.is_active(self.current_tick))
            .cloned()
            .collect();

        self.engine.tick(
            online_count,
            &self.config,
            self.current_tick,
            &self.transactions,
            &active_events,
        );

        let loan_events = self.process_loans();
        let player_loan_events = self.process_player_loans(online_count);

        let capture_economy = self.current_tick.is_multiple_of(12);
        if capture_economy {
            self.capture_economy_snapshot(online_count);
        }

        if let Some(recorder) = &mut self.recorder {
            let snapshot = TickSnapshot {
                tick: self.current_tick,
                online_count,
                total_players: self.players.len(),
                global_volume_multiplier: self.engine.global_volume_multiplier,
                items: &self.engine.items,
                players: &self.players,
                decisions: &all_decision_logs,
            };
            if let Err(e) = recorder.record_tick(snapshot) {
                eprintln!("Recorder error: {e}");
            }

            let mut all_loan_events = loan_events;
            all_loan_events.extend(player_loan_events);
            if !all_loan_events.is_empty() {
                recorder.record_loan_events(&all_loan_events);
            }

            if capture_economy && let Some(econ) = self.economy_snapshots.last() {
                recorder.record_economy_snapshot(econ);
            }
        }
    }

    fn process_loans(&mut self) -> Vec<LoanEventData> {
        let compound_interval = self.config.compound_interval_ticks();
        let recording = self.recorder.is_some();
        let mut events = Vec::new();

        // Interest multiplier: compute based on debt/GDP ratio.
        // Uses counter-cyclical continuous taper when counter_cyclical=true (default, matching Java).
        // Falls back to legacy tiered circuit breaker when counter_cyclical=false.
        let lc = &self.config.loans;
        let (interest_multiplier, tier_name) = if lc.debt_gdp_tier3_ratio > 0.0 {
            let total_debt: f64 = self
                .loans
                .iter()
                .filter(|l| matches!(l.status, LoanStatus::Active | LoanStatus::Defaulted))
                .map(|l| l.current_balance)
                .sum();

            // Use 288-tick windowed GDP (matches Java: last 24h at 5min/tick)
            let gdp_window = 288u64;
            let window_start = self.current_tick.saturating_sub(gdp_window);
            let gdp: f64 = self
                .transactions
                .iter()
                .filter(|tx| tx.tick >= window_start && tx.tx_type == TransactionType::Buy)
                .map(|tx| tx.total_price)
                .sum();

            // Guard: skip circuit breaker if no transactions yet (initialization phase).
            // At tick 0, gdp=0 → ratio=f64::MAX → TIER3 would fire spuriously.
            // Once transactions exist, ratio is meaningful and circuit breaker applies.
            let ratio = if gdp > 0.0 { total_debt / gdp } else { -1.0 };

            if lc.counter_cyclical {
                // Counter-cyclical continuous taper (Java default, matching Java LoanManager):
                // multiplier = max(0, min(1, 1 - ratio / tier3_ratio))
                // D/G=0 → 100%, D/G=3 → 70%, D/G=5 → 50%, D/G=10 → 0%
                // Tier name is still tracked for logging purposes.
                let max_ratio = lc.debt_gdp_tier3_ratio;
                let multiplier = if ratio >= 0.0 {
                    (1.0 - ratio / max_ratio).clamp(0.0, 1.0)
                } else {
                    1.0 // No GDP yet — full interest
                };
                let tier = if ratio >= lc.debt_gdp_tier3_ratio {
                    "TIER3"
                } else if ratio >= lc.debt_gdp_tier2_ratio {
                    "TIER2"
                } else if ratio >= lc.debt_gdp_tier1_ratio {
                    "TIER1"
                } else {
                    "NORMAL"
                };
                (multiplier, tier)
            } else {
                // Legacy tiered circuit breaker:
                // Tier 1 (>=tier1_ratio): cap at tier1_cap (50%) — warning zone
                // Tier 2 (>=tier2_ratio): cap at tier2_cap (25%) — danger zone
                // Tier 3 (>=tier3_ratio): full pause (0%) — emergency zone
                if ratio >= lc.debt_gdp_tier3_ratio {
                    (0.0, "TIER3")
                } else if ratio >= lc.debt_gdp_tier2_ratio {
                    (lc.tier2_interest_cap, "TIER2")
                } else if ratio >= lc.debt_gdp_tier1_ratio {
                    (lc.tier1_interest_cap, "TIER1")
                } else {
                    (1.0, "NORMAL")
                }
            }
        } else {
            (1.0, "NORMAL")
        };

        // Log tier transitions
        let prev_tier = self.interest_circuit_open; // repurposed: true = TIER3, false = normal
        let currently_in_tier3 = tier_name == "TIER3";
        if currently_in_tier3 && !prev_tier && recording {
            let total_debt: f64 = self
                .loans
                .iter()
                .filter(|l| matches!(l.status, LoanStatus::Active | LoanStatus::Defaulted))
                .map(|l| l.current_balance)
                .sum();
            let gdp_window = 288u64;
            let window_start = self.current_tick.saturating_sub(gdp_window);
            let gdp: f64 = self
                .transactions
                .iter()
                .filter(|tx| tx.tick >= window_start && tx.tx_type == TransactionType::Buy)
                .map(|tx| tx.total_price)
                .sum();
            let ratio = if gdp > 0.0 {
                total_debt / gdp
            } else {
                f64::MAX
            };
            eprintln!(
                "[SIMULATION] Loan circuit breaker TIER3 OPEN at tick {} — debt/GDP {:.1}x > {:.1}x. Interest paused.",
                self.current_tick, ratio, lc.debt_gdp_tier3_ratio
            );
        } else if !currently_in_tier3 && prev_tier && recording {
            eprintln!(
                "[SIMULATION] Loan circuit breaker CLOSED at tick {}.",
                self.current_tick
            );
        }
        self.interest_circuit_open = currently_in_tier3;

        // Always process defaults even when interest is paused/tiered
        for loan in &mut self.loans {
            if loan.status != LoanStatus::Active {
                continue;
            }
            if loan.is_overdue(self.current_tick) {
                loan.mark_defaulted();
                if recording {
                    events.push(LoanEventData {
                        tick: self.current_tick,
                        player_id: loan.player_index,
                        event_type: "Defaulted",
                        principal: loan.principal,
                        balance: loan.current_balance,
                        rate: loan.interest_rate,
                        amount: 0.0,
                    });
                }
                if let Some(player) = self.players.get_mut(loan.player_index) {
                    player.credit_score =
                        (player.credit_score - self.config.loans.default_penalty).max(0);
                    player.last_defaulted_at = Some(self.current_tick);
                }
            }
        }

        // Apply interest with tiered multiplier (0.0 = full pause, 0.25 = 25%, etc.)
        if interest_multiplier > 0.0 {
            for loan in &mut self.loans {
                if loan.status != LoanStatus::Active {
                    continue;
                }

                if self.current_tick - loan.last_interest_tick >= compound_interval {
                    let full_interest = loan.current_balance * loan.interest_rate;
                    let actual_interest = full_interest * interest_multiplier;
                    loan.current_balance += actual_interest;
                    loan.last_interest_tick = self.current_tick;
                    if recording {
                        events.push(LoanEventData {
                            tick: self.current_tick,
                            player_id: loan.player_index,
                            event_type: "InterestApplied",
                            principal: loan.principal,
                            balance: loan.current_balance,
                            rate: loan.interest_rate,
                            amount: actual_interest,
                        });
                    }
                }
            }
        }

        events
    }

    fn process_player_loans(&mut self, _online_count: i32) -> Vec<LoanEventData> {
        let mut events = Vec::new();

        if !self.config.loans.enabled {
            return events;
        }

        let recording = self.recorder.is_some();

        for player_idx in 0..self.players.len() {
            let player = &self.players[player_idx];
            if !player.online {
                continue;
            }

            let has_active_loan = self
                .loans
                .iter()
                .any(|l| l.player_index == player_idx && l.status == LoanStatus::Active);

            // Post-default cooldown: player cannot take new loans within cooldown period
            let in_default_cooldown = if self.config.loans.post_default_cooldown_hours > 0 {
                if let Some(last_default) = self.players[player_idx].last_defaulted_at {
                    let cooldown_ticks = self.config.loans.post_default_cooldown_hours as u64 * 12;
                    self.current_tick.saturating_sub(last_default) < cooldown_ticks
                } else {
                    false
                }
            } else {
                false
            };

            // MM opening loan eligibility: either mm_opening_loan_allowed is true,
            // or the player is NOT a MarketMaker. This prevents MM from taking
            // catastrophic opening loans that cascade when MM defaults.
            let is_market_maker = matches!(player.archetype, Archetype::MarketMaker);
            let mm_can_borrow = self.config.loans.mm_opening_loan_allowed || !is_market_maker;

            if !has_active_loan
                && !in_default_cooldown
                && player.balance < 50.0
                && player.credit_score >= self.config.loans.min_credit_score
                && mm_can_borrow
                && rng_next() < 0.1
            {
                let max_loan =
                    (player.total_traded * self.config.loans.max_loan_multiplier).max(100.0);
                let amount_raw = max_loan * 0.5;

                // Per-loan GDP cap: no single loan can exceed economy GDP × single_loan_gdp_cap
                // (matches Java LoanManager.processLoanRequest: singleLoanGdpCap check)
                let amount = if self.config.loans.single_loan_gdp_cap > 0.0 {
                    let gdp: f64 = self
                        .transactions
                        .iter()
                        .filter(|tx| tx.tx_type == TransactionType::Buy)
                        .map(|tx| tx.total_price)
                        .sum();
                    if gdp > 0.0 {
                        let cap_value = gdp * self.config.loans.single_loan_gdp_cap;
                        let capped = amount_raw.min(cap_value);
                        // Record if the cap actually reduced the loan amount
                        if capped < amount_raw - 0.01 {
                            self.loan_cap_log.push(LoanCapRecord {
                                tick: self.current_tick,
                                player_id: player_idx,
                                raw_amount: amount_raw,
                                capped_amount: capped,
                                gdp,
                                cap_ratio: self.config.loans.single_loan_gdp_cap,
                                cap_value,
                            });
                        }
                        capped
                    } else {
                        amount_raw
                    }
                } else {
                    amount_raw
                };

                // Only create loan if amount is meaningful (> 1.0)
                let taken_amount = amount;
                let taken_is_mm = is_market_maker && !has_active_loan;
                if taken_amount < 1.0 {
                    continue;
                }

                let rate = calculate_interest_rate(player.credit_score, &self.config);
                let loan = Loan::new(player_idx, amount, rate, self.current_tick, &self.config);
                self.players[player_idx].balance += amount;
                if recording {
                    events.push(LoanEventData {
                        tick: self.current_tick,
                        player_id: player_idx,
                        event_type: "Taken",
                        principal: amount,
                        balance: amount,
                        rate,
                        amount,
                    });
                }
                self.loans.push(loan);
                // Track MM opening loans after loan is confirmed
                if taken_is_mm {
                    self.mm_opening_loan_count += 1;
                    self.mm_opening_loan_total += taken_amount;
                }
            }

            if has_active_loan {
                let player = &self.players[player_idx];
                #[allow(clippy::collapsible_if)]
                if let Some(loan) = self
                    .loans
                    .iter_mut()
                    .find(|l| l.player_index == player_idx && l.status == LoanStatus::Active)
                {
                    if player.balance > loan.current_balance * 1.5 && rng_next() < 0.3 {
                        let payment = loan.current_balance;
                        loan.make_payment(payment);
                        self.players[player_idx].balance -= payment;
                        let event_type = if loan.status == LoanStatus::Paid {
                            let bonus = (loan.principal / 100.0).min(50.0) as i32;
                            self.players[player_idx].credit_score =
                                (self.players[player_idx].credit_score + bonus).min(1000);
                            "Paid"
                        } else {
                            "Payment"
                        };
                        if recording {
                            events.push(LoanEventData {
                                tick: self.current_tick,
                                player_id: player_idx,
                                event_type,
                                principal: loan.principal,
                                balance: loan.current_balance,
                                rate: loan.interest_rate,
                                amount: payment,
                            });
                        }
                    }
                }
            }
        }

        events
    }

    fn capture_economy_snapshot(&mut self, online_count: i32) {
        let gdp_window = 288u64;
        let window_start = self.current_tick.saturating_sub(gdp_window);

        let gdp: f64 = self
            .transactions
            .iter()
            .filter(|tx| tx.tick >= window_start && tx.tx_type == TransactionType::Buy)
            .map(|tx| tx.total_price)
            .sum();

        let total_debt: f64 = self
            .loans
            .iter()
            .filter(|l| matches!(l.status, LoanStatus::Active | LoanStatus::Defaulted))
            .map(|l| l.current_balance)
            .sum();

        let avg_price_change = if self.engine.items.is_empty() {
            0.0
        } else {
            let sum: f64 = self
                .engine
                .items
                .iter()
                .map(|item| {
                    if item.base_price == 0.0 {
                        0.0
                    } else {
                        ((item.price - item.base_price) / item.base_price) * 100.0
                    }
                })
                .sum();
            sum / self.engine.items.len() as f64
        };

        let snapshot = EconomySnapshot {
            tick: self.current_tick,
            gdp,
            total_debt,
            avg_price_change,
            online_players: online_count,
            total_players: self.players.len(),
        };

        self.economy_snapshots.push(snapshot);
        if self.economy_snapshots.len() > 5000 {
            self.economy_snapshots.remove(0);
        }
    }

    pub fn reset(&mut self) {
        if let Some(recorder) = &mut self.recorder {
            let _ = recorder.finalize();
        }
        self.recorder = None;
        self.engine.reset(&self.config);
        self.players.clear();
        self.loans.clear();
        self.transactions.clear();
        self.economy_snapshots.clear();
        self.current_tick = 0;
        self.paused = true;
        self.tick_accumulator = 0.0;
        self.next_player_id = 0;
    }

    pub fn apply_config(&mut self, config: SimConfig) {
        if let Some(recorder) = &mut self.recorder
            && let Ok(json) = serde_json::to_string(&config)
        {
            recorder.record_config_change(self.current_tick, &json);
        }
        self.config = config;
        self.config_dirty = false;
    }

    pub fn stress_market_crash(&mut self) {
        for player in &mut self.players {
            player.buy_threshold = 999.0;
            player.sell_threshold = 0.0;
        }
        let item_count = self.config.items.len();
        let base_prices: Vec<f64> = self.config.items.iter().map(|i| i.base_price).collect();
        for _ in 0..20 {
            self.next_player_id += 1;
            let mut farmer = PlayerAgent::new_farmer(self.next_player_id, item_count, &base_prices);
            for i in 0..item_count {
                farmer.inventory.insert(i, 100);
            }
            self.players.push(farmer);
        }
    }

    pub fn stress_exploit(&mut self) {
        let item_count = self.config.items.len();
        let base_prices: Vec<f64> = self.config.items.iter().map(|i| i.base_price).collect();
        for _ in 0..3 {
            self.next_player_id += 1;
            let mut exploiter =
                PlayerAgent::new_exploiter(self.next_player_id, item_count, &base_prices);
            exploiter.balance = 100_000.0;
            if item_count > 0 {
                for i in 0..item_count {
                    exploiter.preferences.insert(i, 0.0);
                }
                exploiter.preferences.insert(0, 1.0);
            }
            self.players.push(exploiter);
        }
    }

    pub fn stress_low_players(&mut self) {
        while self.players.len() > 2 {
            self.players.pop();
        }
    }

    pub fn stress_hyperinflation(&mut self) {
        self.config.economy.max_price_change_percent = 10.0;
        let item_count = self.config.items.len();
        let base_prices: Vec<f64> = self.config.items.iter().map(|i| i.base_price).collect();
        for _ in 0..20 {
            self.next_player_id += 1;
            let mut buyer = PlayerAgent::new_hoarder(self.next_player_id, item_count, &base_prices);
            buyer.balance = 500_000.0;
            buyer.buy_threshold = 0.0;
            buyer.activity_rate = 0.9;
            self.players.push(buyer);
        }
    }

    pub fn stress_loan_cascade(&mut self) {
        self.config.loans.compound_interval_hours = 1;

        let mut new_loans = Vec::new();
        for player_idx in 0..self.players.len() {
            let has_loan = self
                .loans
                .iter()
                .any(|l| l.player_index == player_idx && l.status == LoanStatus::Active);
            if !has_loan {
                let player = &self.players[player_idx];
                let max_loan =
                    (player.total_traded * self.config.loans.max_loan_multiplier).max(100.0);
                let rate = calculate_interest_rate(player.credit_score, &self.config);
                let loan = Loan::new(player_idx, max_loan, rate, self.current_tick, &self.config);
                new_loans.push((player_idx, max_loan, loan));
            }
        }
        for (player_idx, amount, loan) in new_loans {
            self.players[player_idx].balance += amount;
            self.loans.push(loan);
        }
    }

    pub fn sim_time_string(&self) -> String {
        let total_hours = self.current_tick as f64 / 12.0;
        let days = (total_hours / 24.0).floor() as u64;
        let hours = (total_hours % 24.0).floor() as u64;
        format!("{days}d {hours}h")
    }

    /// Print a summary of all loan cap events recorded during the simulation.
    pub fn print_loan_cap_summary(&self) {
        if self.loan_cap_log.is_empty() {
            println!("  No loans were capped by per-loan GDP limit.");
            return;
        }
        println!(
            "\n  {:>6} {:>8} {:>12} {:>12} {:>12} {:>10}  Note",
            "Tick", "Player", "Raw($)", "Capped($)", "GDP($)", "CapRatio"
        );
        println!("  {}", "-".repeat(80));

        let mut total_raw = 0.0;
        let mut total_capped = 0.0;
        for record in &self.loan_cap_log {
            let cap_pct = (1.0 - record.capped_amount / record.raw_amount) * 100.0;
            let note = if cap_pct > 50.0 {
                "HEAVILY CAPPED"
            } else if cap_pct > 20.0 {
                "moderately capped"
            } else {
                ""
            };
            println!(
                "  {:>6} {:>8} {:>12.2} {:>12.2} {:>12.2} {:>10.2}  {}",
                record.tick,
                record.player_id,
                record.raw_amount,
                record.capped_amount,
                record.gdp,
                record.cap_ratio,
                note
            );
            total_raw += record.raw_amount;
            total_capped += record.capped_amount;
        }
        println!("  {}", "-".repeat(80));
        let total_cap_pct = (1.0 - total_capped / total_raw) * 100.0;
        println!(
            "  Total: {} capped loans | raw sum=${:.2} | capped sum=${:.2} | {:.1}% reduction",
            self.loan_cap_log.len(),
            total_raw,
            total_capped,
            total_cap_pct
        );

        // Show distribution by day
        let mut by_day: std::collections::HashMap<u64, usize> = std::collections::HashMap::new();
        for r in &self.loan_cap_log {
            let day = r.tick / 288;
            *by_day.entry(day).or_insert(0) += 1;
        }
        print!("  Caps by day: ");
        let mut days: Vec<_> = by_day.keys().collect();
        days.sort();
        for day in days {
            println!("day{}={}", day, by_day[day]);
        }
    }
}
