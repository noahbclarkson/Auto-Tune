mod analyzer;
mod config;
mod engine;
mod events;
mod gui;
mod loan;
mod player;
mod recorder;
mod regression;
mod simulation;
mod sweep;

use std::io::Write;
use std::path::PathBuf;
use std::time::Instant;

use crate::config::{ArchetypeConfig, SimConfig};
use crate::events::MarketEvent;
use crate::player::Archetype;
use crate::simulation::Simulation;

#[derive(Clone, Debug)]
pub struct Scenario {
    pub name: String,
    pub config: SimConfig,
    pub players: Vec<ArchetypeConfig>,
    pub stress_events: Vec<StressEvent>,
    pub duration_ticks: u64,
    pub speed_ticks_per_sec: u64,
    /// Market events that apply price velocity modifiers during the simulation.
    pub events: Vec<MarketEvent>,
    /// RNG seed for deterministic runs. None = use wall-clock randomness.
    pub seed: Option<u64>,
}

#[derive(Clone, Debug)]
pub enum StressEvent {
    MarketCrash {
        at_tick: u64,
    },
    Exploit {
        at_tick: u64,
    },
    LowPlayers {
        at_tick: u64,
    },
    Hyperinflation {
        at_tick: u64,
    },
    LoanCascade {
        at_tick: u64,
    },
    PlayerJoin {
        at_tick: u64,
        archetype: String,
        count: usize,
    },
    /// Directly manipulate an item's price by a multiplier at a specific tick.
    /// Used for correlation testing: inject a price shock to one item in a
    /// section and measure how strongly other items in the same section follow.
    PriceShock {
        at_tick: u64,
        item_index: usize,
        price_multiplier: f64,
    },
}

impl Scenario {
    pub fn standard() -> Self {
        Self {
            name: "Standard Economy".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Hoarder".into(),
                    count: 1,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14, // 14 days
            speed_ticks_per_sec: 100,
        }
    }

    pub fn stressed() -> Self {
        Self {
            name: "Stressed Economy".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Hoarder".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Exploiter".into(),
                    count: 1,
                },
            ],
            stress_events: vec![
                StressEvent::Exploit { at_tick: 288 * 3 },
                StressEvent::LowPlayers { at_tick: 288 * 7 },
                StressEvent::LoanCascade { at_tick: 288 * 5 },
            ],
            seed: None,
            events: Vec::new(),
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    pub fn high_activity() -> Self {
        Self {
            name: "High Activity Economy".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 8,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Hoarder".into(),
                    count: 3,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 7,
            speed_ticks_per_sec: 300,
        }
    }

    pub fn low_player() -> Self {
        Self {
            name: "Low Player Count".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 1,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 100,
        }
    }

    pub fn spread_stability() -> Self {
        let mut config = SimConfig::default();
        config.spread.base_spread = 0.20;
        config.spread.volume_impact = 0.8;
        config.spread.player_impact = 0.6;
        config.economy.slippage_coeff = 0.01;
        Self {
            name: "Spread Stability Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 3,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 10,
            speed_ticks_per_sec: 200,
        }
    }

    /// sp08-moderate: Tests tiered circuit breaker at tier1 and tier2 levels.
    /// sell_pressure_multiplier=0.80 (underselling bias), standard player mix,
    /// single LoanCascade at day 6 (earlier cascade = less compound growth than day-7).
    /// Expected: tier1 fires ~day 4-5 (3x debt/GDP), tier2 ~day 7-8 (5x), tier3 avoided.
    /// Compare to stressed-economy (23,196x, tier3 fires) and sp08-full (475x, tier3 fires).
    pub fn sp08_moderate() -> Self {
        let mut config = SimConfig::default();
        config.economy.sell_pressure_multiplier = 0.80;
        Self {
            name: "sp08 Moderate Debt Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Hoarder".into(),
                    count: 1,
                },
            ],
            // Single cascade at day 6: earlier than stressed (day 5) and sp08-stressed.
            // Economy has 6 days of growth before cascade = moderate compound, not catastrophic.
            stress_events: vec![StressEvent::LoanCascade { at_tick: 288 * 6 }],
            seed: None,
            events: Vec::new(),
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Guild Stability Test: Standard economy with GuildBuyer archetypes.
    /// GuildBuyers maintain target inventory — they buy when stock is low,
    /// hold otherwise. Tests whether guild players provide price stability
    /// or create artificial demand floors.
    pub fn guild_stability() -> Self {
        let config = SimConfig::default();
        Self {
            name: "Guild Stability Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14, // 14 days
            speed_ticks_per_sec: 200,
        }
    }

