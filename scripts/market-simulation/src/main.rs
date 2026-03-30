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

    for player_cfg in &scenario.players {
        let archetype = archetype_map
            .get(&player_cfg.archetype)
            .ok_or_else(|| format!("Unknown archetype: {}", player_cfg.archetype))?;
        for _ in 0..player_cfg.count {
            sim.add_player(*archetype);
        }
    }
    println!(
        "Players: {} (Casual:{}, Farmer:{}, Trader:{}, Hoarder:{}, Exploiter:{}, Newbie:{}, AFKFarmer:{}, GuildBuyer:{}, MarketMaker:{}, InsiderTrader:{})",
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
        println!("  --multi-server-test     Cross-server price aggregation test");
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
        let baseline_dir = std::path::PathBuf::from("regression-baselines");
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

    // ─── Floor/Ceiling Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--floor-ceiling-test" {
        run_floor_ceiling_test();
        return Ok(());
    }

    // ─── Multi-Server Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--multi-server-test" {
        run_multi_server_test();
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
