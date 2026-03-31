use std::collections::VecDeque;

use crate::config::SimConfig;
use crate::engine::{MarketEngine, Transaction, TransactionType};
use crate::events::MarketEvent;
use crate::loan::{Loan, LoanStatus, calculate_interest_rate};
use crate::player::{Archetype, DecisionLog, PlayerAgent, rng_next, set_global_seeded_rng};
use crate::recorder::{DataRecorder, LoanEventData, TickSnapshot};

const MAX_TRANSACTIONS: usize = 50_000;

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
        }
    }

    /// Create a simulation with a seeded RNG for deterministic regression testing.
    /// The seed is set thread-locally during construction so player setup is reproducible.
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
            Archetype::MarketMaker => PlayerAgent::new_market_maker(id, item_count, &base_prices),
            Archetype::InsiderTrader => {
                PlayerAgent::new_insider_trader(id, item_count, &base_prices)
            }
            Archetype::GuildSeller => PlayerAgent::new_guild_seller(id, item_count, &base_prices),
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

        let recording = self.recorder.is_some();
        let slippage_coeff = self.config.economy.slippage_coeff;
        let items_snapshot: Vec<_> = self.engine.items.clone();
        let mut tick_transactions = Vec::new();
        let mut all_decision_logs: Vec<DecisionLog> = Vec::new();

        let mut online_count = 0;

        for player in &mut self.players {
            let result = player.decide(&items_snapshot, recording, slippage_coeff);
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

        // Tiered circuit breaker: compute interest_multiplier based on debt/GDP ratio.
        // Tier 1 (>tier1_ratio): cap at tier1_cap (50%) — warning zone
        // Tier 2 (>tier2_ratio): cap at tier2_cap (25%) — danger zone
        // Tier 3 (>tier3_ratio): full pause (0%) — emergency zone
        let lc = &self.config.loans;
        let (interest_multiplier, tier_name) = if lc.debt_gdp_tier3_ratio > 0.0 {
            let total_debt: f64 = self
                .loans
                .iter()
                .filter(|l| l.status == LoanStatus::Active)
                .map(|l| l.current_balance)
                .sum();
            let gdp: f64 = self
                .transactions
                .iter()
                .filter(|tx| tx.tx_type == TransactionType::Buy)
                .map(|tx| tx.total_price)
                .sum();

            // Guard: skip circuit breaker if no transactions yet (initialization phase).
            // At tick 0, gdp=0 → ratio=f64::MAX → TIER3 would fire spuriously.
            // Once transactions exist, ratio is meaningful and circuit breaker applies.
            let ratio = if gdp > 0.0 {
                total_debt / gdp
            } else {
                // No GDP yet — circuit breaker inactive until economy is running.
                -1.0
            };

            if ratio > lc.debt_gdp_tier3_ratio {
                (0.0, "TIER3")
            } else if ratio > lc.debt_gdp_tier2_ratio {
                (lc.tier2_interest_cap, "TIER2")
            } else if ratio > lc.debt_gdp_tier1_ratio {
                (lc.tier1_interest_cap, "TIER1")
            } else {
                (1.0, "NORMAL")
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
                .filter(|l| l.status == LoanStatus::Active)
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

            if !has_active_loan
                && player.balance < 50.0
                && player.credit_score >= self.config.loans.min_credit_score
                && rng_next() < 0.1
            {
                let max_loan =
                    (player.total_traded * self.config.loans.max_loan_multiplier).max(100.0);
                let amount = max_loan * 0.5;
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
            .filter(|l| l.status == LoanStatus::Active)
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
}