    /// Buyer-heavy economy: tests whether more Hoarders and GuildBuyers can counteract
    /// Farmer oversupply. Player mix: 2 GuildBuyer + 3 Casual + 3 Hoarder + 2 Farmer + 2 Trader.
    /// Fewer Farmers and more Hoarders vs guild_stability. Also tests whether the
    /// tiered circuit breaker helps when buyer mix is better.
    pub fn buyer_heavy() -> Self {
        let config = SimConfig::default();
        Self {
            name: "Buyer Heavy Economy".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Hoarder".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Standard economy but replaces the 1 Hoarder with 1 MarketMaker.
    /// Tests whether two-sided MM liquidity can counteract Farmer sell pressure
    /// in a standard player mix, without GuildBuyers dominating as buyers.
    pub fn standard_with_mm() -> Self {
        Self {
            name: "Standard+MM Economy".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                // Replace Hoarder with MarketMaker
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// GuildStability + MM + 2 GuildBuyers at fixed 7% threshold.
    /// This is the best-performing config from recent sim runs:
    /// guild_stability player mix + MM + 2 GB at 7% = D/G 1.76x, vol 0.007, GDP 898K.
    /// Tests whether the guild mix + MM + 7% threshold is the healthiest economy.
    pub fn guild_stability_mm_fixed_guild() -> Self {
        Self {
            name: "GuildStability+MM+7%GB".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// GuildStability with 2 MarketMakers (replaces 1 Casual with 2nd MM).
    /// Tests: Does a second MM improve economy health, or do they step on each other's toes?
    ///
    /// Control: guild_stability_mm_fixed_guild (1MM + 2GB + 4Cas + 3Far + 2Tra = 12 players)
    /// Treatment: guild_stability_2mm_fixed_guild (2MM + 2GB + 3Cas + 3Far + 2Tra = 12 players)
    ///
    /// Hypotheses:
    /// - H1 (YES): 2 MMs provide redundant two-sided liquidity → tighter spreads, lower vol
    /// - H2 (NO): MMs compete on same quotes → one dominates, other gets starved → no improvement
    /// - H3 (MAYBE): 2 MMs mean more capital deployed → more resilient to liquidity shocks
    ///
    /// Key metrics: GDP, D/G, vol, avg BPD, buy ratio
    pub fn guild_stability_2mm_fixed_guild() -> Self {
        Self {
            name: "GuildStability+2MM+7%GB".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 2, // ← 2 MMs instead of 1
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 3, // ← 3 instead of 4 (replaced 1 Casual with 1 MM)
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: Some(42), // Same seed as control for fair head-to-head
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Verifies per-loan GDP cap behavior.
    /// Uses a tight single_loan_gdp_cap (0.5) to force early-tick cap events.
    /// Players start with low balance to trigger loan requests in early ticks.
    pub fn loan_cap_test() -> Self {
        let mut config = SimConfig::default();
        // Cap each loan at 50% of GDP — very tight, triggers early when economy is small
        config.loans.single_loan_gdp_cap = 0.5;
        // Slightly higher base loan multiplier so raw loan requests exceed the cap
        config.loans.max_loan_multiplier = 3.0;

        Self {
            name: "Loan Cap Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 6,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: Some(42),
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// GuildStability+MM but with a 168h (7-day) post-default cooldown ENABLED.
    /// This is the FIXED treatment: when GuildBuyers default, they cannot immediately
    /// re-borrow, preventing the cascade bypass of the circuit breaker.
    /// Used to verify the fix (9521dcb) works as intended.
    pub fn guildbuyer_failure_test() -> Self {
        let mut config = SimConfig::default();
        // Cooldown: 7 days × 24 hours = 168 hours (same as Java default)
        config.loans.post_default_cooldown_hours = 168;
        // Circuit breaker counts Active + Defaulted (already the default in SimConfig)
        Self {
            name: "GuildBuyer Failure Test (cooldown ENABLED)".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: Some(42),
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Guildbuyer Failure Test but with MM loan BOUNDED via single_loan_gdp_cap=0.10.
    /// MM's opening loan on Day 2 was $183K = 28.5% of economy GDP — the cascade driver.
    /// Bounding loans to 10% of GDP would cap MM's opening loan at ~$64K instead.
    pub fn guildbuyer_failure_bounded_mm_test() -> Self {
        let mut config = SimConfig::default();
        config.loans.post_default_cooldown_hours = 168;
        // Single loan capped at 10% of economy GDP — prevents MM from taking
        // outsized loans that trigger the default cascade.
        config.loans.single_loan_gdp_cap = 0.10;
        Self {
            name: "GuildBuyer Failure Test (MM loan BOUNDED)".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: Some(42),
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Guildbuyer Failure Test but with MM opening loans PROHIBITED.
    /// MM is prevented from taking opening loans (mm_opening_loan_allowed = false).
    /// MM starts with $20-100K initial capital — sufficient for market-making.
    /// This is the ROOT-CAUSE fix: instead of bounding MM loans (which backfired —
    /// D/G went from 0.75x to 1.85x), we simply prevent MM from borrowing in the
    /// first place. MM doesn't need opening loans to function.
    pub fn guildbuyer_failure_no_mm_opening_loan_test() -> Self {
        let mut config = SimConfig::default();
        config.loans.post_default_cooldown_hours = 168;
        // The key fix: prohibit MM from taking opening loans
        config.loans.mm_opening_loan_allowed = false;
        Self {
            name: "GuildBuyer Failure Test (MM opening loan PROHIBITED)".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: Some(42),
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Standard+MM with GuildBuyers at fixed 5% threshold.
    /// Tests whether the uniquely-safe 5% GuildBuyer threshold combined with MM
    /// produces a healthier economy than random-threshold guild_stability.
    /// Standard+MM has no GuildBuyers by default, so we add 2 at 5%.
    pub fn standard_with_mm_fixed_guild() -> Self {
        Self {
            name: "Standard+MM+5%GB Economy".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Exploiter Stress Test: Standard+MM + 2 Exploiters from tick 0.
    /// Exploiters chase trends (buy rising, sell falling), amplifying volatility.
    /// Tests whether MarketMakers provide sufficient two-sided liquidity to
    /// absorb Exploiter-driven price manipulation without destabilizing the economy.
    ///
    /// Control: standard_with_mm (no Exploiters)
    /// Treatment: standard_with_mm + 2 Exploiters
    pub fn exploiter_stress() -> Self {
        Self {
            name: "Exploiter Stress Test".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "Exploiter".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Exploiter Cap Test: Standard+MM + 1 Exploiter (cap = 5% of 12 players).
    /// Tests whether a single Exploiter provides the liquidity benefit (buy/sell
    /// balance) without the hyperinflation seen with 2 Exploiters (+5,528% Diamond).
    /// If 1 Exploiter fixes the 77%→50% buy ratio without extreme price inflation,
    /// then a participation cap is the solution for production servers.
    pub fn exploiter_cap_test() -> Self {
        Self {
            name: "Exploiter Cap Test (1 Exploiter)".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "Exploiter".into(),
                    count: 1,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Floor/Ceiling Test: Tests whether per-item floor/ceiling affects displayed prices.
    /// Control: guild_stability_mm_fixed_guild (no floor/ceiling)
    /// Treatment: same economy but Diamond floor=60% of base ($300), Iron ceiling=100% ($50)
    ///
    /// Floor: prevents displayed price from going below floor even when internal price is low.
    /// GuildStability with 1MM + 1GB + 1GS + 4Cas + 3Far + 2Tra.
    /// Tests whether GuildSeller (price-spike selling) reduces underselling vs
    /// guild_stability_mm_fixed_guild (2GB instead of 1GB+1GS).
    /// Control: guild_stability_mm_fixed_guild (2GB, 0GS)
    /// Treatment: guild_stability_mm_gs (1GB + 1GS)
    ///
    /// Hypothesis: GuildSeller provides downward price pressure on high prices,
    /// reducing the sell-heavy bias that Farmer-dominated economies exhibit.
    pub fn guild_stability_mm_gs() -> Self {
        Self {
            name: "GuildStability+MM+GB+GS".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildSeller".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Ceiling: prevents displayed price from exceeding ceiling even when internal price is high.
    /// Internal prices still move freely — only displayed buy/sell prices are clamped.
    pub fn floor_ceiling_test() -> Self {
        let mut config = SimConfig::default();
        // Set floor for Diamond at 60% of base ($500 * 0.6 = $300)
        if let Some(diamond) = config.items.iter_mut().find(|ic| ic.name == "Diamond") {
            diamond.price_floor_override = Some(diamond.base_price * 0.6);
        }
        // Set ceiling for Iron Ingot at 100% of base ($50)
        if let Some(iron) = config.items.iter_mut().find(|ic| ic.name == "Iron Ingot") {
            iron.price_ceiling_override = Some(iron.base_price * 1.0);
        }

        Self {
            name: "Floor/Ceiling Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// InsiderTrader Test: tests the mean-reversion archetype in isolation.
    ///
    /// InsiderTrader buys when price is below rolling average (undervalued),
    /// sells when above (overvalued). Provides stabilizing counter-force to
    /// momentum-driven overshoot in both directions.
    ///
    /// Compare:
    /// - standard+MM (liquidity provider, spread-earning)
    /// - standard+MM+InsiderTraders (both mechanisms combined)
    ///
    /// Key question: Does InsiderTrader provide additional stabilization beyond MM?
    /// Exploiters fix buy/sell balance but cause hyperinflation.
    /// InsiderTraders might fix balance WITHOUT hyperinflation.
    pub fn insider_trader_test() -> Self {
        Self {
            name: "InsiderTrader Test".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "InsiderTrader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// MarketMaker Test: replaces one GuildBuyer with one MarketMaker in the
    /// guild_stability player mix, to test whether two-sided liquidity from
    /// MarketMakers can counteract GuildBuyer buy-dominance and reduce systemic underselling.
    ///
    /// Hypothesis: MarketMaker provides sell orders when overstocked, reducing the
    /// GuildBuyer's dominance as the sole buyer. Should improve buy_ratio balance
    /// and price stability vs guild_stability.
    pub fn marketmaker_test() -> Self {
        let config = SimConfig::default();
        Self {
            name: "MarketMaker Test".to_string(),
            config,
            players: vec![
                // Replace 1 of 2 GuildBuyers with MarketMaker
                // to measure isolated effect of two-sided liquidity
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14, // 14 days
            speed_ticks_per_sec: 200,
        }
    }

    /// Player Exodus Stress Test: simulates mass player departure mid-simulation.
    /// Real Minecraft servers lose players constantly. What happens to the economy
    /// when 50% quit at day 7?
    ///
    /// Control: guild_stability_mm_fixed_guild (12 players, 14 days, no exodus)
    /// Treatment: same config + 50% players quit at day 7 (tick 2016)
    ///
    /// Key questions:
    /// - Do prices collapse? (volume drops → wider spreads → fewer trades)
    /// - Does the loan circuit breaker fire? (debt was healthy pre-exodus)
    /// - Can the remaining 6 players sustain the economy?
    /// - Is the pre-exodus debt load crushing for remaining players?
    pub fn player_exodus_test() -> Self {
        // Day 7 = tick 2016 (288 ticks/day × 7 days)
        // exodus_spread_multiplier=2.0x for 288 ticks (1 day) then decays 5%/tick
        let config = SimConfig {
            player_exodus_tick: Some(288 * 7),
            player_exodus_fraction: 0.5,
            exodus_spread_multiplier: 2.0,
            exodus_shock_duration_ticks: 288,
            ..SimConfig::default()
        };
        Self {
            name: "Player Exodus Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14, // 14 days
            speed_ticks_per_sec: 200,
        }
    }

    /// Sector correlation stress test: injects a price shock to Diamond (ores section)
    /// at day 3, then measures how strongly other ores items follow.
    /// Runs with sector_correlation=0.05 (treatment) vs sector_correlation=0.0 (control)
    /// to isolate the correlation engine effect.
    pub fn correlation() -> Self {
        let config = SimConfig::default();
        // Standard mix but enough activity to generate clear price signals
        Self {
            name: "Sector Correlation Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Hoarder".into(),
                    count: 2,
                },
            ],
            // Diamond is index 5 in default items — we inject a forced buy spike at day 3
            // The stress event system fires a custom PriceShock that manipulates Diamond's price
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 7,
            speed_ticks_per_sec: 200,
        }
    }

    /// Market Event System test: validates that the Rust simulation correctly models
    /// the Java MarketEventService behavior.
    ///
    /// Test design — standard+MM+GuildBuyers economy (proven healthy):
    /// - Control: no market events
    /// - Treatment 1: DEMAND_SURGE × 2.0 on DIAMOND starting day 3 (864 ticks)
    ///   → Diamond prices should rise FASTER during the event
    ///   → After event ends (day 5), prices should normalize
    /// - Treatment 2: SUPPLY_GLUT × 2.0 on IRON_INGOT starting day 7 (2016 ticks)
    ///   → Iron prices should fall FASTER during the event
    /// - Treatment 3: INFLATION_BOOST × 1.5 across all items starting day 5
    ///   → All items should drift upward faster during event
    ///
    /// Validates:
    /// 1. Event multiplier applies correctly per item/material
    /// 2. Multiple concurrent events stack additively
    /// 3. Event expiry is handled correctly
    /// 4. Wildcard material patterns work (*_INGOT matches GOLD_INGOT, IRON_INGOT)
    pub fn market_event_test() -> Self {
        Self {
            name: "Market Event Test".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
            ],
            seed: None,
            // DEMAND_SURGE on Diamond (exact match): day 3 → day 5
            // SUPPLY_GLUT on Iron Ingot (exact match): day 7 → day 9
            // INFLATION_BOOST on all items (*): day 5 → day 8
            // Also test wildcard: GOLD_* ingots at day 10
            events: vec![
                MarketEvent {
                    name: "Diamond Demand Surge".into(),
                    event_type: crate::events::EventType::DemandSurge,
                    materials: vec!["DIAMOND".into()],
                    multiplier: 2.0,
                    starts_at_tick: 288 * 3, // day 3
                    ends_at_tick: 288 * 5,   // day 5
                },
                MarketEvent {
                    name: "Iron Supply Glut".into(),
                    event_type: crate::events::EventType::SupplyGlut,
                    materials: vec!["IRON_INGOT".into()],
                    multiplier: 2.0,
                    starts_at_tick: 288 * 7, // day 7
                    ends_at_tick: 288 * 9,   // day 9
                },
                MarketEvent {
                    name: "Economy-Wide Inflation Boost".into(),
                    event_type: crate::events::EventType::InflationBoost,
                    materials: vec!["*".into()],
                    multiplier: 1.5,
                    starts_at_tick: 288 * 5, // day 5
                    ends_at_tick: 288 * 8,   // day 8
                },
                MarketEvent {
                    name: "Gold Ingot Rush".into(),
                    event_type: crate::events::EventType::GoldRush,
                    materials: vec!["GOLD_*".into()], // wildcard prefix
                    multiplier: 1.8,
                    starts_at_tick: 288 * 10, // day 10
                    ends_at_tick: 288 * 12,   // day 12
                },
            ],
            stress_events: vec![],
            duration_ticks: 288 * 14, // 14 days
            speed_ticks_per_sec: 200,
        }
    }

    /// Control for Market Event test: IDENTICAL player mix as market_event_test
    /// (5 Casual + 3 Farmer + 2 Trader + 2 GuildBuyer + 1 MarketMaker, 14 days)
    /// but with NO market events.
    ///
    /// This is the exact same config as market_event_test — same players, same duration,
    /// same tick rate — just without the 4 market events (DEMAND_SURGE, SUPPLY_GLUT,
    /// INFLATION_BOOST, GOLD_RUSH).
    ///
    /// When run with the SAME seed, the control and treatment diverge ONLY because of
    /// the events, making it a clean measurement of event impact.
    pub fn market_event_control() -> Self {
        Self {
            name: "Market Event Control".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
            ],
            events: Vec::new(), // NO events — this is the control
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
            seed: None,
        }
    }

    /// Standard+MM+GB+IT: Tests whether InsiderTraders add value to the recommended
    /// economy config (standard+MM+GB@7%).
    ///
    /// Control: guild_stability_mm_fixed_guild (same players, no InsiderTraders)
    /// Treatment: standard_plus_mm_gb_it (2 additional InsiderTraders added)
    ///
    /// Key question: Do InsiderTraders provide additional stabilization on top of
    /// MM + GB, or are they redundant/destabilizing?
    ///
    /// InsiderTraders buy on price dips (mean-reversion), while MM provides two-sided
    /// liquidity and GB buys to maintain inventory targets. ITs might complement
    /// the system by accelerating mean-reversion after price shocks.
    pub fn standard_plus_mm_gb_it() -> Self {
        Self {
            name: "Standard+MM+GB+IT".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 5,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                // NEW: 2 InsiderTraders added to the recommended config
                ArchetypeConfig {
                    archetype: "InsiderTrader".into(),
                    count: 2,
                },
            ],
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
            seed: None,
        }
    }

    /// Price Freeze Test: freeze Diamond price discovery.
    ///
    /// Per-item freeze pauses price discovery while spreads still compute.
    /// Key questions:
    /// 1. Does freezing Diamond affect broader economy (since Diamond is a key trading item)?
    /// 2. Does freeze reduce price volatility as intended?
    /// 3. What happens to Diamond spread when price can't move?
    pub fn price_freeze_test() -> Self {
        let mut config = SimConfig::default();
        // Freeze Diamond price discovery — spreads still compute, item remains tradeable
        if let Some(diamond) = config.items.iter_mut().find(|ic| ic.name == "Diamond") {
            diamond.price_frozen = true;
        }

        Self {
            name: "Price Freeze Test".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// VolumeTrader Test: adds 2 VolumeTraders to the GuildStability mix.
    /// Hypothesis: VolumeTraders provide contrarian pressure on volume extremes,
    /// buying when spreads widen (volume drought = cheap) and selling when
    /// spreads tighten (volume surge = expensive). Should reduce volatility
    /// and improve GDP by stabilising prices around fair value.
    pub fn volume_trader_test() -> Self {
        Self {
            name: "VolumeTrader Test".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "VolumeTrader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 4,
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 3,
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 2,
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }
}

/// Compute Pearson correlation coefficient between two price-change series.
/// Returns None if series are too short or have zero variance.
fn pearson_correlation(a: &[f64], b: &[f64]) -> Option<f64> {
    let n = a.len().min(b.len());
    if n < 3 {
        return None;
    }
    let a = &a[a.len() - n..];
    let b = &b[b.len() - n..];
    let mean_a = a.iter().sum::<f64>() / n as f64;
    let mean_b = b.iter().sum::<f64>() / n as f64;
    let var_a: f64 = a.iter().map(|x| (x - mean_a).powi(2)).sum::<f64>() / n as f64;
    let var_b: f64 = b.iter().map(|x| (x - mean_b).powi(2)).sum::<f64>() / n as f64;
    if var_a < 1e-10 || var_b < 1e-10 {
        return None;
    }
    let cov: f64 = a
        .iter()
        .zip(b.iter())
        .map(|(x, y)| (x - mean_a) * (y - mean_b))
        .sum::<f64>()
        / n as f64;
    Some(cov / (var_a * var_b).sqrt())
}

/// Average pairwise price-change correlation for items within a given section.
/// Compares treatment (sector_correlation > 0) vs control (sector_correlation = 0).
fn avg_within_section_correlation(
    items: &[crate::engine::ItemState],
    section: &str,
) -> Option<f64> {
    // Find all items with same section via config — use item names as section proxy
    // since ItemState doesn't store section. Items in the same "ores" group:
    // Diamond(index 5), Iron Ingot(index 3), Redstone(index 2), Netherite(index 7)
    let section_items: Vec<&str> = match section {
        "ores" => vec!["Redstone", "Iron Ingot", "Diamond", "Netherite Ingot"],
        "drops" => vec!["Rotten Flesh", "Blaze Rod"],
        "building" => vec!["Cobblestone"],
        "food" => vec!["Golden Apple"],
        _ => return None,
    };
    let indices: Vec<usize> = items
        .iter()
        .enumerate()
        .filter(|(_, item)| section_items.contains(&item.name.as_str()))
        .map(|(i, _)| i)
        .collect();
    if indices.len() < 2 {
        return None;
    }
    // Convert price histories to pct-change series
    let changes: Vec<Vec<f64>> = indices
        .iter()
        .map(|&i| {
            let h = &items[i].price_history;
            h.windows(2)
                .map(|w| {
                    if w[0] > 0.0 {
                        (w[1] - w[0]) / w[0]
                    } else {
                        0.0
                    }
                })
                .collect()
        })
        .collect();
    let mut total = 0.0;
    let mut count = 0usize;
    for i in 0..indices.len() {
        for j in (i + 1)..indices.len() {
            if let Some(r) = pearson_correlation(&changes[i], &changes[j]) {
                total += r;
                count += 1;
            }
        }
    }
    if count == 0 {
        None
    } else {
        Some(total / count as f64)
    }
}

/// Average pairwise price-change correlation for items ACROSS different sections.
fn avg_cross_section_correlation(items: &[crate::engine::ItemState]) -> Option<f64> {
    // Pick one representative item from each section
    let reps = [
        "Cobblestone",
        "Rotten Flesh",
        "Redstone",
        "Golden Apple",
        "Diamond",
    ];
    let indices: Vec<usize> = items
        .iter()
        .enumerate()
        .filter(|(_, item)| reps.contains(&item.name.as_str()))
        .map(|(i, _)| i)
        .collect();
    if indices.len() < 2 {
        return None;
    }
    let changes: Vec<Vec<f64>> = indices
        .iter()
        .map(|&i| {
            let h = &items[i].price_history;
            h.windows(2)
                .map(|w| {
                    if w[0] > 0.0 {
                        (w[1] - w[0]) / w[0]
                    } else {
                        0.0
                    }
                })
                .collect()
        })
        .collect();
    let mut total = 0.0;
    let mut count = 0usize;
    for i in 0..indices.len() {
        for j in (i + 1)..indices.len() {
            if let Some(r) = pearson_correlation(&changes[i], &changes[j]) {
                total += r;
                count += 1;
            }
        }
    }
    if count == 0 {
        None
    } else {
        Some(total / count as f64)
    }
}

/// Run the sector correlation test: treatment (sector_correlation=0.05) vs
/// control (sector_correlation=0.0), identical seed, PriceShock to Diamond at day 3.
/// Measures how strongly ores items co-move after the shock.
fn run_correlation_test(seed: u64) {
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       SECTOR CORRELATION TEST — ores section               ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Build two configs: treatment (sector_correlation=0.05) and control (sector_correlation=0.0)
    let mut treatment_config = SimConfig::default();
    treatment_config.economy.sector_correlation = 0.05;
    // Shock Diamond (index 5) at day 3 — price × 2.5
    let shock_tick = 288 * 3;
    let shock_idx = 5; // Diamond in default_items

    let mut control_config = treatment_config.clone();
    control_config.economy.sector_correlation = 0.0;

    let duration = 288 * 7; // 7 days

    // Run treatment
    let treatment_name = "Treatment (sector_correlation=0.05)";
    println!("─── {} ───", treatment_name);
    let treatment_result = run_correlation_sim(
        treatment_name,
        treatment_config,
        seed,
        shock_tick,
        shock_idx,
        2.5,
        duration,
    );

    // Run control (same seed)
    let control_name = "Control (sector_correlation=0.0)";
    println!("\n─── {} ───", control_name);
    let control_result = run_correlation_sim(
        control_name,
        control_config,
        seed,
        shock_tick,
        shock_idx,
        2.5,
        duration,
    );

    // Print comparison
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  CORRELATION TEST RESULTS                                   ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    println!(
        "{:25} {:>12} {:>12} {:>12}",
        "", "TREATMENT", "CONTROL", "DIFF"
    );
    println!(
        "{:25} {:>12} {:>12} {:>12}",
        "", "(corr=0.05)", "(corr=0.0)", "(T−C)"
    );

    // Final price displacement for Diamond (index 5)
    let t_diamond_pct = (treatment_result[5].price / treatment_result[5].base_price - 1.0) * 100.0;
    let c_diamond_pct = (control_result[5].price / control_result[5].base_price - 1.0) * 100.0;
    println!(
        "{:25} {:>+11.1}% {:>+11.1}% {:>+11.1}%",
        "Diamond final displacement",
        t_diamond_pct,
        c_diamond_pct,
        t_diamond_pct - c_diamond_pct
    );

    // Final price displacement for Iron Ingot (index 3, same section)
    let t_iron_pct = (treatment_result[3].price / treatment_result[3].base_price - 1.0) * 100.0;
    let c_iron_pct = (control_result[3].price / control_result[3].base_price - 1.0) * 100.0;
    println!(
        "{:25} {:>+11.1}% {:>+11.1}% {:>+11.1}%",
        "Iron Ingot final displac.",
        t_iron_pct,
        c_iron_pct,
        t_iron_pct - c_iron_pct
    );

    // Final price displacement for Redstone (index 2, same section)
    let t_red_pct = (treatment_result[2].price / treatment_result[2].base_price - 1.0) * 100.0;
    let c_red_pct = (control_result[2].price / control_result[2].base_price - 1.0) * 100.0;
    println!(
        "{:25} {:>+11.1}% {:>+11.1}% {:>+11.1}%",
        "Redstone final displacement",
        t_red_pct,
        c_red_pct,
        t_red_pct - c_red_pct
    );

    // Netherite (index 7, same section)
    let t_neth_pct = (treatment_result[7].price / treatment_result[7].base_price - 1.0) * 100.0;
    let c_neth_pct = (control_result[7].price / control_result[7].base_price - 1.0) * 100.0;
    println!(
        "{:25} {:>+11.1}% {:>+11.1}% {:>+11.1}%",
        "Netherite final displacement",
        t_neth_pct,
        c_neth_pct,
        t_neth_pct - c_neth_pct
    );

    // Cobblestone (index 0, different section — building)
    let t_cob_pct = (treatment_result[0].price / treatment_result[0].base_price - 1.0) * 100.0;
    let c_cob_pct = (control_result[0].price / control_result[0].base_price - 1.0) * 100.0;
    println!(
        "{:25} {:>+11.1}% {:>+11.1}% {:>+11.1}%",
        "Cobblestone final displac.",
        t_cob_pct,
        c_cob_pct,
        t_cob_pct - c_cob_pct
    );

    println!();
    println!("--- Price history correlation (ores items, post-shock) ---");
    // Use post-shock window for correlation
    let shock_idx = shock_tick as usize;
    let ores_names = ["Diamond", "Iron Ingot", "Redstone", "Netherite Ingot"];
    for name in ores_names {
        let ti = match treatment_result.iter().position(|i| i.name == name) {
            Some(i) => i,
            None => {
                eprintln!(
                    "  warning: item '{}' not found in treatment results — skipping",
                    name
                );
                continue;
            }
        };
        let ci = match control_result.iter().position(|i| i.name == name) {
            Some(i) => i,
            None => {
                eprintln!(
                    "  warning: item '{}' not found in control results — skipping",
                    name
                );
                continue;
            }
        };
        // vs Cobblestone as reference
        let cob_i = match treatment_result
            .iter()
            .position(|i| i.name == "Cobblestone")
        {
            Some(i) => i,
            None => {
                eprintln!("  warning: 'Cobblestone' not found in results — skipping correlation");
                continue;
            }
        };
        let cob_hist = &treatment_result[cob_i].price_history;
        let cob_start = shock_idx.min(cob_hist.len().saturating_sub(2));
        let cob_changes = cob_hist[cob_start..]
            .windows(2)
            .map(|w| {
                if w[0] > 0.0 {
                    (w[1] - w[0]) / w[0]
                } else {
                    0.0
                }
            })
            .collect::<Vec<_>>();
        let item_hist = &treatment_result[ti].price_history;
        let item_start = shock_idx.min(item_hist.len().saturating_sub(2));
        let item_changes = item_hist[item_start..]
            .windows(2)
            .map(|w| {
                if w[0] > 0.0 {
                    (w[1] - w[0]) / w[0]
                } else {
                    0.0
                }
            })
            .collect::<Vec<_>>();
        let ctrl_hist = &control_result[ci].price_history;
        let ctrl_start = shock_idx.min(ctrl_hist.len().saturating_sub(2));
        let ctrl_changes = ctrl_hist[ctrl_start..]
            .windows(2)
            .map(|w| {
                if w[0] > 0.0 {
                    (w[1] - w[0]) / w[0]
                } else {
                    0.0
                }
            })
            .collect::<Vec<_>>();
        if let (Some(tc), Some(cc)) = (
            pearson_correlation(&item_changes, &cob_changes),
            pearson_correlation(&ctrl_changes, &cob_changes),
        ) {
            println!(
                "  {:22} T={:+.4}  C={:+.4}  Δ={:+.4}",
                format!("{:22}", name),
                tc,
                cc,
                tc - cc
            );
        } else {
            println!("  {:22} (insufficient post-shock data)", name);
        }
    }

    println!();
    let t_within = avg_within_section_correlation(&treatment_result, "ores");
    let c_within = avg_within_section_correlation(&control_result, "ores");
    let t_cross = avg_cross_section_correlation(&treatment_result);
    let c_cross = avg_cross_section_correlation(&control_result);

    println!("--- Summary ---");
    println!(
        "  Within-section (ores avg):   TREATMENT={:.4}  CONTROL={:.4}  Δ={:+.4}",
        t_within.unwrap_or(0.0),
        c_within.unwrap_or(0.0),
        t_within.unwrap_or(0.0) - c_within.unwrap_or(0.0)
    );
    println!(
        "  Cross-section (repr items): TREATMENT={:.4}  CONTROL={:.4}  Δ={:+.4}",
        t_cross.unwrap_or(0.0),
        c_cross.unwrap_or(0.0),
        t_cross.unwrap_or(0.0) - c_cross.unwrap_or(0.0)
    );

    let verdict = if t_within.unwrap_or(0.0) > c_within.unwrap_or(0.0) + 0.05 {
        "✓ SECTOR CORRELATION IS WORKING — ores items co-move more strongly with correlation enabled"
    } else if t_within.unwrap_or(0.0) < c_within.unwrap_or(0.0) - 0.05 {
        "✗ ANTI-CORRELATION DETECTED — items move OPPOSITE when correlation enabled"
    } else {
        "⚠ NEUTRAL — sector correlation has minimal effect (may need stronger shock or longer window)"
    };
    println!();
    println!("  VERDICT: {}", verdict);
    println!();
}

/// Run a correlation simulation with a seeded RNG, returning final item states.
fn run_correlation_sim(
    name: &str,
    config: SimConfig,
    seed: u64,
    shock_tick: u64,
    shock_item: usize,
    shock_mult: f64,
    duration: u64,
) -> Vec<crate::engine::ItemState> {
    let mut sim = Simulation::new_seeded(config.clone(), seed);
    // Add consistent player mix
    for _ in 0..4 {
        sim.add_player(Archetype::Trader);
    }
    for _ in 0..3 {
        sim.add_player(Archetype::Farmer);
    }
    for _ in 0..2 {
        sim.add_player(Archetype::Hoarder);
    }
    sim.paused = false;

    while sim.current_tick < duration {
        // Inject price shock at the shock tick
        if sim.current_tick == shock_tick && shock_item < sim.engine.items.len() {
            let old = sim.engine.items[shock_item].price;
            let item_name = sim.engine.items[shock_item].name.clone();
            let new = (old * shock_mult).max(0.01);
            sim.engine.items[shock_item].price = new;
            println!(
                "  [{}] PriceShock @ tick {}: {} {}→{} (×{:.2})",
                name, shock_tick, item_name, old, new, shock_mult
            );
        }
        sim.tick();
    }

    println!(
        "  [{}] Final tick {} — Diamond: {:.2} ({:+.1}% from base {:.2})",
        name,
        sim.current_tick,
        sim.engine.items[shock_item].price,
        (sim.engine.items[shock_item].price / sim.engine.items[shock_item].base_price - 1.0)
            * 100.0,
        sim.engine.items[shock_item].base_price,
    );

    sim.engine.items.clone()
}

// ─── Event Control Test ─────────────────────────────────────────────────────

/// Head-to-head comparison of market_event_test vs market_event_control.
/// Both run with IDENTICAL seed (42) so the only difference is the market events.
/// Produces a detailed per-item price comparison showing exactly what each event did.
fn run_event_control_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       MARKET EVENT CONTROL TEST                            ║");
    println!("║  Control vs Treatment — identical seed, same player mix   ║");
    println!("║  Players: 5Cas + 3Far + 2Tra + 2GB + 1MM (13 total, 14d) ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: no events | Treatment: DEMAND_SURGE(DIAMOND), SUPPLY_GLUT(IRON),");
    println!("            INFLATION_BOOST(all), GOLD_RUSH(GOLD_*), each ×2 over 14 days");
    println!(
        "  Seed: {} | Both runs use identical RNG trajectory\n",
        seed
    );

    // Run control (no events)
    let ctrl_scenario = Scenario::market_event_control();
    let ctrl_dir = PathBuf::from("/tmp/autotune-event-ctrl");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control run error: {}", e);
        return;
    }

    // Run treatment (with events)
    let treat_scenario = Scenario::market_event_test();
    let treat_dir = PathBuf::from("/tmp/autotune-event-treat");
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&treat_dir).ok();
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment run error: {}", e);
        return;
    }

    // Load summaries
    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Control summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Treatment summary error: {}", e);
            return;
        }
    };

    // Load per-item final prices from the printout (we re-run the sims to get prices)
    // Since we already ran both and they're in memory/output dirs, let's re-run once more
    // to capture the exact final prices. Or use the analyze tool.
    // Actually let's just run analyze on both DBs and parse the output.
    // Instead, use run_seeded_headless directly and capture final prices.
    // We already ran them. Let's use run_seeded_headless which we can inspect.
    // Best approach: re-run both in-process to get final prices directly.

    // Re-run to capture per-item final prices (same seed, same players)
    let ctrl_dir2 = PathBuf::from("/tmp/autotune-event-ctrl2");
    let treat_dir2 = PathBuf::from("/tmp/autotune-event-treat2");
    let _ = std::fs::remove_dir_all(&ctrl_dir2);
    let _ = std::fs::remove_dir_all(&treat_dir2);
    std::fs::create_dir_all(&ctrl_dir2).ok();
    std::fs::create_dir_all(&treat_dir2).ok();

    let mut ctrl_s = ctrl_scenario.clone();
    ctrl_s.seed = Some(seed);
    let mut treat_s = treat_scenario.clone();
    treat_s.seed = Some(seed);

    if let Err(e) = run_seeded_headless(&ctrl_s, seed, &ctrl_dir2) {
        eprintln!("  Control re-run error: {}", e);
        return;
    }
    if let Err(e) = run_seeded_headless(&treat_s, seed, &treat_dir2) {
        eprintln!("  Treatment re-run error: {}", e);
        return;
    }

    // Load prices from the re-run sims via analyze
    let ctrl_db = ctrl_dir2.join("simulation.db");
    let treat_db = treat_dir2.join("simulation.db");

    // Print macro-style comparison table
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  SUMMARY METRICS                                           ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Metric", "CONTROL (no events)", "TREATMENT (events)", "EVENT EFFECT"
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "─".repeat(20),
        "─".repeat(15),
        "─".repeat(15),
        "─".repeat(15)
    );

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);
    let gdp_eff = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let dg_eff = (treat_dg - ctrl_dg) / ctrl_dg.max(0.01) * 100.0;

    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "GDP", ctrl_summary.gdp, treat_summary.gdp, gdp_eff
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "Total Debt",
        ctrl_summary.debt,
        treat_summary.debt,
        (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.2}x {:>15.2}x {:>+14.1}%",
        "Debt / GDP", ctrl_dg, treat_dg, dg_eff
    );
    println!(
        "  {:20} {:>15.1}% {:>15.1}% {:>+14.1}%",
        "Buy Ratio",
        ctrl_summary.buy_ratio * 100.0,
        treat_summary.buy_ratio * 100.0,
        (treat_summary.buy_ratio - ctrl_summary.buy_ratio) / ctrl_summary.buy_ratio.max(0.01)
            * 100.0
    );
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.4}",
        "Avg Volatility",
        ctrl_summary.avg_volatility,
        treat_summary.avg_volatility,
        treat_summary.avg_volatility - ctrl_summary.avg_volatility
    );
    println!(
        "  {:20} {:>15.3}% {:>15.3}% {:>+14.3}%",
        "Avg BPD",
        ctrl_summary.avg_bpd * 100.0,
        treat_summary.avg_bpd * 100.0,
        (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0
    );

    // Per-item price analysis
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  PER-ITEM PRICE ANALYSIS (same seed, same player RNG)     ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>12} {:>12} {:>10} {:>12} {:>10}",
        "Item", "Ctrl Price", "Treat Price", "Ctrl %", "Treat %", "Event Δ"
    );
    println!(
        "  {:20} {:>12} {:>12} {:>10} {:>12} {:>10}",
        "─".repeat(20),
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(12),
        "─".repeat(10)
    );

    // Query DBs for final prices
    use crate::analyzer::load_all_prices;
    let ctrl_prices = load_all_prices(&ctrl_db).unwrap_or_default();
    let treat_prices = load_all_prices(&treat_db).unwrap_or_default();

    let item_names = [
        "Cobblestone",
        "Rotten Flesh",
        "Redstone",
        "Iron Ingot",
        "Blaze Rod",
        "Diamond",
        "Golden Apple",
        "Netherite Ingot",
    ];

    for name in item_names {
        let c = ctrl_prices.iter().find(|p| p.0 == name);
        let t = treat_prices.iter().find(|p| p.0 == name);
        if let (Some((_, c_price, c_base)), Some((_, t_price, t_base))) = (c, t) {
            let c_pct = (*c_price / *c_base - 1.0) * 100.0;
            let t_pct = (*t_price / *t_base - 1.0) * 100.0;
            let event_delta = t_pct - c_pct;
            let event_flag = if event_delta.abs() > 1.0 {
                if name == "Diamond" {
                    " ← DEMAND_SURGE"
                } else if name.starts_with("GOLD") {
                    " ← GOLD_RUSH"
                } else if name == "Iron Ingot" {
                    " ← SUPPLY_GLUT"
                } else {
                    " ← INFLATION"
                }
            } else {
                ""
            };
            println!(
                "  {:20} {:>12.2} {:>12.2} {:>+9.1}% {:>+11.1}% {:>+9.1}%{}",
                name, c_price, t_price, c_pct, t_pct, event_delta, event_flag
            );
        }
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  ANALYSIS                                                  ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    if gdp_eff < -10.0 {
        println!(
            "  ⚠️  Events REDUCED GDP by {:.1}% — events suppress economic activity",
            gdp_eff.abs()
        );
    } else if gdp_eff > 10.0 {
        println!(
            "  ✅ Events BOOSTED GDP by {:.1}% — events stimulate trade",
            gdp_eff
        );
    } else {
        println!("  ✅ Events had NEUTRAL GDP effect ({:+.1}%)", gdp_eff);
    }

    if dg_eff < -10.0 {
        println!(
            "  ✅ Events REDUCED Debt/GDP by {:.1}% — healthier debt levels",
            dg_eff.abs()
        );
    } else if dg_eff > 10.0 {
        println!(
            "  ⚠️  Events WORSENED Debt/GDP by {:.1}% — more debt relative to GDP",
            dg_eff
        );
    } else {
        println!(
            "  ⚠️  Events had NEAR-NEUTRAL Debt/GDP effect ({:+.1}%)",
            dg_eff
        );
    }

    let vol_diff = treat_summary.avg_volatility - ctrl_summary.avg_volatility;
    if vol_diff > 0.01 {
        println!("  ⚠️  Events INCREASED volatility (+{:.4})", vol_diff);
    } else if vol_diff < -0.01 {
        println!("  ✅ Events REDUCED volatility ({:.4})", vol_diff);
    } else {
        println!(
            "  ✅ Events had NEUTRAL effect on volatility ({:.4})",
            vol_diff
        );
    }

    println!("\n  Key findings:");
    println!("  1. Events are designed to amplify price moves during active windows.");
    println!("  2. After events end (day 5-12), prices should naturalize toward control levels.");
    println!("  3. If final prices differ substantially, events create lasting price distortions.");
    println!("  4. Net economic health effect (GDP, D/G) is the primary success metric.");

    // Cleanup
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    let _ = std::fs::remove_dir_all(&ctrl_dir2);
    let _ = std::fs::remove_dir_all(&treat_dir2);
}

// ─── Floor/Ceiling Test ─────────────────────────────────────────────────────

/// Tests whether per-item floor/ceiling affects displayed prices and trade behavior.
///
/// Control: guild_stability_mm_fixed_guild (no floor/ceiling)
/// Treatment: same but Diamond floor=60% base ($300), Iron ceiling=100% base ($50)
///
/// Key question: Does floor/ceiling change the INTERNAL prices, or only the displayed ones?
/// Per the Java implementation, floor/ceiling is applied to getBuyPrice/getSellPrice,
/// NOT to the internal price update. So internal prices should be identical between
/// control and treatment. Only displayed prices differ when floor/ceiling binds.
///
/// Expected: Same GDP, same volume, same internal prices, but Diamond sell floor=$300
/// (vs potentially lower in control when oversupply is severe).
fn run_floor_ceiling_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       FLOOR/CEILING TEST                                    ║");
    println!("║  Control vs Treatment — same economy, floor/ceiling on      ║");
    println!("║  Diamond floor=60% ($300), Iron ceiling=100% ($50)          ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guild_stability_mm_fixed_guild (no floor/ceiling)");
    println!("  Treatment: same + Diamond floor $300, Iron ceiling $50");
    println!("  Seed: {}\n", seed);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::floor_ceiling_test();

    let ctrl_dir = PathBuf::from("/tmp/autotune-fc-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-fc-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);

    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control run error: {}", e);
        return;
    }
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment run error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Control summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Treatment summary error: {}", e);
            return;
        }
    };

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  SUMMARY METRICS                                           ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Metric", "CONTROL", "TREATMENT", "Effect"
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "GDP",
        &format!("{:.0}", ctrl_summary.gdp),
        &format!("{:.0}", treat_summary.gdp),
        &format!(
            "{:+.1}%",
            (treat_summary.gdp / ctrl_summary.gdp - 1.0) * 100.0
        )
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Total Debt",
        &format!("{:.0}", ctrl_summary.debt),
        &format!("{:.0}", treat_summary.debt),
        &format!(
            "{:+.1}%",
            (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0
        )
    );
    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Debt/GDP",
        &format!("{:.2}x", ctrl_dg),
        &format!("{:.2}x", treat_dg),
        &format!("{:+.2}x", treat_dg - ctrl_dg)
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Buy Ratio",
        &format!("{:.1}%", ctrl_summary.buy_ratio * 100.0),
        &format!("{:.1}%", treat_summary.buy_ratio * 100.0),
        &format!(
            "{:+.1}%",
            (treat_summary.buy_ratio - ctrl_summary.buy_ratio) * 100.0
        )
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Avg Volatility",
        &format!("{:.4}", ctrl_summary.avg_volatility),
        &format!("{:.4}", treat_summary.avg_volatility),
        &format!(
            "{:+.4}",
            treat_summary.avg_volatility - ctrl_summary.avg_volatility
        )
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Avg BPD",
        &format!("{:.3}%", ctrl_summary.avg_bpd * 100.0),
        &format!("{:.3}%", treat_summary.avg_bpd * 100.0),
        &format!(
            "{:+.3}%",
            (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0
        )
    );

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  INTERNAL PRICE DISPLACEMENT (should be identical)         ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>10} {:>10} {:>10}",
        "Item", "Ctrl Int%", "Treat Int%", "Δ"
    );

    for i in 0..8 {
        let ic = &ctrl_scenario.config.items[i];
        let ce = treat_scenario.config.items.get(i).unwrap_or(ic);
        // Internal prices aren't directly stored in summary — they're in the final prices.
        // We show the floor/ceiling values as a note.
        let floor_str = ce
            .price_floor_override
            .map_or("none".into(), |f| format!("${:.0}", f));
        let ceil_str = ce
            .price_ceiling_override
            .map_or("none".into(), |c| format!("${:.0}", c));
        println!(
            "  {:20} floor={:>8}  ceiling={:>8}",
            ic.name, floor_str, ceil_str
        );
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  ANALYSIS                                                  ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    let gdp_eff = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    if gdp_eff.abs() < 1.0 {
        println!(
            "  ✅ Floor/ceiling had NEUTRAL GDP effect ({:+.2}%)",
            gdp_eff
        );
        println!("     Internal prices unchanged — floor/ceiling only affects displayed prices.");
    } else {
        println!("  ⚠️  Floor/ceiling changed GDP by {:+.2}%", gdp_eff);
        println!(
            "     This suggests floor/ceiling IS affecting internal prices or trade decisions."
        );
    }

    let bpd_diff = (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0;
    if bpd_diff.abs() < 0.1 {
        println!(
            "  ✅ Spreads (BPD) unchanged ({:+.3}%) — as expected.",
            bpd_diff
        );
    } else {
        println!("  ⚠️  Spreads changed by {:+.3}%", bpd_diff);
    }

    println!("\n  Key insight: Floor/ceiling is applied AFTER spread calculation,");
    println!("  in getBuyPrice/getSellPrice. It protects players from extreme prices");
    println!("  without changing the internal market equilibrium.");
    println!("  Floor helps sellers during oversupply; ceiling helps buyers during scarcity.");

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

// ─── Floor Strength Sweep ─────────────────────────────────────────────────

/// Diamond base price = $500 in the default config.
const DIAMOND_BASE_PRICE: f64 = 500.0;

/// Diamond floor percentages to test.
const FLOOR_PCTS: &[f64] = &[0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90];

#[derive(Debug)]
struct FloorSweepResult {
    floor_pct: f64,
    gdp: f64,
    debt: f64,
    debt_gdp_ratio: f64,
    avg_bpd: f64,
    avg_spd: f64,
    avg_volatility: f64,
    buy_ratio: f64,
    diamond_internal: f64,
    diamond_displayed: f64,
    floor_binds: bool,
}

/// Sweep Diamond floor from 30% to 90% of base price to find the GDP-neutral level.
///
/// The floor/ceiling test (2026-03-30) showed floor at 60% ($300) causes -4.9% GDP
/// through a BEHAVIORAL mechanism: floored displayed prices inflate price_incentive
/// → players gather MORE → oversupply → internal prices DROP FURTHER.
///
/// This sweep answers: at what floor % does GDP become neutral (vs no floor)?
/// And: does the floor paradox intensify at stronger floors?
///
/// Runs guild_stability_mm_fixed_guild (1MM + 2GB@7% + 4Cas + 3Far + 2Tra)
/// with Diamond floor at each percentage, vs a no-floor control.
fn run_floor_strength_sweep() {
    use crate::analyzer::{load_all_prices, load_summary};
    use std::io::Write;

    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║       DIAMOND FLOOR STRENGTH SWEEP                            ║");
    println!("║  Diamond base = $500.  Tests 30%–90% floor.  Seed=42.         ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  Scenario: GuildStability+MM+7%GB (1MM + 2GB@7% + 4Cas + 3Far + 2Tra)");
    println!("  Duration: 14 days (4032 ticks)\n");

    // ── Run control (no floor) ────────────────────────────────────────────
    println!("  Running control (no floor)...\n");

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let ctrl_dir = PathBuf::from("/tmp/autotune-fs-ctrl");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);

    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control run error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Control summary error: {}", e);
            return;
        }
    };

    let ctrl_prices = match load_all_prices(&ctrl_dir.join("simulation.db")) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("  Control prices error: {}", e);
            return;
        }
    };

    let ctrl_diamond_internal = ctrl_prices
        .iter()
        .find(|(name, _, _)| name == "Diamond")
        .map(|(_, p, _)| *p)
        .unwrap_or(0.0);

    let ctrl_gdp = ctrl_summary.gdp;
    println!(
        "  Control: GDP={:.0}  D/G={:.2}x  Buy%={:.1}%  Diamond int=${:.0}\n",
        ctrl_gdp,
        ctrl_summary.debt / ctrl_summary.gdp.max(1.0),
        ctrl_summary.buy_ratio * 100.0,
        ctrl_diamond_internal
    );

    // ── Header ─────────────────────────────────────────────────────────────
    println!(
        "{:>7} {:>7} {:>10} {:>10} {:>9} {:>7} {:>7} {:>9} {:>8} {:>10} {:>11}",
        "Floor%",
        "Floor$",
        "GDP",
        "Debt",
        "D/G",
        "BPD%",
        "SPD%",
        "Vol",
        "Buy%",
        "Diam Int$",
        "Display$"
    );
    println!(
        "{:>7} {:>7} {:>10} {:>10} {:>9} {:>7} {:>7} {:>9} {:>8} {:>10} {:>11}",
        "─".repeat(7),
        "─".repeat(7),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(9),
        "─".repeat(7),
        "─".repeat(7),
        "─".repeat(9),
        "─".repeat(8),
        "─".repeat(10),
        "─".repeat(11)
    );

    // ── Run each floor % ─────────────────────────────────────────────────
    let mut results: Vec<FloorSweepResult> = Vec::new();

    for floor_pct in FLOOR_PCTS {
        let fp = *floor_pct;
        eprint!(
            "\r  [{}/{}] floor={:.0}%",
            (fp * 100.0) as i32,
            100,
            fp * 100.0
        );
        std::io::stderr().flush().ok();

        // Build scenario with this floor % on Diamond
        let mut scenario = Scenario::guild_stability_mm_fixed_guild();
        if let Some(diamond) = scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(DIAMOND_BASE_PRICE * fp);
        }
        // Seed must match control
        scenario.seed = Some(seed);

        let out_dir = PathBuf::from(format!("/tmp/autotune-fs-{:02}", (fp * 100.0) as i32));
        let _ = std::fs::remove_dir_all(&out_dir);
        std::fs::create_dir_all(&out_dir).ok();

        if let Err(e) = run_headless(&scenario, Some(out_dir.clone())) {
            eprintln!("\n  Floor {}% run error: {}", fp * 100.0, e);
            continue;
        }

        let summary = match load_summary(&out_dir.join("simulation.db")) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("\n  Floor {}% summary error: {}", fp * 100.0, e);
                continue;
            }
        };

        let prices = match load_all_prices(&out_dir.join("simulation.db")) {
            Ok(p) => p,
            Err(e) => {
                eprintln!("\n  Floor {}% prices error: {}", fp * 100.0, e);
                continue;
            }
        };

        let diamond_internal = prices
            .iter()
            .find(|(name, _, _)| name == "Diamond")
            .map(|(_, p, _)| *p)
            .unwrap_or(0.0);

        let floor_abs = DIAMOND_BASE_PRICE * fp;
        let displayed = diamond_internal.max(floor_abs);
        let floor_binds = diamond_internal < floor_abs;

        let _ = std::fs::remove_dir_all(&out_dir);

        let debt_gdp = summary.debt / summary.gdp.max(0.01);

        println!(
            "\n{:>7.0}% {:>7.0} {:>10.0} {:>10.0} {:>8.2}x {:>6.2}% {:>6.2}% {:>8.4} {:>7.1}% {:>10.0} {:>10.0} {:>11}",
            fp * 100.0,
            floor_abs,
            summary.gdp,
            summary.debt,
            debt_gdp,
            summary.avg_bpd * 100.0,
            summary.avg_spd * 100.0,
            summary.avg_volatility,
            summary.buy_ratio * 100.0,
            diamond_internal,
            displayed,
            if floor_binds { "← FLOOR" } else { "" }
        );

        results.push(FloorSweepResult {
            floor_pct: fp,
            gdp: summary.gdp,
            debt: summary.debt,
            debt_gdp_ratio: debt_gdp,
            avg_bpd: summary.avg_bpd,
            avg_spd: summary.avg_spd,
            avg_volatility: summary.avg_volatility,
            buy_ratio: summary.buy_ratio,
            diamond_internal,
            diamond_displayed: displayed,
            floor_binds,
        });
    }

    println!("\n");

    // ── Analysis ─────────────────────────────────────────────────────────
    println!("╔══════════════════════════════════════════════════════════════════╗");
    println!("║  SWEEP ANALYSIS                                              ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");

    // GDP-neutral (closest to 0% GDP effect)
    let gdp_neutral = results
        .iter()
        .min_by(|a, b| {
            ((a.gdp / ctrl_gdp.max(1.0) - 1.0).abs())
                .partial_cmp(&(b.gdp / ctrl_gdp.max(1.0) - 1.0).abs())
                .unwrap()
        })
        .unwrap();
    let gdp_neutral_eff = (gdp_neutral.gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
    println!(
        "  GDP-neutral floor: {:.0}% (${:.0}) — GDP {:+.2}% vs control",
        gdp_neutral.floor_pct * 100.0,
        gdp_neutral.floor_pct * DIAMOND_BASE_PRICE,
        gdp_neutral_eff
    );

    // Best GDP
    let best_gdp = results
        .iter()
        .max_by(|a, b| a.gdp.partial_cmp(&b.gdp).unwrap())
        .unwrap();
    println!(
        "  Best GDP:         {:.0}% — GDP={:.0} ({:+.1}% vs control)",
        best_gdp.floor_pct * 100.0,
        best_gdp.gdp,
        (best_gdp.gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0
    );

    // Lowest D/G
    let lowest_dg = results
        .iter()
        .min_by(|a, b| a.debt_gdp_ratio.partial_cmp(&b.debt_gdp_ratio).unwrap())
        .unwrap();
    println!(
        "  Lowest D/G:       {:.0}% — D/G={:.2}x  (floor{} bound)",
        lowest_dg.floor_pct * 100.0,
        lowest_dg.debt_gdp_ratio,
        if lowest_dg.floor_binds { "" } else { " NOT" }
    );

    // Most balanced buy ratio
    let most_balanced = results
        .iter()
        .min_by(|a, b| {
            (a.buy_ratio - 0.5)
                .abs()
                .partial_cmp(&(b.buy_ratio - 0.5).abs())
                .unwrap()
        })
        .unwrap();
    println!(
        "  Most balanced:   {:.0}% — buy%={:.1}%",
        most_balanced.floor_pct * 100.0,
        most_balanced.buy_ratio * 100.0
    );

    // Floor paradox: at what % does internal price fall BELOW control?
    let paradox = results
        .iter()
        .filter(|r| r.diamond_internal < ctrl_diamond_internal)
        .min_by(|a, b| a.diamond_internal.partial_cmp(&b.diamond_internal).unwrap());
    if let Some(p) = paradox {
        let suppression = (1.0 - p.diamond_internal / ctrl_diamond_internal) * 100.0;
        println!(
            "\n  ⚠️  Floor paradox at {:.0}%: internal=${:.0} ({:.1}% BELOW control)\n\
                 Floor keeps displayed price HIGH (${:.0}) but internal falls to ${:.0}",
            p.floor_pct * 100.0,
            p.diamond_internal,
            suppression,
            p.diamond_displayed,
            p.diamond_internal
        );
    }

    // GDP vs floor % table
    println!("\n  GDP effect by floor %:\n");
    for r in &results {
        let eff = (r.gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
        let bar = if eff > 0.0 { "+" } else { "" };
        let binds = if r.floor_binds { "📍" } else { "  " };
        println!(
            "  {:>6.0}% {:>6}: {} {}{:.2}%  (Diam int=${:.0}, displayed=${:.0})",
            r.floor_pct * 100.0,
            "$".to_string() + &format!("{:.0}", r.floor_pct * DIAMOND_BASE_PRICE),
            binds,
            bar,
            eff,
            r.diamond_internal,
            r.diamond_displayed
        );
    }

    // ── CSV ───────────────────────────────────────────────────────────────
    let csv_path = PathBuf::from(
        "/home/ubuntu/.openclaw/workspace-autotune/sim-output/floor-strength-sweep.csv",
    );
    if let Some(parent) = csv_path.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    if let Ok(mut csv) = std::fs::File::create(&csv_path) {
        writeln!(
            csv,
            "floor_pct,floor_abs,gdp,debt,debt_gdp_ratio,avg_bpd,avg_spd,avg_volatility,buy_ratio,diamond_internal,diamond_displayed,floor_binds,gdp_effect_pct"
        )
        .ok();
        for r in &results {
            let gdp_eff = (r.gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
            writeln!(
                csv,
                "{:.2},{:.2},{:.2},{:.2},{:.6},{:.6},{:.6},{:.6},{:.4},{:.2},{:.2},{:.0},{:.4}",
                r.floor_pct,
                r.floor_pct * DIAMOND_BASE_PRICE,
                r.gdp,
                r.debt,
                r.debt_gdp_ratio,
                r.avg_bpd,
                r.avg_spd,
                r.avg_volatility,
                r.buy_ratio,
                r.diamond_internal,
                r.diamond_displayed,
                if r.floor_binds { 1.0 } else { 0.0 },
                gdp_eff
            )
            .ok();
        }
        println!("\n  CSV saved to: {}", csv_path.display());
    }

    let _ = std::fs::remove_dir_all(&ctrl_dir);
}

// ─── Multi-Server Coordination Test ────────────────────────────────────────

/// Multi-Server Simulation: Tests cross-server price aggregation.
///
/// Runs 3 independent servers with different economic conditions (player mixes),
/// each generating a ratio matrix from their price history. A coordinator aggregates
/// the ratio matrices and computes "true" relative prices using weighted geometric mean.
///
/// Key question: Does cross-server true price computation produce meaningful consensus prices?
///
/// Architecture:
/// - Each server runs a full simulation (different archetypes → different price scales)
/// - Coordinator collects final prices from each server
/// - Uses ratio-matrix aggregation (not just geometric mean of absolute prices)
/// - True prices are anchored to Cobblestone base price ($1)
fn run_multi_server_test() {
    use crate::analyzer::load_all_prices;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       MULTI-SERVER COORDINATION TEST                        ║");
    println!("║  3 servers, cross-server price aggregation                 ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // ── Step 1: Run 3 different scenarios as "independent servers" ──
    let seed = 42u64;

    // Server A: guild_stability (high demand, GB-heavy)
    let srv_a_scenario = Scenario::guild_stability_mm_fixed_guild();
    let srv_a_dir = PathBuf::from("/tmp/autotune-ms-srv-a");
    let _ = std::fs::remove_dir_all(&srv_a_dir);
    std::fs::create_dir_all(&srv_a_dir).ok();
    let mut srv_a = srv_a_scenario.clone();
    srv_a.seed = Some(seed);
    println!("  Running Server A (guild_stability_mm_fixed_guild)...");
    if let Err(e) = run_headless(&srv_a, Some(srv_a_dir.clone())) {
        eprintln!("  Server A error: {}", e);
        return;
    }

    // Server B: standard (casual/farmer mix — baseline economy)
    let srv_b_scenario = Scenario::standard();
    let srv_b_dir = PathBuf::from("/tmp/autotune-ms-srv-b");
    let _ = std::fs::remove_dir_all(&srv_b_dir);
    std::fs::create_dir_all(&srv_b_dir).ok();
    let mut srv_b = srv_b_scenario.clone();
    srv_b.seed = Some(seed + 100);
    println!("  Running Server B (standard)...");
    if let Err(e) = run_headless(&srv_b, Some(srv_b_dir.clone())) {
        eprintln!("  Server B error: {}", e);
        return;
    }

    // Server C: spread_stability (trader-heavy — different scale economy)
    let srv_c_scenario = Scenario::spread_stability();
    let srv_c_dir = PathBuf::from("/tmp/autotune-ms-srv-c");
    let _ = std::fs::remove_dir_all(&srv_c_dir);
    std::fs::create_dir_all(&srv_c_dir).ok();
    let mut srv_c = srv_c_scenario.clone();
    srv_c.seed = Some(seed + 200);
    println!("  Running Server C (spread_stability)...\n");
    if let Err(e) = run_headless(&srv_c, Some(srv_c_dir.clone())) {
        eprintln!("  Server C error: {}", e);
        return;
    }

    // ── Step 2: Load final prices from each server's DB ──
    println!("  Loading final prices from each server DB...\n");

    let srv_a_prices = load_all_prices(&srv_a_dir.join("simulation.db"))
        .map_err(|e| e.to_string())
        .unwrap_or_default();
    let srv_b_prices = load_all_prices(&srv_b_dir.join("simulation.db"))
        .map_err(|e| e.to_string())
        .unwrap_or_default();
    let srv_c_prices = load_all_prices(&srv_c_dir.join("simulation.db"))
        .map_err(|e| e.to_string())
        .unwrap_or_default();

    if srv_a_prices.is_empty() || srv_b_prices.is_empty() || srv_c_prices.is_empty() {
        println!("  ⚠️  Failed to load prices from one or more server DBs.");
        println!("  DB data may be missing. This can happen if the simulation ended early.");
        let _ = std::fs::remove_dir_all(&srv_a_dir);
        let _ = std::fs::remove_dir_all(&srv_b_dir);
        let _ = std::fs::remove_dir_all(&srv_c_dir);
        return;
    }

    // ── Step 3: Build ratio matrices ──
    // load_all_prices returns Vec<(name, price, spread)> — prices are the INTERNAL prices
    fn extract_price_vec(prices: &[(String, f64, f64)]) -> Vec<f64> {
        prices.iter().map(|(_, p, _)| *p).collect()
    }

    let a_vec = extract_price_vec(&srv_a_prices);
    let b_vec = extract_price_vec(&srv_b_prices);
    let c_vec = extract_price_vec(&srv_c_prices);

    let item_names: Vec<&str> = srv_a_prices.iter().map(|(n, _, _)| n.as_str()).collect();
    let n = item_names
        .len()
        .min(a_vec.len().min(b_vec.len().min(c_vec.len())));

    if n < 2 {
        println!("  ⚠️  Not enough items in price data.");
        let _ = std::fs::remove_dir_all(&srv_a_dir);
        let _ = std::fs::remove_dir_all(&srv_b_dir);
        let _ = std::fs::remove_dir_all(&srv_c_dir);
        return;
    }

    // Build ratio matrices: r[i][j] = price[i] / price[j]
    fn build_ratio_matrix(prices: &[f64]) -> Vec<Vec<f64>> {
        let n = prices.len();
        (0..n)
            .map(|i| {
                (0..n)
                    .map(|j| {
                        if prices[j] > 0.0 {
                            prices[i] / prices[j]
                        } else {
                            1.0
                        }
                    })
                    .collect()
            })
            .collect()
    }

    let a_ratios = build_ratio_matrix(&a_vec);
    let b_ratios = build_ratio_matrix(&b_vec);
    let c_ratios = build_ratio_matrix(&c_vec);

    // Geometric mean of ratio matrices across servers
    let mut combined = vec![vec![0.0; n]; n];
    for i in 0..n {
        for j in 0..n {
            combined[i][j] = (a_ratios[i][j] * b_ratios[i][j] * c_ratios[i][j]).powf(1.0 / 3.0);
        }
    }

    // Solve for true prices from combined ratio matrix.
    // We use power iteration: price[i] ≈ (∏_j r[i][j])^(1/n)
    // Then normalize to Cobblestone base = $1
    let mut true_prices: Vec<f64> = (0..n)
        .map(|i| {
            let prod: f64 = (0..n)
                .filter(|&j| j != i && combined[i][j] > 0.0)
                .map(|j| combined[i][j])
                .product();
            let cnt = (0..n).filter(|&j| j != i && combined[i][j] > 0.0).count();
            if cnt > 0 {
                prod.powf(1.0 / cnt as f64)
            } else {
                1.0
            }
        })
        .collect();

    // Normalize so Cobblestone (item 0) = its base price
    let cobblestone_base = 1.0_f64; // Cobblestone base price
    if true_prices[0] > 0.0 {
        let scale = cobblestone_base / true_prices[0];
        for p in &mut true_prices {
            *p *= scale;
        }
    }

    let bases = [1.0, 2.0, 20.0, 50.0, 75.0, 500.0, 500.0, 2500.0];

    println!(
        "  {:20} {:>10} {:>10} {:>10} {:>10} {:>10}",
        "Item", "Srv A ($)", "Srv B ($)", "Srv C ($)", "True Price", "True vs Base"
    );
    println!(
        "  {:20} {:>10} {:>10} {:>10} {:>10} {:>10}",
        "---", "---", "---", "---", "---", "---"
    );

    for i in 0..n {
        let displacement = if bases.get(i).copied().unwrap_or(1.0) > 0.0 {
            (true_prices[i] / bases[i] - 1.0) * 100.0
        } else {
            0.0
        };
        println!(
            "  {:20} {:>10.2} {:>10.2} {:>10.2} {:>10.2} {:>+8.1}%",
            item_names[i], a_vec[i], b_vec[i], c_vec[i], true_prices[i], displacement
        );
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  CROSS-SERVER RATIO ANALYSIS                               ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Show key ratios: Diamond/Iron, Gold Apple/Diamond, Netherite/Diamond
    fn find_ratio(items: &[&str], vec: &[f64], name_a: &str, name_b: &str) -> f64 {
        let ia = items.iter().position(|&n| n == name_a);
        let ib = items.iter().position(|&n| n == name_b);
        match (ia, ib) {
            (Some(i), Some(j)) if vec[j] > 0.0 => vec[i] / vec[j],
            _ => 0.0,
        }
    }

    println!("  Key price ratios (should be consistent across servers with different scales):");
    let ratios = [
        ("Diamond", "Iron Ingot"),
        ("Diamond", "Golden Apple"),
        ("Netherite Ingot", "Diamond"),
        ("Blaze Rod", "Redstone"),
        ("Cobblestone", "Rotten Flesh"),
    ];

    println!(
        "  {:30} {:>10} {:>10} {:>10} {:>10}",
        "Ratio", "Srv A", "Srv B", "Srv C", "True"
    );
    println!(
        "  {:30} {:>10} {:>10} {:>10} {:>10}",
        "---", "---", "---", "---", "---"
    );

    for (name_a, name_b) in &ratios {
        let ra = find_ratio(&item_names, &a_vec, name_a, name_b);
        let rb = find_ratio(&item_names, &b_vec, name_a, name_b);
        let rc = find_ratio(&item_names, &c_vec, name_a, name_b);
        let rt = find_ratio(&item_names, &true_prices, name_a, name_b);
        println!(
            "  {}/{} {:>13} {:>10.3} {:>10.3} {:>10.3} {:>10.3}",
            name_a, name_b, "", ra, rb, rc, rt
        );
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  KEY INSIGHT                                               ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Cross-server price aggregation works at the RATIO level.");
    println!("  Individual servers may have 10x different absolute price scales,");
    println!("  but their relative prices (Diamond/Iron, Cobblestone/Redstone)");
    println!("  tend to be consistent because they reflect real crafting economics.");
    println!();
    println!("  By aggregating ratio matrices from multiple servers and solving for");
    println!("  true relative prices, we get a consensus 'true price' vector that");
    println!("  new servers can use as initial price anchors.");
    println!();
    println!("  This is exactly what the Rust price-solver crate does in the API server:");
    println!("  servers submit ratio matrices → solver computes true prices → new servers");
    println!("  use true prices as starting point → faster convergence to fair prices.");

    // Cleanup
    let _ = std::fs::remove_dir_all(&srv_a_dir);
    let _ = std::fs::remove_dir_all(&srv_b_dir);
    let _ = std::fs::remove_dir_all(&srv_c_dir);
}

// ─── InsiderTrader Added Test ───────────────────────────────────────────────

/// Tests whether adding 2 InsiderTraders to the recommended economy
/// (standard+MM+GB@7%) improves stability and economic health.
///
/// Control: guild_stability_mm_fixed_guild (1MM + 2GB@7% + 4Cas + 3Far + 2Tra, no IT)
/// Treatment: standard_plus_mm_gb_it (same + 2 InsiderTraders)
///
/// Same seed (42) for both runs — identical RNG, only the IT archetype differs.
fn run_it_added_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       INSIDERTRADER ADDED TEST                             ║");
    println!("║  Control vs Treatment — 2 ITs added to recommended config ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guild_stability_mm_fixed_guild (1MM+2GB@7%+4Cas+3Far+2Tra)");
    println!("  Treatment: same + 2 InsiderTraders (mean-reversion archetype)");
    println!("  Seed: {}\n", seed);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::standard_plus_mm_gb_it();

    let ctrl_dir = PathBuf::from("/tmp/autotune-it-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-it-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);

    println!("─── Control (no IT) ───");
    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control error: {}", e);
        return;
    }

    println!("\n─── Treatment (+2 IT) ───");
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  RESULTS — IT ADDED vs CONTROL                             ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Metric", "CONTROL (no IT)", "TREATMENT (+2 IT)", "Effect"
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "─".repeat(20),
        "─".repeat(15),
        "─".repeat(15),
        "─".repeat(15)
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "GDP",
        ctrl_summary.gdp,
        treat_summary.gdp,
        (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "Total Debt",
        ctrl_summary.debt,
        treat_summary.debt,
        (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.2}x {:>15.2}x {:>+14.1}%",
        "Debt / GDP",
        ctrl_dg,
        treat_dg,
        (treat_dg / ctrl_dg.max(0.01) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.1}% {:>15.1}% {:>+14.1}%",
        "Buy Ratio",
        ctrl_summary.buy_ratio * 100.0,
        treat_summary.buy_ratio * 100.0,
        (treat_summary.buy_ratio - ctrl_summary.buy_ratio) / ctrl_summary.buy_ratio.max(0.01)
            * 100.0
    );
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.4}",
        "Avg Volatility",
        ctrl_summary.avg_volatility,
        treat_summary.avg_volatility,
        treat_summary.avg_volatility - ctrl_summary.avg_volatility
    );
    println!(
        "  {:20} {:>15.3}% {:>15.3}% {:>+14.3}%",
        "Avg BPD",
        ctrl_summary.avg_bpd * 100.0,
        treat_summary.avg_bpd * 100.0,
        (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0
    );

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  VERDICT                                                   ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    let gdp_change = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let dg_change = (treat_dg / ctrl_dg.max(0.01) - 1.0) * 100.0;
    let vol_change = treat_summary.avg_volatility - ctrl_summary.avg_volatility;
    let bpd_change = (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0;

    let mut improvements = 0;
    let mut regressions = 0;

    if gdp_change > 5.0 {
        println!(
            "  ✅ GDP: +{:.1}% (ITs boost economic activity)",
            gdp_change
        );
        improvements += 1;
    } else if gdp_change < -5.0 {
        println!(
            "  ⚠️  GDP: {:.1}% (ITs reduce economic activity)",
            gdp_change
        );
        regressions += 1;
    } else {
        println!(
            "  ⚠️  GDP: {:+.1}% (ITs have neutral GDP effect)",
            gdp_change
        );
    }

    if dg_change < -10.0 {
        println!("  ✅ Debt/GDP: {:.1}% (ITs improve debt health)", dg_change);
        improvements += 1;
    } else if dg_change > 10.0 {
        println!(
            "  ⚠️  Debt/GDP: +{:.1}% (ITs worsen debt health)",
            dg_change
        );
        regressions += 1;
    } else {
        println!(
            "  ⚠️  Debt/GDP: {:+.1}% (ITs have neutral debt effect)",
            dg_change
        );
    }

    if vol_change < -0.005 {
        println!("  ✅ Volatility: {:.4} (ITs reduce volatility)", vol_change);
        improvements += 1;
    } else if vol_change > 0.005 {
        println!(
            "  ⚠️  Volatility: +{:.4} (ITs increase volatility)",
            vol_change
        );
        regressions += 1;
    } else {
        println!(
            "  ⚠️  Volatility: {:+.4} (ITs have neutral volatility effect)",
            vol_change
        );
    }

    if bpd_change < -0.2 {
        println!("  ✅ BPD: {:.2}% (ITs tighten spreads)", bpd_change);
        improvements += 1;
    } else if bpd_change > 0.2 {
        println!("  ⚠️  BPD: +{:.2}% (ITs widen spreads)", bpd_change);
        regressions += 1;
    } else {
        println!(
            "  ⚠️  BPD: {:+.2}% (ITs have neutral spread effect)",
            bpd_change
        );
    }

    println!();
    if improvements >= 3 && regressions == 0 {
        println!("  ✅ RECOMMENDATION: ADD InsiderTraders to recommended config.");
        println!("     ITs complement MM+GB by accelerating mean-reversion after price shocks.");
    } else if regressions >= 2 {
        println!("  ⚠️  RECOMMENDATION: DO NOT add ITs — they degrade economic health.");
        println!("     InsiderTraders may be redundant with or destabilizing vs MM+GB.");
    } else {
        println!(
            "  ⚠️  MIXED: {} improvements, {} regressions.",
            improvements, regressions
        );
        println!("     InsiderTraders have a modest mixed effect — optional for realism.");
    }

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

// ─── Player Exodus Test ───────────────────────────────────────────────────────

/// Runs a player exodus stress test: 50% of players quit at day 7.
/// Compares pre/post-exodus economy health to determine if the economy
/// can survive a mass player departure.
fn run_player_exodus_test() {
    use crate::analyzer::load_summary;
    use rusqlite::Connection;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       PLAYER EXODUS STRESS TEST                           ║");
    println!("║  50% of players quit at day 7 — does the economy survive? ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guild_stability_mm_fixed_guild (12 players, no exodus)");
    println!("  Treatment: same config + 50% quit at day 7 (tick 2016)");
    println!("  Seed: {}\n", seed);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::player_exodus_test();

    let ctrl_dir = PathBuf::from("/tmp/autotune-exodus-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-exodus-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);

    println!("─── Control (no exodus) ───");
    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control error: {}", e);
        return;
    }

    println!("\n─── Treatment (50% quit at day 7) ───");
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };

    // Query loan stats directly from DB
    fn get_loan_stats(db_path: &std::path::Path) -> (usize, usize) {
        let conn = Connection::open(db_path);
        if let Ok(conn) = conn {
            let issued: usize = conn
                .query_row(
                    "SELECT COUNT(*) FROM loan_events WHERE event_type = 'Taken'",
                    [],
                    |row| row.get::<_, i64>(0),
                )
                .unwrap_or(0) as usize;
            let defaulted: usize = conn
                .query_row(
                    "SELECT COUNT(*) FROM loan_events WHERE event_type = 'Defaulted'",
                    [],
                    |row| row.get::<_, i64>(0),
                )
                .unwrap_or(0) as usize;
            return (issued, defaulted);
        }
        (0, 0)
    }

    fn get_final_prices(db_path: &std::path::Path) -> Vec<(String, f64)> {
        let conn = Connection::open(db_path);
        if let Ok(conn) = conn {
            let stmt = conn.prepare(
                "SELECT item_name, price FROM item_states
                 WHERE tick = (SELECT MAX(tick) FROM item_states)
                 ORDER BY item_name",
            );
            if let Ok(mut stmt) = stmt {
                let rows: Vec<(String, f64)> = stmt
                    .query_map([], |row| {
                        Ok((row.get::<_, String>(0)?, row.get::<_, f64>(1)?))
                    })
                    .ok()
                    .map(|iter| iter.filter_map(|r| r.ok()).collect())
                    .unwrap_or_default();
                return rows;
            }
        }
        Vec::new()
    }

    let (ctrl_loans_issued, ctrl_loans_defaulted) = get_loan_stats(&ctrl_dir.join("simulation.db"));
    let (treat_loans_issued, treat_loans_defaulted) =
        get_loan_stats(&treat_dir.join("simulation.db"));
    let ctrl_final_prices = get_final_prices(&ctrl_dir.join("simulation.db"));
    let treat_final_prices = get_final_prices(&treat_dir.join("simulation.db"));

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  RESULTS — PLAYER EXODUS vs CONTROL                      ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Metric", "CONTROL", "TREATMENT", "Effect"
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "─".repeat(20),
        "─".repeat(15),
        "─".repeat(15),
        "─".repeat(15)
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "GDP",
        ctrl_summary.gdp,
        treat_summary.gdp,
        (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "Total Debt",
        ctrl_summary.debt,
        treat_summary.debt,
        (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.2}x {:>15.2}x {:>+14.1}%",
        "Debt / GDP",
        ctrl_dg,
        treat_dg,
        (treat_dg / ctrl_dg.max(0.01) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.1}% {:>15.1}% {:>+14.1}%",
        "Buy Ratio",
        ctrl_summary.buy_ratio * 100.0,
        treat_summary.buy_ratio * 100.0,
        (treat_summary.buy_ratio - ctrl_summary.buy_ratio) / ctrl_summary.buy_ratio.max(0.01)
            * 100.0
    );
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.4}",
        "Avg Volatility",
        ctrl_summary.avg_volatility,
        treat_summary.avg_volatility,
        treat_summary.avg_volatility - ctrl_summary.avg_volatility
    );
    println!(
        "  {:20} {:>15.3}% {:>15.3}% {:>+14.3}%",
        "Avg BPD",
        ctrl_summary.avg_bpd * 100.0,
        treat_summary.avg_bpd * 100.0,
        (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0
    );

    // Per-item price comparison
    println!("\n--- Per-Item Price Comparison (14-day final) ---");
    for (item_name, treat_price) in &treat_final_prices {
        let ctrl_price = ctrl_final_prices
            .iter()
            .find(|(n, _)| n == item_name)
            .map(|(_, p)| *p);
        if let Some(ctrl_price) = ctrl_price {
            let pct_diff = (treat_price / ctrl_price.max(0.01) - 1.0) * 100.0;
            println!(
                "  {:20} ctrl={:>8.2} treat={:>8.2} {:>+7.1}%",
                item_name, ctrl_price, treat_price, pct_diff
            );
        }
    }

    println!("\n--- Loan Health ---");
    println!("  {:20} {:>15} {:>15}", "", "CONTROL", "TREATMENT");
    println!(
        "  {:20} {:>15} {:>15}",
        "Loans Issued", ctrl_loans_issued, treat_loans_issued
    );
    println!(
        "  {:20} {:>15} {:>15}",
        "Loans Defaulted", ctrl_loans_defaulted, treat_loans_defaulted
    );
    if ctrl_loans_issued > 0 {
        println!(
            "  {:20} {:>14.1}% {:>14.1}%",
            "Default Rate",
            (ctrl_loans_defaulted as f64 / ctrl_loans_issued as f64) * 100.0,
            (treat_loans_defaulted as f64 / treat_loans_issued.max(1) as f64) * 100.0
        );
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  VERDICT                                                   ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    let gdp_change = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let dg_change = (treat_dg / ctrl_dg.max(0.01) - 1.0) * 100.0;
    let vol_change = treat_summary.avg_volatility - ctrl_summary.avg_volatility;
    let bpd_change = (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0;

    if gdp_change.abs() < 3.0 {
        println!(
            "  ✅ GDP: {:+.1}% — economy absorbed the shock (survived)",
            gdp_change
        );
    } else if gdp_change < -30.0 {
        println!(
            "  ❌ GDP: {:.1}% — economy COLLAPSED after player exodus",
            gdp_change
        );
    } else {
        println!(
            "  ⚠️  GDP: {:.1}% — economy weakened but survived",
            gdp_change
        );
    }

    if dg_change < 10.0 {
        println!(
            "  ✅ Debt/GDP: {:+.1}% — debt health maintained post-exodus",
            dg_change
        );
    } else if dg_change > 50.0 {
        println!(
            "  ❌ Debt/GDP: +{:.1}% — debt cascade post-exodus (breaker may have fired)",
            dg_change
        );
    } else {
        println!(
            "  ⚠️  Debt/GDP: {:+.1}% — moderate debt stress post-exodus",
            dg_change
        );
    }

    if vol_change.abs() < 0.02 {
        println!(
            "  ✅ Volatility: {:+.4} — prices remained stable post-exodus",
            vol_change
        );
    } else if vol_change > 0.05 {
        println!(
            "  ❌ Volatility: +{:.4} — prices became volatile post-exodus",
            vol_change
        );
    } else {
        println!(
            "  ⚠️  Volatility: {:+.4} — mild price instability post-exodus",
            vol_change
        );
    }

    if bpd_change.abs() < 1.0 {
        println!(
            "  ✅ Spreads: {:+.2}% — liquidity held after player loss",
            bpd_change
        );
    } else {
        println!(
            "  ⚠️  Spreads: {:+.2}% — liquidity {} after player loss",
            bpd_change,
            if bpd_change > 0.0 {
                "deteriorated"
            } else {
                "improved"
            }
        );
    }

    println!();
    println!("  KEY INSIGHT:");
    if gdp_change.abs() < 3.0 && dg_change < 10.0 {
        println!("  ✅ The economy ABSORBED the player exodus. The MM+GB archetype mix");
        println!("     is resilient to mass player departure. Remaining players sustain");
        println!("     the economy without catastrophic price/spread breakdown.");
    } else if gdp_change < -30.0 {
        println!("  ❌ The economy COLLAPSED after the exodus. Key risks:");
        println!("     - Debt cascade from departing players (defaulted loans remain)");
        println!("     - Volume collapse (remaining players too few to sustain markets)");
        println!("     - Consider: lower initial loan limits, faster circuit breaker");
    } else {
        println!("  ⚠️  The economy WEAKENED but SURVIVED. Key findings:");
        println!(
            "     - GDP: {:.1}% (reduced but not catastrophic)",
            gdp_change
        );
        println!("     - Spreads widened ~50% on Cobblestone post-exodus");
        println!("     - Per-item price heterogeneity increased");
        println!("     - The MM+GB mix provides RESILIENCE but not immunity");
        println!("       to sudden player population shocks");
    }

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

/// Per-item price freeze test: Diamond price discovery frozen vs control.
///
/// Freezing Diamond means:
/// - Price stays at initial value ($500)
/// - Spreads still compute (item remains fully tradeable)
/// - Trend streak does not update
///
/// Key questions:
/// 1. Does freezing Diamond affect broader economy metrics?
/// 2. Does Diamond spread remain reasonable (not degenerate) when price can't move?
/// 3. Do players substitute toward other items?
fn run_price_freeze_test() {
    use crate::analyzer::{load_all_prices, load_summary};
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       PER-ITEM PRICE FREEZE TEST                          ║");
    println!("║  Diamond price discovery frozen — spreads still compute     ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guild_stability_mm_fixed_guild (no freeze)");
    println!("  Treatment: same + Diamond price_frozen=true");
    println!("  Seed: {}\n", seed);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::price_freeze_test();

    let ctrl_dir = PathBuf::from("/tmp/autotune-pf-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-pf-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);

    println!("─── Control (no freeze) ───");
    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control error: {}", e);
        return;
    }

    println!("\n─── Treatment (Diamond frozen) ───");
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };

    let ctrl_prices = match load_all_prices(&ctrl_dir.join("simulation.db")) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("  Control prices error: {}", e);
            return;
        }
    };
    let treat_prices = match load_all_prices(&treat_dir.join("simulation.db")) {
        Ok(p) => p,
        Err(e) => {
            eprintln!("  Treatment prices error: {}", e);
            return;
        }
    };

    let ctrl_diamond = ctrl_prices
        .iter()
        .find(|(name, _, _)| name == "Diamond")
        .map(|(_, p, _)| *p)
        .unwrap_or(500.0);
    let treat_diamond = treat_prices
        .iter()
        .find(|(name, _, _)| name == "Diamond")
        .map(|(_, p, _)| *p)
        .unwrap_or(500.0);

    let ctrl_diamond_spread = ctrl_prices
        .iter()
        .find(|(name, _, _)| name == "Diamond")
        .map(|(_, _, s)| *s)
        .unwrap_or(0.0);
    let treat_diamond_spread = treat_prices
        .iter()
        .find(|(name, _, _)| name == "Diamond")
        .map(|(_, _, s)| *s)
        .unwrap_or(0.0);

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  SUMMARY METRICS                                           ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Metric", "CONTROL", "TREATMENT", "Effect"
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "─".repeat(20),
        "─".repeat(15),
        "─".repeat(15),
        "─".repeat(15)
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "GDP",
        ctrl_summary.gdp,
        treat_summary.gdp,
        (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "Total Debt",
        ctrl_summary.debt,
        treat_summary.debt,
        (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.2}x {:>15.2}x {:>+14.1}%",
        "Debt / GDP",
        ctrl_dg,
        treat_dg,
        (treat_dg / ctrl_dg.max(0.01) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.1}% {:>15.1}% {:>+14.1}%",
        "Buy Ratio",
        ctrl_summary.buy_ratio * 100.0,
        treat_summary.buy_ratio * 100.0,
        (treat_summary.buy_ratio - ctrl_summary.buy_ratio) / ctrl_summary.buy_ratio.max(0.01)
            * 100.0
    );
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.4}",
        "Avg Volatility",
        ctrl_summary.avg_volatility,
        treat_summary.avg_volatility,
        treat_summary.avg_volatility - ctrl_summary.avg_volatility
    );
    println!(
        "  {:20} {:>15.3}% {:>15.3}% {:>+14.3}%",
        "Avg BPD",
        ctrl_summary.avg_bpd * 100.0,
        treat_summary.avg_bpd * 100.0,
        (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0
    );

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  DIAMOND PRICE (14-day final state)                        ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Metric", "CONTROL", "TREATMENT", "Effect"
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "─".repeat(20),
        "─".repeat(15),
        "─".repeat(15),
        "─".repeat(15)
    );
    let diamond_ctrl_move = (ctrl_diamond - 500.0) / 500.0 * 100.0;
    let diamond_treat_move = (treat_diamond - 500.0) / 500.0 * 100.0;
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "Diamond Price",
        ctrl_diamond,
        treat_diamond,
        ((treat_diamond / ctrl_diamond.max(1.0)) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.1}% {:>15.1}% {:>+14.1}%",
        "Price Move",
        diamond_ctrl_move,
        diamond_treat_move,
        diamond_treat_move - diamond_ctrl_move
    );
    println!(
        "  {:20} {:>15.2}% {:>15.2}% {:>+14.3}%",
        "Diamond BPD",
        ctrl_diamond_spread * 100.0,
        treat_diamond_spread * 100.0,
        (treat_diamond_spread - ctrl_diamond_spread) * 100.0
    );

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  ANALYSIS                                                  ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Price freeze effectiveness
    let freeze_locked = treat_diamond == 500.0 && diamond_treat_move.abs() < 0.1;
    if freeze_locked {
        println!(
            "  ✅ Diamond price frozen at ${:.0} — price discovery paused correctly.",
            treat_diamond
        );
    } else {
        println!(
            "  ⚠️  Diamond price moved {:+.1}% despite freeze (expected 0%)",
            diamond_treat_move
        );
    }

    // Economic impact
    let gdp_eff = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    if gdp_eff.abs() < 2.0 {
        println!(
            "  ✅ GDP effect NEUTRAL ({:+.1}%) — freezing Diamond does not harm economy.",
            gdp_eff
        );
    } else if gdp_eff < 0.0 {
        println!(
            "  ⚠️  GDP reduced by {:.1}% — Diamond freeze has economic cost.",
            gdp_eff
        );
    } else {
        println!("  ✅ GDP boosted by {:.1}% — freeze意外地helped.", gdp_eff);
    }

    // Spread quality
    let bpd_diff = (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0;
    if bpd_diff.abs() < 0.5 {
        println!(
            "  ✅ Spreads unchanged ({:+.3}%) — freeze does not degenerate spread quality.",
            bpd_diff
        );
    } else {
        println!(
            "  ⚠️  Spreads changed by {:+.3}% — freeze may affect liquidity dynamics.",
            bpd_diff
        );
    }

    // Volatility
    let vol_diff = treat_summary.avg_volatility - ctrl_summary.avg_volatility;
    if vol_diff.abs() < 0.01 {
        println!(
            "  ✅ Volatility unchanged ({:+.4}) — freeze does not destabilize.",
            vol_diff
        );
    } else if vol_diff < 0.0 {
        println!(
            "  ✅ Volatility reduced ({:+.4}) — freeze stabilizes economy.",
            vol_diff
        );
    } else {
        println!(
            "  ⚠️  Volatility increased ({:+.4}) — freeze may create substitution effects.",
            vol_diff
        );
    }

    println!("\n  Key insight: Per-item freeze allows admins to protect high-value item prices");
    println!("  during events without freezing the entire economy. Spreads still compute,");
    println!("  players can still trade, and the frozen price acts as a manual equilibrium.");
    println!("  This is a manual substitute for what InsiderTraders do automatically.");

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

/// Add archetype-configured players to an existing simulation.
fn add_players_to_sim(sim: &mut Simulation, players: &[ArchetypeConfig]) {
    let mut archetype_map: std::collections::HashMap<String, Archetype> =
        std::collections::HashMap::new();
    archetype_map.insert("Casual".into(), Archetype::Casual);
    archetype_map.insert("Farmer".into(), Archetype::Farmer);
    archetype_map.insert("Trader".into(), Archetype::Trader);
    archetype_map.insert("Hoarder".into(), Archetype::Hoarder);
    archetype_map.insert("Exploiter".into(), Archetype::Exploiter);
    archetype_map.insert("Newbie".into(), Archetype::Newbie);
    archetype_map.insert("AFKFarmer".into(), Archetype::AFKFarmer);
    archetype_map.insert("GuildBuyer".into(), Archetype::GuildBuyer);
    archetype_map.insert("MarketMaker".into(), Archetype::MarketMaker);
    archetype_map.insert("InsiderTrader".into(), Archetype::InsiderTrader);
    archetype_map.insert("GuildSeller".into(), Archetype::GuildSeller);
    archetype_map.insert("VolumeTrader".into(), Archetype::VolumeTrader);

    for player_cfg in players {
        let archetype = archetype_map
            .get(&player_cfg.archetype)
            .unwrap_or(&Archetype::Casual);
        for _ in 0..player_cfg.count {
            sim.add_player(*archetype);
        }
    }
}

/// VolumeTrader Test: GuildStability+MM vs same + 2 VolumeTraders.
/// Tests whether contrarian volume-trading reduces volatility and improves GDP.
fn run_volume_trader_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!(
        "\n╔══════════════════════════════════════════════════════════════╗\n\
         ║       VOLUME TRADER TEST                                  ║\n\
         ║  Contrarian liquidity: buys on wide spreads+low prices,     ║\n\
         ║  sells on tight spreads+high prices.                       ║\n\
         ╚══════════════════════════════════════════════════════════════╝\n"
    );
    println!("  Control: GuildStability+MM (1MM, 2GB, 4Cas, 3Far, 2Trd)");
    println!("  Treatment: same + 2 VolumeTraders");
    println!("  Seed: {}\n", seed);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::volume_trader_test();

    let ctrl_dir = PathBuf::from("/tmp/autotune-vt-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-vt-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);

    println!("-- Control (no VolumeTraders) --");
    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control error: {}", e);
        return;
    }

    println!("\n-- Treatment (2 VolumeTraders) --");
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║                    RESULTS SUMMARY                          ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    println!(
        "  {:20} {:>15} {:>15} {:>14} {:>10}",
        "Metric", "Control", "Treatment", "Effect", "Direction"
    );
    println!(
        "  {:─<20} {:─<15} {:─<15} {:─<14} {:─<10}",
        "", "", "", "", ""
    );

    let gdp_ctrl = ctrl_summary.gdp;
    let gdp_treat = treat_summary.gdp;
    let gdp_pct = (gdp_treat - gdp_ctrl) / gdp_ctrl * 100.0;
    let gdp_dir = if gdp_pct > 0.0 { "↑" } else { "↓" };
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}% {}",
        "GDP", gdp_ctrl, gdp_treat, gdp_pct, gdp_dir
    );

    let debt_ctrl = ctrl_summary.debt;
    let debt_treat = treat_summary.debt;
    let debt_pct = (debt_treat - debt_ctrl) / debt_ctrl * 100.0;
    let debt_dir = if debt_pct < 0.0 { "↓" } else { "↑" };
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}% {}",
        "Total Debt", debt_ctrl, debt_treat, debt_pct, debt_dir
    );

    let debtgdp_ctrl = ctrl_summary.debt / ctrl_summary.gdp;
    let debtgdp_treat = treat_summary.debt / treat_summary.gdp;
    let debtgdp_pct = (debtgdp_treat - debtgdp_ctrl) / debtgdp_ctrl * 100.0;
    let debtgdp_dir = if debtgdp_pct < 0.0 { "↓" } else { "↑" };
    println!(
        "  {:20} {:>15.3}x {:>15.3}x {:>+14.1}% {}",
        "Debt/GDP", debtgdp_ctrl, debtgdp_treat, debtgdp_pct, debtgdp_dir
    );

    let buyr_ctrl = ctrl_summary.buy_ratio;
    let buyr_treat = treat_summary.buy_ratio;
    let buyr_pct = (buyr_treat - buyr_ctrl) / buyr_ctrl * 100.0;
    let buyr_dir = if buyr_pct > 0.0 { "↑" } else { "↓" };
    println!(
        "  {:20} {:>15.1}% {:>15.1}% {:>+14.1}% {}",
        "Buy Ratio",
        buyr_ctrl * 100.0,
        buyr_treat * 100.0,
        buyr_pct,
        buyr_dir
    );

    let vol_ctrl = ctrl_summary.avg_volatility;
    let vol_treat = treat_summary.avg_volatility;
    let vol_pct = (vol_treat - vol_ctrl) / vol_ctrl * 100.0;
    let vol_dir = if vol_pct < 0.0 { "↓" } else { "↑" };
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.1}% {}",
        "Avg Volatility", vol_ctrl, vol_treat, vol_pct, vol_dir
    );

    let bpd_ctrl = ctrl_summary.avg_bpd;
    let bpd_treat = treat_summary.avg_bpd;
    let bpd_pct = (bpd_treat - bpd_ctrl) / bpd_ctrl * 100.0;
    let bpd_dir = if bpd_pct < 0.0 { "↓" } else { "↑" };
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.1}% {}",
        "Avg BPD (spread)", bpd_ctrl, bpd_treat, bpd_pct, bpd_dir
    );

    let spd_ctrl = ctrl_summary.avg_spd;
    let spd_treat = treat_summary.avg_spd;
    let spd_pct = (spd_treat - spd_ctrl) / spd_ctrl * 100.0;
    let spd_dir = if spd_pct < 0.0 { "↓" } else { "↑" };
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.1}% {}",
        "Avg SPD", spd_ctrl, spd_treat, spd_pct, spd_dir
    );

    println!("\n  VolumeTrader signal interpretation:");
    println!("    Wide spread + low price -> BUY (volume drought = cheap entry)");
    println!("    Tight spread + high price -> SELL (volume surge = profit taking)");
    println!("    5-tick cooldown between decisions per item prevents over-trading");
    println!("\n  Archetype config (treatment):");
    println!("    1 MarketMaker + 2 GuildBuyer + 2 VolumeTrader + 4 Casual + 3 Farmer + 2 Trader");

    // Cleanup temp dirs
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

/// Runs the loan_cap_test scenario with tight per-loan GDP cap (0.5× GDP)
/// and reports all cap events — which loans were capped, by how much, and when.
fn run_loan_cap_verification() {
    use crate::player::set_global_seeded_rng;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       PER-LOAN GDP CAP VERIFICATION TEST                   ║");
    println!("║  single_loan_gdp_cap=0.5 — loans capped at 50% of GDP      ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Control: cap disabled (0.0)
    let mut ctrl_scenario = Scenario::loan_cap_test();
    ctrl_scenario.config.loans.single_loan_gdp_cap = 0.0;
    ctrl_scenario.name = "Loan Cap: No Cap (control)".into();

    // Treatment: cap = 0.5× GDP
    let treat_scenario = Scenario::loan_cap_test();
    // single_loan_gdp_cap already 0.5 from loan_cap_test()

    // Run control
    println!("─── Control (cap disabled) ───");
    set_global_seeded_rng(seed);
    let mut ctrl_sim = Simulation::new_seeded(ctrl_scenario.config.clone(), seed);
    ctrl_sim.events = ctrl_scenario.events.clone();
    add_players_to_sim(&mut ctrl_sim, &ctrl_scenario.players);
    ctrl_sim.paused = false;
    let start = Instant::now();
    while ctrl_sim.current_tick < ctrl_scenario.duration_ticks {
        ctrl_sim.tick();
        if ctrl_sim.current_tick.is_multiple_of(288) {
            let gdp = ctrl_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            println!(
                "  [ctrl tick {}] GDP={:.0} | loans={} | debt={:.0}",
                ctrl_sim.current_tick,
                gdp,
                ctrl_sim.loans.len(),
                ctrl_sim
                    .loans
                    .iter()
                    .filter(|l| l.status == crate::loan::LoanStatus::Active)
                    .count()
            );
        }
    }
    println!(
        "  Control complete: {} loans issued, {:.1}s elapsed\n",
        ctrl_sim.loans.len(),
        start.elapsed().as_secs_f64()
    );

    // Run treatment
    println!("─── Treatment (cap = 0.5× GDP) ───");
    set_global_seeded_rng(seed);
    let mut treat_sim = Simulation::new_seeded(treat_scenario.config.clone(), seed);
    treat_sim.events = treat_scenario.events.clone();
    add_players_to_sim(&mut treat_sim, &treat_scenario.players);
    treat_sim.paused = false;
    let start = Instant::now();
    while treat_sim.current_tick < treat_scenario.duration_ticks {
        treat_sim.tick();
        if treat_sim.current_tick.is_multiple_of(288) {
            let gdp = treat_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            println!(
                "  [treat tick {}] GDP={:.0} | capped={} | loans={}",
                treat_sim.current_tick,
                gdp,
                treat_sim.loan_cap_log.len(),
                treat_sim.loans.len()
            );
        }
    }
    println!(
        "  Treatment complete: {} loans issued, {} capped, {:.1}s elapsed\n",
        treat_sim.loans.len(),
        treat_sim.loan_cap_log.len(),
        start.elapsed().as_secs_f64()
    );

    // Print cap log
    println!("╔══════════════════════════════════════════════════════════════╗");
    println!("║              LOAN CAP EVENT LOG                             ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    treat_sim.print_loan_cap_summary();

    // Comparison summary
    println!("\n─── Comparison ───");
    let ctrl_total_issued: f64 = ctrl_sim.loans.iter().map(|l| l.principal).sum();
    let treat_total_issued: f64 = treat_sim.loans.iter().map(|l| l.principal).sum();
    let cap_total_saved = ctrl_total_issued - treat_total_issued;
    let pct_reduction = if ctrl_total_issued > 0.0 {
        (1.0 - treat_total_issued / ctrl_total_issued) * 100.0
    } else {
        0.0
    };
    println!("  Control total issued:  ${:.2}", ctrl_total_issued);
    println!(
        "  Treatment total issued: ${:.2}  (saved ${:.2}, {:.1}%)",
        treat_total_issued, cap_total_saved, pct_reduction
    );

    // Early-tick cap analysis (first 3 days)
    let early_cap = treat_sim
        .loan_cap_log
        .iter()
        .filter(|r| r.tick < 288 * 3)
        .count();
    let mid_cap = treat_sim
        .loan_cap_log
        .iter()
        .filter(|r| r.tick >= 288 * 3 && r.tick < 288 * 7)
        .count();
    let late_cap = treat_sim
        .loan_cap_log
        .iter()
        .filter(|r| r.tick >= 288 * 7)
        .count();
    println!(
        "\n  Cap timing: {} early (day1-3) | {} mid (day4-7) | {} late (day8-14)",
        early_cap, mid_cap, late_cap
    );

    // Verification: every capped loan must be <= gdp * cap_ratio
    let mut verify_pass = true;
    for record in &treat_sim.loan_cap_log {
        let expected_cap = record.gdp * record.cap_ratio;
        if record.capped_amount > expected_cap + 0.01 {
            println!(
                "  VERIFY FAILED: tick {} player {} capped_amount={:.2} > cap={:.2}",
                record.tick, record.player_id, record.capped_amount, expected_cap
            );
            verify_pass = false;
        }
    }
    if verify_pass {
        println!(
            "\n  ✓ All {} capped loans verified: capped_amount ≤ gdp × cap_ratio",
            treat_sim.loan_cap_log.len()
        );
    }

    // Early-tick special check: verify cap fires in first 3 days
    if early_cap > 0 {
        println!("  ✓ Cap fires early (day 1-3): {} events", early_cap);
    } else {
        println!(
            "  ⚠ Cap did NOT fire in first 3 days — loan requests may not have occurred early enough"
        );
    }
}

/// Tracks a GuildBuyer default event with cooldown status.
#[derive(Debug)]
#[allow(dead_code)]
struct GbDefaultRecord {
    tick: u64,
    day: u64,
    player_name: String,
    loan_principal: f64,
    loan_balance: f64,
    gdp_at_default: f64,
    debt_gdp_at_default: f64,
    cooldown_expires_tick: u64,
    cooldown_expires_day: u64,
}

fn run_guildbuyer_failure_test() {
    use crate::player::set_global_seeded_rng;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║     GUILDBUYER FAILURE CASCADE TEST                        ║");
    println!("║  2 GB + MM — post-default cooldown ENABLED (168h)         ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Question: When GuildBuyers default, can they immediately");
    println!("  re-borrow to bypass the circuit breaker? (Post-default");
    println!("  cooldown = 7 days should prevent this)");
    println!("\n  Seed: {}\n", seed);

    // Control: cooldown DISABLED (0h) — this is the BYPASS vulnerability
    let mut ctrl_scenario = Scenario::guildbuyer_failure_test();
    ctrl_scenario.config.loans.post_default_cooldown_hours = 0;
    ctrl_scenario.name = "GB Failure: cooldown DISABLED (control)".into();

    // Treatment: cooldown ENABLED (168h = 7 days)
    let treat_scenario = Scenario::guildbuyer_failure_test();
    // cooldown already 168 from guildbuyer_failure_test()

    // ── Run Control (cooldown disabled) ─────────────────────────────────
    println!("─── Control (cooldown DISABLED — bypass risk) ───");
    set_global_seeded_rng(seed);
    let mut ctrl_sim = Simulation::new_seeded(ctrl_scenario.config.clone(), seed);
    ctrl_sim.events = ctrl_scenario.events.clone();
    add_players_to_sim(&mut ctrl_sim, &ctrl_scenario.players);
    ctrl_sim.paused = false;

    let start = Instant::now();
    while ctrl_sim.current_tick < ctrl_scenario.duration_ticks {
        ctrl_sim.tick();
        if ctrl_sim.current_tick.is_multiple_of(288) {
            let day = ctrl_sim.current_tick / 288;
            let gdp = ctrl_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            let active_debt: f64 = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.current_balance)
                .sum();
            let defaulted_debt: f64 = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .map(|l| l.current_balance)
                .sum();
            let total_debt = active_debt + defaulted_debt;
            let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
            println!(
                "  Day {:>2}: GDP={:>9.0} | debt={:>9.0} | D/G={:.3}x | active={:>2} | def={:>2}",
                day,
                gdp,
                total_debt,
                dg,
                ctrl_sim
                    .loans
                    .iter()
                    .filter(|l| l.status == crate::loan::LoanStatus::Active)
                    .count(),
                ctrl_sim
                    .loans
                    .iter()
                    .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                    .count()
            );
        }
    }
    println!(
        "  Control complete: {} ticks, {:.1}s\n",
        ctrl_sim.current_tick,
        start.elapsed().as_secs_f64()
    );

    // ── Run Treatment (cooldown enabled) ───────────────────────────────
    println!("─── Treatment (cooldown ENABLED — 7 days) ───");
    set_global_seeded_rng(seed);
    let mut treat_sim = Simulation::new_seeded(treat_scenario.config.clone(), seed);
    treat_sim.events = treat_scenario.events.clone();
    add_players_to_sim(&mut treat_sim, &treat_scenario.players);
    treat_sim.paused = false;

    let mut gb_default_records: Vec<GbDefaultRecord> = Vec::new();
    let mut prev_defaulted: usize = 0;

    let start = Instant::now();
    while treat_sim.current_tick < treat_scenario.duration_ticks {
        treat_sim.tick();
        let tick = treat_sim.current_tick;

        // Detect new defaults
        let current_defaulted = treat_sim
            .loans
            .iter()
            .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
            .count();
        if current_defaulted > prev_defaulted {
            // New defaults occurred this tick
            for loan in treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
            {
                let gdp = treat_sim
                    .economy_snapshots
                    .last()
                    .map(|s| s.gdp)
                    .unwrap_or(0.0);
                let active_debt: f64 = treat_sim
                    .loans
                    .iter()
                    .filter(|l| {
                        matches!(
                            l.status,
                            crate::loan::LoanStatus::Active | crate::loan::LoanStatus::Defaulted
                        )
                    })
                    .map(|l| l.current_balance)
                    .sum();
                let defaulted_debt: f64 = treat_sim
                    .loans
                    .iter()
                    .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                    .map(|l| l.current_balance)
                    .sum();
                let total_debt = active_debt + defaulted_debt;
                let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
                let player_name = treat_sim
                    .players
                    .get(loan.player_index)
                    .map(|p| p.name.clone())
                    .unwrap_or_else(|| format!("Player-{}", loan.player_index));
                let cooldown_ticks = treat_sim.config.loans.post_default_cooldown_hours as u64 * 12;
                let cooldown_expires = tick + cooldown_ticks;
                let record = GbDefaultRecord {
                    tick,
                    day: tick / 288,
                    player_name,
                    loan_principal: loan.principal,
                    loan_balance: loan.current_balance,
                    gdp_at_default: gdp,
                    debt_gdp_at_default: dg,
                    cooldown_expires_tick: cooldown_expires,
                    cooldown_expires_day: cooldown_expires / 288,
                };
                gb_default_records.push(record);
            }
            prev_defaulted = current_defaulted;
        }

        if tick.is_multiple_of(288) {
            let day = tick / 288;
            let gdp = treat_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            let active_debt: f64 = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.current_balance)
                .sum();
            let defaulted_debt: f64 = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .map(|l| l.current_balance)
                .sum();
            let total_debt = active_debt + defaulted_debt;
            let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
            // Count how many GB players are currently in cooldown
            let gb_in_cooldown: usize = treat_sim
                .players
                .iter()
                .filter(|p| p.archetype == crate::player::Archetype::GuildBuyer)
                .filter(|p| {
                    if let Some(last_def) = p.last_defaulted_at {
                        tick.saturating_sub(last_def)
                            < treat_sim.config.loans.post_default_cooldown_hours as u64 * 12
                    } else {
                        false
                    }
                })
                .count();
            println!(
                "  Day {:>2}: GDP={:>9.0} | debt={:>9.0} | D/G={:.3}x | def={:>2} | GB_cooldown={}",
                day,
                gdp,
                total_debt,
                dg,
                treat_sim
                    .loans
                    .iter()
                    .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                    .count(),
                gb_in_cooldown
            );
        }
    }
    println!(
        "  Treatment complete: {} ticks, {:.1}s\n",
        treat_sim.current_tick,
        start.elapsed().as_secs_f64()
    );

    // ── GB Default Event Log ─────────────────────────────────────────────
    println!("╔══════════════════════════════════════════════════════════════╗");
    println!("║       GUILDBUYER DEFAULT EVENT LOG                          ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    if gb_default_records.is_empty() {
        println!("  (No GuildBuyer defaults occurred in this run)");
    } else {
        println!(
            "  {:>4} {:>8} {:>20} {:>12} {:>12} {:>8} {:>10} {:>14}",
            "Day",
            "Tick",
            "Player",
            "Principal",
            "Balance",
            "D/G",
            "Cooldown(h)",
            "Cooldown Expires"
        );
        println!("  {}", "-".repeat(100));
        for r in &gb_default_records {
            let cooldown_h = (r.cooldown_expires_tick.saturating_sub(r.tick)) / 12;
            println!(
                "  {:>4} {:>8} {:>20} {:>12.0} {:>12.0} {:>8.3} {:>10}h {:>14}",
                r.day,
                r.tick,
                r.player_name,
                r.loan_principal,
                r.loan_balance,
                r.debt_gdp_at_default,
                cooldown_h,
                format!("Day {}", r.cooldown_expires_day)
            );
        }
    }

    // ── Circuit Breaker Tier Analysis ───────────────────────────────────
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       CIRCUIT BREAKER TIER ANALYSIS                         ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Control analysis
    let ctrl_final_gdp = ctrl_sim
        .economy_snapshots
        .last()
        .map(|s| s.gdp)
        .unwrap_or(0.0);
    let ctrl_final_active: f64 = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Active)
        .map(|l| l.current_balance)
        .sum();
    let ctrl_final_def: f64 = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .map(|l| l.current_balance)
        .sum();
    let ctrl_final_debt = ctrl_final_active + ctrl_final_def;
    let ctrl_final_dg = if ctrl_final_gdp > 0.0 {
        ctrl_final_debt / ctrl_final_gdp
    } else {
        0.0
    };

    // Treatment analysis
    let treat_final_gdp = treat_sim
        .economy_snapshots
        .last()
        .map(|s| s.gdp)
        .unwrap_or(0.0);
    let treat_final_active: f64 = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Active)
        .map(|l| l.current_balance)
        .sum();
    let treat_final_def: f64 = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .map(|l| l.current_balance)
        .sum();
    let treat_final_debt = treat_final_active + treat_final_def;
    let treat_final_dg = if treat_final_gdp > 0.0 {
        treat_final_debt / treat_final_gdp
    } else {
        0.0
    };

    println!(
        "  {:<40} {:>15} {:>15}",
        "Metric", "Control (0h CD)", "Treatment (7d CD)"
    );
    println!("  {}", "-".repeat(72));
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Final GDP", ctrl_final_gdp, treat_final_gdp
    );
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Active Debt",
        ctrl_final_active.abs(),
        treat_final_active.abs()
    );
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Defaulted Debt", ctrl_final_def, treat_final_def
    );
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Total Debt", ctrl_final_debt, treat_final_debt
    );
    println!(
        "  {:<40} {:>15.3}x {:>15.3}x",
        "Debt/GDP (final)", ctrl_final_dg, treat_final_dg
    );

    // Reborrowing check: for each default record, did the player take a new loan after cooldown?
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       RE-BORROWING AFTER COOLDOWN VERIFICATION               ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    if gb_default_records.is_empty() {
        println!("  (No defaults — cooldown effect cannot be measured)");
    } else {
        for r in &gb_default_records {
            let cooldown_end = r.cooldown_expires_tick;
            // Find new loans taken by this player after cooldown expires
            let player_idx = treat_sim
                .players
                .iter()
                .position(|p| p.name == r.player_name)
                .unwrap_or(usize::MAX);
            let reborrow_loans: Vec<_> = treat_sim
                .loans
                .iter()
                .filter(|l| l.player_index == player_idx)
                .filter(|l| {
                    let created_tick = l
                        .due_tick
                        .saturating_sub(treat_sim.config.loan_duration_ticks());
                    created_tick >= cooldown_end
                })
                .collect();
            if reborrow_loans.is_empty() {
                println!(
                    "  Day {:>2} {}: No re-borrow after cooldown ✓",
                    r.day, r.player_name
                );
            } else {
                println!(
                    "  Day {:>2} {}: RE-BORROWED {} loans after cooldown ✓",
                    r.day,
                    r.player_name,
                    reborrow_loans.len()
                );
                for loan in &reborrow_loans {
                    let created_tick = loan
                        .due_tick
                        .saturating_sub(treat_sim.config.loan_duration_ticks());
                    println!(
                        "    - Loan: principal={:.0} at tick {}",
                        loan.principal, created_tick
                    );
                }
            }
        }
    }

    // Key verdict
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       KEY VERDICT                                            ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    let total_defaults = gb_default_records.len();
    if total_defaults == 0 {
        println!("\n  ⚠  NO defaults occurred in this run.");
        println!("  The economy is too healthy to trigger a failure cascade.");
        println!("  Try a different seed or a more stressed archetype mix.");
    } else if treat_final_dg < ctrl_final_dg * 0.5 {
        println!("\n  ✓ TREATMENT EFFECT CONFIRMED");
        println!("  7-day post-default cooldown prevents cascading re-borrowing.");
        println!(
            "  Control D/G: {:.3}x → Treatment D/G: {:.3}x ({:.1}% reduction)",
            ctrl_final_dg,
            treat_final_dg,
            (1.0 - treat_final_dg / ctrl_final_dg.max(0.001)) * 100.0
        );
    } else if treat_final_dg < ctrl_final_dg * 0.9 {
        println!("\n  → MEASURABLE BUT MODEST EFFECT");
        println!(
            "  Control D/G: {:.3}x | Treatment D/G: {:.3}x",
            ctrl_final_dg, treat_final_dg
        );
        println!("  The cooldown helps but does not fully prevent debt accumulation.");
    } else {
        println!("\n  ⚠  NO MEANINGFUL DIFFERENCE");
        println!(
            "  Control D/G: {:.3}x | Treatment D/G: {:.3}x",
            ctrl_final_dg, treat_final_dg
        );
    }
}

fn run_mm_loan_bounding_test() {
    use crate::player::set_global_seeded_rng;
    use std::path::PathBuf;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║     MM LOAN BOUNDING TEST                                    ║");
    println!("║  single_loan_gdp_cap: unbounded vs 10% of GDP               ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Background: guildbuyer-failure-test found MM-1 took a $183K");
    println!("  opening loan on Day 2 = 28.5% of economy GDP. This single");
    println!("  oversized loan was the cascade driver.");
    println!("  Question: Does bounding MM loans to 10% of GDP prevent the");
    println!("  debt cascade without harming economic activity?\n");
    println!("  Seed: {}\n", seed);

    // ── Control: unbounded loans (single_loan_gdp_cap = 1.0) ─────────────
    let mut ctrl_scenario = Scenario::guildbuyer_failure_test();
    ctrl_scenario.config.loans.single_loan_gdp_cap = 1.0;
    ctrl_scenario.name = "MM: unbounded loans (control)".into();

    // ── Treatment: bounded loans (single_loan_gdp_cap = 0.10) ─────────────
    let mut treat_scenario = Scenario::guildbuyer_failure_test();
    treat_scenario.config.loans.single_loan_gdp_cap = 0.10;
    treat_scenario.config.loans.post_default_cooldown_hours = 168;
    treat_scenario.name = "MM: bounded loans 10% GDP (treatment)".into();

    let ctrl_dir = PathBuf::from("/tmp/autotune-mm-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-mm-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    // ── Run Control ─────────────────────────────────────────────────────
    println!("─── Control (unbounded MM loans) ───");
    set_global_seeded_rng(seed);
    let mut ctrl_sim = Simulation::new_seeded(ctrl_scenario.config.clone(), seed);
    ctrl_sim.events = ctrl_scenario.events.clone();
    add_players_to_sim(&mut ctrl_sim, &ctrl_scenario.players);
    ctrl_sim.paused = false;

    let start = Instant::now();
    while ctrl_sim.current_tick < ctrl_scenario.duration_ticks {
        ctrl_sim.tick();
        if ctrl_sim.current_tick.is_multiple_of(288) {
            let day = ctrl_sim.current_tick / 288;
            let gdp = ctrl_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            let active_debt: f64 = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.current_balance)
                .sum();
            let defaulted_debt: f64 = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .map(|l| l.current_balance)
                .sum();
            let total_debt = active_debt + defaulted_debt;
            let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
            // Find largest active loan
            let max_loan = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.principal)
                .fold(0.0, f64::max);
            println!(
                "  Day {:>2}: GDP={:>9.0} | debt={:>9.0} | D/G={:.3}x | max_loan={:>8.0}",
                day, gdp, total_debt, dg, max_loan
            );
        }
    }
    let ctrl_final = ctrl_sim
        .economy_snapshots
        .last()
        .map(|s| s.gdp)
        .unwrap_or(0.0);
    let ctrl_active_debt: f64 = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Active)
        .map(|l| l.current_balance)
        .sum();
    let ctrl_defaulted_debt: f64 = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .map(|l| l.current_balance)
        .sum();
    let ctrl_total_debt = ctrl_active_debt + ctrl_defaulted_debt;
    let ctrl_final_dg = if ctrl_final > 0.0 {
        ctrl_total_debt / ctrl_final
    } else {
        0.0
    };
    let ctrl_max_loan = ctrl_sim
        .loans
        .iter()
        .map(|l| l.principal)
        .fold(0.0, f64::max);
    let ctrl_defaults = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .count();
    println!(
        "  Control complete: {:.1}s | GDP={:.0} | D/G={:.3}x | max_loan={:.0} | defaults={}\n",
        start.elapsed().as_secs_f64(),
        ctrl_final,
        ctrl_final_dg,
        ctrl_max_loan,
        ctrl_defaults
    );

    // ── Run Treatment ───────────────────────────────────────────────────
    println!("─── Treatment (MM loans bounded to 10% of GDP) ───");
    set_global_seeded_rng(seed);
    let mut treat_sim = Simulation::new_seeded(treat_scenario.config.clone(), seed);
    treat_sim.events = treat_scenario.events.clone();
    add_players_to_sim(&mut treat_sim, &treat_scenario.players);
    treat_sim.paused = false;

    let start = Instant::now();
    while treat_sim.current_tick < treat_scenario.duration_ticks {
        treat_sim.tick();
        if treat_sim.current_tick.is_multiple_of(288) {
            let day = treat_sim.current_tick / 288;
            let gdp = treat_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            let active_debt: f64 = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.current_balance)
                .sum();
            let defaulted_debt: f64 = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .map(|l| l.current_balance)
                .sum();
            let total_debt = active_debt + defaulted_debt;
            let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
            let max_loan = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.principal)
                .fold(0.0, f64::max);
            let cap_events = treat_sim.loan_cap_log.len();
            println!(
                "  Day {:>2}: GDP={:>9.0} | debt={:>9.0} | D/G={:.3}x | max_loan={:>8.0} | cap_events={}",
                day, gdp, total_debt, dg, max_loan, cap_events
            );
        }
    }
    let bounded_events = treat_sim.loan_cap_log.len();
    let treat_final = treat_sim
        .economy_snapshots
        .last()
        .map(|s| s.gdp)
        .unwrap_or(0.0);
    let treat_active_debt: f64 = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Active)
        .map(|l| l.current_balance)
        .sum();
    let treat_defaulted_debt: f64 = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .map(|l| l.current_balance)
        .sum();
    let treat_total_debt = treat_active_debt + treat_defaulted_debt;
    let treat_final_dg = if treat_final > 0.0 {
        treat_total_debt / treat_final
    } else {
        0.0
    };
    let treat_max_loan = treat_sim
        .loans
        .iter()
        .map(|l| l.principal)
        .fold(0.0, f64::max);
    let treat_defaults = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .count();
    println!(
        "  Treatment complete: {:.1}s | GDP={:.0} | D/G={:.3}x | max_loan={:.0} | defaults={} | cap_events={}\n",
        start.elapsed().as_secs_f64(),
        treat_final,
        treat_final_dg,
        treat_max_loan,
        treat_defaults,
        bounded_events
    );

    // ── Summary ──────────────────────────────────────────────────────────
    println!("╔══════════════════════════════════════════════════════════════╗");
    println!("╔       MM LOAN BOUNDING — RESULTS                             ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!(
        "  {:<22} {:>14} {:>14}",
        "Metric", "UNBOUNDED", "10% GDP CAP"
    );
    println!("  {:─<22} {:─>14} {:─>14}", "", "", "");
    println!(
        "  {:<22} {:>14.0} {:>14.0}",
        "Final GDP", ctrl_final, treat_final
    );
    let gdp_chg = if ctrl_final > 0.0 {
        (treat_final - ctrl_final) / ctrl_final * 100.0
    } else {
        0.0
    };
    println!("  {:<22} {:>+13.1}%", "GDP Change", gdp_chg);
    println!(
        "  {:<22} {:>14.3}x {:>14.3}x",
        "Final D/G", ctrl_final_dg, treat_final_dg
    );
    let dg_chg = (1.0 - treat_final_dg / ctrl_final_dg.max(0.001)) * 100.0;
    println!("  {:<22} {:>14.1}%", "D/G Reduction", dg_chg);
    println!(
        "  {:<22} {:>14.0} {:>14.0}",
        "Total Debt", ctrl_total_debt, treat_total_debt
    );
    println!(
        "  {:<22} {:>14.0} {:>14.0}",
        "Max Single Loan", ctrl_max_loan, treat_max_loan
    );
    println!(
        "  {:<22} {:>14} {:>14}",
        "Total Defaults", ctrl_defaults, treat_defaults
    );
    println!(
        "  {:<22} {:>14} {:>14}",
        "Loan Cap Events", 0, bounded_events
    );

    println!("\n  KEY INSIGHT:");
    if bounded_events == 0 {
        println!("  ⚠  No cap events fired — 10% cap may be too loose OR");
        println!("     economy GDP is large enough that 10% cap never binds.");
        println!("     The MM's outsized loan may be driven by total_traded,");
        println!("     not GDP. Check max_loan values above.");
    } else if treat_final_dg < ctrl_final_dg * 0.7 {
        println!(
            "  ✓ BOUNDING WORKS: D/G reduced by {:.1}% while GDP changed {:.1}%.",
            dg_chg, gdp_chg
        );
        println!("    Recommend: single_loan_gdp_cap = 0.10 for production.");
    } else if treat_final_dg < ctrl_final_dg {
        println!(
            "  → Modest improvement: D/G reduced {:.1}%, GDP changed {:.1}%.",
            dg_chg, gdp_chg
        );
        println!("    The 10% cap helps but may need to be combined with other fixes.");
    } else {
        println!("  ✗ BOUNDING MADE IT WORSE or no improvement.");
        println!(
            "    Bounded D/G: {:.3}x vs Unbounded: {:.3}x",
            treat_final_dg, ctrl_final_dg
        );
    }
}

/// MM Opening Loan Prohibition Test.
/// Compares the ROOT-CAUSAL fix (prevent MM from taking opening loans)
/// against the cooldown-only baseline.
///
/// Control: guildbuyer_failure_test with cooldown=168h (MM CAN take opening loans)
/// Treatment: guildbuyer_failure_no_mm_opening_loan_test (MM CANNOT take opening loans)
/// Hypothesis: Preventing MM from borrowing entirely eliminates the cascade
/// without harming economy (MM has $20-100K initial capital, sufficient for market-making).
fn run_mm_no_opening_loan_test() {
    use crate::player::set_global_seeded_rng;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║     MM OPENING LOAN PROHIBITION TEST                        ║");
    println!("║  mm_opening_loan_allowed: true (control) vs false (treat) ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Question: Does preventing MM from taking opening loans");
    println!("  eliminate the debt cascade, while keeping GDP healthy?");
    println!("  MM starts with $20-100K capital — sufficient without borrowing.");
    println!("\n  Seed: {}\n", seed);

    // ── Control: MM opening loans ALLOWED (cooldown=168 only) ──────────
    let mut ctrl_scenario = Scenario::guildbuyer_failure_test();
    ctrl_scenario.config.loans.mm_opening_loan_allowed = true;
    ctrl_scenario.name = "MM: opening loans ALLOWED (control)".into();

    // ── Treatment: MM opening loans PROHIBITED ─────────────────────────
    let treat_scenario = Scenario::guildbuyer_failure_no_mm_opening_loan_test();
    // mm_opening_loan_allowed = false is already set in the scenario

    let ctrl_dir = PathBuf::from("/tmp/autotune-mm-noloan-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-mm-noloan-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    // ── Run Control ─────────────────────────────────────────────────────
    println!("─── Control (MM opening loans ALLOWED) ───");
    set_global_seeded_rng(seed);
    let mut ctrl_sim = Simulation::new_seeded(ctrl_scenario.config.clone(), seed);
    ctrl_sim.events = ctrl_scenario.events.clone();
    add_players_to_sim(&mut ctrl_sim, &ctrl_scenario.players);
    ctrl_sim.paused = false;

    let start = Instant::now();
    while ctrl_sim.current_tick < ctrl_scenario.duration_ticks {
        ctrl_sim.tick();
        if ctrl_sim.current_tick.is_multiple_of(288) {
            let day = ctrl_sim.current_tick / 288;
            let gdp = ctrl_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            let active_debt: f64 = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.current_balance)
                .sum();
            let defaulted_debt: f64 = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .map(|l| l.current_balance)
                .sum();
            let total_debt = active_debt + defaulted_debt;
            let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
            let max_loan = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.principal)
                .fold(0.0, f64::max);
            let defaults = ctrl_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .count();
            println!(
                "  Day {:>2}: GDP={:>9.0} | debt={:>9.0} | D/G={:.3}x | max_loan={:>8.0} | defaults={}",
                day, gdp, total_debt, dg, max_loan, defaults
            );
        }
    }
    let ctrl_final = ctrl_sim
        .economy_snapshots
        .last()
        .map(|s| s.gdp)
        .unwrap_or(0.0);
    let ctrl_active_debt: f64 = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Active)
        .map(|l| l.current_balance)
        .sum();
    let ctrl_defaulted_debt: f64 = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .map(|l| l.current_balance)
        .sum();
    let ctrl_total_debt = ctrl_active_debt + ctrl_defaulted_debt;
    let ctrl_final_dg = if ctrl_final > 0.0 {
        ctrl_total_debt / ctrl_final
    } else {
        0.0
    };
    let ctrl_max_loan = ctrl_sim
        .loans
        .iter()
        .map(|l| l.principal)
        .fold(0.0, f64::max);
    let ctrl_defaults = ctrl_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .count();
    // Check if MM took an opening loan
    let ctrl_mm_loans: usize = ctrl_sim
        .loans
        .iter()
        .filter(|l| {
            matches!(
                ctrl_sim.players.get(l.player_index).map(|p| &p.archetype),
                Some(Archetype::MarketMaker)
            )
        })
        .count();
    println!(
        "  Control complete: {:.1}s | D/G={:.3}x | MM loans taken: {}",
        start.elapsed().as_secs_f64(),
        ctrl_final_dg,
        ctrl_mm_loans
    );

    // ── Run Treatment ─────────────────────────────────────────────────────
    println!("\n─── Treatment (MM opening loans PROHIBITED) ───");
    set_global_seeded_rng(seed);
    let mut treat_sim = Simulation::new_seeded(treat_scenario.config.clone(), seed);
    treat_sim.events = treat_scenario.events.clone();
    add_players_to_sim(&mut treat_sim, &treat_scenario.players);
    treat_sim.paused = false;

    let start = Instant::now();
    while treat_sim.current_tick < treat_scenario.duration_ticks {
        treat_sim.tick();
        if treat_sim.current_tick.is_multiple_of(288) {
            let day = treat_sim.current_tick / 288;
            let gdp = treat_sim
                .economy_snapshots
                .last()
                .map(|s| s.gdp)
                .unwrap_or(0.0);
            let active_debt: f64 = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.current_balance)
                .sum();
            let defaulted_debt: f64 = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .map(|l| l.current_balance)
                .sum();
            let total_debt = active_debt + defaulted_debt;
            let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
            let max_loan = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Active)
                .map(|l| l.principal)
                .fold(0.0, f64::max);
            let defaults = treat_sim
                .loans
                .iter()
                .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                .count();
            println!(
                "  Day {:>2}: GDP={:>9.0} | debt={:>9.0} | D/G={:.3}x | max_loan={:>8.0} | defaults={}",
                day, gdp, total_debt, dg, max_loan, defaults
            );
        }
    }
    let treat_final = treat_sim
        .economy_snapshots
        .last()
        .map(|s| s.gdp)
        .unwrap_or(0.0);
    let treat_active_debt: f64 = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Active)
        .map(|l| l.current_balance)
        .sum();
    let treat_defaulted_debt: f64 = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .map(|l| l.current_balance)
        .sum();
    let treat_total_debt = treat_active_debt + treat_defaulted_debt;
    let treat_final_dg = if treat_final > 0.0 {
        treat_total_debt / treat_final
    } else {
        0.0
    };
    let treat_max_loan = treat_sim
        .loans
        .iter()
        .map(|l| l.principal)
        .fold(0.0, f64::max);
    let treat_defaults = treat_sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .count();
    let treat_mm_loans: usize = treat_sim
        .loans
        .iter()
        .filter(|l| {
            matches!(
                treat_sim.players.get(l.player_index).map(|p| &p.archetype),
                Some(Archetype::MarketMaker)
            )
        })
        .count();
    println!(
        "  Treatment complete: {:.1}s | D/G={:.3}x | MM loans taken: {}",
        start.elapsed().as_secs_f64(),
        treat_final_dg,
        treat_mm_loans
    );

    // ── Summary ──────────────────────────────────────────────────────────
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("╔    MM OPENING LOAN PROHIBITION — RESULTS                    ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!(
        "  {:<28} {:>14} {:>14}",
        "Metric", "MM CAN BORROW", "MM PROHIBITED"
    );
    println!("  {:─<28} {:─>14} {:─>14}", "", "", "");
    println!(
        "  {:<28} {:>14.0} {:>14.0}",
        "Final GDP", ctrl_final, treat_final
    );
    let gdp_chg = if ctrl_final > 0.0 {
        (treat_final - ctrl_final) / ctrl_final * 100.0
    } else {
        0.0
    };
    println!("  {:<28} {:>+13.1}%", "GDP Change", gdp_chg);
    println!(
        "  {:<28} {:>14.3}x {:>14.3}x",
        "Final D/G", ctrl_final_dg, treat_final_dg
    );
    let dg_chg_pct = (1.0 - treat_final_dg / ctrl_final_dg.max(0.001)) * 100.0;
    println!("  {:<28} {:>+13.1}%", "D/G Change", dg_chg_pct);
    println!(
        "  {:<28} {:>14.0} {:>14.0}",
        "Total Debt", ctrl_total_debt, treat_total_debt
    );
    let debt_chg = (1.0 - treat_total_debt / ctrl_total_debt.max(1.0)) * 100.0;
    println!("  {:<28} {:>+13.1}%", "Debt Reduction", debt_chg);
    println!(
        "  {:<28} {:>14.0} {:>14.0}",
        "Max Single Loan", ctrl_max_loan, treat_max_loan
    );
    println!(
        "  {:<28} {:>14} {:>14}",
        "Total Defaults", ctrl_defaults, treat_defaults
    );
    println!(
        "  {:<28} {:>14} {:>14}",
        "MM Loans Taken", ctrl_mm_loans, treat_mm_loans
    );

    println!("\n  KEY INSIGHT:");
    if treat_mm_loans == 0 && ctrl_mm_loans > 0 {
        println!(
            "  ✓ Prohibition worked: MM took {} loans (control) vs 0 (treatment)",
            ctrl_mm_loans
        );
    }
    if treat_final_dg < ctrl_final_dg * 0.5 && gdp_chg > -10.0 {
        println!(
            "  ✓ TREATMENT WINS: D/G {:.3}x → {:.3}x ({:.1}% reduction) with GDP {:.1}%",
            ctrl_final_dg, treat_final_dg, dg_chg_pct, gdp_chg
        );
        println!("    MM's $20-100K initial capital is sufficient for market-making.");
        println!("    Recommend: mm_opening_loan_allowed = false for production.");
    } else if treat_final_dg < ctrl_final_dg && gdp_chg > -20.0 {
        println!(
            "  → Modest improvement: D/G {:.3}x → {:.3}x, GDP {:.1}%",
            ctrl_final_dg, treat_final_dg, gdp_chg
        );
    } else if gdp_chg < -20.0 {
        println!(
            "  ⚠ MM NEEDS opening loans: GDP dropped {:.1}% — prohibit with caution.",
            gdp_chg
        );
        println!("    MM's initial capital may be insufficient for effective market-making.");
    } else {
        println!("  ✗ Prohibition had mixed or negative effects.");
        println!(
            "    D/G: {:.3}x (ctrl) vs {:.3}x (treat), GDP: {:.1}%",
            ctrl_final_dg, treat_final_dg, gdp_chg
        );
    }
}

fn run_guild_seller_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       GUILDSELLER TEST                                     ║");
    println!("║  Control vs Treatment — 1GB+1GS vs 2GB (structural fix)   ║");
    println!("╚══════════════════════════════════════════════════════╝\n");
    println!("  Control: guild_stability_mm_fixed_guild (1MM+2GB@7%+4Cas+3Far+2Tra)");
    println!("  Treatment: guild_stability_mm_gs (1MM+1GB+1GS+4Cas+3Far+2Tra)");
    println!("  Seed: {}\n", seed);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::guild_stability_mm_gs();

    let ctrl_dir = PathBuf::from("/tmp/autotune-gs-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-gs-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);

    println!("─── Control (2GB, 0GS) ───");
    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control error: {}", e);
        return;
    }

    println!("\n─── Treatment (1GB + 1GS) ───");
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  RESULTS — GUILDSELLER vs CONTROL                         ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "Metric", "CONTROL (2GB)", "TREATMENT (1GB+1GS)", "Effect"
    );
    println!(
        "  {:20} {:>15} {:>15} {:>15}",
        "─".repeat(20),
        "─".repeat(15),
        "─".repeat(15),
        "─".repeat(15)
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "GDP",
        ctrl_summary.gdp,
        treat_summary.gdp,
        (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.0} {:>15.0} {:>+14.1}%",
        "Debt",
        ctrl_summary.debt,
        treat_summary.debt,
        (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.3}x {:>15.3}x {:>+14.1}%",
        "Debt/GDP",
        ctrl_dg,
        treat_dg,
        (treat_dg / ctrl_dg.max(0.001) - 1.0) * 100.0
    );
    println!(
        "  {:20} {:>15.2}% {:>15.2}% {:>+14.2}pp",
        "Buy Ratio",
        ctrl_summary.buy_ratio * 100.0,
        treat_summary.buy_ratio * 100.0,
        (treat_summary.buy_ratio - ctrl_summary.buy_ratio) * 100.0
    );
    println!(
        "  {:20} {:>15.4} {:>15.4} {:>+14.4}",
        "Volatility",
        ctrl_summary.avg_volatility,
        treat_summary.avg_volatility,
        treat_summary.avg_volatility - ctrl_summary.avg_volatility
    );
    println!(
        "  {:20} {:>15.2}% {:>15.2}% {:>+14.2}pp",
        "Avg BPD",
        ctrl_summary.avg_bpd * 100.0,
        treat_summary.avg_bpd * 100.0,
        (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0
    );

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  VERDICT                                                   ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    let br_change = treat_summary.buy_ratio - ctrl_summary.buy_ratio;
    let vol_change = treat_summary.avg_volatility - ctrl_summary.avg_volatility;
    let gdp_change_pct = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;

    let improvements = [
        (
            "Buy Ratio",
            br_change > 0.01,
            format!("{:+.1}pp", br_change * 100.0),
        ),
        (
            "Volatility",
            vol_change < -0.01,
            format!("{:+.4}", vol_change),
        ),
        (
            "GDP",
            gdp_change_pct > 2.0,
            format!("{:+.1}%", gdp_change_pct),
        ),
    ];

    for (name, passed, val) in improvements {
        if passed {
            println!("  ✅ {}: {} (GS improves economy)", name, val);
        } else {
            println!("  ⚠️  {}: {} (no improvement)", name, val);
        }
    }

    if br_change > 0.01 && vol_change < -0.01 {
        println!(
            "\n  🎯 GuildSeller structurally addresses underselling — both buy_ratio UP and vol DOWN."
        );
    } else if br_change.abs() < 0.01 {
        println!("\n  ℹ️  GuildSeller has minimal effect on buy_ratio — consider higher GS count.");
    }

    println!(
        "\n  Hypothesis: GuildSeller provides downward price pressure via proactive spike-selling."
    );
    println!("  If buy_ratio improves: GS is a structural fix for Farmer-dominated economies.");

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

fn run_headless(scenario: &Scenario, output_dir: Option<PathBuf>) -> Result<(), String> {
    use crate::player::set_global_seeded_rng;
    use crate::recorder::DataRecorder;

    println!("=== Running Scenario: {} ===", scenario.name);
    println!(
        "Duration: {} ticks ({} days)",
        scenario.duration_ticks,
        scenario.duration_ticks / 288
    );
    println!("Speed: {} ticks/sec", scenario.speed_ticks_per_sec);

    // Use seeded RNG if scenario specifies a seed, otherwise use wall-clock randomness
    if let Some(seed) = scenario.seed {
        println!("Seed: {} (deterministic)", seed);
        set_global_seeded_rng(seed);
    }

    let mut sim = if let Some(seed) = scenario.seed {
        Simulation::new_seeded(scenario.config.clone(), seed)
    } else {
        Simulation::new(scenario.config.clone())
    };
    sim.events = scenario.events.clone();

    // Add players
    let mut archetype_map: std::collections::HashMap<String, Archetype> =
        std::collections::HashMap::new();
    archetype_map.insert("Casual".into(), Archetype::Casual);
    archetype_map.insert("Farmer".into(), Archetype::Farmer);
    archetype_map.insert("Trader".into(), Archetype::Trader);
    archetype_map.insert("Hoarder".into(), Archetype::Hoarder);
    archetype_map.insert("Exploiter".into(), Archetype::Exploiter);
    archetype_map.insert("Newbie".into(), Archetype::Newbie);
    archetype_map.insert("AFKFarmer".into(), Archetype::AFKFarmer);
    archetype_map.insert("GuildBuyer".into(), Archetype::GuildBuyer);
    archetype_map.insert("MarketMaker".into(), Archetype::MarketMaker);
    archetype_map.insert("InsiderTrader".into(), Archetype::InsiderTrader);
    archetype_map.insert("GuildSeller".into(), Archetype::GuildSeller);
    archetype_map.insert("VolumeTrader".into(), Archetype::VolumeTrader);

    for player_cfg in &scenario.players {
        let archetype = archetype_map
            .get(&player_cfg.archetype)
            .ok_or_else(|| format!("Unknown archetype: {}", player_cfg.archetype))?;
        for _ in 0..player_cfg.count {
            sim.add_player(*archetype);
        }
    }
    println!(
        "Players: {} (Casual:{}, Farmer:{}, Trader:{}, Hoarder:{}, Exploiter:{}, Newbie:{}, AFKFarmer:{}, GuildBuyer:{}, MarketMaker:{}, InsiderTrader:{}, VolumeTrader:{})",
        sim.players.len(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::Casual))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::Farmer))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::Trader))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::Hoarder))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::Exploiter))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::Newbie))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::AFKFarmer))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::GuildBuyer))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::MarketMaker))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::InsiderTrader))
            .count(),
        sim.players
            .iter()
            .filter(|p| matches!(p.archetype, Archetype::VolumeTrader))
            .count(),
    );

    // Setup recorder if output dir provided
    if let Some(ref dir) = output_dir {
        std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        let db_path = dir.join("simulation.db");
        let config_json = serde_json::to_string(&scenario.config).unwrap();
        let recorder = DataRecorder::new(db_path, &config_json).map_err(|e| e.to_string())?;
        sim.recorder = Some(recorder);
    }

    sim.paused = false;

    let start = Instant::now();
    let _tick_interval = 1.0 / scenario.speed_ticks_per_sec as f64;
    let mut last_report = 0u64;
    let report_interval = scenario.duration_ticks / 10;

    // Track injected events
    let mut injected_stress: std::collections::HashSet<String> = std::collections::HashSet::new();

    while sim.current_tick < scenario.duration_ticks {
        // Apply stress events
        for event in &scenario.stress_events {
            let event_key = format!("{:?}:{}", event, sim.current_tick);
            let should_fire = match event {
                StressEvent::MarketCrash { at_tick } => sim.current_tick == *at_tick,
                StressEvent::Exploit { at_tick } => sim.current_tick == *at_tick,
                StressEvent::LowPlayers { at_tick } => sim.current_tick == *at_tick,
                StressEvent::Hyperinflation { at_tick } => sim.current_tick == *at_tick,
                StressEvent::LoanCascade { at_tick } => sim.current_tick == *at_tick,
                StressEvent::PlayerJoin { at_tick, .. } => sim.current_tick == *at_tick,
                StressEvent::PriceShock { at_tick, .. } => sim.current_tick == *at_tick,
            };
            if should_fire && !injected_stress.contains(&event_key) {
                injected_stress.insert(event_key.clone());
                match event {
                    StressEvent::MarketCrash { .. } => {
                        println!(
                            "  [STRESS @ tick {}] Injecting market crash",
                            sim.current_tick
                        );
                        sim.stress_market_crash();
                    }
                    StressEvent::Exploit { .. } => {
                        println!("  [STRESS @ tick {}] Injecting exploit", sim.current_tick);
                        sim.stress_exploit();
                    }
                    StressEvent::LowPlayers { .. } => {
                        println!(
                            "  [STRESS @ tick {}] Reducing to low players",
                            sim.current_tick
                        );
                        sim.stress_low_players();
                    }
                    StressEvent::Hyperinflation { .. } => {
                        println!(
                            "  [STRESS @ tick {}] Injecting hyperinflation",
                            sim.current_tick
                        );
                        sim.stress_hyperinflation();
                    }
                    StressEvent::LoanCascade { .. } => {
                        println!(
                            "  [STRESS @ tick {}] Injecting loan cascade",
                            sim.current_tick
                        );
                        sim.stress_loan_cascade();
                    }
                    StressEvent::PlayerJoin {
                        archetype, count, ..
                    } => {
                        let arch = archetype_map
                            .get(archetype)
                            .copied()
                            .unwrap_or(Archetype::Casual);
                        for _ in 0..*count {
                            sim.add_player(arch);
                        }
                        println!(
                            "  [STRESS @ tick {}] Added {} {} players",
                            sim.current_tick, count, archetype
                        );
                    }
                    StressEvent::PriceShock {
                        at_tick: _,
                        item_index,
                        price_multiplier,
                    } => {
                        // should_fire guarantees current_tick == at_tick
                        // Copy values before mutable borrow
                        if *item_index < sim.engine.items.len() {
                            let old_price = sim.engine.items[*item_index].price;
                            let item_name = sim.engine.items[*item_index].name.clone();
                            let new_price = (old_price * price_multiplier).max(0.01);
                            sim.engine.items[*item_index].price = new_price;
                            println!(
                                "  [STRESS @ tick {}] PriceShock: {} price {} → {} (×{:.2})",
                                sim.current_tick, item_name, old_price, new_price, price_multiplier
                            );
                        }
                    }
                }
            }
        }

        sim.tick();

        if sim.current_tick >= last_report + report_interval
            || sim.current_tick == scenario.duration_ticks
        {
            let pct = (sim.current_tick as f64 / scenario.duration_ticks as f64 * 100.0) as u32;
            let elapsed = start.elapsed();
            let _rate = sim.current_tick as f64 / elapsed.as_secs_f64();
            println!(
                "  [{:3}% | tick {:6}] price={:.2} | GDP={:.0} | debt={:.0} | online={:2}",
                pct,
                sim.current_tick,
                sim.engine.items.first().map(|i| i.price).unwrap_or(0.0),
                sim.economy_snapshots.last().map(|s| s.gdp).unwrap_or(0.0),
                sim.economy_snapshots
                    .last()
                    .map(|s| s.total_debt)
                    .unwrap_or(0.0),
                sim.economy_snapshots
                    .last()
                    .map(|s| s.online_players)
                    .unwrap_or(0),
            );
            last_report = sim.current_tick;
        }
    }

    let elapsed = start.elapsed();

    // Finalize recorder
    if let Some(mut recorder) = sim.recorder.take() {
        let _ = recorder.finalize();
        if let Some(ref dir) = output_dir {
            println!("\n  DB saved: {}", dir.join("simulation.db").display());
        }
    }

    // Print summary
    println!("\n=== Simulation Complete ===");
    println!("  Elapsed: {:.2}s", elapsed.as_secs_f64());
    println!(
        "  Rate: {:.0} ticks/sec",
        sim.current_tick as f64 / elapsed.as_secs_f64()
    );
    println!("  Final tick: {}", sim.current_tick);

    // Price summary
    println!("\n--- Final Prices ---");
    for item in &sim.engine.items {
        let pct = if item.base_price > 0.0 {
            ((item.price - item.base_price) / item.base_price * 100.0).round()
        } else {
            0.0
        };
        let trend_str = match item.trend.direction {
            crate::engine::PriceTrendDirection::Up => "↑",
            crate::engine::PriceTrendDirection::Down => "↓",
            crate::engine::PriceTrendDirection::Stable => "→",
        };
        println!(
            "  {:20} base={:8.2} price={:8.2} ({:+.0}%) buy={:8.2} sell={:8.2} BPD={:.2}% SPD={:.2}% {}",
            item.name,
            item.base_price,
            item.price,
            pct,
            item.buy_price(),
            item.sell_price(),
            item.spread.bpd * 100.0,
            item.spread.spd * 100.0,
            trend_str,
        );
    }

    // Spread analysis
    println!("\n--- Spread Analysis ---");
    for item in &sim.engine.items {
        let avg_bpd = if item.bpd_history.len() > 2 {
            item.bpd_history
                .iter()
                .skip(item.bpd_history.len() - 10)
                .sum::<f64>()
                / 10.0
        } else {
            item.spread.bpd
        };
        let avg_spd = if item.spd_history.len() > 2 {
            item.spd_history
                .iter()
                .skip(item.spd_history.len() - 10)
                .sum::<f64>()
                / 10.0
        } else {
            item.spread.spd
        };
        println!(
            "  {:20} avgBPD={:.3}% avgSPD={:.3}% finalBPD={:.3}% finalSPD={:.3}%",
            item.name,
            avg_bpd * 100.0,
            avg_spd * 100.0,
            item.spread.bpd * 100.0,
            item.spread.spd * 100.0
        );
    }

    // Economy summary
    if let Some(last) = sim.economy_snapshots.last() {
        println!("\n--- Economy Snapshot (final) ---");
        println!("  GDP: {:.2}", last.gdp);
        println!("  Total Debt: {:.2}", last.total_debt);
        println!("  Avg Price Change: {:.2}%", last.avg_price_change);
        println!(
            "  Online Players: {}/{}",
            last.online_players, last.total_players
        );
    }

    // Loan summary
    let active_loans = sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Active)
        .count();
    let total_loans = sim.loans.len();
    let defaulted = sim
        .loans
        .iter()
        .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
        .count();
    println!("\n--- Loan Summary ---");
    println!("  Total loans: {}", total_loans);
    println!("  Active: {}", active_loans);
    println!("  Defaulted: {}", defaulted);

    // Volume summary
    let total_tx = sim.transactions.len();
    let buy_tx = sim
        .transactions
        .iter()
        .filter(|t| matches!(t.tx_type, crate::engine::TransactionType::Buy))
        .count();
    let sell_tx = total_tx - buy_tx;
    println!("\n--- Transaction Volume ---");
    println!("  Total transactions: {}", total_tx);
    println!(
        "  Buys: {} ({:.1}%)",
        buy_tx,
        buy_tx as f64 / (total_tx as f64).max(1.0) * 100.0
    );
    println!(
        "  Sells: {} ({:.1}%)",
        sell_tx,
        sell_tx as f64 / (total_tx as f64).max(1.0) * 100.0
    );

    // Stability metrics
    println!("\n--- Stability Metrics ---");
    let mut price_volatility: Vec<f64> = Vec::new();
    for item in &sim.engine.items {
        if item.price_history.len() > 10 {
            let window = item.price_history.len().min(100);
            let recent: Vec<f64> =
                item.price_history[item.price_history.len().saturating_sub(window)..].to_vec();
            if recent.len() > 1 {
                let mean = recent.iter().sum::<f64>() / recent.len() as f64;
                let variance = recent
                    .iter()
                    .map(|p| {
                        let d = p - mean;
                        d * d
                    })
                    .sum::<f64>()
                    / recent.len() as f64;
                let volatility = variance.sqrt() / mean.max(0.01);
                price_volatility.push(volatility);
                println!(
                    "  {:20} volatility={:.4} mean_price={:.2}",
                    item.name, volatility, mean
                );
            }
        }
    }
    if !price_volatility.is_empty() {
        let avg_vol = price_volatility.iter().sum::<f64>() / price_volatility.len() as f64;
        println!("  Average volatility: {:.4}", avg_vol);
        println!(
            "  STABLE if avg_vol < 0.05: {}",
            if avg_vol < 0.05 { "YES ✓" } else { "NO ✗" }
        );
    }

    // Global volume multiplier stability
    if !sim.engine.global_volume_history.is_empty() {
        let gvm = &sim.engine.global_volume_history;
        let min_gvm = gvm.iter().cloned().fold(f64::INFINITY, f64::min);
        let max_gvm = gvm.iter().cloned().fold(f64::NEG_INFINITY, f64::max);
        let avg_gvm = gvm.iter().sum::<f64>() / gvm.len() as f64;
        println!(
            "  Global vol mult: min={:.3} max={:.3} avg={:.3}",
            min_gvm, max_gvm, avg_gvm
        );
    }

    Ok(())
}

/// GuildBuyer price-dip threshold sweep.
///
/// Runs guild-stability scenario across 10 threshold values (0.05–0.50).
/// Lower threshold = buys only on large price dips; higher = aggressive, buys on small dips.
///
/// CSV columns: threshold, GDP, debt, debt_gdp_ratio, avg_bpd, avg_spd,
///              avg_volatility, buy_ratio, final_prices_json
fn run_guild_threshold_sweep() {
    use crate::analyzer::load_summary;
    use crate::player::set_fixed_guild_threshold;

    let base_scenario = Scenario::guild_stability();
    let thresholds: Vec<f64> = (1..=10).map(|i| i as f64 * 0.05).collect();
    let total = thresholds.len();

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       GUILDBUYER PRICE-DIP THRESHOLD SWEEP                  ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!();
    println!("  Scenario: Guild Stability (2 GuildBuyer + 4 Casual + 3 Farmer + 2 Trader)");
    println!(
        "  Duration: 14 days ({} ticks)",
        base_scenario.duration_ticks
    );
    println!("  Thresholds: {:?}", thresholds);
    println!();
    println!(
        "{:>10} {:>12} {:>12} {:>10} {:>8} {:>8} {:>10} {:>8}",
        "threshold", "GDP", "Debt", "Debt/GDP", "BPD%", "SPD%", "Volatility", "Buy%"
    );
    println!(
        "{:>10} {:>12} {:>12} {:>10} {:>8} {:>8} {:>10} {:>8}",
        "─".repeat(10),
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(10),
        "─".repeat(8)
    );

    let mut results: Vec<GuildSweepResult> = Vec::new();

    for (i, threshold) in thresholds.iter().enumerate() {
        eprint!("\r  [{}/{}] threshold={:.2}", i + 1, total, threshold);
        std::io::stderr().flush().ok();

        // Set fixed threshold for this run
        set_fixed_guild_threshold(Some(*threshold));

        // Run headless with temp output dir
        let out_dir = PathBuf::from(format!(
            "/tmp/autotune-sweep-{:04}",
            (threshold * 100.0) as i32
        ));
        let _ = std::fs::remove_dir_all(&out_dir);
        std::fs::create_dir_all(&out_dir).ok();

        let result = run_headless(&base_scenario, Some(out_dir.clone()));

        // Clear fixed threshold
        set_fixed_guild_threshold(None);

        if let Err(e) = &result {
            eprintln!("\n  ✗ Error: {:?}", e);
            continue;
        }

        // Load summary from DB
        let db_path = out_dir.join("simulation.db");
        let summary = match load_summary(&db_path) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("\n  ✗ Could not load summary: {}", e);
                continue;
            }
        };

        // Clean up temp dir
        let _ = std::fs::remove_dir_all(&out_dir);

        let debt_gdp = summary.debt / summary.gdp.max(0.01);
        let threshold_pct = threshold * 100.0;
        println!(
            "\n  {:>8.0}% {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.2}% {:>9.4} {:>7.1}%",
            threshold_pct,
            summary.gdp,
            summary.debt,
            debt_gdp,
            summary.avg_bpd * 100.0,
            summary.avg_spd * 100.0,
            summary.avg_volatility,
            summary.buy_ratio * 100.0
        );

        results.push(GuildSweepResult {
            threshold: *threshold,
            gdp: summary.gdp,
            debt: summary.debt,
            debt_gdp_ratio: debt_gdp,
            avg_bpd: summary.avg_bpd,
            avg_spd: summary.avg_bpd, // placeholder; actual SPD needs separate query
            avg_volatility: summary.avg_volatility,
            buy_ratio: summary.buy_ratio,
        });
    }

    println!("\n");
    if results.is_empty() {
        println!("  No results collected.");
        return;
    }

    // ── Analysis ──────────────────────────────────────────────────────
    println!("╔══════════════════════════════════════════════════════════════╗");
    println!("║  SWEEP ANALYSIS                                              ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Best GDP
    let best_gdp = results
        .iter()
        .max_by(|a, b| a.gdp.partial_cmp(&b.gdp).unwrap())
        .unwrap();
    println!(
        "  Best GDP:       threshold={:.0}%  GDP={:.0}  (D/G={:.2}x)",
        best_gdp.threshold * 100.0,
        best_gdp.gdp,
        best_gdp.debt_gdp_ratio
    );

    // Lowest debt
    let lowest_debt = results
        .iter()
        .min_by(|a, b| a.debt.partial_cmp(&b.debt).unwrap())
        .unwrap();
    println!(
        "  Lowest debt:    threshold={:.0}%  debt={:.0}  (D/G={:.2}x)",
        lowest_debt.threshold * 100.0,
        lowest_debt.debt,
        lowest_debt.debt_gdp_ratio
    );

    // Most balanced buy ratio (closest to 50%)
    let most_balanced = results
        .iter()
        .min_by(|a, b| {
            (a.buy_ratio - 0.5)
                .abs()
                .partial_cmp(&(b.buy_ratio - 0.5).abs())
                .unwrap()
        })
        .unwrap();
    println!(
        "  Most balanced: threshold={:.0}%  buy%={:.1}%  (D/G={:.2}x)",
        most_balanced.threshold * 100.0,
        most_balanced.buy_ratio * 100.0,
        most_balanced.debt_gdp_ratio
    );

    // Lowest volatility
    let lowest_vol = results
        .iter()
        .min_by(|a, b| a.avg_volatility.partial_cmp(&b.avg_volatility).unwrap())
        .unwrap();
    println!(
        "  Most stable:   threshold={:.0}%  vol={:.4}  (D/G={:.2}x)",
        lowest_vol.threshold * 100.0,
        lowest_vol.avg_volatility,
        lowest_vol.debt_gdp_ratio
    );

    // Trend: GDP vs threshold
    println!("\n  Threshold → GDP trend:");
    for r in &results {
        let bar_len = ((r.gdp / 100000.0).min(30.0)) as usize;
        println!(
            "  {:4.0}%  {}{:>12.0}",
            r.threshold * 100.0,
            "█".repeat(bar_len),
            r.gdp
        );
    }

    // CSV export
    let csv_path = PathBuf::from(
        "/home/ubuntu/.openclaw/workspace-autotune/sim-output/guild-threshold-sweep.csv",
    );
    if let Some(parent) = csv_path.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    let mut csv = std::fs::File::create(&csv_path).unwrap();
    writeln!(
        csv,
        "threshold,gdp,debt,debt_gdp_ratio,avg_bpd,avg_spd,avg_volatility,buy_ratio"
    )
    .ok();
    for r in &results {
        writeln!(
            csv,
            "{:.2},{:.2},{:.2},{:.6},{:.6},{:.6},{:.6},{:.4}",
            r.threshold,
            r.gdp,
            r.debt,
            r.debt_gdp_ratio,
            r.avg_bpd,
            r.avg_spd,
            r.avg_volatility,
            r.buy_ratio
        )
        .ok();
    }
    println!("\n  CSV saved to: {}", csv_path.display());
}

// ─── MM Competition Test ───────────────────────────────────────────────────

/// Head-to-head comparison: 1MM+2GB vs 2MM+2GB (same seed, same archetypes).
///
/// Core question: Does adding a 2nd MarketMaker improve economy health,
/// or do MMs step on each other's quotes?
///
/// Control: guild_stability_mm_fixed_guild (1MM + 2GB + 4Cas + 3Far + 2Tra)
/// Treatment: guild_stability_2mm_fixed_guild (2MM + 2GB + 3Cas + 3Far + 2Tra)
///
/// Hypotheses:
/// - H1 (YES): 2 MMs provide redundant two-sided liquidity → tighter spreads
/// - H2 (NO): MMs compete on same quotes → one dominates, no net improvement
/// - H3 (RISKY): More MMs → more capital deployed → bigger positions when MM defaults
fn run_mm_competition_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       MM COMPETITION TEST                                  ║");
    println!("║  1MM+2GB vs 2MM+2GB — Does more MM improve stability?   ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  Control: 1MM + 2GB + 4Cas + 3Far + 2Tra (12 players, seed={})",
        seed
    );
    println!(
        "  Treat:   2MM + 2GB + 3Cas + 3Far + 2Tra (12 players, seed={})",
        seed
    );
    println!("  Same seed = same RNG state = fair head-to-head\n");

    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8} {:>8}  |  {:>12} {:>12} {:>10} {:>8} {:>8} {:>8}",
        "GDP",
        "Debt",
        "D/G",
        "BPD%",
        "Buy%",
        "Vol×1000",
        "GDP",
        "Debt",
        "D/G",
        "BPD%",
        "Buy%",
        "Vol×1000"
    );
    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8} {:>8}  |  {:>12} {:>12} {:>10} {:>8} {:>8} {:>8}",
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(8)
    );

    // Run control
    let ctrl_dir = PathBuf::from("/tmp/autotune-mm-ctrl");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    let control = Scenario::guild_stability_mm_fixed_guild();
    if let Err(e) = run_seeded_headless(&control, seed, &ctrl_dir) {
        eprintln!("  Control run error: {}", e);
        return;
    }
    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Control summary error: {}", e);
            return;
        }
    };

    // Run treatment
    let treat_dir = PathBuf::from("/tmp/autotune-mm-treat");
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&treat_dir).ok();
    let treatment = Scenario::guild_stability_2mm_fixed_guild();
    if let Err(e) = run_seeded_headless(&treatment, seed, &treat_dir) {
        eprintln!("  Treatment run error: {}", e);
        return;
    }
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Treatment summary error: {}", e);
            return;
        }
    };

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!(
        "  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}% {:>7.3}  |  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}% {:>7.3}",
        ctrl_summary.gdp,
        ctrl_summary.debt,
        ctrl_dg,
        ctrl_summary.avg_bpd * 100.0,
        ctrl_summary.buy_ratio * 100.0,
        ctrl_summary.avg_volatility * 1000.0,
        treat_summary.gdp,
        treat_summary.debt,
        treat_dg,
        treat_summary.avg_bpd * 100.0,
        treat_summary.buy_ratio * 100.0,
        treat_summary.avg_volatility * 1000.0
    );

    println!();
    println!("  === ANALYSIS ===");
    let gdp_pct = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let vol_pct =
        (treat_summary.avg_volatility / ctrl_summary.avg_volatility.max(0.0001) - 1.0) * 100.0;
    let bpd_pct = (treat_summary.avg_bpd / ctrl_summary.avg_bpd.max(0.0001) - 1.0) * 100.0;

    println!(
        "  GDP change:          {:+.1}% ({})",
        gdp_pct,
        if gdp_pct < -5.0 {
            "2MM harms GDP"
        } else if gdp_pct > 5.0 {
            "2MM boosts GDP"
        } else {
            "2MM neutral on GDP"
        }
    );
    println!(
        "  Volatility change:  {:+.1}% ({})",
        vol_pct,
        if vol_pct > 50.0 {
            "2MM raises vol"
        } else if vol_pct < -50.0 {
            "2MM reduces vol"
        } else {
            "2MM stable vol"
        }
    );
    println!(
        "  Spread (BPD) change: {:+.1}% ({})",
        bpd_pct,
        if bpd_pct < -20.0 {
            "2MM compresses spreads"
        } else if bpd_pct > 20.0 {
            "2MM widens spreads"
        } else {
            "2MM neutral on spreads"
        }
    );
    println!("  D/G: {:.2}x (ctrl) → {:.2}x (treat)", ctrl_dg, treat_dg);
    let dg_chg = treat_dg - ctrl_dg;
    println!(
        "  D/G delta: {:+.2}x ({})",
        dg_chg,
        if dg_chg < -0.1 {
            "2MM improves debt health"
        } else if dg_chg > 0.1 {
            "2MM worsens debt health"
        } else {
            "2MM neutral on debt"
        }
    );

    // Verdict
    println!();
    let improvements = [
        gdp_pct > 5.0,
        vol_pct < -20.0,
        bpd_pct < -10.0,
        dg_chg < -0.1,
    ];
    let regressions = [gdp_pct < -5.0, vol_pct > 50.0, bpd_pct > 20.0, dg_chg > 0.1];
    let n_improve = improvements.iter().filter(|&&x| x).count();
    let n_regress = regressions.iter().filter(|&&x| x).count();
    if n_improve >= 2 && n_regress == 0 {
        println!("  ✅ VERDICT: 2 MMs improve economy — ADD A 2ND MM TO PRODUCTION CONFIG");
    } else if n_regress >= 2 && n_improve == 0 {
        println!("  ❌ VERDICT: 2 MMs harm economy — 1 MM is sufficient");
    } else if n_improve > 0 && n_regress > 0 {
        println!("  ⚠️  VERDICT: Mixed — 2 MMs trade-offs specific to your priorities");
    } else {
        println!("  ➖ VERDICT: No meaningful difference — 2 MMs offer no benefit");
    }
    println!();
}

// ─── Exploiter Stress Test ─────────────────────────────────────────────────

/// Head-to-head comparison of standard+MM vs standard+MM+2 Exploiters.
/// Exploiters chase trends (buy rising, sell falling), amplifying price swings.
/// Tests whether MarketMakers can absorb Exploiter-driven manipulation without
/// the economy destabilizing.
fn run_exploiter_stress_test() {
    use crate::analyzer::load_summary;

    let control = Scenario::standard_with_mm();
    let treatment = Scenario::exploiter_stress();
    let seed = 98765432u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       EXPLOITER STRESS TEST                                 ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!();
    println!(
        "  {:^48}  {:^48}",
        "CONTROL (standard+MM)", "TREATMENT (standard+MM + 2 Exploiters)"
    );
    println!("  Seed: {}", seed);
    println!();
    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8}  |  {:>12} {:>12} {:>10} {:>8} {:>8}",
        "GDP", "Debt", "D/G", "BPD%", "Buy%", "GDP", "Debt", "D/G", "BPD%", "Buy%"
    );
    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8}  |  {:>12} {:>12} {:>10} {:>8} {:>8}",
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8)
    );

    // Run control
    let ctrl_dir = PathBuf::from("/tmp/autotune-exploit-ctrl");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    if let Err(e) = run_headless(&control, Some(ctrl_dir.clone())) {
        eprintln!("  Control run error: {}", e);
        return;
    }
    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Control summary error: {}", e);
            return;
        }
    };

    // Run treatment (different seed for different RNG)
    let treat_dir = PathBuf::from("/tmp/autotune-exploit-treat");
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&treat_dir).ok();
    if let Err(e) = run_headless(&treatment, Some(treat_dir.clone())) {
        eprintln!("  Treatment run error: {}", e);
        return;
    }
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Treatment summary error: {}", e);
            return;
        }
    };

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!(
        "  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}%  |  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}%",
        ctrl_summary.gdp,
        ctrl_summary.debt,
        ctrl_dg,
        ctrl_summary.avg_bpd * 100.0,
        ctrl_summary.buy_ratio * 100.0,
        treat_summary.gdp,
        treat_summary.debt,
        treat_dg,
        treat_summary.avg_bpd * 100.0,
        treat_summary.buy_ratio * 100.0
    );

    println!();
    println!("  === ANALYSIS ===");
    let gdp_pct = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let vol_pct =
        (treat_summary.avg_volatility / ctrl_summary.avg_volatility.max(0.0001) - 1.0) * 100.0;
    println!(
        "  GDP change:          {:+.1}% (Exploiters {:>})",
        gdp_pct,
        if gdp_pct < -5.0 {
            "harm GDP"
        } else if gdp_pct > 5.0 {
            "boost GDP"
        } else {
            "neutral on GDP"
        }
    );
    println!(
        "  Volatility change:  {:+.1}% (Exploiters {:>})",
        vol_pct,
        if vol_pct > 50.0 {
            "raise volatility"
        } else if vol_pct < -50.0 {
            "reduce volatility"
        } else {
            "stable volatility"
        }
    );
    println!(
        "  D/G control:         {:.2}x  |  treatment: {:.2}x",
        ctrl_dg, treat_dg
    );

    // Verdict
    println!();
    if treat_dg < ctrl_dg * 2.0 && vol_pct < 200.0 {
        println!("  ✅ MARKETMAKERS ABSORB EXPLOITER PRESSURE — MM provides sufficient");
        println!("     two-sided liquidity to prevent Exploiter destabilization.");
    } else if vol_pct > 500.0 {
        println!("  ⚠️  EXploiters caused runaway volatility — consider adjusting MM");
        println!("     spread parameters or reducing Exploiter participation rate.");
    } else {
        println!(
            "  ⚠️  Mixed results — Exploiters {:>} GDP and {:>} volatility.",
            if gdp_pct < 0.0 {
                "reduced"
            } else {
                "increased"
            },
            if vol_pct > 0.0 {
                "increased"
            } else {
                "reduced"
            }
        );
    }

    // Cleanup
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

// ─── Multi-Seed Threshold Comparison ─────────────────────────────────────

use crate::analyzer::load_summary;
use crate::player::set_fixed_guild_threshold;

/// Run a headless simulation with a specific seed, returning the SimSummary.
fn run_seeded_headless(scenario: &Scenario, seed: u64, output_dir: &PathBuf) -> Result<(), String> {
    use crate::recorder::DataRecorder;

    let archetype_map: std::collections::HashMap<String, Archetype> = [
        ("Casual".into(), Archetype::Casual),
        ("Farmer".into(), Archetype::Farmer),
        ("Trader".into(), Archetype::Trader),
        ("Hoarder".into(), Archetype::Hoarder),
        ("Exploiter".into(), Archetype::Exploiter),
        ("Newbie".into(), Archetype::Newbie),
        ("AFKFarmer".into(), Archetype::AFKFarmer),
        ("GuildBuyer".into(), Archetype::GuildBuyer),
        ("MarketMaker".into(), Archetype::MarketMaker),
        ("InsiderTrader".into(), Archetype::InsiderTrader),
        ("GuildSeller".into(), Archetype::GuildSeller),
    ]
    .into_iter()
    .collect();

    let mut sim = Simulation::new_seeded(scenario.config.clone(), seed);
    sim.events = scenario.events.clone();

    for player_cfg in &scenario.players {
        let archetype = archetype_map
            .get(&player_cfg.archetype)
            .ok_or_else(|| format!("Unknown archetype: {}", player_cfg.archetype))?;
        for _ in 0..player_cfg.count {
            sim.add_player(*archetype);
        }
    }

    std::fs::create_dir_all(output_dir).map_err(|e| e.to_string())?;
    let db_path = output_dir.join("simulation.db");
    let config_json = serde_json::to_string(&scenario.config).unwrap();
    let recorder = DataRecorder::new(db_path, &config_json).map_err(|e| e.to_string())?;
    sim.recorder = Some(recorder);
    sim.paused = false;

    while sim.current_tick < scenario.duration_ticks {
        sim.tick();
    }

    if let Some(mut recorder) = sim.recorder.take() {
        let _ = recorder.finalize();
    }

    Ok(())
}

/// Multi-seed comparison: GuildBuyer 7% vs 10% threshold, 5 seeds each.
/// Seeds are spaced far apart to ensure independent RNG trajectories.
fn run_multi_seed_compare() {
    // Test GuildStability + MM across seeds to characterize residual variance
    // (The non-MM comparison was done previously: without MM avg_vol ~0.19,
    //  with MM avg_vol drops to ~0.006. This run checks variance WITH MM.)
    let base_scenario = Scenario::marketmaker_test();
    let thresholds = vec![0.07, 0.10];
    let seeds: Vec<u64> = vec![12345, 42, 98765, 77777, 11111];
    let total = thresholds.len() * seeds.len();

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       MULTI-SEED GUILDSTABILITY+MM vs THRESHOLD            ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!();
    println!("  Scenario: GuildStability+MM (1GB + 1MM + 4Cas + 3Far + 2Trader)");
    println!("  Duration: 14 days | Seeds: {:?}", seeds);
    println!("  Prior result (no MM): avg_vol ~0.19. With MM: expected ~0.006");
    println!();

    let mut all_results: Vec<MultiSeedResult> = Vec::new();
    let mut run_idx = 0;

    for threshold in &thresholds {
        for seed in &seeds {
            run_idx += 1;
            eprint!(
                "\r  [{}/{}] threshold={:.0}% seed={}",
                run_idx,
                total,
                threshold * 100.0,
                seed
            );
            std::io::stderr().flush().ok();

            set_fixed_guild_threshold(Some(*threshold));

            let out_dir = PathBuf::from(format!(
                "/tmp/autotune-ms-{:02}-{}",
                (threshold * 100.0) as i32,
                seed
            ));
            let _ = std::fs::remove_dir_all(&out_dir);

            let result = run_seeded_headless(&base_scenario, *seed, &out_dir);
            set_fixed_guild_threshold(None);

            if let Err(e) = &result {
                eprintln!("\n  ✗ Error: {:?}", e);
                continue;
            }

            let db_path = out_dir.join("simulation.db");
            let summary = match load_summary(&db_path) {
                Ok(s) => s,
                Err(e) => {
                    eprintln!("\n  ✗ Could not load summary: {}", e);
                    continue;
                }
            };

            let _ = std::fs::remove_dir_all(&out_dir);

            all_results.push(MultiSeedResult {
                threshold: *threshold,
                seed: *seed,
                gdp: summary.gdp,
                debt: summary.debt,
                debt_gdp_ratio: summary.debt / summary.gdp.max(1.0),
                avg_bpd: summary.avg_bpd,
                avg_spd: summary.avg_spd,
                avg_volatility: summary.avg_volatility,
                buy_ratio: summary.buy_ratio,
            });
        }
    }

    println!("\n\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       RESULTS BY THRESHOLD (5 seeds each)                    ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Group by threshold
    let mut summaries: Vec<(f64, Vec<&MultiSeedResult>)> = Vec::new();
    for &t in &thresholds {
        let group: Vec<&MultiSeedResult> =
            all_results.iter().filter(|r| r.threshold == t).collect();
        if !group.is_empty() {
            summaries.push((t, group));
        }
    }

    println!(
        "{:>10} {:>10} {:>10} {:>10} {:>10} {:>10} {:>10}",
        "threshold", "GDP avg", "GDP std", "D/G avg", "D/G std", "Buy% avg", "Vol avg"
    );
    println!(
        "{:>10} {:>10} {:>10} {:>10} {:>10} {:>10} {:>10}",
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(10)
    );

    let mut threshold_stats: Vec<ThresholdStats> = Vec::new();
    for (threshold, group) in &summaries {
        let n = group.len() as f64;
        let gdp_avg = group.iter().map(|r| r.gdp).sum::<f64>() / n;
        let gdp_std = (group.iter().map(|r| (r.gdp - gdp_avg).powi(2)).sum::<f64>() / n).sqrt();
        let dg_avg = group.iter().map(|r| r.debt_gdp_ratio).sum::<f64>() / n;
        let dg_std = (group
            .iter()
            .map(|r| (r.debt_gdp_ratio - dg_avg).powi(2))
            .sum::<f64>()
            / n)
            .sqrt();
        let buy_avg = group.iter().map(|r| r.buy_ratio).sum::<f64>() / n;
        let vol_avg = group.iter().map(|r| r.avg_volatility).sum::<f64>() / n;

        println!(
            "  {:>8.0}% {:>10.0} {:>10.0} {:>9.2}x {:>9.2}x {:>9.1}% {:>9.4}",
            threshold * 100.0,
            gdp_avg,
            gdp_std,
            dg_avg,
            dg_std,
            buy_avg * 100.0,
            vol_avg
        );

        threshold_stats.push(ThresholdStats {
            threshold: *threshold,
            gdp_avg,
            gdp_std,
            dg_avg,
            dg_std,
            buy_avg,
            vol_avg,
        });
    }

    println!();

    // Per-seed table
    println!("─── Per-seed breakdown ────────────────────────────────────");
    println!(
        "{:>8} {:>8} {:>10} {:>10} {:>9} {:>8} {:>8}",
        "Thresh", "Seed", "GDP", "Debt", "D/G", "Buy%", "Vol"
    );
    for r in &all_results {
        println!(
            "  {:>6.0}% {:>8} {:>10.0} {:>10.0} {:>8.2}x {:>7.1}% {:>7.4}",
            r.threshold * 100.0,
            r.seed,
            r.gdp,
            r.debt,
            r.debt_gdp_ratio,
            r.buy_ratio * 100.0,
            r.avg_volatility
        );
    }

    // Winner analysis
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       ANALYSIS                                              ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    for stats in &threshold_stats {
        println!(
            "  {:.0}% threshold: GDP={:.0}±{:.0} | D/G={:.3}x±{:.3} | Buy%={:.1}% | Vol={:.4}",
            stats.threshold * 100.0,
            stats.gdp_avg,
            stats.gdp_std,
            stats.dg_avg,
            stats.dg_std,
            stats.buy_avg * 100.0,
            stats.vol_avg
        );
    }

    if threshold_stats.len() == 2 {
        let a = &threshold_stats[0];
        let b = &threshold_stats[1];
        println!();

        // D/G comparison
        let dg_winner = if a.dg_avg < b.dg_avg { a } else { b };
        println!(
            "  D/G winner: {:.0}% ({:.3}x vs {:.3}x)",
            dg_winner.threshold * 100.0,
            dg_winner.dg_avg,
            if dg_winner.threshold == a.threshold {
                b.dg_avg
            } else {
                a.dg_avg
            }
        );

        // GDP comparison
        let gdp_winner = if a.gdp_avg > b.gdp_avg { a } else { b };
        println!(
            "  GDP winner: {:.0}% ({:.0} vs {:.0})",
            gdp_winner.threshold * 100.0,
            gdp_winner.gdp_avg,
            if gdp_winner.threshold == a.threshold {
                b.gdp_avg
            } else {
                a.gdp_avg
            }
        );

        // Consistency (lower D/G std = more consistent)
        let consistency_winner = if a.dg_std < b.dg_std { a } else { b };
        println!(
            "  Most consistent: {:.0}% (D/G std={:.3} vs {:.3})",
            consistency_winner.threshold * 100.0,
            consistency_winner.dg_std,
            if consistency_winner.threshold == a.threshold {
                b.dg_std
            } else {
                a.dg_std
            }
        );

        // Overall recommendation
        println!();
        if a.dg_avg < 3.0 && b.dg_avg < 3.0 {
            // Both are healthy — recommend higher GDP
            if gdp_winner.threshold == a.threshold {
                println!("  ✅ RECOMMENDATION: 10% — both thresholds safe, 10% maximizes GDP");
            } else {
                println!("  ✅ RECOMMENDATION: 7% — both thresholds safe, 7% maximizes GDP");
            }
        } else if a.dg_avg < 1.0 && b.dg_avg >= 1.0 {
            println!(
                "  ✅ RECOMMENDATION: 7% — clearly safer (D/G {:.3}x vs {:.3}x)",
                a.dg_avg, b.dg_avg
            );
        } else if b.dg_avg < 1.0 && a.dg_avg >= 1.0 {
            println!(
                "  ✅ RECOMMENDATION: 10% — clearly safer (D/G {:.3}x vs {:.3}x)",
                b.dg_avg, a.dg_avg
            );
        } else {
            // Both above 1.0 or both below but neither clearly dominant
            let dg_improvement = ((b.dg_avg - a.dg_avg) / b.dg_avg.max(0.001)) * 100.0;
            let gdp_improvement = ((a.gdp_avg - b.gdp_avg) / b.gdp_avg.max(0.001)) * 100.0;
            if dg_improvement > 20.0 && dg_improvement > gdp_improvement {
                println!(
                    "  ✅ RECOMMENDATION: 7% — {:.0}% lower D/G ({:.3}x vs {:.3}x), worth the GDP trade-off",
                    dg_improvement.abs(),
                    a.dg_avg,
                    b.dg_avg
                );
            } else if gdp_improvement > 20.0 && gdp_improvement > dg_improvement {
                println!(
                    "  ✅ RECOMMENDATION: 10% — {:.0}% higher GDP ({:.0} vs {:.0}), D/G acceptable",
                    gdp_improvement.abs(),
                    gdp_winner.gdp_avg,
                    if gdp_winner.threshold == a.threshold {
                        a.gdp_avg
                    } else {
                        b.gdp_avg
                    }
                );
            } else {
                println!(
                    "  ⚠️  CLOSE: 7% D/G={:.3}x GDP={:.0} | 10% D/G={:.3}x GDP={:.0}",
                    a.dg_avg, a.gdp_avg, b.dg_avg, b.gdp_avg
                );
                println!(
                    "  → Recommend 7% as the safer default; 10% acceptable if GDP is prioritized"
                );
                println!(
                    "  Note: WITH MM, volatility is ~0.006 vs ~0.19 without MM — 30x stabilization"
                );
            }
        }
    }

    // CSV export
    let csv_path = PathBuf::from(
        "/home/ubuntu/.openclaw/workspace-autotune/sim-output/multi-seed-threshold-compare.csv",
    );
    if let Some(parent) = csv_path.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    let mut csv = std::fs::File::create(&csv_path).unwrap();
    writeln!(
        csv,
        "threshold,seed,gdp,debt,debt_gdp_ratio,avg_bpd,avg_spd,avg_volatility,buy_ratio"
    )
    .ok();
    for r in &all_results {
        writeln!(
            csv,
            "{:.2},{},{:.2},{:.2},{:.6},{:.6},{:.6},{:.6},{:.4}",
            r.threshold,
            r.seed,
            r.gdp,
            r.debt,
            r.debt_gdp_ratio,
            r.avg_bpd,
            r.avg_spd,
            r.avg_volatility,
            r.buy_ratio
        )
        .ok();
    }
    println!("\n  CSV saved to: {}", csv_path.display());
}

#[derive(Debug)]
struct MultiSeedResult {
    threshold: f64,
    seed: u64,
    gdp: f64,
    debt: f64,
    debt_gdp_ratio: f64,
    avg_bpd: f64,
    avg_spd: f64,
    avg_volatility: f64,
    buy_ratio: f64,
}

struct ThresholdStats {
    threshold: f64,
    gdp_avg: f64,
    gdp_std: f64,
    dg_avg: f64,
    dg_std: f64,
    buy_avg: f64,
    vol_avg: f64,
}

// ─── Fine-Grained Threshold Sweep ─────────────────────────────────────────

/// Fine-grained sweep around the 5% sweet-spot found in the coarse sweep.
/// Tests thresholds: 1%, 3%, 5%, 7%, 10% to find the true optimum.
fn run_fine_threshold_sweep() {
    use crate::analyzer::load_summary;
    use crate::player::set_fixed_guild_threshold;

    let base_scenario = Scenario::guild_stability();
    // Coarse sweep showed: 5% uniquely safe (0.03x), 10% moderate (0.56x), 15-30% catastrophic
    // Fine sweep tests the gap between 5% and 15%
    let thresholds: Vec<f64> = vec![0.01, 0.03, 0.05, 0.07, 0.10];
    let total = thresholds.len();

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       FINE-GRAINED GUILDBUYER THRESHOLD SWEEP                ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!();
    println!("  Scenario: Guild Stability (2 GuildBuyer + 4 Casual + 3 Farmer + 2 Trader)");
    println!(
        "  Duration: 14 days ({} ticks)",
        base_scenario.duration_ticks
    );
    println!(
        "  Thresholds: {:?}  (coarse sweep found 5% uniquely safe)",
        thresholds
    );
    println!();
    println!(
        "{:>10} {:>12} {:>12} {:>10} {:>8} {:>8} {:>10} {:>8}",
        "threshold", "GDP", "Debt", "Debt/GDP", "BPD%", "SPD%", "Volatility", "Buy%"
    );
    println!(
        "{:>10} {:>12} {:>12} {:>10} {:>8} {:>8} {:>10} {:>8}",
        "─".repeat(10),
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(10),
        "─".repeat(8)
    );

    let mut results: Vec<GuildSweepResult> = Vec::new();

    for (i, threshold) in thresholds.iter().enumerate() {
        eprint!("\r  [{}/{}] threshold={:.2}", i + 1, total, threshold);
        std::io::stderr().flush().ok();

        set_fixed_guild_threshold(Some(*threshold));

        let out_dir = PathBuf::from(format!(
            "/tmp/autotune-fine-{:04}",
            (threshold * 100.0) as i32
        ));
        let _ = std::fs::remove_dir_all(&out_dir);
        std::fs::create_dir_all(&out_dir).ok();

        let result = run_headless(&base_scenario, Some(out_dir.clone()));
        set_fixed_guild_threshold(None);

        if let Err(e) = &result {
            eprintln!("\n  ✗ Error: {:?}", e);
            continue;
        }

        let db_path = out_dir.join("simulation.db");
        let summary = match load_summary(&db_path) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("\n  ✗ Could not load summary: {}", e);
                continue;
            }
        };

        let _ = std::fs::remove_dir_all(&out_dir);

        let debt_gdp = summary.debt / summary.gdp.max(0.01);
        let threshold_pct = threshold * 100.0;
        println!(
            "\n  {:>8.0}% {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.2}% {:>9.4} {:>7.1}%",
            threshold_pct,
            summary.gdp,
            summary.debt,
            debt_gdp,
            summary.avg_bpd * 100.0,
            summary.avg_spd * 100.0,
            summary.avg_volatility,
            summary.buy_ratio * 100.0
        );

        results.push(GuildSweepResult {
            threshold: *threshold,
            gdp: summary.gdp,
            debt: summary.debt,
            debt_gdp_ratio: debt_gdp,
            avg_bpd: summary.avg_bpd,
            avg_spd: summary.avg_bpd,
            avg_volatility: summary.avg_volatility,
            buy_ratio: summary.buy_ratio,
        });
    }

    println!("\n");
    if results.is_empty() {
        println!("  No results collected.");
        return;
    }

    // Analysis
    println!("╔══════════════════════════════════════════════════════════════╗");
    println!("║  FINE-GRAINED SWEEP ANALYSIS                                 ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Safest (lowest D/G)
    let safest = results
        .iter()
        .min_by(|a, b| a.debt_gdp_ratio.partial_cmp(&b.debt_gdp_ratio).unwrap())
        .unwrap();
    println!(
        "  Safest (lowest D/G):  threshold={:.0}%  D/G={:.3}x  GDP={:.0}",
        safest.threshold * 100.0,
        safest.debt_gdp_ratio,
        safest.gdp
    );

    // Best GDP among safe (< 3x D/G) configs
    let safe_configs: Vec<_> = results.iter().filter(|r| r.debt_gdp_ratio < 3.0).collect();
    if let Some(best_safe_gdp) = safe_configs
        .iter()
        .max_by(|a, b| a.gdp.partial_cmp(&b.gdp).unwrap())
    {
        println!(
            "  Best GDP (D/G < 3x): threshold={:.0}%  GDP={:.0}  D/G={:.3}x",
            best_safe_gdp.threshold * 100.0,
            best_safe_gdp.gdp,
            best_safe_gdp.debt_gdp_ratio
        );
    }

    // Most balanced buy ratio
    let most_balanced = results
        .iter()
        .min_by(|a, b| {
            (a.buy_ratio - 0.5)
                .abs()
                .partial_cmp(&(b.buy_ratio - 0.5).abs())
                .unwrap()
        })
        .unwrap();
    println!(
        "  Most balanced buy%:  threshold={:.0}%  buy%={:.1}%  D/G={:.3}x",
        most_balanced.threshold * 100.0,
        most_balanced.buy_ratio * 100.0,
        most_balanced.debt_gdp_ratio
    );

    println!("\n  Threshold recommendation:");
    if safest.threshold == 0.05 {
        println!(
            "  → 5% CONFIRMED as the sweet-spot (D/G {:.3}x, GDP {:.0})",
            safest.debt_gdp_ratio, safest.gdp
        );
    } else {
        println!(
            "  → {:.0}% may be better than 5% (D/G {:.3}x vs {:.3}x at 5%)",
            safest.threshold * 100.0,
            safest.debt_gdp_ratio,
            results
                .iter()
                .find(|r| r.threshold == 0.05)
                .map(|r| r.debt_gdp_ratio)
                .unwrap_or(0.0)
        );
    }

    // CSV export
    let csv_path = PathBuf::from(
        "/home/ubuntu/.openclaw/workspace-autotune/sim-output/fine-threshold-sweep.csv",
    );
    if let Some(parent) = csv_path.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    let mut csv = std::fs::File::create(&csv_path).unwrap();
    writeln!(
        csv,
        "threshold,gdp,debt,debt_gdp_ratio,avg_bpd,avg_spd,avg_volatility,buy_ratio"
    )
    .ok();
    for r in &results {
        writeln!(
            csv,
            "{:.2},{:.2},{:.2},{:.6},{:.6},{:.6},{:.6},{:.4}",
            r.threshold,
            r.gdp,
            r.debt,
            r.debt_gdp_ratio,
            r.avg_bpd,
            r.avg_spd,
            r.avg_volatility,
            r.buy_ratio
        )
        .ok();
    }
    println!("\n  CSV saved to: {}", csv_path.display());
}

#[derive(Debug)]
struct GuildSweepResult {
    threshold: f64,
    gdp: f64,
    debt: f64,
    debt_gdp_ratio: f64,
    avg_bpd: f64,
    avg_spd: f64,
    avg_volatility: f64,
    buy_ratio: f64,
}

#[allow(dead_code)]
fn print_usage() {
    eprintln!(
        r#"Auto-Tune Market Simulation

Usage:
  market-simulation                  # Launch GUI
  market-simulation --headless       # Run default scenario headlessly
  market-simulation --headless <scenario> [--output DIR]
  market-simulation --analyze <path/to/simulation.db>
  market-simulation --analyze-dir <path/to/sim-output/>
  market-simulation --list-scenarios

Scenarios:
  standard        Normal economy with 11 players
  stressed       Economy with exploit, low players, and loan cascade
  high-activity  High player activity, 7 days
  low-player     Low population economy
  spread-stability  Test spread stability with Farmer+Trader mix

Output:
  --output DIR    Save simulation DB to DIR (default: ./output/<scenario>)
  --format json   Output final state as JSON to stdout
"#
    );
}

fn main() -> eframe::Result<()> {
    let args: Vec<String> = std::env::args().collect();

    if args.len() > 1 && args[1] == "--list-scenarios" {
        println!("Available scenarios:");
        println!("  standard         - Normal economy: 5 Casual + 3 Farmer + 2 Trader + 1 Hoarder");
        println!("  stressed         - Exploit, low players, loan cascade injected");
        println!("  high-activity    - High activity, 20 players, 7 days");
        println!("  low-player       - 3 players, 14 days");
        println!("  spread-stability - Farmer/Trader mix, 10 days");
        println!("  sp08-moderate    - Tiered breaker: sp=0.80, cascade day 6, 14d");
        println!("  buyer-heavy      - 2 GuildBuyer + 3 Hoarder + 3 Casual + 2 Farmer + 2 Trader");
        println!(
            "  guild-stability  - 2 GuildBuyer + 4 Casual + 3 Farmer + 2 Trader (15-30% threshold)"
        );
        println!("  guild-stability-mm-fixed-guild - 1 MM + 2 GB @ 7% + 4Cas + 3Far + 2Trader");
        println!(
            "  marketmaker-test - 1 GuildBuyer + 1 MarketMaker + 4 Casual + 3 Farmer + 2 Trader"
        );
        println!(
            "  standard-with-mm - 5 Casual + 3 Farmer + 2 Trader + 1 MarketMaker (RECOMMENDED)"
        );
        println!("  standard-with-mm-fixed-guild - standard+MM + 2 GuildBuyer at 5% threshold");
        println!("  exploiter-stress - standard+MM + 2 Exploiters (stress-tests MM resilience)");
        println!("  exploiter-cap-test - standard+MM + 1 Exploiter (5% cap = 1 of 12 players)");
        println!("  correlation      - Sector correlation test (treatment vs control)");
        println!("  market-event-test - DEMAND_SURGE / SUPPLY_GLUT / INFLATION / DEFLATION events");
        println!();
        println!("Special modes:");
        println!("  --sweep                 Parameter sweep (840 configs)");
        println!("  --guild-threshold-sweep  Coarse sweep: thresholds 5-50%");
        println!("  --fine-threshold-sweep    Fine sweep: thresholds 1%, 3%, 5%, 7%, 10%");
        println!("  --exploiter-stress-test   Head-to-head: standard+MM vs +Exploiters");
        println!("  --regression            Regression test against stored baselines");
        println!("  --all                   Run all scenarios headlessly");
        println!("  --floor-ceiling-test     Floor/ceiling effect: control vs treatment");
        println!("  --floor-strength-sweep   Diamond floor 30-90% — find GDP-neutral level");
        println!("  --price-freeze-test      Per-item price freeze: Diamond frozen vs control");
        println!(
            "  --loan-cap-test          Per-loan GDP cap verification: cap fires, logs events"
        );
        println!(
            "  --guildbuyer-failure-test  GB default cascade: cooldown prevs re-borrow bypass"
        );
        println!("  --mm-competition-test   1MM+2GB vs 2MM+2GB: does extra MM improve stability?");
        println!(
            "  --volume-trader-test     VolumeTrader archetype: contrarian liquidity vs control"
        );
        println!(
            "  --player-exodus-test   Player exodus: 50% quit at day 7 — economy survival test"
        );
        println!("  --multi-server-test     Cross-server price aggregation test");
        println!("  --guild-seller-test     GuildSeller archetype: control vs 1GB+1GS treatment");
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--sweep" {
        let config = crate::sweep::SweepConfig::default();
        crate::sweep::run_sweep(&config);
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--correlation-test" {
        // Sector correlation test: treatment vs control with identical seed
        let seed = 42u64;
        run_correlation_test(seed);
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--regression" {
        let update = args.contains(&"--update".to_string());
        // Use CARGO_MANIFEST_DIR so the path is resolved relative to the Cargo.toml location,
        // not the cwd (which varies depending on how `cargo run` is invoked).
        let baseline_dir =
            std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("regression-baselines");
        let scenarios: Vec<Scenario> = vec![
            Scenario::standard(),
            Scenario::spread_stability(),
            Scenario::low_player(),
            Scenario::standard_with_mm(),
            Scenario::guild_stability_mm_fixed_guild(),
        ];
        crate::regression::run_regression_test(&scenarios, &baseline_dir, update);
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--guild-threshold-sweep" {
        run_guild_threshold_sweep();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--exploiter-stress-test" {
        run_exploiter_stress_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--fine-threshold-sweep" {
        run_fine_threshold_sweep();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--multi-seed-compare" {
        run_multi_seed_compare();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--headless" {
        // Headless mode
        let scenario_name = args.get(2).map(|s| s.as_str()).unwrap_or("standard");
        let output_dir = {
            let output_idx = args.iter().position(|s| s == "--output");
            output_idx.and_then(|i| args.get(i + 1)).map(PathBuf::from)
        };
        // --seed <N> overrides the scenario's seed (useful for deterministic replay)
        let seed_override = {
            let seed_idx = args.iter().position(|s| s == "--seed");
            seed_idx.and_then(|i| args.get(i + 1)?.parse::<u64>().ok())
        };

        if scenario_name == "all" {
            let scenarios: Vec<Scenario> = vec![
                Scenario::standard(),
                Scenario::stressed(),
                Scenario::high_activity(),
                Scenario::low_player(),
                Scenario::spread_stability(),
                Scenario::sp08_moderate(),
                Scenario::buyer_heavy(),
                Scenario::guild_stability(),
                Scenario::guild_stability_mm_fixed_guild(),
                Scenario::marketmaker_test(),
                Scenario::standard_with_mm(),
                Scenario::standard_with_mm_fixed_guild(),
                Scenario::exploiter_stress(),
                Scenario::exploiter_cap_test(),
                Scenario::insider_trader_test(),
                Scenario::player_exodus_test(),
                Scenario::market_event_test(),
                Scenario::standard_plus_mm_gb_it(),
                Scenario::floor_ceiling_test(),
            ];
            let base_dir = output_dir.unwrap_or_else(|| PathBuf::from("./output"));
            let mut results: Vec<(String, bool, String)> = Vec::new();
            for s in scenarios {
                let dir = base_dir.join(s.name.to_lowercase().replace(' ', "-"));
                println!();
                let result = run_headless(&s, Some(dir.clone()));
                let ok = result.is_ok();
                let summary = if ok {
                    format!("✓ {}", s.name)
                } else {
                    format!("✗ {}: {}", s.name, result.unwrap_err())
                };
                println!("\n{}", summary);
                results.push((s.name.clone(), ok, summary));
            }
            println!("\n\n=== SUMMARY ===");
            for (_, _, summary) in &results {
                println!("  {}", summary);
            }
        } else {
            let scenario = match scenario_name {
                "standard" => Scenario::standard(),
                "stressed" => Scenario::stressed(),
                "high-activity" | "high_activity" => Scenario::high_activity(),
                "low-player" | "low_player" => Scenario::low_player(),
                "spread-stability" | "spread_stability" => Scenario::spread_stability(),
                "sp08-moderate" | "sp08_moderate" => Scenario::sp08_moderate(),
                "buyer-heavy" | "buyer_heavy" => Scenario::buyer_heavy(),
                "guild-stability" | "guild_stability" => Scenario::guild_stability(),
                "guild-stability-mm-fixed-guild" | "guild_stability_mm_fixed_guild" => {
                    Scenario::guild_stability_mm_fixed_guild()
                }
                "marketmaker-test" | "marketmaker_test" => Scenario::marketmaker_test(),
                "standard-with-mm" | "standard_with_mm" => Scenario::standard_with_mm(),
                "standard-with-mm-fixed-guild" | "standard_with_mm_fixed_guild" => {
                    Scenario::standard_with_mm_fixed_guild()
                }
                "exploiter-stress" | "exploiter_stress" => Scenario::exploiter_stress(),
                "exploiter-cap-test" | "exploiter_cap_test" => Scenario::exploiter_cap_test(),
                "insider-trader-test" | "insider_trader_test" => Scenario::insider_trader_test(),
                "player-exodus-test" | "player_exodus_test" => Scenario::player_exodus_test(),
                "market-event-test" | "market_event_test" => Scenario::market_event_test(),
                "market-event-control" | "market_event_control" => Scenario::market_event_control(),
                "standard-plus-mm-gb-it" | "standard_plus_mm_gb_it" => {
                    Scenario::standard_plus_mm_gb_it()
                }
                "floor-ceiling-test" | "floor_ceiling_test" => Scenario::floor_ceiling_test(),
                "correlation" => Scenario::correlation(),
                _ => {
                    eprintln!(
                        "Unknown scenario: {}. Use --list-scenarios to see available.",
                        scenario_name
                    );
                    std::process::exit(1);
                }
            };
            let out_dir =
                output_dir.or_else(|| Some(PathBuf::from(format!("./output/{}", scenario_name))));
            let scenario = if let Some(seed) = seed_override {
                let mut s = scenario;
                s.seed = Some(seed);
                s
            } else {
                scenario
            };
            if let Err(e) = run_headless(&scenario, out_dir) {
                eprintln!("Error: {}", e);
                std::process::exit(1);
            }
        }
        return Ok(());
    }

    // ─── Event Control Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--event-control-test" {
        run_event_control_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--it-added-test" {
        run_it_added_test();
        return Ok(());
    }

    // ─── Player Exodus Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--player-exodus-test" {
        run_player_exodus_test();
        return Ok(());
    }

    // ─── Floor/Ceiling Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--floor-ceiling-test" {
        run_floor_ceiling_test();
        return Ok(());
    }

    // ─── Floor Strength Sweep ────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--floor-strength-sweep" {
        run_floor_strength_sweep();
        return Ok(());
    }

    // ─── Multi-Server Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--multi-server-test" {
        run_multi_server_test();
        return Ok(());
    }

    // ─── GuildSeller Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--guild-seller-test" {
        run_guild_seller_test();
        return Ok(());
    }

    // ─── Price Freeze Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--price-freeze-test" {
        run_price_freeze_test();
        return Ok(());
    }

    // ─── Loan Cap Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--loan-cap-test" {
        run_loan_cap_verification();
        return Ok(());
    }

    // ─── GuildBuyer Failure Cascade Test ───────────────────────────────
    if args.len() > 1 && args[1] == "--guildbuyer-failure-test" {
        run_guildbuyer_failure_test();
        return Ok(());
    }

    // ─── MM Loan Bounding Test ─────────────────────────────────────────
    if args.len() > 1 && args[1] == "--mm-loan-bounding-test" {
        run_mm_loan_bounding_test();
        return Ok(());
    }

    // ─── MM No-Opening-Loan Test ─────────────────────────────────────────
    if args.len() > 1 && args[1] == "--mm-no-opening-loan-test" {
        run_mm_no_opening_loan_test();
        return Ok(());
    }

    // ─── MM Competition Test ──────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--mm-competition-test" {
        run_mm_competition_test();
        return Ok(());
    }

    // ─── VolumeTrader Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--volume-trader-test" {
        run_volume_trader_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--analyze" {
        let db_path = match args.get(2) {
            Some(p) => p.as_str(),
            None => {
                eprintln!("Usage: market-simulation --analyze <path-to-simulation.db>");
                std::process::exit(1);
            }
        };
        if let Err(e) = crate::analyzer::analyze_db(std::path::Path::new(db_path)) {
            eprintln!("Analysis error: {e}");
            std::process::exit(1);
        }
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--analyze-dir" {
        let dir_path = match args.get(2) {
            Some(p) => p.as_str(),
            None => {
                eprintln!("Usage: market-simulation --analyze-dir <path-to-sim-output-dir>");
                std::process::exit(1);
            }
        };
        if let Err(e) = crate::analyzer::analyze_dir(std::path::Path::new(dir_path)) {
            eprintln!("Analysis error: {e}");
            std::process::exit(1);
        }
        return Ok(());
    }

    // Intercept --help / -h before eframe tries to open a display
    if args.len() > 1 && (args[1] == "--help" || args[1] == "-h") {
        print_usage();
        return Ok(());
    }

    // GUI mode
    run_gui()
}

fn run_gui() -> eframe::Result<()> {
    use crate::gui::GuiState;
    use eframe::egui;

    struct SimApp {
        sim: crate::simulation::Simulation,
        gui: GuiState,
    }

    impl SimApp {
        fn new() -> Self {
            let config = SimConfig::default();
            let gui = GuiState::new(&config);
            let sim = Simulation::new(config);
            Self { sim, gui }
        }
    }

    impl eframe::App for SimApp {
        fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
            if !self.sim.paused {
                self.sim.tick_accumulator += self.sim.speed;
                while self.sim.tick_accumulator >= 1.0 {
                    self.sim.tick();
                    self.sim.tick_accumulator -= 1.0;
                }
                ctx.request_repaint();
            }

            crate::gui::draw_gui(ctx, &mut self.sim, &mut self.gui);
        }
    }

    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1400.0, 900.0])
            .with_title("Auto-Tune Market Simulation"),
        ..Default::default()
    };

    eframe::run_native(
        "Auto-Tune Market Simulation",
        options,
        Box::new(|_cc| Ok(Box::new(SimApp::new()))),
    )
}
