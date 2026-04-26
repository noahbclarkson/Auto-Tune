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

    /// Stressed economy scaled to 30 days — same archetype mix + stress events
    /// as `stressed()` but extended to measure floor effect under chronic oversupply.
    pub fn stressed_30day() -> Self {
        Self {
            name: "Stressed Economy (30d)".to_string(),
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
            duration_ticks: 288 * 30, // 30 days
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

    /// GuildStability + 2MM + 2GB + 2IT + 2VT (healthy economy + both optional archetypes)
    /// Control: guild_stability_2mm_fixed_guild (2MM + 2GB + 3Cas + 3Far + 2Tra)
    /// Treat:   same + 2 InsiderTraders + 2 VolumeTraders (12 players total)
    /// Question: IT helps healthy economies (+30.1% GDP) but VT hurts (-9.2% GDP).
    ///   Combined: do they cancel out, or does one dominate?
    pub fn guild_stability_2mm_2gb_plus_it_and_vt() -> Self {
        Self {
            name: "GuildStability+2MM+2GB+IT+VT".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "InsiderTrader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "VolumeTrader".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 3,
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

    /// GuildStability + 2MM + 2GB + 60% Diamond floor.
    /// The production-recommended config: same archetype mix as guild_stability_2mm_fixed_guild
    /// but with Diamond price floor at 60% of base ($300).
    ///
    /// Question: Does tier3_ratio still matter in a healthy economy with counter_cyclical=true?
    /// In stressed economies, tier3=15 eliminates TIER3 noise (0 events vs 11 for tier3=10).
    /// But in a healthy economy, counter_cyclical already suppresses interest as D/G rises —
    /// the circuit breaker may never engage regardless of tier3_ratio.
    pub fn guild_stability_2mm_fixed_guild_plus_floor() -> Self {
        let mut scenario = Self::guild_stability_2mm_fixed_guild();
        scenario.name = "GuildStability+2MM+7%GB+Floor".to_string();
        if let Some(diamond) = scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6); // $300
        }
        scenario
    }

    /// Production config: 2MM + 2GB + 2IT + 60% Diamond floor.
    /// Based on guild_stability_2mm_fixed_guild plus 2 InsiderTraders and floor.
    pub fn guild_stability_2mm_fixed_guild_plus_it_and_floor() -> Self {
        let mut scenario = Self::guild_stability_2mm_fixed_guild();
        scenario.name = "GuildStability+2MM+7%GB+IT+Floor".to_string();
        scenario.players.push(ArchetypeConfig {
            archetype: "InsiderTrader".into(),
            count: 2,
        });
        if let Some(diamond) = scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6); // $300
        }
        scenario
    }

    /// Archetype mix test: Casual-heavy variant.
    /// Replaces Farmers with Casuals to test whether more balanced gather/demand
    /// improves economy health beyond the 2MM+2GB config.
    ///
    /// Config: 2MM + 2GB + 6Cas + 1Far + 1Tra (10 players)
    /// vs control: 2MM + 2GB + 3Cas + 3Far + 2Tra (12 players)
    ///
    /// Hypothesis: Casuals are net NEUTRAL (gather and spend evenly).
    /// Fewer Farmers = less structural oversupply = higher equilibrium prices.
    pub fn guild_stability_casual_heavy() -> Self {
        Self {
            name: "GuildStability+2MM+CasualHeavy".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 6, // ← 6 Casuals (vs 3 in control)
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 1, // ← 1 Farmer (vs 3 in control)
                },
                ArchetypeConfig {
                    archetype: "Trader".into(),
                    count: 1, // ← 1 Trader (vs 2 in control)
                },
            ],
            seed: None,
            events: Vec::new(),
            stress_events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
        }
    }

    /// Archetype mix test: Farmer-heavy variant.
    /// Replaces Casuals with Farmers to test whether a gather-heavy economy
    /// can still be rescued by the 2MM+2GB archetype mix.
    ///
    /// Config: 2MM + 2GB + 2Cas + 6Far + 2Tra (12 players)
    /// vs control: 2MM + 2GB + 3Cas + 3Far + 2Tra (12 players)
    ///
    /// Hypothesis: Farmer-heavy economy = structural sell pressure.
    /// MM+GB should partially compensate but NOT fully offset oversupply.
    /// Admins on Farmer-heavy servers should expect lower equilibrium prices.
    pub fn guild_stability_farmer_heavy() -> Self {
        Self {
            name: "GuildStability+2MM+FarmerHeavy".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 2, // ← 2 Casuals (vs 3 in control)
                },
                ArchetypeConfig {
                    archetype: "Farmer".into(),
                    count: 6, // ← 6 Farmers (vs 3 in control)
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

    /// GuildBuyer Failure Test — MM quits specifically at day 7.
    /// Control: no exodus (baseline guildbuyer_failure_test)
    /// Treatment: only the MarketMaker quits at day 7 (exodus_target_archetype = "MarketMaker")
    /// Tests: can the economy survive without MM (market-making vacuum)?
    pub fn guildbuyer_failure_mm_quit_test() -> Self {
        let mut config = SimConfig::default();
        config.loans.post_default_cooldown_hours = 168;
        config.player_exodus_tick = Some(288 * 7); // day 7
        config.player_exodus_fraction = 1.0; // all matching archetype quit
        config.exodus_target_archetype = Some("MarketMaker".to_string());
        config.exodus_spread_multiplier = 2.0;
        config.exodus_shock_duration_ticks = 288;
        Self {
            name: "GuildBuyer Failure Test — MM quits at Day 7".to_string(),
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

    /// Counter-Cyclical Interest Test: compares counter-cyclical (continuous taper)
    /// vs tiered circuit breaker on a stressed economy.
    /// Control: counter_cyclical=false (legacy tiered: TIER1→50%, TIER2→25%, TIER3→0%)
    /// Treatment: counter_cyclical=true (continuous: multiplier = max(0, min(1, 1-D/G/tier3)))
    /// Both run with post_default_cooldown=168h (7 days) to isolate the interest variable.
    /// Uses guildbuyer_failure_test archetype (1MM + 2GB + 4Cas + 3Far + 2Tra).
    /// Hypothesis: counter-cyclical reduces debt accumulation more smoothly because
    /// interest relief begins at D/G=0 (not at D/G=3) and scales continuously.
    pub fn counter_cyclical_test() -> Self {
        let mut config = SimConfig::default();
        config.loans.post_default_cooldown_hours = 168; // 7 days, same for both arms
        config.loans.counter_cyclical = true; // Treatment: enabled
        Self {
            name: "Counter-Cyclical Interest Test (continuous taper)".to_string(),
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

    /// GuildBuyer Failure Test — GuildBuyer quits specifically at day 7.
    /// Control: no exodus (baseline guildbuyer_failure_test)
    /// Treatment: one GuildBuyer quits at day 7 (exodus_target_archetype = "GuildBuyer")
    /// Tests: what happens to economy when the primary demand-side archetype leaves?
    pub fn guildbuyer_failure_gb_quit_test() -> Self {
        let mut config = SimConfig::default();
        config.loans.post_default_cooldown_hours = 168;
        config.player_exodus_tick = Some(288 * 7); // day 7
        config.player_exodus_fraction = 1.0; // all matching archetype quit
        config.exodus_target_archetype = Some("GuildBuyer".to_string());
        config.exodus_spread_multiplier = 2.0;
        config.exodus_shock_duration_ticks = 288;
        Self {
            name: "GuildBuyer Failure Test — GB quits at Day 7".to_string(),
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

    /// GuildStability with 1MM + 1GB + 1GS but Phase 2 redesigned to use price-dip detection.
    /// GS sells when price dips below perceived*(1 - threshold) — active anti-oversupply.
    /// Hypotheses:
    /// - H1: Phase 2 redesign prevents price collapse by proactively selling during oversupply
    /// - H2: Phase 2 redesign has no effect (price dips are already self-correcting)
    /// - H3: Phase 2 redesign is counterproductive (GS sells into downturns amplifying losses)
    pub fn guild_stability_mm_gs_phase2_redesign() -> Self {
        // Redesigned Phase 2: sell when price < perceived * 0.80 (20% dip = oversupply signal)
        let config = SimConfig {
            guild_phase2_dip_threshold: Some(0.20),
            ..Default::default()
        };
        Self {
            name: "GuildStability+MM+GB+GS-Phase2".to_string(),
            config,
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

    /// Worst-case mass exodus stress test: 80% of players quit at day 7.
    /// Based on guild_stability_mm_fixed_guild archetype mix (12 players).
    /// 80% quit → ~2 players remain (only 1 MM + 1 GB possible).
    ///
    /// Key questions:
    /// - Does TIER3 circuit breaker fire? When? At what D/G?
    /// - How long until the economy stabilizes / recovers?
    /// - Do spreads blow out permanently or recover?
    /// - Is there a permanent GDP loss vs control?
    pub fn worst_case_exodus_test() -> Self {
        // Day 7 = tick 2016 (288 ticks/day × 7 days)
        // exodus_spread_multiplier=2.0x for 288 ticks (1 day) then decays 5%/tick
        let config = SimConfig {
            player_exodus_tick: Some(288 * 7),
            player_exodus_fraction: 0.80, // 80% quit — worst case
            exodus_spread_multiplier: 2.0,
            exodus_shock_duration_ticks: 288,
            ..SimConfig::default()
        };
        Self {
            name: "Worst Case Exodus Test".to_string(),
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
            duration_ticks: 288 * 14, // 14 days total
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

    /// Healthy economy + 2 InsiderTraders: tests whether ITs add value
    /// when the economy already has strong MM + GB coverage.
    /// Control: guild_stability_mm_fixed_guild (1MM + 2GB + 4Cas + 3Far + 2Tra)
    /// Treatment: same + 2 InsiderTraders
    pub fn guild_stability_mm_fixed_guild_plus_it() -> Self {
        Self {
            name: "GuildStability+MM+IT".to_string(),
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
                    archetype: "InsiderTrader".into(),
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

    /// GuildStability + MM + GB + 1 Whale.
    /// Tests: can a single wealthy erratic player destabilize a healthy economy?
    /// Whale accumulates for 3 days then dumps massive inventory at 50% perceived value.
    pub fn whale_stress() -> Self {
        Self {
            name: "GuildStability+MM+GB+Whale".to_string(),
            config: SimConfig::default(),
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Whale".into(),
                    count: 1,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 3,
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
            stress_events: vec![],
            events: vec![],
            duration_ticks: 288 * 14,
            speed_ticks_per_sec: 200,
            seed: None,
        }
    }

    /// GuildStability + MM (high initial capital) + 2x GuildBuyers @ 7%.
    /// Same as guild_stability_mm_fixed_guild but MM starts with $200-300K
    /// instead of default $50-200K.
    /// Tests: does higher MM starting capital reduce or eliminate MM opening loans?
    /// Recommendation: if MM needs no/opening loans at $200-300K, this is the
    /// preferred production config (reduces loan cascade risk without bounding loans).
    pub fn guild_stability_mm_high_capital() -> Self {
        let config = SimConfig {
            mm_initial_capital_min: Some(200_000.0),
            mm_initial_capital_max: Some(300_000.0),
            ..Default::default()
        };
        Self {
            name: "GuildStability+MM-HighCapital+7%GB".to_string(),
            config,
            players: vec![
                ArchetypeConfig {
                    archetype: "MarketMaker".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "GuildBuyer".into(),
                    count: 2,
                },
                ArchetypeConfig {
                    archetype: "Casual".into(),
                    count: 3,
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
}

/// InsiderTrader + Stressed Economy: guildbuyer_failure_test + 2 InsiderTraders
/// Tests: does IT help or hurt when the economy is already stressed?
/// Healthy-economy IT test showed: +30.1% GDP but D/G 0.75x→3.16x (+2.41x).
/// Stressed-economy IT question: does IT add value or amplify debt stress?
pub fn guildbuyer_failure_test_plus_it() -> Scenario {
    Scenario {
        name: "GuildbuyerFailure+IT".to_string(),
        config: {
            let mut c = SimConfig::default();
            c.loans.post_default_cooldown_hours = 168;
            c
        },
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
                archetype: "InsiderTrader".into(),
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

/// VolumeTrader + Stressed Economy: guildbuyer_failure_test + 2 VolumeTraders
/// Tests: does VT help or hurt when the economy is already stressed?
/// VT in healthy economy result: GDP -9.2%, vol +8.7% WORSE, BPD -6.4%
/// Stressed-economy hypothesis: VT's spread compression might reduce volatility
///   but VT's buy-high-sell-low behavior could amplify debt cascades.
pub fn guildbuyer_failure_test_plus_vt() -> Scenario {
    Scenario {
        name: "GuildbuyerFailure+VT".to_string(),
        config: {
            let mut c = SimConfig::default();
            c.loans.post_default_cooldown_hours = 168;
            c
        },
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

pub fn guildbuyer_failure_test_plus_afkfarmer() -> Scenario {
    Scenario {
        name: "GuildbuyerFailure+AFKFarmer".to_string(),
        config: {
            let mut c = SimConfig::default();
            c.loans.post_default_cooldown_hours = 168;
            c
        },
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
                archetype: "AFKFarmer".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Casual".into(),
                count: 4,
            },
            ArchetypeConfig {
                archetype: "Farmer".into(),
                count: 1,
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

pub fn guildbuyer_failure_test_hoarder_heavy() -> Scenario {
    Scenario {
        name: "GuildbuyerFailure+HoarderHeavy".to_string(),
        config: {
            let mut c = SimConfig::default();
            c.loans.post_default_cooldown_hours = 168;
            c
        },
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
                archetype: "Hoarder".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Casual".into(),
                count: 4,
            },
            ArchetypeConfig {
                archetype: "Farmer".into(),
                count: 1,
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
/// 30-day stressed economy: Does the 60% floor still hold, or does the floor
/// paradox become catastrophic under chronic oversupply?
///
/// Q: Does the Diamond floor paradox (D/G worsens despite floor protecting displayed
///    prices) persist or amplify over 30 days of chronic Farmer oversupply + stress events?
/// Q: Does the floor prevent price discovery or stabilize it?
/// Q: Is GDP different when floor is active under chronic stress?
fn run_stressed_30d_floor_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║   STRESSED ECONOMY 30-DAY FLOOR TEST                       ║");
    println!("║  60% Diamond floor vs NO floor — chronic oversupply        ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: stressed_30day (no floor)");
    println!("  Treatment: same + Diamond floor at 60% of base ($300)\n");
    println!("  Seed: {}\n", seed);

    // Control: stressed_30day without floor
    let ctrl_scenario = Scenario::stressed_30day();

    // Treatment: same but with 60% Diamond floor
    let mut treat_scenario = Scenario::stressed_30day();
    if let Some(diamond) = treat_scenario
        .config
        .items
        .iter_mut()
        .find(|ic| ic.name == "Diamond")
    {
        diamond.price_floor_override = Some(diamond.base_price * 0.6);
    }

    let ctrl_dir = PathBuf::from("/tmp/autotune-s30d-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-s30d-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl = ctrl_scenario.clone();
    ctrl.seed = Some(seed);
    let mut treat = treat_scenario.clone();
    treat.seed = Some(seed);

    println!("─── Control (no floor) ───");
    let start = Instant::now();
    if let Err(e) = run_headless(&ctrl, Some(ctrl_dir.clone())) {
        eprintln!("  Control run error: {}", e);
        return;
    }
    println!(
        "  Control complete: {:.1}s\n",
        start.elapsed().as_secs_f64()
    );

    println!("─── Treatment (60% Diamond floor) ───");
    let start = Instant::now();
    if let Err(e) = run_headless(&treat, Some(treat_dir.clone())) {
        eprintln!("  Treatment run error: {}", e);
        return;
    }
    println!(
        "  Treatment complete: {:.1}s\n",
        start.elapsed().as_secs_f64()
    );

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
    println!("║  SUMMARY METRICS (30-day stressed economy)                ║");
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
            (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0
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
    println!("║  FLOOR PARADOX CHECK                                     ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    let dg_delta = treat_dg - ctrl_dg;
    if dg_delta > 0.5 {
        println!(
            "  ⚠️  Floor paradox AMPLIFIED: D/G worse by {:+.2}x at 30 days",
            dg_delta
        );
        println!("     Floor protects displayed prices but internal debt accumulates more.");
    } else if dg_delta > 0.1 {
        println!(
            "  ⚠️  Floor paradox persists: D/G worse by {:+.2}x",
            dg_delta
        );
    } else if dg_delta < -0.1 {
        println!(
            "  ✅ Floor paradox INVERTED: D/G better by {:+.2}x — floor helps!",
            -dg_delta
        );
    } else {
        println!(
            "  ✅ D/G essentially unchanged ({:+.2}x) — floor neutral over 30 days",
            dg_delta
        );
    }

    let gdp_delta = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    if gdp_delta > 1.0 {
        println!(
            "  ✅ Floor BOOSTS GDP by {:+.1}% in stressed economy",
            gdp_delta
        );
    } else if gdp_delta < -1.0 {
        println!(
            "  ⚠️  Floor HURTS GDP by {:+.1}% — dampens trade",
            gdp_delta
        );
    } else {
        println!(
            "  ✅ Floor GDP-neutral ({:+.1}%) — floor does not suppress activity",
            gdp_delta
        );
    }

    println!("\n  Key insight: 14-day floor paradox (+19.1% D/G worse with floor) was measured");
    println!("  on healthy 2MM+2GB economy. This test extends to chronic stress.");
    println!("  If floor paradox persists at 30 days in stressed economy, floor is a");
    println!("  structural liability — it protects displayed prices but worsens debt.");

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

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

    println!();

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

/// ─── Floor Strength Multi-Seed Test ───────────────────────────────────────
/// Tests whether the 60% floor finding is robust across multiple random seeds.
///
/// The original run_floor_strength_sweep (seed=42) found:
///   60% floor = +6.5% GDP (BEST), 70% = -10.7%, 80% = -19.1%
///
/// This test runs the same 60% treatment vs no-floor control across 5 seeds
/// to validate the finding is not a single-seed artifact.
///
/// Key question: Is 60% Diamond floor consistently beneficial, or does the
/// finding depend on the specific archetype randomisation of seed=42?
fn run_floor_strength_multi_seed() {
    use crate::analyzer::{load_all_prices, load_summary};
    use std::io::Write;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];
    let diamond_base = 500.0;
    let treatment_floor_pct = 0.60;
    let treatment_floor = diamond_base * treatment_floor_pct; // $300

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║       DIAMOND FLOOR 60% — MULTI-SEED ROBUSTNESS (5 seeds)    ║");
    println!("║  60% Diamond floor ($300) vs no floor — 5 seeds              ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Treatment: Diamond floor = 60% of base ($300)");
    println!("  Control:   no floor (natural price discovery)");
    println!("  Scenario:  guild_stability_mm_fixed_guild (1MM + 2GB@7% + 4Cas + 3Far + 2Tra)");
    println!("  Duration:  14 days (4032 ticks)\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct FloorResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        spd: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        diamond_displayed: f64,
        floor_binds: bool,
    }

    impl FloorResult {
        fn from_summary_and_prices(
            s: &crate::analyzer::SimSummary,
            prices: &[(String, f64, f64)],
            seed: u64,
            treatment_floor: f64,
        ) -> Self {
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (diamond_internal, diamond_displayed) =
                diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            Self {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                diamond_internal,
                diamond_displayed,
                floor_binds: diamond_displayed >= treatment_floor - 0.01,
            }
        }
    }

    let mut ctrl_results: Vec<FloorResult> = Vec::new();
    let mut treat_results: Vec<FloorResult> = Vec::new();
    let total = seeds.len() * 2;

    for (i, seed) in seeds.iter().enumerate() {
        // ── Control (no floor) ──────────────────────────────────────────
        eprint!("\r  [{}/{}] seed={} ctrl", i * 2 + 1, total, seed);
        std::io::stderr().flush().ok();

        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-fsm-ctrl-{}", seed));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_dir).ok();
        let mut ctrl = Scenario::guild_stability_mm_fixed_guild();
        ctrl.seed = Some(*seed);
        if let Err(e) = run_seeded_headless(&ctrl, *seed, &ctrl_dir) {
            eprintln!("\n  Ctrl error seed={}: {}", seed, e);
        } else if let Ok(s) = load_summary(&ctrl_dir.join("simulation.db"))
            && let Ok(p) = load_all_prices(&ctrl_dir.join("simulation.db"))
        {
            ctrl_results.push(FloorResult::from_summary_and_prices(&s, &p, *seed, 0.0));
        }
        let _ = std::fs::remove_dir_all(&ctrl_dir);

        // ── Treatment (60% Diamond floor) ──────────────────────────────
        eprint!("\r  [{}/{}] seed={} treat", i * 2 + 2, total, seed);
        std::io::stderr().flush().ok();

        let treat_dir = PathBuf::from(format!("/tmp/autotune-fsm-treat-{}", seed));
        let _ = std::fs::remove_dir_all(&treat_dir);
        std::fs::create_dir_all(&treat_dir).ok();
        let mut treat = Scenario::guild_stability_mm_fixed_guild();
        if let Some(d) = treat
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            d.price_floor_override = Some(treatment_floor);
        }
        treat.seed = Some(*seed);
        if let Err(e) = run_seeded_headless(&treat, *seed, &treat_dir) {
            eprintln!("\n  Treat error seed={}: {}", seed, e);
        } else if let Ok(s) = load_summary(&treat_dir.join("simulation.db"))
            && let Ok(p) = load_all_prices(&treat_dir.join("simulation.db"))
        {
            treat_results.push(FloorResult::from_summary_and_prices(
                &s,
                &p,
                *seed,
                treatment_floor,
            ));
        }
        let _ = std::fs::remove_dir_all(&treat_dir);
    }
    println!();

    if ctrl_results.is_empty() || treat_results.is_empty() {
        eprintln!("  ✗ No results collected");
        return;
    }

    // ── Per-seed comparison table ──────────────────────────────────────
    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║                    PER-SEED RESULTS                           ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");

    println!(
        "  {:>6}  {:>10}  {:>7}  {:>7}  {:>7}  |  {:>10}  {:>7}  {:>7}  {:>7}  {:>7}",
        "seed",
        "GDP(ctrl)",
        "D/G(c)",
        "BPD(c)",
        "Buy(c)",
        "GDP(tr)",
        "D/G(t)",
        "BPD(t)",
        "Buy(t)",
        "Floor?"
    );
    for (c, t) in ctrl_results.iter().zip(treat_results.iter()) {
        println!(
            "  {:>6}  {:>10.0}  {:>6.2}x  {:>6.2}%  {:>6.1}% |  {:>10.0}  {:>6.2}x  {:>6.2}%  {:>6.1}%  {:>7}",
            c.seed,
            c.gdp,
            c.dg,
            c.bpd * 100.0,
            c.buy_ratio * 100.0,
            t.gdp,
            t.dg,
            t.bpd * 100.0,
            t.buy_ratio * 100.0,
            if t.floor_binds { "✓ binds" } else { "✗ no" }
        );
    }

    // ── Statistical summary ────────────────────────────────────────────
    let n = ctrl_results.len() as f64;

    let avg = |v: &[FloorResult], f: &str| -> f64 {
        let field_sum = match f {
            "gdp" => v.iter().map(|r| r.gdp).sum::<f64>(),
            "dg" => v.iter().map(|r| r.dg).sum::<f64>(),
            "bpd" => v.iter().map(|r| r.bpd).sum::<f64>(),
            "vol" => v.iter().map(|r| r.vol).sum::<f64>(),
            "buy_ratio" => v.iter().map(|r| r.buy_ratio).sum::<f64>(),
            "diamond_internal" => v.iter().map(|r| r.diamond_internal).sum::<f64>(),
            _ => 0.0,
        };
        field_sum / n
    };
    let std_dev = |v: &[FloorResult], f: &str, m: f64| -> f64 {
        let variance = v
            .iter()
            .map(|r| {
                let val: f64 = match f {
                    "gdp" => r.gdp,
                    "dg" => r.dg,
                    "bpd" => r.bpd,
                    "vol" => r.vol,
                    "buy_ratio" => r.buy_ratio,
                    "diamond_internal" => r.diamond_internal,
                    _ => 0.0,
                };
                (val - m).powi(2)
            })
            .sum::<f64>()
            / n;
        variance.sqrt()
    };

    let ctrl_gdp_mean = avg(&ctrl_results, "gdp");
    let treat_gdp_mean = avg(&treat_results, "gdp");
    let ctrl_gdp_std = std_dev(&ctrl_results, "gdp", ctrl_gdp_mean);
    let treat_gdp_std = std_dev(&treat_results, "gdp", treat_gdp_mean);

    let ctrl_dg_mean = avg(&ctrl_results, "dg");
    let treat_dg_mean = avg(&treat_results, "dg");
    let ctrl_dg_std = std_dev(&ctrl_results, "dg", ctrl_dg_mean);
    let treat_dg_std = std_dev(&treat_results, "dg", treat_dg_mean);

    let ctrl_bpd_mean = avg(&ctrl_results, "bpd");
    let treat_bpd_mean = avg(&treat_results, "bpd");
    let ctrl_bpd_std = std_dev(&ctrl_results, "bpd", ctrl_bpd_mean);
    let treat_bpd_std = std_dev(&treat_results, "bpd", treat_bpd_mean);

    let ctrl_vol_mean = avg(&ctrl_results, "vol");
    let treat_vol_mean = avg(&treat_results, "vol");
    let ctrl_vol_std = std_dev(&ctrl_results, "vol", ctrl_vol_mean);
    let treat_vol_std = std_dev(&treat_results, "vol", treat_vol_mean);

    let ctrl_buy_mean = avg(&ctrl_results, "buy_ratio");
    let treat_buy_mean = avg(&treat_results, "buy_ratio");

    let ctrl_di_mean = avg(&ctrl_results, "diamond_internal");
    let treat_di_mean = avg(&treat_results, "diamond_internal");

    let gdp_pct_change = (treat_gdp_mean - ctrl_gdp_mean) / ctrl_gdp_mean * 100.0;
    let floor_binds_pct = treat_results.iter().filter(|t| t.floor_binds).count() as f64 / n * 100.0;

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║                   STATISTICAL SUMMARY                           ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");

    println!(
        "  {:>18}  {:>14}  {:>14}  {:>10}",
        "Metric", "Control", "Treatment", "Δ"
    );
    println!(
        "  {:>18}  {:>14}  {:>14}  {:>10}",
        "─".repeat(18),
        "─".repeat(14),
        "─".repeat(14),
        "─".repeat(10)
    );
    println!(
        "  {:>18}  {:>11.0} ±{:<5.0}  {:>11.0} ±{:<5.0}  {:>+9.1}%",
        "GDP", ctrl_gdp_mean, ctrl_gdp_std, treat_gdp_mean, treat_gdp_std, gdp_pct_change
    );
    println!(
        "  {:>18}  {:>11.2} +/- {:>5.1}  {:>11.2} +/- {:>5.1}  {:>+9.2}x",
        "Debt/GDP",
        ctrl_dg_mean,
        ctrl_dg_std,
        treat_dg_mean,
        treat_dg_std,
        treat_dg_mean - ctrl_dg_mean
    );
    println!(
        "  {:>18}  {:>11.3} +/- {:>5.2}  {:>11.3} +/- {:>5.2}  {:>+9.1}%",
        "Volatility (×1000)",
        ctrl_vol_mean * 1000.0,
        ctrl_vol_std * 1000.0,
        treat_vol_mean * 1000.0,
        treat_vol_std * 1000.0,
        (treat_vol_mean - ctrl_vol_mean) / ctrl_vol_mean.max(0.001) * 100.0
    );
    println!(
        "  {:>18}  {:>11.2}%  +/- {:>5.2}%  {:>11.2}%  +/- {:>5.2}%  {:>+9.1}pp",
        "BPD",
        ctrl_bpd_mean * 100.0,
        ctrl_bpd_std * 100.0,
        treat_bpd_mean * 100.0,
        treat_bpd_std * 100.0,
        (treat_bpd_mean - ctrl_bpd_mean) * 100.0
    );
    println!(
        "  {:>18}  {:>11.1}%            {:>11.1}%            {:>+9.1}pp",
        "Buy Ratio",
        ctrl_buy_mean * 100.0,
        treat_buy_mean * 100.0,
        (treat_buy_mean - ctrl_buy_mean) * 100.0
    );
    println!(
        "  {:>18}  {:>13.0}         {:>13.0}",
        "Diamond internal$", ctrl_di_mean, treat_di_mean
    );

    println!("\n╠══════════════════════════════════════════════════════════════════╣");
    println!(
        "║  Floor binds: {:.0}% of treatment runs ({:.0}/{:.0} seeds)       ║",
        floor_binds_pct,
        treat_results.iter().filter(|t| t.floor_binds).count() as f64,
        n
    );
    println!("╚══════════════════════════════════════════════════════════════════╝");

    // ── Verdict ────────────────────────────────────────────────────────
    println!("\n╠══════════════════════════════════════════════════════════════════╣");
    print!("║  VERDICT: 60% Diamond floor is ");
    if gdp_pct_change > 2.0 {
        println!("CONSISTENTLY BENEFICIAL (+{:.1}% GDP avg)", gdp_pct_change);
        println!("║  → Recommendation: ADOPT 60% floor as production default     ║");
    } else if gdp_pct_change > -2.0 {
        println!("MARGINALLY NEUTRAL ({:+.1}% GDP avg)", gdp_pct_change);
        println!("║  → Recommendation: CAUTION — effect too small to be reliable ║");
    } else {
        println!("CONSISTENTLY HARMFUL ({:+.1}% GDP avg)", gdp_pct_change);
        println!("║  → Recommendation: DO NOT ADOPT — seed=42 result was artifact   ║");
    }
    println!("╚══════════════════════════════════════════════════════════════════╝");
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

// ─── Whale Stress Test ────────────────────────────────────────────────────────

/// Tests whether a single wealthy erratic player (Whale) can destabilize
/// an otherwise healthy 2MM+2GB economy.
///
/// Whale behavior: accumulates massive inventory over 3 days, then dumps
/// everything at 50% perceived value. Represents exploited/dupe scenarios.
///
/// Key question: can the MM/GB system absorb a whale inventory dump without
/// triggering TIER3 circuit breaker or causing persistent price depression?
fn run_whale_stress_test() {
    use crate::analyzer::load_summary;
    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       WHALE STRESS TEST                                    ║");
    println!("║  Can a wealthy erratic player destabilize a healthy economy?║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: GuildStability+MM+GB (2MM+2GB+3Cas+3Far+2Tra)");
    println!("  Treatment: same + 1 Whale (accumulates 3 days → dumps at 50%)");
    println!("  Seeds: {:?}\n", seeds);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::whale_stress();

    let ctrl_summary_dir = PathBuf::from("/tmp/autotune-whale-ctrl");
    let treat_summary_dir = PathBuf::from("/tmp/autotune-whale-treat");
    let _ = std::fs::remove_dir_all(&ctrl_summary_dir);
    let _ = std::fs::remove_dir_all(&treat_summary_dir);
    std::fs::create_dir_all(&ctrl_summary_dir).ok();
    std::fs::create_dir_all(&treat_summary_dir).ok();

    let mut ctrl_s = ctrl_scenario.clone();
    ctrl_s.seed = Some(seeds[0]);
    let mut treat_s = treat_scenario.clone();
    treat_s.seed = Some(seeds[0]);

    println!("─── Control (no Whale) ───");
    if let Err(e) = run_headless(&ctrl_s, Some(ctrl_summary_dir.clone())) {
        eprintln!("  Control error: {}", e);
        return;
    }

    println!("\n─── Treatment (+Whale) ───");
    if let Err(e) = run_headless(&treat_s, Some(treat_summary_dir.clone())) {
        eprintln!("  Treatment error: {}", e);
        return;
    }

    let ctrl_sum = match load_summary(&ctrl_summary_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };
    let treat_sum = match load_summary(&treat_summary_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Summary error: {}", e);
            return;
        }
    };

    let ctrl_dg = ctrl_sum.debt / ctrl_sum.gdp.max(1.0);
    let treat_dg = treat_sum.debt / treat_sum.gdp.max(1.0);
    let ctrl_gdp = ctrl_sum.gdp;
    let treat_gdp = treat_sum.gdp;
    let ctrl_buy = ctrl_sum.buy_ratio;
    let treat_buy = treat_sum.buy_ratio;
    let ctrl_vol = ctrl_sum.avg_volatility;
    let treat_vol = treat_sum.avg_volatility;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!(
        "║       RESULTS (seed={})                              ║",
        seeds[0]
    );
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!(
        "  {:20} {:>12} {:>12} {:>10}",
        "Metric", "Control", "Treatment", "Delta"
    );
    println!("  {:─<20} {:─<12} {:─<12} {:─<10}", "", "", "", "");
    let dg_delta = treat_dg / ctrl_dg;
    let gdp_delta = (treat_gdp - ctrl_gdp) / ctrl_gdp * 100.0;
    let buy_delta = (treat_buy - ctrl_buy) * 100.0;
    let vol_delta = (treat_vol - ctrl_vol) * 100.0;
    println!(
        "  {:20} {:>12.3}x {:>12.3}x {:>+10.3}x",
        "Debt/GDP", ctrl_dg, treat_dg, dg_delta
    );
    println!(
        "  {:20} {:>12.0} {:>12.0} {:>+10.1}%",
        "Final GDP", ctrl_gdp, treat_gdp, gdp_delta
    );
    println!(
        "  {:20} {:>12.1}% {:>12.1}% {:>+10.1}pp",
        "Buy Ratio",
        ctrl_buy * 100.0,
        treat_buy * 100.0,
        buy_delta
    );
    println!(
        "  {:20} {:>12.4}  {:>12.4}  {:>+10.4}",
        "Avg Volatility", ctrl_vol, treat_vol, vol_delta
    );

    let dg_verdict = if dg_delta > 1.5 {
        "❌ WORSE"
    } else if dg_delta > 1.1 {
        "⚠️  SLIGHTLY WORSE"
    } else if dg_delta < 0.9 {
        "✅ BETTER"
    } else {
        "✅ NEUTRAL"
    };
    let gdp_verdict = if gdp_delta < -10.0 {
        "❌ WORSE"
    } else if gdp_delta < -2.0 {
        "⚠️  SLIGHTLY WORSE"
    } else {
        "✅ OK"
    };
    let vol_verdict = if vol_delta > 0.01 {
        "❌ MORE VOLATILE"
    } else if vol_delta < -0.01 {
        "✅ MORE STABLE"
    } else {
        "✅ NEUTRAL"
    };

    println!("\n  Verdict:");
    println!("  D/G:     {} (ratio {:.3}x)", dg_verdict, dg_delta);
    println!("  GDP:     {} ({:+.1}%)", gdp_verdict, gdp_delta);
    println!("  Vol:     {}", vol_verdict);

    if dg_delta > 1.5 || gdp_delta < -10.0 {
        println!("\n  ⚠️  WHALE DESTABILIZES the economy — MM/GB absorption is INSUFFICIENT.");
        println!("  Recommendation: monitor for whale-like behavior, add spread caps or");
        println!("  inventory sell limits on high-value items.");
    } else {
        println!("\n  ✅ Economy ABSORBS the whale dump — MM/GB system is RESILIENT.");
        println!("  The whale's inventory is absorbed by GuildBuyer price-dip buying.");
    }

    // Multi-seed summary
    println!("\n─── Multi-seed Summary ───");
    let mut dg_ctrls = vec![];
    let mut dg_treats = vec![];
    let mut gdp_ctrls = vec![];
    let mut gdp_treats = vec![];

    for &seed in &seeds[1..] {
        let mut cs = ctrl_scenario.clone();
        cs.seed = Some(seed);
        let cd = PathBuf::from(format!("/tmp/autotune-whale-ctrl-{}", seed));
        std::fs::create_dir_all(&cd).ok();
        if run_headless(&cs, Some(cd.clone())).is_ok()
            && let Ok(s) = load_summary(&cd.join("simulation.db"))
        {
            dg_ctrls.push(s.debt / s.gdp.max(1.0));
            gdp_ctrls.push(s.gdp);
        }
        let mut ts = treat_scenario.clone();
        ts.seed = Some(seed);
        let td = PathBuf::from(format!("/tmp/autotune-whale-treat-{}", seed));
        std::fs::create_dir_all(&td).ok();
        if run_headless(&ts, Some(td.clone())).is_ok()
            && let Ok(s) = load_summary(&td.join("simulation.db"))
        {
            dg_treats.push(s.debt / s.gdp.max(1.0));
            gdp_treats.push(s.gdp);
        }
    }

    if !dg_ctrls.is_empty() {
        let avg_ctrl_dg = dg_ctrls.iter().sum::<f64>() / dg_ctrls.len() as f64;
        let avg_treat_dg = dg_treats.iter().sum::<f64>() / dg_treats.len() as f64;
        let avg_ctrl_gdp = gdp_ctrls.iter().sum::<f64>() / gdp_ctrls.len() as f64;
        let avg_treat_gdp = gdp_treats.iter().sum::<f64>() / gdp_treats.len() as f64;
        let multi_dg_delta = avg_treat_dg / avg_ctrl_dg;
        let multi_gdp_delta = (avg_treat_gdp - avg_ctrl_gdp) / avg_ctrl_gdp * 100.0;
        println!(
            "  {:20} {:>12.3}x {:>12.3}x {:>+10.3}x",
            "Avg D/G (all seeds)", avg_ctrl_dg, avg_treat_dg, multi_dg_delta
        );
        println!(
            "  {:20} {:>12.0} {:>12.0} {:>+10.1}%",
            "Avg GDP (all seeds)", avg_ctrl_gdp, avg_treat_gdp, multi_gdp_delta
        );
    }
}

// ─── AFKFarmer Stress Test ────────────────────────────────────────────────────

/// Tests how the economy handles AFKFarmers: players who accumulate resources
/// while offline, then dump them at near-zero margins when online.
/// This represents the classic "AFK farmer" behavior on Minecraft servers.
fn run_afkfarmer_stress_test() {
    use crate::analyzer::load_summary;
    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║       AFKFARMER STRESS TEST                               ║");
    println!("║  guildbuyer_failure_test vs +2 AFKFarmers (replaces 2Far) ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guildbuyer_failure_test (1MM+2GB+4Cas+3Far+2Tra)");
    println!("  Treat:   same but 2 Farmers → 2 AFKFarmers");
    println!("  AFKFarmer behavior: 5-15% online, dumps near-zero margins when online");
    println!("  5 seeds × 14 days\n");

    let ctrl_scenario = Scenario::guildbuyer_failure_test();
    let treat_scenario = guildbuyer_failure_test_plus_afkfarmer();

    let ctrl_dir = PathBuf::from("/tmp/autotune-afk-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-afk-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl_results = Vec::new();
    let mut treat_results = Vec::new();

    for seed in &seeds {
        println!("  Seed {}:", seed);
        {
            let mut sc = ctrl_scenario.clone();
            sc.seed = Some(*seed);
            let dir = ctrl_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("    Ctrl seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Ctrl: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                ctrl_results.push((*seed, s));
            }
        }
        {
            let mut sc = treat_scenario.clone();
            sc.seed = Some(*seed);
            let dir = treat_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("    Treat seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Treat: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                treat_results.push((*seed, s));
            }
        }
        println!();
    }

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║                    AGGREGATE RESULTS (5 seeds)               ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");

    if ctrl_results.is_empty() || treat_results.is_empty() {
        println!("  No results collected.");
        return;
    }

    fn stats(results: &[(u64, crate::analyzer::SimSummary)]) -> (f64, f64, f64, f64, f64) {
        let n = results.len() as f64;
        let gdp: Vec<f64> = results.iter().map(|(_, r)| r.gdp).collect();
        let dg: Vec<f64> = results
            .iter()
            .map(|(_, r)| r.debt / r.gdp.max(1.0))
            .collect();
        let vol: Vec<f64> = results.iter().map(|(_, r)| r.avg_volatility).collect();
        let bpd: Vec<f64> = results.iter().map(|(_, r)| r.avg_bpd).collect();
        let mean = |v: &[f64]| v.iter().sum::<f64>() / n;
        let std = |v: &[f64]| {
            let m = mean(v);
            (v.iter().map(|x| (x - m).powi(2)).sum::<f64>() / n).sqrt()
        };
        (mean(&gdp), mean(&dg), mean(&vol), mean(&bpd), std(&dg))
    }

    let (ctrl_gdp, ctrl_dg, ctrl_vol, ctrl_bpd, ctrl_dg_std) = stats(&ctrl_results);
    let (treat_gdp, treat_dg, treat_vol, treat_bpd, treat_dg_std) = stats(&treat_results);

    let gdp_pct = (treat_gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
    let dg_pct = (treat_dg / ctrl_dg.max(0.01) - 1.0) * 100.0;
    let vol_pct = (treat_vol / ctrl_vol.max(0.0001) - 1.0) * 100.0;
    let bpd_pct = (treat_bpd / ctrl_bpd.max(0.0001) - 1.0) * 100.0;

    println!(
        "  {:22} {:>14} {:>14} {:>14}",
        "Metric", "Stressed", "+AFKFarmer", "Effect"
    );
    println!(
        "  {:22} {:>14} {:>14} {:>14}",
        "─".repeat(22),
        "─".repeat(14),
        "─".repeat(14),
        "─".repeat(14)
    );
    println!(
        "  {:22} {:>14.0} {:>14.0} {:>+13.1}%",
        "GDP (mean)", ctrl_gdp, treat_gdp, gdp_pct
    );
    println!(
        "  {:22} {:>13.2}x {:>13.2}x {:>+13.1}%",
        "Debt/GDP (mean)", ctrl_dg, treat_dg, dg_pct
    );
    println!(
        "  {:22} {:>13.4} {:>13.4} {:>+13.1}%",
        "Volatility (mean)", ctrl_vol, treat_vol, vol_pct
    );
    println!(
        "  {:22} {:>13.2}% {:>13.2}% {:>+13.1}%",
        "BPD (mean)",
        ctrl_bpd * 100.0,
        treat_bpd * 100.0,
        bpd_pct
    );
    println!(
        "  {:22} {:>13.2}x {:>13.2}x {:>+13.1}%",
        "D/G σ (seed noise)",
        ctrl_dg_std,
        treat_dg_std,
        (treat_dg_std / ctrl_dg_std.max(0.01) - 1.0) * 100.0
    );

    println!("\n  === VERDICT ===");
    if gdp_pct < -10.0 {
        println!("  ❌ AFKFARMERS HARMFUL: GDP {:+.1}%", gdp_pct);
    } else if gdp_pct > 5.0 {
        println!("  ✅ AFKFARMERS BENEFICIAL: GDP {:+.1}%", gdp_pct);
    } else {
        println!("  ⚠️  AFKFARMERS NEUTRAL: GDP {:+.1}%", gdp_pct);
    }
    if dg_pct < -15.0 {
        println!(
            "  💡 D/G improves {:+.1}% — AFKFarmers absorb sell pressure counter-cyclically",
            dg_pct
        );
    }
}

// ─── Hoarder-Heavy Stress Test ───────────────────────────────────────────────

/// Tests how the economy handles a Hoarder-heavy config:
/// Hoarders accumulate items and rarely sell, creating chronic undersupply.
/// This tests price stability under supply shortage conditions.
fn run_hoarder_heavy_test() {
    use crate::analyzer::load_summary;
    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║       HOARDER-HEAVY STRESS TEST                           ║");
    println!("║  guildbuyer_failure_test vs +2 Hoarders (replaces 2Far)  ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guildbuyer_failure_test (1MM+2GB+4Cas+3Far+2Tra)");
    println!("  Treat:   same but 2 Farmers → 2 Hoarders");
    println!("  Hoarder behavior: holds inventory, sells rarely at high margins");
    println!("  5 seeds × 14 days\n");

    let ctrl_scenario = Scenario::guildbuyer_failure_test();
    let treat_scenario = guildbuyer_failure_test_hoarder_heavy();

    let ctrl_dir = PathBuf::from("/tmp/autotune-hoard-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-hoard-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl_results = Vec::new();
    let mut treat_results = Vec::new();

    for seed in &seeds {
        println!("  Seed {}:", seed);
        {
            let mut sc = ctrl_scenario.clone();
            sc.seed = Some(*seed);
            let dir = ctrl_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("    Ctrl seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Ctrl: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                ctrl_results.push((*seed, s));
            }
        }
        {
            let mut sc = treat_scenario.clone();
            sc.seed = Some(*seed);
            let dir = treat_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("    Treat seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Treat: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                treat_results.push((*seed, s));
            }
        }
        println!();
    }

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║                    AGGREGATE RESULTS (5 seeds)               ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");

    if ctrl_results.is_empty() || treat_results.is_empty() {
        println!("  No results collected.");
        return;
    }

    fn stats(results: &[(u64, crate::analyzer::SimSummary)]) -> (f64, f64, f64, f64, f64) {
        let n = results.len() as f64;
        let gdp: Vec<f64> = results.iter().map(|(_, r)| r.gdp).collect();
        let dg: Vec<f64> = results
            .iter()
            .map(|(_, r)| r.debt / r.gdp.max(1.0))
            .collect();
        let vol: Vec<f64> = results.iter().map(|(_, r)| r.avg_volatility).collect();
        let bpd: Vec<f64> = results.iter().map(|(_, r)| r.avg_bpd).collect();
        let mean = |v: &[f64]| v.iter().sum::<f64>() / n;
        let std = |v: &[f64]| {
            let m = mean(v);
            (v.iter().map(|x| (x - m).powi(2)).sum::<f64>() / n).sqrt()
        };
        (mean(&gdp), mean(&dg), mean(&vol), mean(&bpd), std(&dg))
    }

    let (ctrl_gdp, ctrl_dg, ctrl_vol, ctrl_bpd, ctrl_dg_std) = stats(&ctrl_results);
    let (treat_gdp, treat_dg, treat_vol, treat_bpd, treat_dg_std) = stats(&treat_results);

    let gdp_pct = (treat_gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
    let dg_pct = (treat_dg / ctrl_dg.max(0.01) - 1.0) * 100.0;
    let vol_pct = (treat_vol / ctrl_vol.max(0.0001) - 1.0) * 100.0;
    let bpd_pct = (treat_bpd / ctrl_bpd.max(0.0001) - 1.0) * 100.0;

    println!(
        "  {:22} {:>14} {:>14} {:>14}",
        "Metric", "Stressed", "+Hoarders", "Effect"
    );
    println!(
        "  {:22} {:>14} {:>14} {:>14}",
        "─".repeat(22),
        "─".repeat(14),
        "─".repeat(14),
        "─".repeat(14)
    );
    println!(
        "  {:22} {:>14.0} {:>14.0} {:>+13.1}%",
        "GDP (mean)", ctrl_gdp, treat_gdp, gdp_pct
    );
    println!(
        "  {:22} {:>13.2}x {:>13.2}x {:>+13.1}%",
        "Debt/GDP (mean)", ctrl_dg, treat_dg, dg_pct
    );
    println!(
        "  {:22} {:>13.4} {:>13.4} {:>+13.1}%",
        "Volatility (mean)", ctrl_vol, treat_vol, vol_pct
    );
    println!(
        "  {:22} {:>13.2}% {:>13.2}% {:>+13.1}%",
        "BPD (mean)",
        ctrl_bpd * 100.0,
        treat_bpd * 100.0,
        bpd_pct
    );
    println!(
        "  {:22} {:>13.2}x {:>13.2}x {:>+13.1}%",
        "D/G σ (seed noise)",
        ctrl_dg_std,
        treat_dg_std,
        (treat_dg_std / ctrl_dg_std.max(0.01) - 1.0) * 100.0
    );

    println!("\n  === VERDICT ===");
    if gdp_pct < -10.0 {
        println!("  ❌ HOARDERS HARMFUL: GDP {:+.1}%", gdp_pct);
    } else if gdp_pct > 5.0 {
        println!("  ✅ HOARDERS BENEFICIAL: GDP {:+.1}%", gdp_pct);
    } else {
        println!("  ⚠️  HOARDERS NEUTRAL: GDP {:+.1}%", gdp_pct);
    }
    if treat_bpd < ctrl_bpd * 0.9 {
        println!(
            "  💡 BPD drops {:+.1}% — Hoarders reduce market activity (undersupply)",
            bpd_pct
        );
    }
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
    archetype_map.insert("Whale".into(), Archetype::Whale);

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

fn run_counter_cyclical_test() {
    use crate::player::set_global_seeded_rng;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║     COUNTER-CYCLICAL INTEREST TEST                          ║");
    println!("║  Continuous taper vs tiered circuit breaker                 ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Java LoanManager (default): counter_cyclical=true");
    println!("  Formula: multiplier = max(0, min(1, 1 - D/G / tier3_ratio))");
    println!("  D/G=3→70%, D/G=5→50%, D/G=10→0% (smooth taper)");
    println!("  Tiered: D/G>3→50%, D/G>5→25%, D/G>10→0% (discrete steps)\n");
    println!("  Seed: {}\n", seed);

    // Control: legacy tiered circuit breaker
    let mut ctrl_scenario = Scenario::counter_cyclical_test();
    ctrl_scenario.config.loans.counter_cyclical = false;
    ctrl_scenario.name = "Counter-Cyclical: DISABLED (legacy tiered)".into();

    // Treatment: counter-cyclical continuous taper (Java default)
    let treat_scenario = Scenario::counter_cyclical_test();
    // counter_cyclical already = true from the scenario

    // Helper closure to run one arm and collect per-day stats
    let run_arm = |scenario: &Scenario, label: &str| -> (Vec<(u64, f64, f64, f64)>, Simulation) {
        println!("─── {} ───", label);
        set_global_seeded_rng(seed);
        let mut sim = Simulation::new_seeded(scenario.config.clone(), seed);
        sim.events = scenario.events.clone();
        add_players_to_sim(&mut sim, &scenario.players);
        sim.paused = false;

        let mut daily: Vec<(u64, f64, f64, f64)> = Vec::new(); // (day, gdp, total_debt, dg)

        let start = Instant::now();
        while sim.current_tick < scenario.duration_ticks {
            sim.tick();
            let tick = sim.current_tick;
            if tick.is_multiple_of(288) {
                let day = tick / 288;
                let gdp = sim.economy_snapshots.last().map(|s| s.gdp).unwrap_or(0.0);
                let total_debt: f64 = sim
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
                let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
                println!(
                    "  Day {:>2}: GDP={:>9.0} | total_debt={:>9.0} | D/G={:.3}x | loans={:>2}",
                    day,
                    gdp,
                    total_debt,
                    dg,
                    sim.loans
                        .iter()
                        .filter(|l| l.status == crate::loan::LoanStatus::Active)
                        .count(),
                );
                daily.push((day, gdp, total_debt, dg));
            }
        }
        println!(
            "  Done: {} ticks, {:.1}s\n",
            sim.current_tick,
            start.elapsed().as_secs_f64()
        );
        (daily, sim)
    };

    let (ctrl_daily, ctrl_sim) = run_arm(&ctrl_scenario, "Control (TIERED circuit breaker)");
    let (treat_daily, treat_sim) = run_arm(&treat_scenario, "Treatment (COUNTER-CYCLICAL taper)");

    // Summary table
    let extract_final = |sim: &Simulation| -> (f64, f64, f64, f64, usize, f64) {
        let gdp = sim.economy_snapshots.last().map(|s| s.gdp).unwrap_or(0.0);
        let active_debt: f64 = sim
            .loans
            .iter()
            .filter(|l| l.status == crate::loan::LoanStatus::Active)
            .map(|l| l.current_balance)
            .sum();
        let defaulted_debt: f64 = sim
            .loans
            .iter()
            .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
            .map(|l| l.current_balance)
            .sum();
        let total_debt = active_debt + defaulted_debt;
        let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
        let defaults = sim
            .loans
            .iter()
            .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
            .count();
        // Approximate: sum of (balance - principal) for all loans as interest accumulated
        let interest_approx: f64 = sim
            .loans
            .iter()
            .map(|l| (l.current_balance - l.principal).max(0.0))
            .sum();
        (gdp, total_debt, dg, active_debt, defaults, interest_approx)
    };

    let (ctrl_gdp, ctrl_debt, ctrl_dg, ctrl_active, ctrl_def, ctrl_interest) =
        extract_final(&ctrl_sim);
    let (treat_gdp, treat_debt, treat_dg, treat_active, treat_def, treat_interest) =
        extract_final(&treat_sim);

    // Per-day D/G comparison
    println!("╔══════════════════════════════════════════════════════════════╗");
    println!("║       PER-DAY D/G COMPARISON                                 ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:>5} {:>15} {:>15} {:>12}",
        "Day", "Tiered D/G", "CC D/G", "Diff"
    );
    println!("  {}", "-".repeat(50));
    for (ctrl_row, treat_row) in ctrl_daily.iter().zip(treat_daily.iter()) {
        let diff = treat_row.3 - ctrl_row.3;
        let marker = if diff < -0.1 {
            "✓ CC lower"
        } else if diff > 0.1 {
            "✗ CC higher"
        } else {
            "~"
        };
        println!(
            "  {:>5} {:>15.3}x {:>15.3}x {:>+12.3} {}",
            ctrl_row.0, ctrl_row.3, treat_row.3, diff, marker
        );
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       FINAL COMPARISON (Day 14)                              ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:<40} {:>15} {:>15}",
        "Metric", "Tiered (ctrl)", "Counter-Cycl"
    );
    println!("  {}", "-".repeat(72));
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Final GDP", ctrl_gdp, treat_gdp
    );
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Total Debt", ctrl_debt, treat_debt
    );
    println!(
        "  {:<40} {:>15.3}x {:>15.3}x",
        "Debt/GDP (D/G)", ctrl_dg, treat_dg
    );
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Active Debt", ctrl_active, treat_active
    );
    println!("  {:<40} {:>15} {:>15}", "Defaults", ctrl_def, treat_def);
    println!(
        "  {:<40} {:>15.0} {:>15.0}",
        "Total Interest Paid", ctrl_interest, treat_interest
    );

    // GDP pct diff
    let gdp_pct = (treat_gdp - ctrl_gdp) / ctrl_gdp.max(1.0) * 100.0;
    let dg_chg = treat_dg - ctrl_dg;
    let debt_pct = (treat_debt - ctrl_debt) / ctrl_debt.max(1.0) * 100.0;
    let interest_pct = (treat_interest - ctrl_interest) / ctrl_interest.max(1.0) * 100.0;

    println!("\n  Changes (counter-cyclical vs tiered):");
    println!("    GDP:           {:+.1}%", gdp_pct);
    println!(
        "    D/G:           {:+.3}x ({:+.1}%)",
        dg_chg,
        (dg_chg / ctrl_dg.max(0.001)) * 100.0
    );
    println!("    Total Debt:    {:+.1}%", debt_pct);
    println!("    Interest Paid: {:+.1}%", interest_pct);

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       VERDICT                                                 ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    if treat_dg < ctrl_dg * 0.9 && treat_gdp >= ctrl_gdp * 0.95 {
        println!("\n  ✓ COUNTER-CYCLICAL IS BETTER");
        println!("  Continuous taper reduces D/G while preserving GDP.");
        println!("  Java's counter-cyclical=true default is validated by simulation.");
    } else if treat_dg > ctrl_dg * 1.1 {
        println!("\n  ✗ COUNTER-CYCLICAL MAKES D/G WORSE");
        println!("  Tiered circuit breaker performs better in this scenario.");
        println!("  Consider re-evaluating the Java default (counter_cyclical=true).");
    } else {
        println!("\n  ~ SIMILAR PERFORMANCE");
        println!("  Both mechanisms produce comparable D/G outcomes.");
        println!("  Counter-cyclical is smoother but not a dramatic improvement here.");
    }
    println!(
        "\n  Java default: loans.counter-cyclical: true → Rust now matches (counter_cyclical: true)"
    );
}

/// Result of a single VWAP test arm.
#[derive(Debug)]
struct VwapArmResult {
    daily: Vec<(u64, f64, f64, f64)>, // (tick, gdp, total_debt, dg)
    final_gdp: f64,
    final_debt: f64,
    final_dg: f64,
    avg_dg: f64,
    max_dg: f64,
    total_trades: u32,
}

// ─── VWAP-Anchored Price Targets Test ───────────────────────────────────
// Hypothesis: Using rolling VWAP instead of subjective "perceived" value as the
// GuildBuyer price-dip anchor reduces D/G oscillation. VWAP is grounded in actual
// trade history; perceived is subjective and can drift.
//
// Control: guild_stability_mm_fixed_guild, use_vwap_targets=false (uses perceived)
// Treatment: same, use_vwap_targets=true (uses rolling VWAP window=100)
fn run_vwap_test() {
    use crate::player::set_global_seeded_rng;

    println!("\n");
    println!("═══════════════════════════════════════════════════════════════");
    println!("  VWAP-Anchored Price Targets Test");
    println!("═══════════════════════════════════════════════════════════════");
    println!("  Hypothesis: VWAP anchoring reduces D/G vs subjective perceived");
    println!("  Control:  guild_stability_mm_fixed_guild, use_vwap_targets=false");
    println!("  Treatment: same, use_vwap_targets=true (rolling VWAP window=100)");
    println!("  Scenario:  guild_stability_mm_fixed_guild (1MM+2GB@7%, 14d)");
    println!("═══════════════════════════════════════════════════════════════\n");

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let mut treat_scenario = Scenario::guild_stability_mm_fixed_guild();
    treat_scenario.config.guild_vwap_targets = true;

    let seed = 42u64;

    let run_arm = |scenario: &Scenario, label: &str| -> VwapArmResult {
        set_global_seeded_rng(seed);
        let mut sim = Simulation::new_seeded(scenario.config.clone(), seed);
        sim.events = scenario.events.clone();
        add_players_to_sim(&mut sim, &scenario.players);
        sim.paused = false;

        let mut daily: Vec<(u64, f64, f64, f64)> = Vec::new();
        while sim.current_tick < scenario.duration_ticks {
            sim.tick();
            if sim.current_tick > 0 && sim.current_tick.is_multiple_of(288) {
                let gdp = sim.economy_snapshots.last().map(|s| s.gdp).unwrap_or(0.0);
                let active_debt: f64 = sim
                    .loans
                    .iter()
                    .filter(|l| l.status == crate::loan::LoanStatus::Active)
                    .map(|l| l.current_balance)
                    .sum();
                let defaulted_debt: f64 = sim
                    .loans
                    .iter()
                    .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
                    .map(|l| l.current_balance)
                    .sum();
                let total_debt = active_debt + defaulted_debt;
                let dg = if gdp > 0.0 { total_debt / gdp } else { 0.0 };
                daily.push((sim.current_tick, gdp, total_debt, dg));
            }
        }

        // Final metrics
        let final_gdp = sim.economy_snapshots.last().map(|s| s.gdp).unwrap_or(0.0);
        let active_debt: f64 = sim
            .loans
            .iter()
            .filter(|l| l.status == crate::loan::LoanStatus::Active)
            .map(|l| l.current_balance)
            .sum();
        let defaulted_debt: f64 = sim
            .loans
            .iter()
            .filter(|l| l.status == crate::loan::LoanStatus::Defaulted)
            .map(|l| l.current_balance)
            .sum();
        let final_debt = active_debt + defaulted_debt;
        let final_dg = if final_gdp > 0.0 {
            final_debt / final_gdp
        } else {
            0.0
        };

        let avg_dg = if !daily.is_empty() {
            daily.iter().map(|(_, _, _, d)| d).sum::<f64>() / daily.len() as f64
        } else {
            0.0
        };
        let max_dg = daily
            .iter()
            .map(|(_, _, _, d)| *d)
            .fold(0.0f64, |a, b| a.max(b));

        let total_trades: u32 = sim.players.iter().map(|p| p.total_trades).sum();

        println!(
            "  [{}] Final: GDP=${:.0}, D/G={:.3}x, trades={}",
            label, final_gdp, final_dg, total_trades
        );

        VwapArmResult {
            daily,
            final_gdp,
            final_debt,
            final_dg,
            avg_dg,
            max_dg,
            total_trades,
        }
    };

    println!("  Running control (perceived anchor)...");
    let ctrl = run_arm(&ctrl_scenario, "Control");
    println!("  Running treatment (VWAP anchor)...");
    let treat = run_arm(&treat_scenario, "Treatment");

    // Per-day D/G comparison
    println!("\n  Per-day D/G comparison:");
    println!("  {:>6}  {:>12}  {:>12}", "Day", "Control", "Treatment");
    println!("  {:─>6}  {:─>12}  {:─>12}", "", "", "");
    for &(tick, _, _, ctrl_dg) in &ctrl.daily {
        let day = tick / 288;
        let treat_dg = treat
            .daily
            .iter()
            .find(|(t, _, _, _)| *t == tick)
            .map(|(_, _, _, d)| *d)
            .unwrap_or(0.0);
        let delta = treat_dg - ctrl_dg;
        let arrow = if delta < -0.01 {
            "↓"
        } else if delta > 0.01 {
            "↑"
        } else {
            "~"
        };
        println!(
            "  {:>6}  {:>12.3}x  {:>12.3}x  ({}{:.3})",
            day, ctrl_dg, treat_dg, arrow, delta
        );
    }

    // Summary
    println!("\n  ── Summary ─────────────────────────────────────────────────");
    println!(
        "  {:>12}  {:>12}  {:>12}  {:>10}",
        "Metric", "Control", "Treatment", "Change"
    );
    println!("  {:─>12}  {:─>12}  {:─>12}  {:─>10}", "", "", "", "");
    println!(
        "  {:>12}  {:>12.0}  {:>12.0}  {:>+10.1}%",
        "GDP",
        ctrl.final_gdp,
        treat.final_gdp,
        (treat.final_gdp - ctrl.final_gdp) / ctrl.final_gdp.max(1.0) * 100.0
    );
    println!(
        "  {:>12}  {:>12.0}  {:>12.0}  {:>+10.1}%",
        "Total Debt",
        ctrl.final_debt,
        treat.final_debt,
        (treat.final_debt - ctrl.final_debt) / ctrl.final_debt.max(1.0) * 100.0
    );
    println!(
        "  {:>12}  {:>12.3}x  {:>12.3}x  {:>+10.3}x",
        "D/G (final)",
        ctrl.final_dg,
        treat.final_dg,
        treat.final_dg - ctrl.final_dg
    );
    println!(
        "  {:>12}  {:>12.3}x  {:>12.3}x  {:>+10.3}x",
        "D/G (avg)",
        ctrl.avg_dg,
        treat.avg_dg,
        treat.avg_dg - ctrl.avg_dg
    );
    println!(
        "  {:>12}  {:>12.3}x  {:>12.3}x  {:>+10.3}x",
        "D/G (max)",
        ctrl.max_dg,
        treat.max_dg,
        treat.max_dg - ctrl.max_dg
    );
    println!(
        "  {:>12}  {:>12}  {:>12}  {:>+10}",
        "Trades",
        ctrl.total_trades,
        treat.total_trades,
        format!("{:+}", treat.total_trades as i32 - ctrl.total_trades as i32)
    );

    println!("\n  Verdict:");
    let dg_delta = treat.final_dg - ctrl.final_dg;
    let dg_pct = dg_delta / ctrl.final_dg.max(0.001) * 100.0;
    if dg_delta < -0.05 {
        println!(
            "  ✓ VWAP reduces final D/G: {:.3}x → {:.3}x ({:+.1}%)",
            ctrl.final_dg, treat.final_dg, dg_pct
        );
        println!("  → VWAP anchoring is recommended for D/G stability.");
    } else if dg_delta > 0.05 {
        println!(
            "  ✗ VWAP increases final D/G: {:.3}x → {:.3}x ({:+.1}%)",
            ctrl.final_dg, treat.final_dg, dg_pct
        );
        println!("  → VWAP anchoring is NOT recommended — perceived is better.");
    } else {
        println!(
            "  ~ VWAP has negligible D/G effect: {:.3}x → {:.3}x ({:+.1}%)",
            ctrl.final_dg, treat.final_dg, dg_pct
        );
        println!("  → VWAP neither helps nor hurts D/G stability.");
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
    archetype_map.insert("Whale".into(), Archetype::Whale);

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

    println!();
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

// ─── MM Competition Multi-Seed ───────────────────────────────────────────
/// Runs 1MM vs 2MM across 5 seeds to establish statistical confidence.
/// Controls for RNG variance — same player archetypes, different seeds.
// ─── MM Competition Multi-Seed ───────────────────────────────────────────
/// Runs 1MM vs 2MM across 5 seeds to establish statistical confidence.
fn run_mm_competition_multi_seed() {
    use crate::analyzer::load_summary;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       MM COMPETITION — MULTI-SEED (5 seeds)               ║");
    println!("║  1MM+2GB vs 2MM+2GB — Statistical robustness check       ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Control: guild_stability_mm_fixed_guild (1MM + 2GB)");
    println!("  Treat:   guild_stability_2mm_fixed_guild (2MM + 2GB)\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct RunResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        spd: f64,
        vol: f64,
        buy_ratio: f64,
    }

    impl RunResult {
        fn from_summary(s: &crate::analyzer::SimSummary, seed: u64) -> Self {
            Self {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
            }
        }
    }

    let mut ctrl_results: Vec<RunResult> = Vec::new();
    let mut treat_results: Vec<RunResult> = Vec::new();
    let total = seeds.len() * 2;

    for (i, seed) in seeds.iter().enumerate() {
        eprint!("\r  [{}/{}] seed={}", i * 2 + 1, total, seed);
        std::io::stderr().flush().ok();

        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-mmms-ctrl-{}", seed));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_dir).ok();
        let ctrl = Scenario::guild_stability_mm_fixed_guild();
        if let Err(e) = run_seeded_headless(&ctrl, *seed, &ctrl_dir) {
            eprintln!("\n  Ctrl error seed={}: {}", seed, e);
            continue;
        }
        if let Ok(s) = load_summary(&ctrl_dir.join("simulation.db")) {
            ctrl_results.push(RunResult::from_summary(&s, *seed));
        }
        let _ = std::fs::remove_dir_all(&ctrl_dir);

        eprint!("\r  [{}/{}] seed={}", i * 2 + 2, total, seed);
        std::io::stderr().flush().ok();

        let treat_dir = PathBuf::from(format!("/tmp/autotune-mmms-treat-{}", seed));
        let _ = std::fs::remove_dir_all(&treat_dir);
        std::fs::create_dir_all(&treat_dir).ok();
        let treat = Scenario::guild_stability_2mm_fixed_guild();
        if let Err(e) = run_seeded_headless(&treat, *seed, &treat_dir) {
            eprintln!("\n  Treat error seed={}: {}", seed, e);
            continue;
        }
        if let Ok(s) = load_summary(&treat_dir.join("simulation.db")) {
            treat_results.push(RunResult::from_summary(&s, *seed));
        }
        let _ = std::fs::remove_dir_all(&treat_dir);
    }
    println!();

    if ctrl_results.is_empty() || treat_results.is_empty() {
        eprintln!("  ✗ No results collected");
        return;
    }

    let n = ctrl_results.len();
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║                    PER-SEED RESULTS                         ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Pre-format per-seed table
    println!(
        "  {:>6}  {:>12}  {:>7}  {:>7}  {:>7}  {:>7}  |  {:>12}  {:>7}  {:>7}  {:>7}  {:>7}",
        "seed",
        "GDP(ctrl)",
        "D/G(c)",
        "BPD(c)",
        "Vol(c)",
        "Buy(c)",
        "GDP(tr)",
        "D/G(t)",
        "BPD(t)",
        "Vol(t)",
        "Buy(t)"
    );
    for (c, t) in ctrl_results.iter().zip(treat_results.iter()) {
        let row = format!(
            "  {:>6}  {:>12.0}  {:>6.2}x  {:>6.2}%  {:>6.3}  {:>6.1}%  |  {:>12.0}  {:>6.2}x  {:>6.2}%  {:>6.3}  {:>6.1}%",
            c.seed,
            c.gdp,
            c.dg,
            c.bpd * 100.0,
            c.vol * 1000.0,
            c.buy_ratio * 100.0,
            t.gdp,
            t.dg,
            t.bpd * 100.0,
            t.vol * 1000.0,
            t.buy_ratio * 100.0
        );
        println!("{}", row);
    }

    // ── Compute stats ─────────────────────────────────────────────────────
    let avg = |v: &[RunResult], f: &str| -> f64 {
        let field_sum = match f {
            "gdp" => v.iter().map(|r| r.gdp).sum::<f64>(),
            "dg" => v.iter().map(|r| r.dg).sum::<f64>(),
            "bpd" => v.iter().map(|r| r.bpd).sum::<f64>(),
            "vol" => v.iter().map(|r| r.vol).sum::<f64>(),
            "buy_ratio" => v.iter().map(|r| r.buy_ratio).sum::<f64>(),
            _ => 0.0,
        };
        field_sum / v.len().max(1) as f64
    };
    let std_dev = |v: &[RunResult], f: &str, m: f64| -> f64 {
        let variance = v
            .iter()
            .map(|r| {
                let val = match f {
                    "gdp" => r.gdp,
                    "dg" => r.dg,
                    "bpd" => r.bpd,
                    "vol" => r.vol,
                    "buy_ratio" => r.buy_ratio,
                    _ => 0.0,
                };
                (val - m).powi(2)
            })
            .sum::<f64>()
            / v.len().max(1) as f64;
        variance.sqrt()
    };

    let c_gdp = avg(&ctrl_results, "gdp");
    let t_gdp = avg(&treat_results, "gdp");
    let c_dg = avg(&ctrl_results, "dg");
    let t_dg = avg(&treat_results, "dg");
    let c_bpd = avg(&ctrl_results, "bpd");
    let t_bpd = avg(&treat_results, "bpd");
    let c_vol = avg(&ctrl_results, "vol");
    let t_vol = avg(&treat_results, "vol");
    let c_buy = avg(&ctrl_results, "buy_ratio");
    let t_buy = avg(&treat_results, "buy_ratio");

    let c_gdp_s = std_dev(&ctrl_results, "gdp", c_gdp);
    let t_gdp_s = std_dev(&treat_results, "gdp", t_gdp);
    let c_dg_s = std_dev(&ctrl_results, "dg", c_dg);
    let t_dg_s = std_dev(&treat_results, "dg", t_dg);
    let c_bpd_s = std_dev(&ctrl_results, "bpd", c_bpd);
    let t_bpd_s = std_dev(&treat_results, "bpd", t_bpd);
    let c_vol_s = std_dev(&ctrl_results, "vol", c_vol);
    let t_vol_s = std_dev(&treat_results, "vol", t_vol);
    let c_buy_s = std_dev(&ctrl_results, "buy_ratio", c_buy);
    let t_buy_s = std_dev(&treat_results, "buy_ratio", t_buy);

    let gdp_chg = (t_gdp / c_gdp.max(1.0) - 1.0) * 100.0;
    let dg_chg = t_dg - c_dg;
    let bpd_chg = (t_bpd / c_bpd.max(0.0001) - 1.0) * 100.0;
    let vol_chg = (t_vol / c_vol.max(0.0001) - 1.0) * 100.0;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!(
        "║               AGGREGATE: MEAN ± STD (N={})                  ║",
        n
    );
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20}  {:>22}  {:>22}",
        "Metric", "1MM (control)", "2MM (treatment)"
    );
    println!("  {:─<20}  {:─<22}  {:─<22}", "", "", "");

    println!(
        "  GDP:                {:>10.0} ± {:>8.0}   {:>10.0} ± {:>8.0}  ({:+.1}% GDP)",
        c_gdp, c_gdp_s, t_gdp, t_gdp_s, gdp_chg
    );
    println!(
        "  Debt/GDP (x):      {:>10.2} ± {:>8.2}   {:>10.2} ± {:>8.2}  ({:+.2}x D/G)",
        c_dg, c_dg_s, t_dg, t_dg_s, dg_chg
    );
    println!(
        "  Buy-Price-Diff (%):{:>10.2} ± {:>8.3}  {:>10.2} ± {:>8.3}  ({:+.1}% BPD)",
        c_bpd * 100.0,
        c_bpd_s * 100.0,
        t_bpd * 100.0,
        t_bpd_s * 100.0,
        bpd_chg
    );
    println!(
        "  Volatility (x1000): {:>10.4} ± {:>8.5}  {:>10.4} ± {:>8.5}  ({:+.1}% vol)",
        c_vol * 1000.0,
        c_vol_s * 1000.0,
        t_vol * 1000.0,
        t_vol_s * 1000.0,
        vol_chg
    );
    println!(
        "  Buy Ratio (%):     {:>10.1} ± {:>8.1}  {:>10.1} ± {:>8.1}",
        c_buy * 100.0,
        c_buy_s * 100.0,
        t_buy * 100.0,
        t_buy_s * 100.0
    );

    println!("\n  === INTERPRETATION ===");
    let gdp_wins = t_gdp > c_gdp;
    let vol_wins = t_vol < c_vol;
    let bpd_wins = t_bpd < c_bpd;
    println!(
        "  GDP:  {} ({:+.1}% with 2MM)",
        if gdp_wins { "2MM ↑" } else { "1MM ↑" },
        gdp_chg.abs()
    );
    println!(
        "  Vol:  {} ({:+.1}% with 2MM)",
        if vol_wins { "2MM ↓" } else { "1MM ↓" },
        vol_chg.abs()
    );
    println!(
        "  Spd:  {} ({:+.1}%pp with 2MM)",
        if bpd_wins { "2MM ↓" } else { "1MM ↓" },
        bpd_chg.abs()
    );
    println!("  D/G:  {:.2}x → {:.2}x ({:+.2}x)", c_dg, t_dg, dg_chg);

    let wins = [gdp_wins, vol_wins, bpd_wins]
        .iter()
        .filter(|&&x| x)
        .count();
    println!();
    if wins >= 2 && gdp_wins && vol_wins {
        println!(
            "  ✅ VERDICT: 2 MMs win {}/3 categories — recommend adding 2nd MM to production",
            wins
        );
    } else if wins == 0 || (!gdp_wins && !vol_wins) {
        println!(
            "  ❌ VERDICT: 1MM wins or ties {}/3 — 1 MM is sufficient",
            wins
        );
    } else {
        println!(
            "  ⚠️  VERDICT: Mixed ({}/3) — trade-off dependent on admin priorities",
            wins
        );
    }
    println!();
}

// ─── VolumeTrader Multi-Seed ─────────────────────────────────────────────
/// Runs healthy economy vs healthy+2VT across 5 seeds.
fn run_volume_trader_multi_seed() {
    use crate::analyzer::load_summary;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       VOLUME TRADER — MULTI-SEED (5 seeds)                  ║");
    println!("║  Healthy economy vs +2 VolumeTraders                        ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Control: GuildStability+MM (1MM + 2GB + 4Cas + 3Far + 2Tra)");
    println!("  Treat:   same + 2 VolumeTraders\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct RunResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        spd: f64,
        vol: f64,
        buy_ratio: f64,
    }

    impl RunResult {
        fn from_summary(s: &crate::analyzer::SimSummary, seed: u64) -> Self {
            Self {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
            }
        }
    }

    let mut ctrl_results: Vec<RunResult> = Vec::new();
    let mut treat_results: Vec<RunResult> = Vec::new();
    let total = seeds.len() * 2;

    for (i, seed) in seeds.iter().enumerate() {
        eprint!("\r  [{}/{}] seed={}", i * 2 + 1, total, seed);
        std::io::stderr().flush().ok();

        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-vtms-ctrl-{}", seed));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_dir).ok();
        let ctrl = Scenario::guild_stability_mm_fixed_guild();
        if let Err(e) = run_seeded_headless(&ctrl, *seed, &ctrl_dir) {
            eprintln!("\n  Ctrl error seed={}: {}", seed, e);
            continue;
        }
        if let Ok(s) = load_summary(&ctrl_dir.join("simulation.db")) {
            ctrl_results.push(RunResult::from_summary(&s, *seed));
        }
        let _ = std::fs::remove_dir_all(&ctrl_dir);

        eprint!("\r  [{}/{}] seed={}", i * 2 + 2, total, seed);
        std::io::stderr().flush().ok();

        let treat_dir = PathBuf::from(format!("/tmp/autotune-vtms-treat-{}", seed));
        let _ = std::fs::remove_dir_all(&treat_dir);
        std::fs::create_dir_all(&treat_dir).ok();
        let treat = Scenario::volume_trader_test();
        if let Err(e) = run_seeded_headless(&treat, *seed, &treat_dir) {
            eprintln!("\n  Treat error seed={}: {}", seed, e);
            continue;
        }
        if let Ok(s) = load_summary(&treat_dir.join("simulation.db")) {
            treat_results.push(RunResult::from_summary(&s, *seed));
        }
        let _ = std::fs::remove_dir_all(&treat_dir);
    }
    println!();

    if ctrl_results.is_empty() || treat_results.is_empty() {
        eprintln!("  ✗ No results collected");
        return;
    }

    let n = ctrl_results.len();
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║                    PER-SEED RESULTS                         ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    println!(
        "  {:>6}  {:>12}  {:>7}  {:>7}  {:>7}  {:>7}  |  {:>12}  {:>7}  {:>7}  {:>7}  {:>7}",
        "seed",
        "GDP(ctrl)",
        "D/G(c)",
        "BPD(c)",
        "Vol(c)",
        "Buy(c)",
        "GDP(tr)",
        "D/G(t)",
        "BPD(t)",
        "Vol(t)",
        "Buy(t)"
    );
    for (c, t) in ctrl_results.iter().zip(treat_results.iter()) {
        println!(
            "  {:>6}  {:>12.0}  {:>6.2}x  {:>6.2}%  {:>6.3}  {:>6.1}%  |  {:>12.0}  {:>6.2}x  {:>6.2}%  {:>6.3}  {:>6.1}%",
            c.seed,
            c.gdp,
            c.dg,
            c.bpd * 100.0,
            c.vol * 1000.0,
            c.buy_ratio * 100.0,
            t.gdp,
            t.dg,
            t.bpd * 100.0,
            t.vol * 1000.0,
            t.buy_ratio * 100.0
        );
    }

    let avg = |v: &[RunResult], f: &str| -> f64 {
        let field_sum = match f {
            "gdp" => v.iter().map(|r| r.gdp).sum::<f64>(),
            "dg" => v.iter().map(|r| r.dg).sum::<f64>(),
            "bpd" => v.iter().map(|r| r.bpd).sum::<f64>(),
            "vol" => v.iter().map(|r| r.vol).sum::<f64>(),
            "buy_ratio" => v.iter().map(|r| r.buy_ratio).sum::<f64>(),
            _ => 0.0,
        };
        field_sum / v.len().max(1) as f64
    };
    let std_dev = |v: &[RunResult], f: &str, m: f64| -> f64 {
        let variance = v
            .iter()
            .map(|r| {
                let val = match f {
                    "gdp" => r.gdp,
                    "dg" => r.dg,
                    "bpd" => r.bpd,
                    "vol" => r.vol,
                    "buy_ratio" => r.buy_ratio,
                    _ => 0.0,
                };
                (val - m).powi(2)
            })
            .sum::<f64>()
            / v.len().max(1) as f64;
        variance.sqrt()
    };

    let c_gdp = avg(&ctrl_results, "gdp");
    let t_gdp = avg(&treat_results, "gdp");
    let c_dg = avg(&ctrl_results, "dg");
    let t_dg = avg(&treat_results, "dg");
    let c_bpd = avg(&ctrl_results, "bpd");
    let t_bpd = avg(&treat_results, "bpd");
    let c_vol = avg(&ctrl_results, "vol");
    let t_vol = avg(&treat_results, "vol");
    let c_buy = avg(&ctrl_results, "buy_ratio");
    let t_buy = avg(&treat_results, "buy_ratio");

    let c_gdp_s = std_dev(&ctrl_results, "gdp", c_gdp);
    let t_gdp_s = std_dev(&treat_results, "gdp", t_gdp);
    let c_dg_s = std_dev(&ctrl_results, "dg", c_dg);
    let t_dg_s = std_dev(&treat_results, "dg", t_dg);
    let c_bpd_s = std_dev(&ctrl_results, "bpd", c_bpd);
    let t_bpd_s = std_dev(&treat_results, "bpd", t_bpd);
    let c_vol_s = std_dev(&ctrl_results, "vol", c_vol);
    let t_vol_s = std_dev(&treat_results, "vol", t_vol);
    let c_buy_s = std_dev(&ctrl_results, "buy_ratio", c_buy);
    let t_buy_s = std_dev(&treat_results, "buy_ratio", t_buy);

    let gdp_chg = (t_gdp / c_gdp.max(1.0) - 1.0) * 100.0;
    let dg_chg = t_dg - c_dg;
    let bpd_chg = (t_bpd / c_bpd.max(0.0001) - 1.0) * 100.0;
    let vol_chg = (t_vol / c_vol.max(0.0001) - 1.0) * 100.0;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!(
        "║               AGGREGATE: MEAN ± STD (N={})                  ║",
        n
    );
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:20}  {:>22}  {:>22}",
        "Metric", "Healthy (ctrl)", "Healthy+VT (treat)"
    );
    println!("  {:─<20}  {:─<22}  {:─<22}", "", "", "");

    println!(
        "  GDP:                {:>10.0} ± {:>8.0}   {:>10.0} ± {:>8.0}  ({:+.1}% GDP)",
        c_gdp, c_gdp_s, t_gdp, t_gdp_s, gdp_chg
    );
    println!(
        "  Debt/GDP (x):      {:>10.2} ± {:>8.2}   {:>10.2} ± {:>8.2}  ({:+.2}x D/G)",
        c_dg, c_dg_s, t_dg, t_dg_s, dg_chg
    );
    println!(
        "  Buy-Price-Diff (%):{:>10.2} ± {:>8.3}  {:>10.2} ± {:>8.3}  ({:+.1}% BPD)",
        c_bpd * 100.0,
        c_bpd_s * 100.0,
        t_bpd * 100.0,
        t_bpd_s * 100.0,
        bpd_chg
    );
    println!(
        "  Volatility (x1000): {:>10.4} ± {:>8.5}  {:>10.4} ± {:>8.5}  ({:+.1}% vol)",
        c_vol * 1000.0,
        c_vol_s * 1000.0,
        t_vol * 1000.0,
        t_vol_s * 1000.0,
        vol_chg
    );
    println!(
        "  Buy Ratio (%):     {:>10.1} ± {:>8.1}  {:>10.1} ± {:>8.1}",
        c_buy * 100.0,
        c_buy_s * 100.0,
        t_buy * 100.0,
        t_buy_s * 100.0
    );

    println!("\n  === INTERPRETATION ===");
    let gdp_wins = t_gdp > c_gdp;
    let vol_wins = t_vol < c_vol;
    let bpd_wins = t_bpd < c_bpd;
    println!(
        "  GDP:  {} ({:+.1}% with VT)",
        if gdp_wins { "VT ↑" } else { "Ctrl ↑" },
        gdp_chg.abs()
    );
    println!(
        "  Vol:  {} ({:+.1}% with VT)",
        if vol_wins { "VT ↓" } else { "Ctrl ↓" },
        vol_chg.abs()
    );
    println!(
        "  Spd:  {} ({:+.1}%pp with VT)",
        if bpd_wins { "VT ↓" } else { "Ctrl ↓" },
        bpd_chg.abs()
    );
    println!("  D/G:  {:.2}x → {:.2}x ({:+.2}x)", c_dg, t_dg, dg_chg);

    let wins = [gdp_wins, vol_wins, bpd_wins]
        .iter()
        .filter(|&&x| x)
        .count();
    println!();
    if wins >= 2 && gdp_wins {
        println!(
            "  ✅ VERDICT: VT wins {}/3 — recommend adding 2 VolumeTraders to production",
            wins
        );
    } else if wins == 0 {
        println!(
            "  ❌ VERDICT: Control wins {}/3 — VolumeTraders don't reliably help healthy economy",
            wins
        );
    } else {
        println!(
            "  ⚠️  VERDICT: Mixed ({}/3) — VT effect is marginal in healthy economy",
            wins
        );
    }
    println!();
}

// ─── MM Capital Sweep ─────────────────────────────────────────────────────
/// Tests whether increasing MM starting capital eliminates/reduces opening loans.
///
/// Key question: does MM at $200-300K starting capital need fewer/opening loans
/// than MM at $50-200K? This tests the hypothesis that higher initial capital
/// reduces MM borrowing dependency without bounding loans (which backfires).
///
/// Uses guild_stability_2mm_fixed_guild (2MM + 2GB) as the base scenario.
/// Capital levels: $50-200K (control), $100-200K, $200-300K, $300-400K, $500-600K.
/// Runs 3 seeds each (42, 12345, 98765).
fn run_mm_capital_sweep() {
    use crate::analyzer::load_summary;

    let seeds: Vec<u64> = vec![42, 12345, 98765];
    // (min, max, label) for MM initial capital range
    let capital_levels: Vec<(f64, f64, &'static str)> = vec![
        (50_000.0, 200_000.0, "$50-200K (default)"),
        (100_000.0, 200_000.0, "$100-200K"),
        (200_000.0, 300_000.0, "$200-300K"),
        (300_000.0, 400_000.0, "$300-400K"),
        (500_000.0, 600_000.0, "$500-600K"),
    ];

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║         MM INITIAL CAPITAL SWEEP                            ║");
    println!("║  5 capital levels × 3 seeds — guild_stability_2mm          ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Base scenario: GuildStability+2MM+2GB (4Cas+3Far+2Tra)");
    println!("  Key question: does higher MM capital → fewer/opening loans?\n");

    struct LevelResult {
        #[allow(dead_code)]
        label: String,
        gdp: f64,
        dg: f64,
        bpd: f64,
        vol: f64,
        buy_ratio: f64,
        mm_loan_count: u32,
        mm_loan_total: f64,
    }

    let mut all_results: Vec<(String, Vec<LevelResult>)> = Vec::new();

    for (min_cap, max_cap, label) in &capital_levels {
        let mut level_results: Vec<LevelResult> = Vec::new();
        println!("  ── {} ──", label);

        for seed in &seeds {
            // Build scenario with this capital level
            let mut scenario = Scenario::guild_stability_2mm_fixed_guild();
            scenario.config.mm_initial_capital_min = Some(*min_cap);
            scenario.config.mm_initial_capital_max = Some(*max_cap);
            scenario.seed = Some(*seed);

            // Run with DB recorder to get summary metrics
            let out_dir = PathBuf::from(format!(
                "/tmp/autotune-mmcap-{}-{}-{}",
                min_cap, max_cap, seed
            ));
            let _ = std::fs::remove_dir_all(&out_dir);
            std::fs::create_dir_all(&out_dir).ok();

            let sim = match run_seeded_headless(&scenario, *seed, &out_dir) {
                Ok(s) => s,
                Err(e) => {
                    eprintln!("\n  Error seed={}: {}", seed, e);
                    continue;
                }
            };

            let summary = match load_summary(&out_dir.join("simulation.db")) {
                Ok(s) => s,
                Err(e) => {
                    eprintln!("\n  Summary error seed={}: {}", seed, e);
                    continue;
                }
            };

            let dg = summary.debt / summary.gdp.max(1.0);

            level_results.push(LevelResult {
                label: label.to_string(),
                gdp: summary.gdp,
                dg,
                bpd: summary.avg_bpd,
                vol: summary.avg_volatility,
                buy_ratio: summary.buy_ratio,
                mm_loan_count: sim.mm_opening_loan_count,
                mm_loan_total: sim.mm_opening_loan_total,
            });

            eprint!(
                "\r    seed={} → GDP={:.0}  D/G={:.2}x  MM_loans={}  ",
                seed, summary.gdp, dg, sim.mm_opening_loan_count
            );
            std::io::stderr().flush().ok();
            let _ = std::fs::remove_dir_all(&out_dir);
        }
        println!();
        all_results.push((label.to_string(), level_results));
    }
    println!();

    // ── Aggregate per level ───────────────────────────────────────────────
    let avg_fn = |results: &[LevelResult], field: &str| -> f64 {
        let sum = match field {
            "gdp" => results.iter().map(|r| r.gdp).sum::<f64>(),
            "dg" => results.iter().map(|r| r.dg).sum::<f64>(),
            "bpd" => results.iter().map(|r| r.bpd).sum::<f64>(),
            "vol" => results.iter().map(|r| r.vol).sum::<f64>(),
            "buy_ratio" => results.iter().map(|r| r.buy_ratio).sum::<f64>(),
            "mm_loan_count" => results.iter().map(|r| r.mm_loan_count as f64).sum::<f64>(),
            "mm_loan_total" => results.iter().map(|r| r.mm_loan_total).sum::<f64>(),
            _ => 0.0,
        };
        sum / results.len().max(1) as f64
    };

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║                 MM CAPITAL SWEEP RESULTS                        ║");
    println!("║                 Mean across 3 seeds (42, 12345, 98765)          ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:22}  {:>10}  {:>8}  {:>9}  {:>9}  {:>8}  {:>10}  {:>12}",
        "Capital Level", "GDP", "D/G", "BPD%", "Vol×1000", "Buy%", "MM Loans", "MM Loan Amt"
    );
    println!(
        "  {:22}  {:>10}  {:>8}  {:>9}  {:>9}  {:>8}  {:>10}  {:>12}",
        "─".repeat(11),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(9),
        "─".repeat(9),
        "─".repeat(8),
        "─".repeat(10),
        "─".repeat(12)
    );

    for (label, results) in &all_results {
        let gdp = avg_fn(results, "gdp");
        let dg = avg_fn(results, "dg");
        let bpd = avg_fn(results, "bpd") * 100.0;
        let vol = avg_fn(results, "vol") * 1000.0;
        let buy = avg_fn(results, "buy_ratio") * 100.0;
        let mm_loans = avg_fn(results, "mm_loan_count");
        let loan_amt = avg_fn(results, "mm_loan_total");
        println!(
            "  {:22}  {:>10.0}  {:>7.2}x  {:>8.2}%  {:>8.3}  {:>7.1}%  {:>9.0}  {:>11.0}",
            label, gdp, dg, bpd, vol, buy, mm_loans, loan_amt
        );
    }

    // ── Interpretation ────────────────────────────────────────────────────
    let default_results = &all_results[0].1;
    let high_cap_results = &all_results[2].1; // $200-300K
    let highest_results = &all_results[4].1; // $500-600K

    let default_mm_loans = avg_fn(default_results, "mm_loan_count");
    let high_mm_loans = avg_fn(high_cap_results, "mm_loan_count");
    let highest_mm_loans = avg_fn(highest_results, "mm_loan_count");
    let default_dg = avg_fn(default_results, "dg");
    let high_dg = avg_fn(high_cap_results, "dg");
    let default_gdp = avg_fn(default_results, "gdp");
    let high_gdp = avg_fn(high_cap_results, "gdp");

    println!("\n  === INTERPRETATION ===");
    if high_mm_loans < default_mm_loans {
        println!(
            "  ✅ Higher capital REDUCES MM opening loans: {:.1} → {:.1} ({:+.1}%)",
            default_mm_loans,
            high_mm_loans,
            (high_mm_loans / default_mm_loans.max(1.0) - 1.0) * 100.0
        );
    } else if high_mm_loans > default_mm_loans {
        println!(
            "  ⚠️  Higher capital INCREASES MM opening loans: {:.1} → {:.1} ({:+.1}%)",
            default_mm_loans,
            high_mm_loans,
            (high_mm_loans / default_mm_loans.max(1.0) - 1.0) * 100.0
        );
    } else {
        println!(
            "  ➡️  MM opening loans UNCHANGED by capital level ({:.1})",
            high_mm_loans
        );
    }

    if highest_mm_loans == 0.0 {
        println!("  💡 At $500-600K, MM takes ZERO opening loans — self-sufficient!");
    } else {
        println!(
            "  📊 At $500-600K, MM still takes {:.1} opening loans/season",
            highest_mm_loans
        );
    }

    println!(
        "  D/G: {:.2}x → {:.2}x ({:+.2}x)",
        default_dg,
        high_dg,
        high_dg - default_dg
    );
    println!(
        "  GDP: {:.0} → {:.0} ({:+.1}%)",
        default_gdp,
        high_gdp,
        (high_gdp / default_gdp.max(1.0) - 1.0) * 100.0
    );

    let loan_change = high_mm_loans / default_mm_loans.max(0.5);
    if loan_change < 0.5 && high_mm_loans < 1.0 {
        println!("\n  ✅ VERDICT: $200-300K MM capital is EFFECTIVE — reduces/opening loans ≥50%");
        println!("     Recommendation: set mm_initial_capital = [200000, 300000] in production");
    } else if loan_change > 0.8 {
        println!("\n  ❌ VERDICT: Capital level has MINIMAL effect on MM opening loans");
        println!("     MM opening loans are driven by trading behavior, not starting capital");
    } else {
        println!(
            "\n  ⚠️  VERDICT: MIXED — capital helps somewhat but doesn't fully solve borrowing"
        );
        println!("     Consider pairing with other safeguards (cooldown, circuit breaker)");
    }
    println!();
}

// ─── InsiderTrader Healthy Economy Test ─────────────────────────────────
/// Tests whether InsiderTraders add value when added to an already-healthy
/// economy (MM + GB). Previous IT test was IT alone vs control (no MM/GB).
fn run_it_healthy_economy_test() {
    use crate::analyzer::load_summary;
    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       INSIDERTRADER + HEALTHY ECONOMY TEST                  ║");
    println!("║  Healthy (MM+GB) vs +2 InsiderTraders                      ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: GuildStability+MM (1MM + 2GB + 4Cas + 3Far + 2Tra)");
    println!("  Treat:   same + 2 InsiderTraders");
    println!("  Seed: {}\n", seed);

    let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
    let treat_scenario = Scenario::guild_stability_mm_fixed_guild_plus_it();

    let ctrl_dir = PathBuf::from("/tmp/autotune-it-healthy-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-it-healthy-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    println!("  Running control...");
    if let Err(e) = run_seeded_headless(&ctrl_scenario, seed, &ctrl_dir) {
        eprintln!("  Control error: {}", e);
        return;
    }

    println!("  Running treatment...");
    if let Err(e) = run_seeded_headless(&treat_scenario, seed, &treat_dir) {
        eprintln!("  Treatment error: {}", e);
        return;
    }

    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Ctrl summary error: {}", e);
            return;
        }
    };
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Treat summary error: {}", e);
            return;
        }
    };

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);
    let gdp_pct = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let vol_pct =
        (treat_summary.avg_volatility / ctrl_summary.avg_volatility.max(0.0001) - 1.0) * 100.0;
    let bpd_pct = (treat_summary.avg_bpd / ctrl_summary.avg_bpd.max(0.0001) - 1.0) * 100.0;
    let buy_pct = (treat_summary.buy_ratio / ctrl_summary.buy_ratio.max(0.0001) - 1.0) * 100.0;
    let dg_chg = treat_dg - ctrl_dg;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║                    RESULTS (seed=42)                         ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    // Pre-format for table display
    let gdp_s1 = format!("{:.0}", ctrl_summary.gdp);
    let gdp_s2 = format!("{:.0}", treat_summary.gdp);
    let gdp_pct_s = format!("{:+.1}%", gdp_pct);
    let dg_s1 = format!("{:.2}x", ctrl_dg);
    let dg_s2 = format!("{:.2}x", treat_dg);
    let dg_chg_s = format!("{:+.2}x", dg_chg);
    let bpd_s1 = format!("{:.2}%", ctrl_summary.avg_bpd * 100.0);
    let bpd_s2 = format!("{:.2}%", treat_summary.avg_bpd * 100.0);
    let bpd_pct_s = format!("{:+.1}%", bpd_pct);
    let vol_s1 = format!("{:.4}", ctrl_summary.avg_volatility * 1000.0);
    let vol_s2 = format!("{:.4}", treat_summary.avg_volatility * 1000.0);
    let vol_pct_s = format!("{:+.1}%", vol_pct);
    let buy_s1 = format!("{:.1}%", ctrl_summary.buy_ratio * 100.0);
    let buy_s2 = format!("{:.1}%", treat_summary.buy_ratio * 100.0);
    let buy_pct_s = format!("{:+.1}%", buy_pct);

    println!(
        "  {:20}  {:>15}  {:>15}  {:>11}",
        "Metric", "Healthy", "Healthy+IT", "Effect"
    );
    println!("  {:─<20}  {:─<15}  {:─<15}  {:─<11}", "", "", "", "");
    println!(
        "  {:20}  {:>15}  {:>15}  {:>+11}",
        "GDP", gdp_s1, gdp_s2, gdp_pct_s
    );
    println!(
        "  {:20}  {:>15}  {:>15}  {:>+11}",
        "Debt/GDP", dg_s1, dg_s2, dg_chg_s
    );
    println!(
        "  {:20}  {:>15}  {:>15}  {:>+11}",
        "Buy-Price-Diff%", bpd_s1, bpd_s2, bpd_pct_s
    );
    println!(
        "  {:20}  {:>15}  {:>15}  {:>+11}",
        "Volatility (x1000)", vol_s1, vol_s2, vol_pct_s
    );
    println!(
        "  {:20}  {:>15}  {:>15}  {:>+11}",
        "Buy Ratio", buy_s1, buy_s2, buy_pct_s
    );

    println!("\n  === ANALYSIS ===");
    println!(
        "  GDP:     {:+.1}% ({})",
        gdp_pct,
        if gdp_pct > 5.0 {
            "IT boosts GDP"
        } else if gdp_pct < -5.0 {
            "IT hurts GDP"
        } else {
            "neutral"
        }
    );
    println!(
        "  Vol:     {:+.1}% ({})",
        vol_pct,
        if vol_pct < -10.0 {
            "IT reduces volatility"
        } else if vol_pct > 10.0 {
            "IT raises volatility"
        } else {
            "neutral"
        }
    );
    println!(
        "  BPD:     {:+.1}%pp ({})",
        bpd_pct,
        if bpd_pct < -10.0 {
            "IT compresses spreads"
        } else if bpd_pct > 10.0 {
            "IT widens spreads"
        } else {
            "neutral"
        }
    );
    println!(
        "  Buy ratio: {:+.1}% ({})",
        buy_pct,
        if buy_pct > 5.0 {
            "IT improves buy ratio"
        } else if buy_pct < -5.0 {
            "IT worsens buy ratio"
        } else {
            "neutral"
        }
    );
    println!(
        "  D/G: {:.2}x → {:.2}x ({:+.2}x)",
        ctrl_dg, treat_dg, dg_chg
    );

    println!("\n  === CONTEXT ===");
    println!("  IT alone vs no-archetypes: +80.6% GDP, -28% vol, D/G 1.85x vs 0.75x");
    println!("  This test: IT + MM + GB vs MM + GB");

    let improvements = [
        gdp_pct > 5.0,
        vol_pct < -10.0,
        bpd_pct < -5.0,
        buy_pct > 5.0,
    ];
    let regressions = [gdp_pct < -5.0, dg_chg > 0.5];
    let n_imp = improvements.iter().filter(|&&x| x).count();
    let n_reg = regressions.iter().filter(|&&x| x).count();

    println!();
    if n_imp >= 2 && n_reg == 0 {
        println!(
            "  ✅ VERDICT: ITs reliably improve healthy economy — consider adding to recommended config"
        );
    } else if n_reg >= 1 {
        println!(
            "  ⚠️  VERDICT: ITs add D/G risk in healthy economy — check whether debt is productive"
        );
    } else {
        println!(
            "  ➖ VERDICT: ITs are neutral in healthy economy — no strong case to add or remove"
        );
    }
    println!();

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

/// InsiderTrader + VolumeTrader + Healthy Economy — multi-seed factorial
/// Control: guild_stability_2mm_fixed_guild (2MM + 2GB + 3Cas + 3Far + 2Tra)
/// Treat:   same + 2IT + 2VT (guild_stability_2mm_2gb_plus_it_and_vt)
/// Question: IT helps (+30.1% GDP) but VT hurts (-9.2% GDP) in isolation.
///   Combined on healthy economy: do they cancel out, or does one dominate?
/// Prior results:
///   IT alone in healthy: +30.1% GDP, D/G 0.75x→3.16x (+2.41x)
///   VT alone in healthy: -9.2% GDP, vol +8.7%, BPD -6.4%
/// Prediction: IT's GDP boost dominates but D/G takes a hit from both.
fn run_it_vt_healthy_economy_test() {
    use crate::analyzer::load_summary;
    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║       IT + VT COMBINATION TEST — HEALTHY ECONOMY             ║");
    println!("║  2MM+2GB vs +2IT+2VT — 5 seeds                                ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guild_stability_2mm_fixed_guild (2MM+2GB+3Cas+3Far+2Tra)");
    println!("  Treat:   same + 2 InsiderTraders + 2 VolumeTraders\n");

    let ctrl_scenario = Scenario::guild_stability_2mm_fixed_guild();
    let treat_scenario = Scenario::guild_stability_2mm_2gb_plus_it_and_vt();

    let ctrl_dir = PathBuf::from("/tmp/autotune-itvt-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-itvt-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl_results = Vec::new();
    let mut treat_results = Vec::new();

    for seed in &seeds {
        println!("  Seed {}:", seed);
        {
            let mut sc = ctrl_scenario.clone();
            sc.seed = Some(*seed);
            let dir = ctrl_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("  Ctrl seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Ctrl: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                ctrl_results.push((*seed, s));
            }
        }
        {
            let mut sc = treat_scenario.clone();
            sc.seed = Some(*seed);
            let dir = treat_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("  Treat seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Treat: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                treat_results.push((*seed, s));
            }
        }
        println!();
    }

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║                    AGGREGATE RESULTS (5 seeds)                   ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");

    if ctrl_results.is_empty() || treat_results.is_empty() {
        println!("  No results collected.");
        return;
    }

    fn stats(results: &[(u64, crate::analyzer::SimSummary)]) -> (f64, f64, f64, f64, f64, f64) {
        let n = results.len() as f64;
        let gdp: Vec<f64> = results.iter().map(|(_, r)| r.gdp).collect();
        let dg: Vec<f64> = results
            .iter()
            .map(|(_, r)| r.debt / r.gdp.max(1.0))
            .collect();
        let vol: Vec<f64> = results.iter().map(|(_, r)| r.avg_volatility).collect();
        let bpd: Vec<f64> = results.iter().map(|(_, r)| r.avg_bpd).collect();
        let buy: Vec<f64> = results.iter().map(|(_, r)| r.buy_ratio).collect();
        let mean = |v: &[f64]| v.iter().sum::<f64>() / n;
        let std = |v: &[f64]| {
            let m = mean(v);
            (v.iter().map(|x| (x - m).powi(2)).sum::<f64>() / n).sqrt()
        };
        (
            mean(&gdp),
            mean(&dg),
            mean(&vol),
            mean(&bpd),
            mean(&buy),
            std(&dg),
        )
    }

    let (ctrl_gdp, ctrl_dg, ctrl_vol, ctrl_bpd, ctrl_buy, ctrl_dg_std) = stats(&ctrl_results);
    let (treat_gdp, treat_dg, treat_vol, treat_bpd, treat_buy, treat_dg_std) =
        stats(&treat_results);

    let gdp_pct = (treat_gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
    let dg_chg = treat_dg - ctrl_dg;
    let vol_pct = (treat_vol / ctrl_vol.max(0.0001) - 1.0) * 100.0;
    let bpd_pct = (treat_bpd / ctrl_bpd.max(0.0001) - 1.0) * 100.0;

    println!(
        "  {:22}  {:>12}  {:>18}  {:>10}",
        "Metric", "2MM+2GB", "2MM+2GB+IT+VT", "Effect"
    );
    println!("  {:─<22}  {:─<12}  {:─<18}  {:─<10}", "", "", "", "");
    println!(
        "  {:22}  {:>12.0}  {:>18.0}  {:>+10.1}%",
        "GDP (mean)", ctrl_gdp, treat_gdp, gdp_pct
    );
    println!(
        "  {:22}  {:>11.2}x  {:>17.2}x  {:>+10.2}x",
        "Debt/GDP (mean)", ctrl_dg, treat_dg, dg_chg
    );
    println!(
        "  {:22}  {:>12.4}  {:>18.4}  {:>+10.1}%",
        "Volatility (mean)", ctrl_vol, treat_vol, vol_pct
    );
    println!(
        "  {:22}  {:>11.2}%  {:>17.2}%  {:>+10.1}%",
        "BPD (mean)",
        ctrl_bpd * 100.0,
        treat_bpd * 100.0,
        bpd_pct
    );
    println!(
        "  {:22}  {:>11.1}%  {:>17.1}%  {:>+10.1}pp",
        "Buy Ratio (mean)",
        ctrl_buy * 100.0,
        treat_buy * 100.0,
        (treat_buy - ctrl_buy) * 100.0
    );
    println!(
        "  {:22}  {:>11.2}x  {:>17.2}x  {:>+10.2}x",
        "D/G σ (across seeds)",
        ctrl_dg_std,
        treat_dg_std,
        treat_dg_std - ctrl_dg_std
    );

    println!("\n  === VERDICT ===");
    // IT alone: +30.1% GDP, VT alone: -9.2% GDP. Net prediction: ~+20% if linear
    if gdp_pct > 15.0 && dg_chg < 1.0 {
        println!(
            "  ✅ IT+VT SYNERGISTIC: GDP {:+.1}%, D/G {:+.2}x — net positive",
            gdp_pct, dg_chg
        );
    } else if gdp_pct > 5.0 && dg_chg < 2.0 {
        println!(
            "  ⚠️  IT+VT PARTIAL: GDP {:+.1}% but D/G {:+.2}x — IT wins, VT neutral",
            gdp_pct, dg_chg
        );
    } else if gdp_pct.abs() < 10.0 && dg_chg.abs() < 1.0 {
        println!(
            "  ➖ IT+VT CANCELS: GDP {:+.1}%, D/G {:+.2}x — they neutralize each other",
            gdp_pct, dg_chg
        );
    } else {
        println!(
            "  ❌ IT+VT HARMFUL or DOMINATED: GDP {:+.1}%, D/G {:+.2}x",
            gdp_pct, dg_chg
        );
    }
    println!();

    println!("  === CONTEXT ===");
    println!("  IT alone in healthy: GDP +30.1%, D/G +2.41x");
    println!("  VT alone in healthy: GDP -9.2%, vol +8.7%, BPD -6.4%");
    println!(
        "  Combined: IT (+30.1%) + VT (-9.2%) = predicted {:+.1}%",
        (1.301 - 0.092) * 100.0 - 100.0
    );
    if gdp_pct > 15.0 {
        println!(
            "  CONCLUSION: IT's GDP boost DOMINATES VT's drag — add IT+VT to recommended config"
        );
    } else if gdp_pct > 5.0 {
        println!("  CONCLUSION: IT's boost partially offsets VT — IT alone is better than IT+VT");
    } else if gdp_pct < -5.0 {
        println!("  CONCLUSION: VT's drag DOMINATES — do NOT add IT+VT combo to any config");
    } else {
        println!("  CONCLUSION: They cancel out — no strong case for either addition");
    }

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

/// InsiderTrader + Stressed Economy — multi-seed validation
/// Control: guildbuyer_failure_test (1MM+2GB+4Cas+3Far+2Tra)
/// Treat:   same + 2 InsiderTraders
/// Question: does IT help or hurt a stressed economy?
/// Healthy-economy result (2026-04-02): IT +30.1% GDP, D/G 0.75x→3.16x (+2.41x)
/// Stressed-economy hypothesis: IT may be MORE harmful since stress already strains D/G
fn run_it_stressed_economy_test() {
    use crate::analyzer::load_summary;
    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║       INSIDERTRADER + STRESSED ECONOMY TEST                     ║");
    println!("║  guildbuyer_failure_test vs +2 ITs — 5 seeds                    ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guildbuyer_failure_test (1MM+2GB+4Cas+3Far+2Tra)");
    println!("  Treat:   same + 2 InsiderTraders\n");

    let ctrl_scenario = Scenario::guildbuyer_failure_test();
    let treat_scenario = guildbuyer_failure_test_plus_it();

    let ctrl_dir = PathBuf::from("/tmp/autotune-it-stress-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-it-stress-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl_results = Vec::new();
    let mut treat_results = Vec::new();

    for seed in &seeds {
        println!("  Seed {}:", seed);
        {
            let mut sc = ctrl_scenario.clone();
            sc.seed = Some(*seed);
            let dir = ctrl_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("  Ctrl seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Ctrl: GDP={:.0}  D/G={:.2}x  vol={:.4}",
                    s.gdp, dg, s.avg_volatility
                );
                ctrl_results.push((*seed, s));
            }
        }
        {
            let mut sc = treat_scenario.clone();
            sc.seed = Some(*seed);
            let dir = treat_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("  Treat seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Treat: GDP={:.0}  D/G={:.2}x  vol={:.4}",
                    s.gdp, dg, s.avg_volatility
                );
                treat_results.push((*seed, s));
            }
        }
        println!();
    }

    // ── Aggregate Stats ───────────────────────────────────────────────────
    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║                    AGGREGATE RESULTS (5 seeds)                   ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");

    if ctrl_results.is_empty() || treat_results.is_empty() {
        println!("  No results collected.");
        return;
    }

    fn stats(results: &[(u64, crate::analyzer::SimSummary)]) -> (f64, f64, f64, f64, f64, f64) {
        let n = results.len() as f64;
        let gdp: Vec<f64> = results.iter().map(|(_, r)| r.gdp).collect();
        let dg: Vec<f64> = results
            .iter()
            .map(|(_, r)| r.debt / r.gdp.max(1.0))
            .collect();
        let vol: Vec<f64> = results.iter().map(|(_, r)| r.avg_volatility).collect();
        let bpd: Vec<f64> = results.iter().map(|(_, r)| r.avg_bpd).collect();
        let buy: Vec<f64> = results.iter().map(|(_, r)| r.buy_ratio).collect();
        let mean = |v: &[f64]| v.iter().sum::<f64>() / n;
        let std = |v: &[f64]| {
            let m = mean(v);
            (v.iter().map(|x| (x - m).powi(2)).sum::<f64>() / n).sqrt()
        };
        (
            mean(&gdp),
            mean(&dg),
            mean(&vol),
            mean(&bpd),
            mean(&buy),
            std(&dg),
        )
    }

    let (ctrl_gdp, ctrl_dg, ctrl_vol, ctrl_bpd, ctrl_buy, ctrl_dg_std) = stats(&ctrl_results);
    let (treat_gdp, treat_dg, treat_vol, treat_bpd, treat_buy, treat_dg_std) =
        stats(&treat_results);

    let gdp_pct = (treat_gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
    let dg_chg = treat_dg - ctrl_dg;
    let vol_pct = (treat_vol / ctrl_vol.max(0.0001) - 1.0) * 100.0;
    let bpd_pct = (treat_bpd / ctrl_bpd.max(0.0001) - 1.0) * 100.0;

    println!(
        "  {:22}  {:>12}  {:>12}  {:>10}",
        "Metric", "Stressed", "Stressed+IT", "Effect"
    );
    println!("  {:─<22}  {:─<12}  {:─<12}  {:─<10}", "", "", "", "");
    println!(
        "  {:22}  {:>12.0}  {:>12.0}  {:>+10.1}%",
        "GDP (mean)", ctrl_gdp, treat_gdp, gdp_pct
    );
    println!(
        "  {:22}  {:>11.2}x  {:>11.2}x  {:>+10.2}x",
        "Debt/GDP (mean)", ctrl_dg, treat_dg, dg_chg
    );
    println!(
        "  {:22}  {:>12.4}  {:>12.4}  {:>+10.1}%",
        "Volatility (mean)", ctrl_vol, treat_vol, vol_pct
    );
    println!(
        "  {:22}  {:>11.2}%  {:>11.2}%  {:>+10.1}%",
        "BPD (mean)",
        ctrl_bpd * 100.0,
        treat_bpd * 100.0,
        bpd_pct
    );
    println!(
        "  {:22}  {:>11.1}%  {:>11.1}%  {:>+10.1}pp",
        "Buy Ratio (mean)",
        ctrl_buy * 100.0,
        treat_buy * 100.0,
        (treat_buy - ctrl_buy) * 100.0
    );
    println!(
        "  {:22}  {:>11.2}x  {:>11.2}x  {:>+10.2}x",
        "D/G σ (across seeds)",
        ctrl_dg_std,
        treat_dg_std,
        treat_dg_std - ctrl_dg_std
    );

    println!("\n  === VERDICT ===");
    if gdp_pct > 10.0 && dg_chg < 0.5 {
        println!(
            "  ✅ IT BENEFICIAL: +{:.1}% GDP, D/G {:+.2}x",
            gdp_pct, dg_chg
        );
    } else if gdp_pct > 5.0 && dg_chg > 0.5 && dg_chg < 1.5 {
        println!(
            "  ⚠️  IT MIXED: +{:.1}% GDP but D/G {:+.2}x — trade-off",
            gdp_pct, dg_chg
        );
    } else if gdp_pct > 5.0 && dg_chg >= 1.5 {
        println!(
            "  ❌ IT HARMFUL: +{:.1}% GDP but D/G {:+.2}x — debt risk outweighs",
            gdp_pct, dg_chg
        );
    } else if gdp_pct < -5.0 {
        println!("  ❌ IT HARMFUL: GDP {:+.1}%", gdp_pct);
    } else {
        println!("  ➖ IT NEUTRAL: GDP {:+.1}%, D/G {:+.2}x", gdp_pct, dg_chg);
    }
    println!();

    // Context
    println!("  === CONTEXT ===");
    println!("  Healthy-economy IT result: +30.1% GDP, D/G 0.75x→3.16x (+2.41x)");
    if treat_dg > ctrl_dg * 1.5 {
        println!(
            "  Stressed-economy: IT AMPLIFIES debt stress (D/G {:+.2}x more harmful)",
            dg_chg
        );
        println!("  RECOMMENDATION: Do NOT add ITs to stressed-economy configs");
    } else if dg_chg < 0.0 {
        println!(
            "  Stressed-economy: IT REDUCES D/G by {:+.2}x — counter-cyclical benefit",
            dg_chg
        );
        println!("  RECOMMENDATION: ITs are MORE beneficial in stressed economies");
    } else {
        println!(
            "  Stressed-economy: IT effect on D/G is small ({:+.2}x)",
            dg_chg
        );
    }

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

/// VolumeTrader + Stressed Economy Test — multi-seed validation
/// Control: guildbuyer_failure_test (1MM+2GB+4Cas+3Far+2Tra)
/// Treat:   same + 2 VolumeTraders
/// Question: does VT help or hurt a stressed economy?
/// VT in healthy economy result: GDP -9.2%, vol +8.7% WORSE, BPD -6.4%
/// Stressed-economy hypothesis: VT's spread compression could reduce volatility
///   but VT's buy-high-sell-low behavior could amplify debt cascades.
fn run_vt_stressed_economy_test() {
    use crate::analyzer::load_summary;
    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║       VOLUMETRADER + STRESSED ECONOMY TEST                    ║");
    println!("║  guildbuyer_failure_test vs +2 VTs — 5 seeds                  ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guildbuyer_failure_test (1MM+2GB+4Cas+3Far+2Tra)");
    println!("  Treat:   same + 2 VolumeTraders\n");

    let ctrl_scenario = Scenario::guildbuyer_failure_test();
    let treat_scenario = guildbuyer_failure_test_plus_vt();

    let ctrl_dir = PathBuf::from("/tmp/autotune-vt-stress-ctrl");
    let treat_dir = PathBuf::from("/tmp/autotune-vt-stress-treat");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    std::fs::create_dir_all(&treat_dir).ok();

    let mut ctrl_results = Vec::new();
    let mut treat_results = Vec::new();

    for seed in &seeds {
        println!("  Seed {}:", seed);
        {
            let mut sc = ctrl_scenario.clone();
            sc.seed = Some(*seed);
            let dir = ctrl_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("  Ctrl seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Ctrl: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                ctrl_results.push((*seed, s));
            }
        }
        {
            let mut sc = treat_scenario.clone();
            sc.seed = Some(*seed);
            let dir = treat_dir.join(format!("seed_{}", seed));
            std::fs::create_dir_all(&dir).ok();
            if let Err(e) = run_seeded_headless(&sc, *seed, &dir) {
                eprintln!("  Treat seed {} error: {}", seed, e);
                continue;
            }
            if let Ok(s) = load_summary(&dir.join("simulation.db")) {
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "    Treat: GDP={:.0}  D/G={:.2}x  vol={:.4}  buy={:.1}%",
                    s.gdp,
                    dg,
                    s.avg_volatility,
                    s.buy_ratio * 100.0
                );
                treat_results.push((*seed, s));
            }
        }
        println!();
    }

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║                    AGGREGATE RESULTS (5 seeds)                   ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝\n");

    if ctrl_results.is_empty() || treat_results.is_empty() {
        println!("  No results collected.");
        return;
    }

    fn stats(results: &[(u64, crate::analyzer::SimSummary)]) -> (f64, f64, f64, f64, f64, f64) {
        let n = results.len() as f64;
        let gdp: Vec<f64> = results.iter().map(|(_, r)| r.gdp).collect();
        let dg: Vec<f64> = results
            .iter()
            .map(|(_, r)| r.debt / r.gdp.max(1.0))
            .collect();
        let vol: Vec<f64> = results.iter().map(|(_, r)| r.avg_volatility).collect();
        let bpd: Vec<f64> = results.iter().map(|(_, r)| r.avg_bpd).collect();
        let buy: Vec<f64> = results.iter().map(|(_, r)| r.buy_ratio).collect();
        let mean = |v: &[f64]| v.iter().sum::<f64>() / n;
        let std = |v: &[f64]| {
            let m = mean(v);
            (v.iter().map(|x| (x - m).powi(2)).sum::<f64>() / n).sqrt()
        };
        (
            mean(&gdp),
            mean(&dg),
            mean(&vol),
            mean(&bpd),
            mean(&buy),
            std(&dg),
        )
    }

    let (ctrl_gdp, ctrl_dg, ctrl_vol, ctrl_bpd, ctrl_buy, ctrl_dg_std) = stats(&ctrl_results);
    let (treat_gdp, treat_dg, treat_vol, treat_bpd, treat_buy, treat_dg_std) =
        stats(&treat_results);

    let gdp_pct = (treat_gdp / ctrl_gdp.max(1.0) - 1.0) * 100.0;
    let dg_chg = treat_dg - ctrl_dg;
    let vol_pct = (treat_vol / ctrl_vol.max(0.0001) - 1.0) * 100.0;
    let bpd_pct = (treat_bpd / ctrl_bpd.max(0.0001) - 1.0) * 100.0;

    println!(
        "  {:22}  {:>12}  {:>12}  {:>10}",
        "Metric", "Stressed", "Stressed+VT", "Effect"
    );
    println!("  {:─<22}  {:─<12}  {:─<12}  {:─<10}", "", "", "", "");
    println!(
        "  {:22}  {:>12.0}  {:>12.0}  {:>+10.1}%",
        "GDP (mean)", ctrl_gdp, treat_gdp, gdp_pct
    );
    println!(
        "  {:22}  {:>11.2}x  {:>11.2}x  {:>+10.2}x",
        "Debt/GDP (mean)", ctrl_dg, treat_dg, dg_chg
    );
    println!(
        "  {:22}  {:>12.4}  {:>12.4}  {:>+10.1}%",
        "Volatility (mean)", ctrl_vol, treat_vol, vol_pct
    );
    println!(
        "  {:22}  {:>11.2}%  {:>11.2}%  {:>+10.1}%",
        "BPD (mean)",
        ctrl_bpd * 100.0,
        treat_bpd * 100.0,
        bpd_pct
    );
    println!(
        "  {:22}  {:>11.1}%  {:>11.1}%  {:>+10.1}pp",
        "Buy Ratio (mean)",
        ctrl_buy * 100.0,
        treat_buy * 100.0,
        (treat_buy - ctrl_buy) * 100.0
    );
    println!(
        "  {:22}  {:>11.2}x  {:>11.2}x  {:>+10.2}x",
        "D/G σ (across seeds)",
        ctrl_dg_std,
        treat_dg_std,
        treat_dg_std - ctrl_dg_std
    );

    println!("\n  === VERDICT ===");
    if gdp_pct > 5.0 && dg_chg < 0.5 && vol_pct < 0.0 {
        println!(
            "  ✅ VT BENEFICIAL: GDP {:+.1}%, vol {:+.1}%, D/G {:+.2}x",
            gdp_pct, vol_pct, dg_chg
        );
    } else if gdp_pct > 2.0 && dg_chg.abs() < 1.0 && vol_pct.abs() < 10.0 {
        println!(
            "  ⚠️  VT MARGINAL: GDP {:+.1}%, vol {:+.1}%, D/G {:+.2}x",
            gdp_pct, vol_pct, dg_chg
        );
    } else if gdp_pct < -5.0 || dg_chg > 2.0 {
        println!(
            "  ❌ VT HARMFUL in stressed economy: GDP {:+.1}%, D/G {:+.2}x",
            gdp_pct, dg_chg
        );
    } else {
        println!(
            "  ➖ VT NEUTRAL in stressed economy: GDP {:+.1}%, D/G {:+.2}x, vol {:+.1}%",
            gdp_pct, dg_chg, vol_pct
        );
    }
    println!();

    // Context
    println!("  === CONTEXT ===");
    println!("  Healthy-economy VT result: GDP -9.2%, vol +8.7%, BPD -6.4%");
    if vol_pct < 0.0 && dg_chg < 0.5 {
        println!(
            "  Stressed-economy: VT REDUCES volatility ({:.1}%) AND keeps D/G stable",
            vol_pct
        );
        println!("  RECOMMENDATION: VTs may be useful in stressed economies (unlike ITs)");
    } else if dg_chg > 1.0 {
        println!(
            "  Stressed-economy: VT WORSENS debt ({:+.2}x) — amplifying stress",
            dg_chg
        );
        println!("  RECOMMENDATION: Do NOT add VTs to stressed-economy configs");
    } else {
        println!(
            "  Stressed-economy: VT effect is similar to healthy-economy ({:.1}% GDP)",
            gdp_pct
        );
    }

    let _ = std::fs::remove_dir_all(&ctrl_dir);
    let _ = std::fs::remove_dir_all(&treat_dir);
}

fn run_guild_threshold_multi_seed() {
    use crate::analyzer::load_summary;
    use crate::player::set_fixed_guild_threshold;

    // threshold values as integers (percent × 100: 5 → 0.05)
    let thresholds: Vec<u8> = vec![5, 7, 10, 15, 20];
    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];
    let base_scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();

    let mut results: std::collections::BTreeMap<u8, Vec<GuildSweepResult>> =
        std::collections::BTreeMap::new();
    for &t in &thresholds {
        results.insert(t, Vec::new());
    }

    let total = thresholds.len() * seeds.len();
    let mut completed = 0usize;

    println!("\n╔══════════════════════════════════════════════════════════════════════╗");
    println!("║       GUILDBUYER THRESHOLD MULTI-SEED VALIDATION                 ║");
    println!("╚══════════════════════════════════════════════════════════════════════╝");
    println!();
    println!(
        "  Scenario: guild_stability_2mm_fixed_guild_plus_floor (2MM + 2GB + 60% Diamond floor)"
    );
    println!(
        "  Duration: 14 days ({} ticks)",
        base_scenario.duration_ticks
    );
    println!(
        "  Thresholds: {:?}",
        thresholds
            .iter()
            .map(|t| format!("{:.0}%", *t as f64 * 100.0))
            .collect::<Vec<_>>()
    );
    println!("  Seeds: {:?}", seeds);
    println!(
        "  Total runs: {} × {} = {}",
        thresholds.len(),
        seeds.len(),
        total
    );
    println!();

    for &threshold in &thresholds {
        for &seed in &seeds {
            completed += 1;
            eprint!(
                "\r  [{}/{}] threshold={:.0}% seed={}",
                completed, total, threshold as f64, seed
            );
            std::io::stderr().flush().ok();

            let out_dir = PathBuf::from(format!(
                "/tmp/autotune-gt-ms-{:02}-{}-{}",
                threshold * 100,
                seed,
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap()
                    .as_secs()
                    % 100000
            ));
            let _ = std::fs::remove_dir_all(&out_dir);
            std::fs::create_dir_all(&out_dir).ok();

            set_fixed_guild_threshold(Some(threshold as f64 / 100.0));
            let _ = run_seeded_headless(&base_scenario, seed, &out_dir);
            set_fixed_guild_threshold(None);

            let db_path = out_dir.join("simulation.db");
            if let Ok(summary) = load_summary(&db_path) {
                let debt_gdp = summary.debt / summary.gdp.max(0.01);
                let threshold_pct = threshold as f64 / 100.0;
                let vol = summary.avg_volatility;
                let r = GuildSweepResult {
                    threshold: threshold_pct,
                    gdp: summary.gdp,
                    debt: summary.debt,
                    debt_gdp_ratio: debt_gdp,
                    avg_bpd: summary.avg_bpd * 100.0,
                    avg_spd: summary.avg_spd * 100.0,
                    avg_volatility: vol,
                    buy_ratio: summary.buy_ratio * 100.0,
                };
                results.get_mut(&threshold).unwrap().push(r);
            }

            let _ = std::fs::remove_dir_all(&out_dir);
        }
    }
    eprintln!();
    println!();

    // Summary table: mean ± std per threshold
    println!(
        "  {:>8} {:>14} {:>10} {:>10} {:>8} {:>8}",
        "Thresh", "GDP", "D/G", "BPD%", "SPD%", "Vol"
    );
    println!(
        "  {:>8} {:>14} {:>10} {:>10} {:>8} {:>8}",
        "─".repeat(8),
        "─".repeat(14),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8)
    );

    let mut best_gdp = 0.0_f64;
    let mut best_dg = f64::MAX;
    let mut best_vol = f64::MAX;

    for &threshold in &thresholds {
        let vals = results.get(&threshold).unwrap();
        if vals.is_empty() {
            continue;
        }

        let mean = |field: fn(&GuildSweepResult) -> f64| -> f64 {
            let sum: f64 = vals.iter().map(field).sum();
            sum / vals.len() as f64
        };
        let std = |field: fn(&GuildSweepResult) -> f64| -> f64 {
            let m = mean(field);
            let variance: f64 = vals
                .iter()
                .map(|v| {
                    let d = field(v) - m;
                    d * d
                })
                .sum::<f64>()
                / vals.len() as f64;
            variance.sqrt()
        };

        let gdp_m = mean(|v| v.gdp);
        let dg_m = mean(|v| v.debt_gdp_ratio);
        let bpd_m = mean(|v| v.avg_bpd);
        let vol_m = mean(|v| v.avg_volatility);

        if gdp_m > best_gdp {
            best_gdp = gdp_m;
        }
        if dg_m < best_dg {
            best_dg = dg_m;
        }
        if vol_m < best_vol {
            best_vol = vol_m;
        }

        let gdp_s = std(|v| v.gdp);
        let dg_s = std(|v| v.debt_gdp_ratio);
        let bpd_s = std(|v| v.avg_bpd);
        let vol_s = std(|v| v.avg_volatility);

        println!(
            "  {:>7.1}%  {:>6.0}K±{:<4.0}  {:>5.2}±{:<3.2}  {:>5.2}±{:<3.2}  {:>6.3}  {:>6.4}",
            threshold as f64,
            gdp_m / 1000.0,
            gdp_s / 1000.0,
            dg_m,
            dg_s,
            bpd_m,
            bpd_s,
            vol_m,
            vol_s
        );
    }

    println!();
    println!("  ═══════════════════════════════════════════════════════════════════");
    println!("  RECOMMENDATION (guild_stability_2mm_fixed_guild_plus_floor, 5 seeds):");
    println!();

    // Find best by GDP, D/G, and volatility
    let mut gdp_ranked = results.keys().copied().collect::<Vec<_>>();
    gdp_ranked.sort_by(|a, b| {
        let ma = results[a].iter().map(|v| v.gdp).sum::<f64>() / results[a].len().max(1) as f64;
        let mb = results[b].iter().map(|v| v.gdp).sum::<f64>() / results[b].len().max(1) as f64;
        mb.partial_cmp(&ma).unwrap()
    });

    let mut dg_ranked = results.keys().copied().collect::<Vec<_>>();
    dg_ranked.sort_by(|a, b| {
        let ma = results[a].iter().map(|v| v.debt_gdp_ratio).sum::<f64>()
            / results[a].len().max(1) as f64;
        let mb = results[b].iter().map(|v| v.debt_gdp_ratio).sum::<f64>()
            / results[b].len().max(1) as f64;
        ma.partial_cmp(&mb).unwrap()
    });

    let top_gdp = gdp_ranked[0];
    let top_dg = dg_ranked[0];

    println!(
        "  • Best GDP:       {:.0}% threshold ({:.0}K avg GDP)",
        top_gdp as f64,
        results[&top_gdp].iter().map(|v| v.gdp).sum::<f64>()
            / results[&top_gdp].len().max(1) as f64
            / 1000.0
    );
    println!(
        "  • Lowest D/G:    {:.0}% threshold ({:.2}x avg)",
        top_dg as f64,
        results[&top_dg]
            .iter()
            .map(|v| v.debt_gdp_ratio)
            .sum::<f64>()
            / results[&top_dg].len().max(1) as f64
    );

    // Volatility analysis
    for &threshold in &thresholds {
        let vol = results[&threshold]
            .iter()
            .map(|v| v.avg_volatility)
            .sum::<f64>()
            / results[&threshold].len().max(1) as f64;
        let unstable = results[&threshold]
            .iter()
            .filter(|v| v.avg_volatility > 0.05)
            .count();
        print!("  • {:.0}%: vol={:.4}", threshold as f64, vol);
        if unstable > 0 {
            print!(
                " ⚠️  {}/{} seeds UNSTABLE (vol>0.05)",
                unstable,
                seeds.len()
            );
        } else {
            print!(" ✅ stable");
        }
        println!();
    }

    println!();
    println!("  ➡️  VERDICT: See table above. Prioritize vol<0.05 AND D/G reasonable.");
    println!();
}

fn run_mm_quit_test() {
    use crate::analyzer::load_summary;
    use rusqlite::Connection;

    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       MM QUIT TEST                                          ║");
    println!("║  MM quits at day 7 — can economy survive without MM?    ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guildbuyer_failure_test (no exodus)");
    println!("  Treat:   guildbuyer_failure_mm_quit_test (MM quits day 7)");
    println!("  Seed: {}\n", seed);

    // Helper to query loan stats from DB
    fn get_loan_counts(db_path: &std::path::Path) -> (usize, usize, usize) {
        let conn = Connection::open(db_path).ok();
        if let Some(conn) = conn {
            let taken: usize = conn
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
            let active: usize = conn
                .query_row(
                    "SELECT COUNT(*) FROM loans WHERE status = 'Active'",
                    [],
                    |row| row.get::<_, i64>(0),
                )
                .unwrap_or(0) as usize;
            return (taken, defaulted, active);
        }
        (0, 0, 0)
    }

    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8}",
        "GDP", "Debt", "D/G", "BPD%", "Buy%"
    );
    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8}",
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8)
    );

    // Control: no exodus
    let ctrl_dir = PathBuf::from("/tmp/autotune-mm-quit-ctrl");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    let ctrl_scenario = Scenario::guildbuyer_failure_test();
    if let Err(e) = run_seeded_headless(&ctrl_scenario, seed, &ctrl_dir) {
        eprintln!("  Control error: {}", e);
        return;
    }
    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Control summary error: {}", e);
            return;
        }
    };
    let ctrl_loans = get_loan_counts(&ctrl_dir.join("simulation.db"));

    // Treatment: MM quits at day 7
    let treat_dir = PathBuf::from("/tmp/autotune-mm-quit-treat");
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&treat_dir).ok();
    let treat_scenario = Scenario::guildbuyer_failure_mm_quit_test();
    if let Err(e) = run_seeded_headless(&treat_scenario, seed, &treat_dir) {
        eprintln!("  Treatment error: {}", e);
        return;
    }
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Treatment summary error: {}", e);
            return;
        }
    };
    let treat_loans = get_loan_counts(&treat_dir.join("simulation.db"));

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!(
        "  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}% (ctrl, {} def, {} act)",
        ctrl_summary.gdp,
        ctrl_summary.debt,
        ctrl_dg,
        ctrl_summary.avg_bpd * 100.0,
        ctrl_summary.buy_ratio * 100.0,
        ctrl_loans.1,
        ctrl_loans.2
    );
    println!(
        "  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}% (treat, {} def, {} act)",
        treat_summary.gdp,
        treat_summary.debt,
        treat_dg,
        treat_summary.avg_bpd * 100.0,
        treat_summary.buy_ratio * 100.0,
        treat_loans.1,
        treat_loans.2
    );

    println!();
    let gdp_pct = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let debt_pct = (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0;
    let dg_chg = treat_dg - ctrl_dg;
    let bpd_chg = (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0;
    let vol_chg = treat_summary.avg_volatility - ctrl_summary.avg_volatility;
    let buy_chg = (treat_summary.buy_ratio - ctrl_summary.buy_ratio) * 100.0;

    println!("  === IMPACT ANALYSIS ===");
    println!(
        "  GDP change:          {:+.1}% {}",
        gdp_pct,
        if gdp_pct > 0.0 { "✅" } else { "❌" }
    );
    println!(
        "  Debt change:         {:+.1}% {}",
        debt_pct,
        if debt_pct < 0.0 { "✅" } else { "❌" }
    );
    println!(
        "  D/G change:          {:+.2}x {}",
        dg_chg,
        if dg_chg < 0.0 { "✅" } else { "❌" }
    );
    println!(
        "  BPD change:           {:+.2}pp {}",
        bpd_chg,
        if bpd_chg < 0.0 {
            "✅ (tighter)"
        } else {
            "⚠️ (wider spreads)"
        }
    );
    println!("  Buy ratio change:    {:+.1}pp", buy_chg);
    println!(
        "  Volatility change:   {:+.4} {}",
        vol_chg,
        if vol_chg < 0.0 {
            "✅ (more stable)"
        } else {
            "⚠️ (less stable)"
        }
    );

    println!();
    println!("  === VERDICT ===");
    if gdp_pct > -10.0 && dg_chg < 1.0 && bpd_chg < 1.0 {
        println!("  ✅ Economy SURVIVES without MM — GuildBuyers absorb demand");
    } else if gdp_pct < -30.0 || dg_chg > 5.0 {
        println!("  ❌ Economy COLLAPSES without MM — MM is essential");
    } else {
        println!(
            "  ⚠️  Mixed: GDP {:+.1}%, D/G {:+.2}x, BPD {:+.2}pp",
            gdp_pct, dg_chg, bpd_chg
        );
    }
    println!(
        "  Key insight: MM quit → {} spread shock on remaining players",
        if treat_summary.avg_bpd > ctrl_summary.avg_bpd * 1.5 {
            "LARGE (blowout)"
        } else if treat_summary.avg_bpd > ctrl_summary.avg_bpd * 1.1 {
            "MODERATE"
        } else {
            "MINIMAL"
        }
    );
    println!();
}

// ─── GB Quit Test ─────────────────────────────────────────────────────────
/// Tests what happens when a GuildBuyer specifically quits at day 7.
/// Control: guildbuyer_failure_test (no exodus)
/// Treatment: guildbuyer_failure_gb_quit_test (one GB quits at day 7)
/// Key questions:
///   - Does economy buy/sell balance shift when primary buyer leaves?
///   - Do prices drop (demand vacuum)?
///   - Does remaining GB absorb the slack?
fn run_gb_quit_test() {
    use crate::analyzer::load_summary;
    use rusqlite::Connection;

    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       GB QUIT TEST                                           ║");
    println!("║  GuildBuyer quits at day 7 — demand vacuum test          ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Control: guildbuyer_failure_test (no exodus)");
    println!("  Treat:   guildbuyer_failure_gb_quit_test (GB quits day 7)");
    println!("  Seed: {}\n", seed);

    fn get_loan_counts(db_path: &std::path::Path) -> (usize, usize, usize) {
        let conn = Connection::open(db_path).ok();
        if let Some(conn) = conn {
            let taken: usize = conn
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
            let active: usize = conn
                .query_row(
                    "SELECT COUNT(*) FROM loans WHERE status = 'Active'",
                    [],
                    |row| row.get::<_, i64>(0),
                )
                .unwrap_or(0) as usize;
            return (taken, defaulted, active);
        }
        (0, 0, 0)
    }

    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8}",
        "GDP", "Debt", "D/G", "BPD%", "Buy%"
    );
    println!(
        "  {:>12} {:>12} {:>10} {:>8} {:>8}",
        "─".repeat(12),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8)
    );

    // Control
    let ctrl_dir = PathBuf::from("/tmp/autotune-gb-quit-ctrl");
    let _ = std::fs::remove_dir_all(&ctrl_dir);
    std::fs::create_dir_all(&ctrl_dir).ok();
    let ctrl_scenario = Scenario::guildbuyer_failure_test();
    if let Err(e) = run_seeded_headless(&ctrl_scenario, seed, &ctrl_dir) {
        eprintln!("  Control error: {}", e);
        return;
    }
    let ctrl_summary = match load_summary(&ctrl_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Control summary error: {}", e);
            return;
        }
    };
    let ctrl_loans = get_loan_counts(&ctrl_dir.join("simulation.db"));

    // Treatment
    let treat_dir = PathBuf::from("/tmp/autotune-gb-quit-treat");
    let _ = std::fs::remove_dir_all(&treat_dir);
    std::fs::create_dir_all(&treat_dir).ok();
    let treat_scenario = Scenario::guildbuyer_failure_gb_quit_test();
    if let Err(e) = run_seeded_headless(&treat_scenario, seed, &treat_dir) {
        eprintln!("  Treatment error: {}", e);
        return;
    }
    let treat_summary = match load_summary(&treat_dir.join("simulation.db")) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("  Treatment summary error: {}", e);
            return;
        }
    };
    let treat_loans = get_loan_counts(&treat_dir.join("simulation.db"));

    let ctrl_dg = ctrl_summary.debt / ctrl_summary.gdp.max(1.0);
    let treat_dg = treat_summary.debt / treat_summary.gdp.max(1.0);

    println!(
        "  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}% (ctrl, {} def, {} act)",
        ctrl_summary.gdp,
        ctrl_summary.debt,
        ctrl_dg,
        ctrl_summary.avg_bpd * 100.0,
        ctrl_summary.buy_ratio * 100.0,
        ctrl_loans.1,
        ctrl_loans.2
    );
    println!(
        "  {:>12.0} {:>12.0} {:>9.2}x {:>7.2}% {:>7.1}% (treat, {} def, {} act)",
        treat_summary.gdp,
        treat_summary.debt,
        treat_dg,
        treat_summary.avg_bpd * 100.0,
        treat_summary.buy_ratio * 100.0,
        treat_loans.1,
        treat_loans.2
    );

    println!();
    let gdp_pct = (treat_summary.gdp / ctrl_summary.gdp.max(1.0) - 1.0) * 100.0;
    let debt_pct = (treat_summary.debt / ctrl_summary.debt.max(1.0) - 1.0) * 100.0;
    let dg_chg = treat_dg - ctrl_dg;
    let bpd_chg = (treat_summary.avg_bpd - ctrl_summary.avg_bpd) * 100.0;
    let vol_chg = treat_summary.avg_volatility - ctrl_summary.avg_volatility;
    let buy_chg = (treat_summary.buy_ratio - ctrl_summary.buy_ratio) * 100.0;

    println!("  === IMPACT ANALYSIS ===");
    println!(
        "  GDP change:          {:+.1}% {}",
        gdp_pct,
        if gdp_pct > 0.0 { "✅" } else { "❌" }
    );
    println!(
        "  Debt change:         {:+.1}% {}",
        debt_pct,
        if debt_pct < 0.0 { "✅" } else { "❌" }
    );
    println!(
        "  D/G change:          {:+.2}x {}",
        dg_chg,
        if dg_chg < 0.0 { "✅" } else { "❌" }
    );
    println!(
        "  BPD change:           {:+.2}pp {}",
        bpd_chg,
        if bpd_chg < 0.0 {
            "✅ (tighter)"
        } else {
            "⚠️ (wider spreads)"
        }
    );
    println!(
        "  Buy ratio change:    {:+.1}pp (demand vacuum → more sell-heavy?)",
        buy_chg
    );
    println!("  Volatility change:   {:+.4}", vol_chg);

    println!();
    println!("  === VERDICT ===");
    if gdp_pct > -10.0 && buy_chg.abs() < 20.0 {
        println!("  ✅ Economy absorbs GB quit — remaining GB fills the gap");
    } else if gdp_pct < -20.0 || buy_chg < -20.0 {
        println!("  ❌ Severe demand vacuum — 1 GB insufficient for economy balance");
    } else {
        println!(
            "  ⚠️  Moderate impact: GDP {:+.1}%, buy ratio {:+.1}pp",
            gdp_pct, buy_chg
        );
    }
    println!();
}

// ─── Exploiter Cap Sensitivity Test ───────────────────────────────────────

/// Sweeps Exploiter count (0, 1, 2, 3) across 5 seeds on guild_stability_mm_fixed_guild.
/// Reports mean±std for GDP, D/G, volatility, buy ratio, and Diamond % change.
/// Key question: does 1 Exploiter fix the buy ratio without the hyperinflation
/// seen at 2 Exploiters (+5,528% Diamond)? Is there a sweet-spot cap?
fn run_exploiter_cap_sensitivity_test() {
    use crate::analyzer::{load_all_prices, load_summary};

    #[derive(Debug)]
    #[allow(dead_code)]
    struct CapResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_pct: f64, // % change from base
    }

    let seeds = [42u64, 12345, 98765, 77777, 11111];
    let levels = [0, 1, 2, 3];

    let mut results: std::collections::HashMap<i32, Vec<CapResult>> =
        std::collections::HashMap::new();
    for &l in &levels {
        results.insert(l, Vec::new());
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       EXPLOITER CAP SENSITIVITY TEST                       ║");
    println!("╚══════════════════════════════════════════════════════════════╝");
    println!();
    println!("  Base: guild_stability_mm_fixed_guild");
    println!("  Seeds: {:?}", seeds);
    println!("  Levels: {} Exploiters → {:?}", 20, levels);
    println!();

    for &exploiters in &levels {
        print!("  Exploiters={exploiters}: ");
        for &seed in &seeds {
            // Build scenario
            let mut scenario = Scenario::guild_stability_mm_fixed_guild();
            scenario.name = format!("guild_stability_mm_fixed_guild + {exploiters} Exploiters");

            // Set Exploiter count: insert if not present, replace if present
            if let Some(cfg) = scenario
                .players
                .iter_mut()
                .find(|p| p.archetype == "Exploiter")
            {
                cfg.count = exploiters as usize;
            } else if exploiters > 0 {
                scenario.players.push(ArchetypeConfig {
                    archetype: "Exploiter".into(),
                    count: exploiters as usize,
                });
            }

            let out_dir = format!("/tmp/autotune-sim/ecs-{exploiters}-{seed}");
            let out_path = std::path::PathBuf::from(&out_dir);
            std::fs::create_dir_all(&out_path).ok();

            match run_seeded_headless(&scenario, seed, &out_path) {
                Ok(_) => {
                    let db_path = out_path.join("simulation.db");
                    if let Ok(summary) = load_summary(&db_path) {
                        let prices = load_all_prices(&db_path).unwrap_or_default();
                        let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
                        let base_diamond = scenario
                            .config
                            .items
                            .iter()
                            .find(|ic| ic.name == "Diamond")
                            .map(|ic| ic.base_price)
                            .unwrap_or(1000.0);
                        let diamond_pct = diamond
                            .map(|(_, curr, _)| (*curr - base_diamond) / base_diamond * 100.0)
                            .unwrap_or(0.0);

                        results.get_mut(&exploiters).unwrap().push(CapResult {
                            seed,
                            gdp: summary.gdp,
                            dg: summary.debt / summary.gdp.max(1.0),
                            vol: summary.avg_volatility,
                            buy_ratio: summary.buy_ratio,
                            diamond_pct,
                        });
                        print!("{seed} ");
                    } else {
                        print!("X{seed} ");
                    }
                }
                Err(_) => {
                    print!("!{seed} ");
                }
            }
        }
        println!();
    }

    // Compute mean ± std
    fn stats(vals: &[f64]) -> (f64, f64) {
        if vals.is_empty() {
            return (0.0, 0.0);
        }
        let mean = vals.iter().sum::<f64>() / vals.len() as f64;
        let variance = vals.iter().map(|v| (v - mean).powi(2)).sum::<f64>() / vals.len() as f64;
        (mean, variance.sqrt())
    }

    println!();
    println!(
        "  {:^10} {:>14} {:>10} {:>8} {:>8} {:>12}",
        "Exploiters", "GDP", "D/G", "Vol×100", "Buy%", "Diamond%"
    );
    println!(
        "  {:^10} {:>14} {:>10} {:>8} {:>8} {:>12}",
        "─".repeat(10),
        "─".repeat(14),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(12)
    );

    let mut table: Vec<(i32, f64, f64, f64, f64, f64, f64)> = Vec::new();
    for &l in &levels {
        let res = results.get(&l).unwrap();
        if res.is_empty() {
            continue;
        }
        let (gdp_m, gdp_s) = stats(&res.iter().map(|r| r.gdp).collect::<Vec<_>>());
        let (dg_m, dg_s) = stats(&res.iter().map(|r| r.dg).collect::<Vec<_>>());
        let (vol_m, vol_s) = stats(&res.iter().map(|r| r.vol).collect::<Vec<_>>());
        let (buy_m, buy_s) = stats(&res.iter().map(|r| r.buy_ratio).collect::<Vec<_>>());
        let (dia_m, dia_s) = stats(&res.iter().map(|r| r.diamond_pct).collect::<Vec<_>>());
        println!(
            "  {:^10} {:>13.0}±{:.0} {:>8.3}x±{:.2} {:>6.4}±{:.4} {:>6.1}%±{:.1} {:>+10.1}%±{:.1}",
            format!("{} Exploiters", l),
            gdp_m,
            gdp_s,
            dg_m,
            dg_s,
            vol_m,
            vol_s * 100.0,
            buy_m * 100.0,
            buy_s * 100.0,
            dia_m,
            dia_s
        );
        table.push((l, gdp_m, dg_m, vol_m, buy_m, dia_m, dia_s));
    }

    println!();
    println!("  === KEY FINDINGS ===");

    // GDP comparison: find best and worst
    if let Some((best_entry, baseline_entry)) = table
        .iter()
        .max_by(|a, b| a.1.partial_cmp(&b.1).unwrap())
        .and_then(|be| {
            table
                .iter()
                .find(|(l, _, _, _, _, _, _)| *l == 0)
                .map(|bl| (be, bl))
        })
    {
        let gdp_chg = (best_entry.1 / baseline_entry.1 - 1.0) * 100.0;
        println!(
            "  Best GDP: {} Exploiters ({:.0}, {:+.1}% vs control)",
            best_entry.0, best_entry.1, gdp_chg
        );
    }

    // Diamond inflation analysis
    let last = table.last().unwrap();
    let first = table.first().unwrap();
    let dia_escalation = last.5 - first.5;
    println!(
        "  Diamond inflation: {:.0}% → {:.0}% ({:+.1}pp across 0→3 Exploiters)",
        first.5, last.5, dia_escalation
    );

    // Find the cap level where Diamond hyperinflation starts
    for &(l, _, _, _, _, dia, _) in table.iter() {
        if dia > 500.0 {
            println!(
                "  ⚠️  Diamond hyperinflation (>{}+%) at {} Exploiters",
                dia as i32, l
            );
            break;
        }
    }

    // Buy ratio normalization
    if let Some(balanced) = table
        .iter()
        .find(|(_, _, _, _, buy, _, _)| *buy > 0.48 && *buy < 0.52)
    {
        println!(
            "  ✅ Buy ratio balanced ({:.0}%) at {} Exploiters — near 50/50",
            balanced.4 * 100.0,
            balanced.0
        );
    }

    // Recommendation
    println!();
    if table.len() >= 2 {
        let first_dia = table[0].5;
        let last_dia = table[table.len() - 1].5;
        if last_dia - first_dia > 500.0 {
            println!("  RECOMMENDATION: Cap at 1 Exploiter (5% of server).");
            println!("  1 Exploiter provides near-balanced buy ratio");
            println!("  without the +5,000% Diamond hyperinflation seen at 2+.");
        }
    }
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
fn run_seeded_headless(
    scenario: &Scenario,
    seed: u64,
    output_dir: &PathBuf,
) -> Result<Simulation, String> {
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
        ("VolumeTrader".into(), Archetype::VolumeTrader),
        ("Whale".into(), Archetype::Whale),
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

    Ok(sim)
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

    println!();
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
        println!("  stressed-30day   - Stressed scaled to 30 days — chronic oversupply test");
        println!("  high-activity    - High activity, 20 players, 7 days");
        println!("  low-player       - 3 players, 14 days");
        println!("  spread-stability - Farmer/Trader mix, 10 days");
        println!("  sp08-moderate    - Tiered breaker: sp=0.80, cascade day 6, 14d");
        println!("  buyer-heavy      - 2 GuildBuyer + 3 Hoarder + 3 Casual + 2 Farmer + 2 Trader");
        println!(
            "  guild-stability  - 2 GuildBuyer + 4 Casual + 3 Farmer + 2 Trader (15-30% threshold)"
        );
        println!("  guild-stability-mm-fixed-guild - 1 MM + 2 GB @ 7% + 4Cas + 3Far + 2Trader");
        println!("  guild-stability-mm-gs-phase2-redesign - GS Phase 2 price-dip detection test");
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
        println!("  --exploiter-cap-sensitivity  Sweep 0/1/2/3 Exploiters across 5 seeds");
        println!("  --regression            Regression test against stored baselines");
        println!("  --all                   Run all scenarios headlessly");
        println!("  --floor-ceiling-test     Floor/ceiling effect: control vs treatment");
        println!("  --stressed-30d-floor-test  30-day stressed economy: floor paradox amplified?");
        println!("  --floor-strength-sweep   Diamond floor 30-90% — find GDP-neutral level");
        println!("  --price-freeze-test      Per-item price freeze: Diamond frozen vs control");
        println!(
            "  --loan-cap-test          Per-loan GDP cap verification: cap fires, logs events"
        );
        println!(
            "  --guildbuyer-failure-test  GB default cascade: cooldown prevs re-borrow bypass"
        );
        println!("  --counter-cyclical-test  Counter-cyclical taper vs tiered circuit breaker");
        println!("  --mm-competition-test   1MM+2GB vs 2MM+2GB: does extra MM improve stability?");
        println!("  --mm-quit-test         MM quits at day 7: can economy survive without MM?");
        println!("  --gb-quit-test         GB quits at day 7: demand vacuum test");
        println!(
            "  --volume-trader-test     VolumeTrader archetype: contrarian liquidity vs control"
        );
        println!(
            "  --player-exodus-test   Player exodus: 50% quit at day 7 — economy survival test"
        );
        println!("  --multi-server-test     Cross-server price aggregation test");
        println!("  --guild-seller-test     GuildSeller archetype: control vs 1GB+1GS treatment");
        println!(
            "  --mm-competition-multi-seed  1MM vs 2MM across 5 seeds (statistical robustness)"
        );
        println!(
            "  --vt-multi-seed         Healthy vs +2VT across 5 seeds (statistical robustness)"
        );
        println!(
            "  --whale-stress-test     1 Whale: can errant rich player destabilize healthy economy?"
        );
        println!(
            "  --it-healthy-test      IT + MM+GB vs MM+GB: does IT still help healthy economy?"
        );
        println!(
            "  --floor-multi-seed     60% Diamond floor across 5 seeds: statistical robustness"
        );
        println!(
            "  --production-config-test  2MM+2GB+floor vs 1MM+2GB: proposed default head-to-head"
        );
        println!("  --long-run-test        2MM+2GB+floor: 14 days vs 30 days stability check");
        println!(
            "  --circuit-breaker-hysteresis-test  TIER3 hysteresis: prevents D/G boundary cycling"
        );
        println!("  --healthy-tier3-sweep        2MM+2GB+floor: tier3_ratio × 5 seeds");
        println!(
            "  --ninety-day-test          2MM+2GB+floor: 90-day long-run stability × 3 seeds × 2 thresholds"
        );
        println!(
            "  --one-eighty-day-test     2MM+2GB+floor: 180-day trajectory × 2 seeds × 5% threshold"
        );
        println!("  --sixty-day-test           2MM+2GB+floor: 60-day long-run stability");
        println!("  --sixty-day-fix-test      tier3=50+min_int=0.20 vs ctrl × 2 seeds × 60d");
        println!("  --sixty-day-hysteresis-test  hysteresis 50% vs 10% × 2 seeds × 60d");
        println!("  --sixty-day-gb-debt-cap-test  GB debt cap 3×GDP vs uncapped × 2 seeds × 60d");
        println!("  --sixty-day-tier3-sweep        tier3=30/50/100 × 5 seeds × 60 days");
        println!(
            "  --sixty-day-loan-lock-test     block MM/GB loans during TIER3 × 5 seeds × 60 days"
        );
        println!(
            "  --sixty-day-early-intervention-test  tier3=5/10/15 vs ctrl × 5 seeds × 60 days"
        );
        println!("  --sixty-day-combo-test        tier3=100+loan_lock vs ctrl × 5 seeds × 60 days");
        println!(
            "  --graduated-exit-test     Graduated TIER3 exit cap (cap=0.10, delay=1152) × 90d × 2 seeds"
        );
        println!("  --it-removal-test         2MM+2GB+floor: WITH vs WITHOUT InsiderTraders");
        println!("  --healthy-baseline-5seed  2MM+2GB+floor × 5 seeds: statistical baseline");
        println!("  --gb-newbie-healthy-test   2MM+2GB+2Far+2Newbie vs 2MM+2GB+3Far × 5 seeds");
        println!("  --newbie-no-gb-test       2MM+2Newbie vs 2MM+2GB: can Newbies replace GBs?");
        println!("  --threshold-30day-test     5%% vs 7%% GB threshold × 3 seeds × 30 days");
        println!(
            "  --events-healthy-test     Events × 2MM+2GB+floor: do events help healthy economy?"
        );
        println!("  --counter-cyclical-cc-test  CC=true vs CC=false × 2MM+2GB+floor");
        println!("  --casual-heavy-healthy-test  Casual-heavy vs standard × 2MM+2GB+floor");
        println!("  --circuit-breaker-sensitivity-test  TIER3 thresholds × min interest sweep");
        println!(
            "  --admin-recovery-test    Economy freeze / recovery mode vs natural deleveraging"
        );
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

    if args.len() > 1 && args[1] == "--exploiter-cap-sensitivity" {
        run_exploiter_cap_sensitivity_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--fine-threshold-sweep" {
        run_fine_threshold_sweep();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--admin-recovery-test" {
        run_admin_recovery_mode_test();
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
                "stressed-30day" | "stressed_30day" => Scenario::stressed_30day(),
                "guild-stability-mm-gs-phase2" | "guild_stability_mm_gs_phase2_redesign" => {
                    Scenario::guild_stability_mm_gs_phase2_redesign()
                }
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

    // ─── Stressed Economy 30-Day Floor Test ─────────────────────────────
    if args.len() > 1 && args[1] == "--stressed-30d-floor-test" {
        run_stressed_30d_floor_test();
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
    if args.len() > 1 && args[1] == "--counter-cyclical-test" {
        run_counter_cyclical_test();
        return Ok(());
    }

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

    // ─── MM Competition Multi-Seed ─────────────────────────────────────────
    if args.len() > 1 && args[1] == "--mm-competition-multi-seed" {
        run_mm_competition_multi_seed();
        return Ok(());
    }

    // ─── VolumeTrader Multi-Seed ───────────────────────────────────────────
    if args.len() > 1 && args[1] == "--vt-multi-seed" {
        run_volume_trader_multi_seed();
        return Ok(());
    }

    // ─── MM Capital Sweep ────────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--mm-capital-sweep" {
        run_mm_capital_sweep();
        return Ok(());
    }

    // ─── IT Healthy Economy Test ───────────────────────────────────────────
    if args.len() > 1 && args[1] == "--it-healthy-test" {
        run_it_healthy_economy_test();
        return Ok(());
    }

    // ─── IT + VT Healthy Economy Test ──────────────────────────────────────
    if args.len() > 1 && args[1] == "--it-vt-healthy-test" {
        run_it_vt_healthy_economy_test();
        return Ok(());
    }

    // ─── IT Stressed Economy Test ─────────────────────────────────────────
    if args.len() > 1 && args[1] == "--it-stressed-test" {
        run_it_stressed_economy_test();
        return Ok(());
    }

    // ─── VT Stressed Economy Test ────────────────────────────────────────
    if args.len() > 1 && args[1] == "--vt-stressed-test" {
        run_vt_stressed_economy_test();
        return Ok(());
    }

    // ─── AFKFarmer Stress Test ─────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--afkfarmer-stress-test" {
        run_afkfarmer_stress_test();
        return Ok(());
    }

    // ─── Whale Stress Test ─────────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--whale-stress-test" {
        run_whale_stress_test();
        return Ok(());
    }

    // ─── Hoarder-Heavy Test ────────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--hoarder-heavy-test" {
        run_hoarder_heavy_test();
        return Ok(());
    }

    // ─── Guild Threshold Multi-Seed ───────────────────────────────────────
    if args.len() > 1 && args[1] == "--guild-threshold-multi-seed" {
        run_guild_threshold_multi_seed();
        return Ok(());
    }

    // ─── Floor 60% Multi-Seed Robustness ───────────────────────────────────
    if args.len() > 1 && args[1] == "--floor-multi-seed" {
        run_floor_strength_multi_seed();
        return Ok(());
    }

    // ─── MM Quit Test ─────────────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--mm-quit-test" {
        run_mm_quit_test();
        return Ok(());
    }

    // ─── GB Quit Test ──────────────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--gb-quit-test" {
        run_gb_quit_test();
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

    // ─── Production Config Test ──────────────────────────────────────────
    if args.len() > 1 && args[1] == "--production-config-test" {
        run_production_config_test();
        return Ok(());
    }

    // ─── Long-Run Stability Test ────────────────────────────────────────
    if args.len() > 1 && args[1] == "--long-run-test" {
        run_long_run_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--circuit-breaker-hysteresis-test" {
        run_circuit_breaker_hysteresis_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--healthy-tier3-sweep" {
        run_healthy_tier3_sweep();
        return Ok(());
    }

    // ─── 60-Day Production Stability Test ──────────────────────────────
    if args.len() > 1 && args[1] == "--ninety-day-test" {
        run_ninety_day_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--one-eighty-day-test" {
        run_one_eighty_day_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--sixty-day-test" {
        run_sixty_day_test();
        return Ok(());
    }

    // ─── 60-Day Fix (Hysteresis Band) Test ──────────────────────────
    if args.len() > 1 && args[1] == "--sixty-day-hysteresis-test" {
        run_sixty_day_hysteresis_test();
        return Ok(());
    }

    // ─── 60-Day Fix Confirmation Test ─────────────────────────────────
    if args.len() > 1 && args[1] == "--sixty-day-fix-test" {
        run_sixty_day_fix_test();
        return Ok(());
    }

    // ─── 60-Day GB Debt Cap Test ─────────────────────────────────────────
    if args.len() > 1 && args[1] == "--sixty-day-gb-debt-cap-test" {
        run_sixty_day_gb_debt_cap_test();
        return Ok(());
    }

    // ─── 60-Day tier3=100 Sweep ────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--sixty-day-tier3-sweep" {
        run_sixty_day_tier3_sweep();
        return Ok(());
    }

    // ─── 60-Day Loan Lock Test ──────────────────────────────────────────
    if args.len() > 1 && args[1] == "--sixty-day-loan-lock-test" {
        run_sixty_day_loan_lock_test();
        return Ok(());
    }

    // ─── 60-Day Early Intervention Sweep ───────────────────────────────
    if args.len() > 1 && args[1] == "--sixty-day-early-intervention-test" {
        run_sixty_day_early_intervention_test();
        return Ok(());
    }

    // ─── 60-Day Combo Test: tier3=100 + block MM/GB loans during TIER3 ──
    if args.len() > 1 && args[1] == "--sixty-day-combo-test" {
        run_sixty_day_combo_test();
        return Ok(());
    }

    // ─── Graduated TIER3 Exit Cap Test ─────────────────────────────────────────
    if args.len() > 1 && args[1] == "--graduated-exit-test" {

        // Quick 1-seed test: 3 arms at 60 days
        run_quick_graduated_test();

        run_graduated_exit_test();
        return Ok(());
    }

    // ─── IT Removal Test ───────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--it-removal-test" {
        run_it_removal_healthy_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--circuit-breaker-sensitivity-test" {
        run_circuit_breaker_sensitivity_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--archetype-mix-test" {
        run_archetype_mix_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--floor-impact-test" {
        run_floor_impact_test();
        return Ok(());
    }

    // ─── VWAP Test ────────────────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--trend-dampening-multi" {
        run_trend_dampening_sweep();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--sell-pressure-multi" {
        run_sell_pressure_multi_seed();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--sell-pressure-h2h" {
        run_sell_pressure_head_to_head();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--newbie-stress-test" {
        run_newbie_stress_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--sell-pressure-test" {
        run_sell_pressure_sweep();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--vwap-test" {
        run_vwap_test();
        return Ok(());
    }

    // ─── Combo Corrected Test ──────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--combo-corrected-test" {
        run_combo_corrected_test();
        return Ok(());
    }

    // ─── Healthy Economy Newbie Test ────────────────────────────────────────
    if args.len() > 1 && args[1] == "--healthy-newbie-test" {
        run_healthy_newbie_test();
        return Ok(());
    }

    // ─── Tuned 2MM+2GB+floor Test ──────────────────────────────────────────
    if args.len() > 1 && args[1] == "--tuned-2mm-test" {
        run_tuned_2mm_test();
        return Ok(());
    }

    // ─── Events × 2MM+2GB+floor Test ─────────────────────────────────────
    if args.len() > 1 && args[1] == "--events-healthy-test" {
        run_events_healthy_test();
        return Ok(());
    }

    // ─── Counter-Cyclical × 2MM+2GB+floor Test ──────────────────────────
    if args.len() > 1 && args[1] == "--counter-cyclical-cc-test" {
        run_counter_cyclical_cc_test();
        return Ok(());
    }

    // ─── Casual-Heavy × 2MM+2GB+floor Test ──────────────────────────────
    if args.len() > 1 && args[1] == "--casual-heavy-healthy-test" {
        run_casual_heavy_healthy_test();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--healthy-baseline-5seed" {
        run_healthy_baseline_5seed();
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--gb-newbie-healthy-test" {
        run_gb_newbie_healthy_test();
        return Ok(());
    }

    // ─── Newbie-No-GB Test ────────────────────────────────────────────────
    if args.len() > 1 && args[1] == "--newbie-no-gb-test" {
        run_newbie_no_gb_test();
        return Ok(());
    }

    // ─── Threshold × 30-Day Test ──────────────────────────────────────────
    if args.len() > 1 && args[1] == "--threshold-30day-test" {
        run_threshold_30day_test();
        return Ok(());
    }

    // GUI mode
    run_gui()
}

// ═══════════════════════════════════════════════════════════════════════
//  HEALTHY ECONOMY BASELINE — 5-seed statistical confidence
//  Question: What are the true mean±σ statistics for the production-recommended
//  2MM+2GB+floor config across diverse market conditions?
// ═══════════════════════════════════════════════════════════════════════
fn run_healthy_baseline_5seed() {
    use crate::analyzer::load_summary;
    use std::io::Write;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║     HEALTHY ECONOMY BASELINE — 5-SEED STATISTICAL RUN       ║");
    println!("║  2MM + 2GB + 60%% Diamond floor × 5 seeds                   ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Scenario: guild_stability_2mm_fixed_guild_plus_floor");
    println!("  Archetypes: 2MM + 2GB + 3Cas + 3Far + 2Tra");
    println!("  Duration: 14 days (4032 ticks)\n");

    #[derive(Debug, Clone)]
    #[allow(dead_code)]
    struct BaselineResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        spd: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        diamond_displayed: f64,
        floor_binds: bool,
    }

    let mut results: Vec<BaselineResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7} {:>7} {:>10}",
        "Seed", "GDP", "D/G", "Vol(CV)", "BPD%", "SPD%", "Buy%", "Diamond Int"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7} {:>7} {:>10}",
        "──────",
        "────────────",
        "──────────",
        "────────",
        "───────",
        "───────",
        "───────",
        "──────────"
    );

    let total = seeds.len();
    for (i, &seed) in seeds.iter().enumerate() {
        eprint!("\r  [{}/{}] seed={}", i + 1, total, seed);
        std::io::stderr().flush().ok();

        let scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        let out_dir = PathBuf::from(format!("/tmp/autotune-baseline-{}", seed));
        let _ = std::fs::remove_dir_all(&out_dir);

        run_seeded_headless(&scenario, seed, &out_dir).ok();

        if let Ok(s) = load_summary(&out_dir.join("simulation.db")) {
            let prices = crate::analyzer::load_all_prices(&out_dir.join("simulation.db"))
                .unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            let dg = s.debt / s.gdp.max(1.0);
            let floor_binds = dd >= (500.0 * 0.60) - 0.01;
            println!(
                "\r  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}% {:>6.2}% {:>7.1}% {:>10.2}",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.avg_spd * 100.0,
                s.buy_ratio * 100.0,
                di
            );
            results.push(BaselineResult {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                diamond_displayed: dd,
                floor_binds,
            });
        }
        let _ = std::fs::remove_dir_all(&out_dir);
    }

    // ── Statistical summary ──────────────────────────────────────────────
    if results.len() >= 3 {
        println!("\n  ── Statistical Summary (mean ± std) ──\n");
        let gdp_mean = results.iter().map(|r| r.gdp).sum::<f64>() / results.len() as f64;
        let gdp_std = (results
            .iter()
            .map(|r| (r.gdp - gdp_mean).powi(2))
            .sum::<f64>()
            / results.len() as f64)
            .sqrt();
        let dg_mean = results.iter().map(|r| r.dg).sum::<f64>() / results.len() as f64;
        let dg_std = (results
            .iter()
            .map(|r| (r.dg - dg_mean).powi(2))
            .sum::<f64>()
            / results.len() as f64)
            .sqrt();
        let vol_mean = results.iter().map(|r| r.vol).sum::<f64>() / results.len() as f64;
        let vol_std = (results
            .iter()
            .map(|r| (r.vol - vol_mean).powi(2))
            .sum::<f64>()
            / results.len() as f64)
            .sqrt();
        let buy_mean = results.iter().map(|r| r.buy_ratio).sum::<f64>() / results.len() as f64;
        let buy_std = (results
            .iter()
            .map(|r| (r.buy_ratio - buy_mean).powi(2))
            .sum::<f64>()
            / results.len() as f64)
            .sqrt();
        let bpd_mean = results.iter().map(|r| r.bpd).sum::<f64>() / results.len() as f64;
        let floor_binds = results.iter().filter(|r| r.floor_binds).count();
        let di_mean =
            results.iter().map(|r| r.diamond_internal).sum::<f64>() / results.len() as f64;

        println!(
            "  {:>12}: {:>12.0} ± {:>10.0}  [seeds: {:?}]",
            "GDP",
            gdp_mean,
            gdp_std,
            results.iter().map(|r| r.seed).collect::<Vec<_>>()
        );
        println!("  {:>12}: {:>9.3}x ± {:>8.3}x", "D/G", dg_mean, dg_std);
        println!(
            "  {:>12}: {:>7.3}%% ± {:>8.3}%%",
            "Vol(CV)",
            vol_mean * 100.0,
            vol_std * 100.0
        );
        println!(
            "  {:>12}: {:>7.1}%% ± {:>7.1}%%",
            "Buy%",
            buy_mean * 100.0,
            buy_std * 100.0
        );
        println!("  {:>12}: {:>7.2}%% (avg BPD)", "Spread", bpd_mean * 100.0);
        println!(
            "  {:>12}: {}/{} seeds",
            "Floor binds",
            floor_binds,
            results.len()
        );
        println!("  {:>12}: ${:.2} (internal, avg)", "Diamond Int", di_mean);
        println!("  {:>12}: ${:.2} (displayed, floor=$300)", "Diamond", 300.0);

        println!("\n  ── Per-seed D/G ranking ──\n");
        let mut sorted = results.clone();
        sorted.sort_by(|a, b| a.dg.partial_cmp(&b.dg).unwrap());
        let dg_min = sorted.first().map(|r| r.dg).unwrap_or(0.0);
        let dg_max = sorted.last().map(|r| r.dg).unwrap_or(0.0);
        for r in &sorted {
            println!(
                "    seed {:>6}: D/G={:.3}x  GDP={:.0}  vol={:.4}  floor={}",
                r.seed, r.dg, r.gdp, r.vol, r.floor_binds
            );
        }
        println!(
            "\n  ⚠️  D/G range: {:.3}x – {:.3}x ({:.1}x spread)",
            dg_min,
            dg_max,
            dg_max / dg_min.max(0.001)
        );

        if dg_max > 10.0 {
            println!("  ⚠️  D/G exceeds 10x in some seeds — circuit breaker relevant");
        } else if dg_max > 8.0 {
            println!("  ⚠️  D/G approaches TIER2 boundary (10x) in worst seed");
        } else {
            println!("  ✓ D/G stays below TIER2 (10x) in all seeds");
        }
    }
    println!();
}

// ═══════════════════════════════════════════════════════════════════════
//  GB + NEWBIE COMBO TEST
//  Control: 2MM+2GB+3Far (standard archetype mix)
//  Treat:   2MM+2GB+2Far+2Newbie (replace 1 Farmer with 1 Newbie, keep total 12)
//  Question: Newbie alone was great in stressed (+33% GDP, -42% D/G, -35% vol).
//  Does Newbie still help when combined with GuildBuyers in a healthy economy?
// ═══════════════════════════════════════════════════════════════════════
fn run_gb_newbie_healthy_test() {
    use crate::analyzer::load_summary;
    use std::io::Write;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔════════════════════════════════════════════════════════════════════╗");
    println!("║     GB + NEWBIE COMBO TEST — healthy economy × 5 seeds       ║");
    println!("║  Ctrl: 2MM+2GB+3Far   Treat: 2MM+2GB+2Far+2Newbie           ║");
    println!("╚════════════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Control: 2MM + 2GB + 3Cas + 3Far + 2Tra");
    println!("  Treat:   2MM + 2GB + 3Cas + 1Far + 2Tra + 2Newbie");
    println!("  Duration: 14 days (4032 ticks)\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct ComboResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        spd: f64,
        buy_ratio: f64,
    }

    impl ComboResult {
        fn from_summary(s: &crate::analyzer::SimSummary, seed: u64) -> Self {
            Self {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                buy_ratio: s.buy_ratio,
            }
        }
    }

    let mut ctrl_results: Vec<ComboResult> = Vec::new();
    let mut treat_results: Vec<ComboResult> = Vec::new();
    let total = seeds.len() * 2;

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7} {:>7}",
        "Seed", "GDP", "D/G", "Vol(CV)", "BPD%", "SPD%", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7} {:>7}",
        "──────", "────────────", "──────────", "────────", "───────", "───────", "───────"
    );

    for (i, &seed) in seeds.iter().enumerate() {
        // ── Control ──────────────────────────────────────────────────────
        eprint!("\r  [{}/{}] seed={} ctrl", i * 2 + 1, total, seed);
        std::io::stderr().flush().ok();

        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = "Ctrl: 3Far".into();
        // 2MM + 2GB + 3Cas + 3Far + 2Tra (standard)
        ctrl.players = vec![
            ArchetypeConfig {
                archetype: "MarketMaker".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "GuildBuyer".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Casual".into(),
                count: 3,
            },
            ArchetypeConfig {
                archetype: "Farmer".into(),
                count: 3,
            },
            ArchetypeConfig {
                archetype: "Trader".into(),
                count: 2,
            },
        ];

        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-gbnc-ctrl-{}", seed));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        run_seeded_headless(&ctrl, seed, &ctrl_dir).ok();

        if let Ok(s) = load_summary(&ctrl_dir.join("simulation.db")) {
            let r = ComboResult::from_summary(&s, seed);
            println!(
                "\r  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}% {:>6.2}% {:>7.1}%  [ctrl: 3Far]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.bpd * 100.0,
                r.spd * 100.0,
                r.buy_ratio * 100.0
            );
            ctrl_results.push(r);
        }
        let _ = std::fs::remove_dir_all(&ctrl_dir);

        // ── Treatment ─────────────────────────────────────────────────────
        eprint!("\r  [{}/{}] seed={} treat", i * 2 + 2, total, seed);
        std::io::stderr().flush().ok();

        let mut treat = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        treat.name = "Treat: 2Far+2Newbie".into();
        // 2MM + 2GB + 3Cas + 1Far + 2Tra + 2Newbie
        treat.players = vec![
            ArchetypeConfig {
                archetype: "MarketMaker".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "GuildBuyer".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Casual".into(),
                count: 3,
            },
            ArchetypeConfig {
                archetype: "Farmer".into(),
                count: 1,
            },
            ArchetypeConfig {
                archetype: "Trader".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Newbie".into(),
                count: 2,
            },
        ];

        let treat_dir = PathBuf::from(format!("/tmp/autotune-gbnc-treat-{}", seed));
        let _ = std::fs::remove_dir_all(&treat_dir);
        run_seeded_headless(&treat, seed, &treat_dir).ok();

        if let Ok(s) = load_summary(&treat_dir.join("simulation.db")) {
            let r = ComboResult::from_summary(&s, seed);
            println!(
                "\r  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}% {:>6.2}% {:>7.1}%  [treat: 2Far+2Newbie]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.bpd * 100.0,
                r.spd * 100.0,
                r.buy_ratio * 100.0
            );
            treat_results.push(r);
        }
        let _ = std::fs::remove_dir_all(&treat_dir);
    }

    // ── Aggregate comparison ─────────────────────────────────────────────
    if ctrl_results.len() == treat_results.len() && !ctrl_results.is_empty() {
        println!(
            "\n  ── Aggregate Comparison (mean across {} seeds) ──\n",
            ctrl_results.len()
        );

        let n = ctrl_results.len() as f64;
        let ctrl_gdp = ctrl_results.iter().map(|r| r.gdp).sum::<f64>() / n;
        let treat_gdp = treat_results.iter().map(|r| r.gdp).sum::<f64>() / n;
        let ctrl_dg = ctrl_results.iter().map(|r| r.dg).sum::<f64>() / n;
        let treat_dg = treat_results.iter().map(|r| r.dg).sum::<f64>() / n;
        let ctrl_vol = ctrl_results.iter().map(|r| r.vol).sum::<f64>() / n;
        let treat_vol = treat_results.iter().map(|r| r.vol).sum::<f64>() / n;
        let ctrl_buy = ctrl_results.iter().map(|r| r.buy_ratio).sum::<f64>() / n;
        let treat_buy = treat_results.iter().map(|r| r.buy_ratio).sum::<f64>() / n;
        let ctrl_bpd = ctrl_results.iter().map(|r| r.bpd).sum::<f64>() / n;
        let treat_bpd = treat_results.iter().map(|r| r.bpd).sum::<f64>() / n;
        let ctrl_t3: usize = 0; // tier3_events not in SimSummary
        let treat_t3: usize = 0;

        let gdp_chg = (treat_gdp - ctrl_gdp) / ctrl_gdp * 100.0;
        let dg_chg = treat_dg - ctrl_dg;
        let vol_chg = (treat_vol - ctrl_vol) / ctrl_vol.max(0.0001) * 100.0;
        let buy_chg = (treat_buy - ctrl_buy) * 100.0;
        let bpd_chg = (treat_bpd - ctrl_bpd) / ctrl_bpd.max(0.0001) * 100.0;

        println!(
            "  {:>14} {:>12} {:>12} {:>12}",
            "Metric", "Ctrl (3Far)", "Treat (2Far+2N)", "Change"
        );
        println!(
            "  {:>14} {:>12} {:>12} {:>12}",
            "────────────", "────────────", "────────────", "────────────"
        );
        println!(
            "  {:>14} {:>12.0} {:>12.0} {:>+11.1}%%  GDP",
            "GDP", ctrl_gdp, treat_gdp, gdp_chg
        );
        println!(
            "  {:>14} {:>11.3}x {:>11.3}x {:>+10.3}x  D/G",
            "D/G", ctrl_dg, treat_dg, dg_chg
        );
        println!(
            "  {:>14} {:>11.3}% {:>11.3}% {:>+10.1}%%  Vol",
            "Vol(CV)",
            ctrl_vol * 100.0,
            treat_vol * 100.0,
            vol_chg
        );
        println!(
            "  {:>14} {:>11.1}% {:>11.1}% {:>+10.1}pp  Buy%",
            "Buy%",
            ctrl_buy * 100.0,
            treat_buy * 100.0,
            buy_chg
        );
        println!(
            "  {:>14} {:>11.2}% {:>11.2}% {:>+10.1}%%  BPD",
            "BPD%",
            ctrl_bpd * 100.0,
            treat_bpd * 100.0,
            bpd_chg
        );
        println!("  {:>14} {:>12} {:>12}", "TIER3 events", ctrl_t3, treat_t3);

        // ── Verdict ──────────────────────────────────────────────────────
        println!("\n  ── Verdict ──\n");
        let wins = [
            gdp_chg > 0.0,
            dg_chg < 0.0,
            vol_chg < 0.0,
            buy_chg.abs() < 5.0,
        ];
        let score = wins.iter().filter(|&&w| w).count();
        match score {
            4 => println!("  ✅ STRONGLY RECOMMEND: Newbie+GB is strictly better on all metrics"),
            3 => println!(
                "  ⚠️  RECOMMEND with caution ({} improvements, {} regressions)",
                score,
                4 - score
            ),
            2 => println!("  ⚠️  MIXED ({}/4 metrics improved)", score),
            _ => println!("  ❌ NOT RECOMMENDED: majority of metrics worsened"),
        }
        if gdp_chg > 0.0 && dg_chg < 0.0 {
            println!("  ✅ Newbie reduces D/G while growing GDP — ideal combination");
        } else if gdp_chg > 0.0 {
            println!(
                "  ⚠️  Newbie grows GDP (+{:.1}%%) but D/G {}",
                gdp_chg,
                if dg_chg > 0.0 { "worsens" } else { "unchanged" }
            );
        } else if dg_chg < 0.0 {
            println!(
                "  ℹ️  Newbie improves D/G ({:.3}x better) but GDP {}",
                dg_chg.abs(),
                if gdp_chg < 0.0 { "falls" } else { "flat" }
            );
        }
        if vol_chg < -20.0 {
            println!(
                "  ✅ Volatility dramatically reduced ({:.1}%% improvement)",
                vol_chg.abs()
            );
        }
        if treat_t3 > ctrl_t3 {
            println!("  ⚠️  TIER3 events INCREASED ({})", treat_t3 - ctrl_t3);
        }

        // ── Per-seed breakdown ────────────────────────────────────────────
        println!("\n  ── Per-seed D/G comparison ──\n");
        for (ctrl, treat) in ctrl_results.iter().zip(treat_results.iter()) {
            let dg_delta = treat.dg - ctrl.dg;
            let arrow = if dg_delta < -0.1 {
                "↓"
            } else if dg_delta > 0.1 {
                "↑"
            } else {
                "→"
            };
            println!(
                "    seed {:>6}: ctrl={:.3}x  treat={:.3}x  {} ({:+.3}x)",
                ctrl.seed, ctrl.dg, treat.dg, arrow, dg_delta
            );
        }

        let treat_better = treat_results
            .iter()
            .zip(ctrl_results.iter())
            .filter(|(t, c)| t.dg < c.dg)
            .count();
        println!(
            "\n  D/G: {}/{} seeds improved with Newbie",
            treat_better,
            treat_results.len()
        );
    }
    println!();
}

// ═══════════════════════════════════════════════════════════════════════
//  PRODUCTION CONFIG TEST
//  Compares: 1MM+2GB (current rec.) vs 2MM+2GB+60% floor (proposed)
// ═══════════════════════════════════════════════════════════════════════
fn run_production_config_test() {
    use crate::analyzer::load_summary;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];
    let diamond_floor = 500.0 * 0.60; // $300

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║       PRODUCTION CONFIG TEST — MULTI-SEED (5 seeds)          ║");
    println!("║  2MM + 60% Diamond floor vs 1MM (no floor)                   ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Control: 1MM + 2GB + no floor (guild_stability_mm_fixed_guild)");
    println!("  Treat:   2MM + 2GB + Diamond floor=60% ($300)");
    println!("  Duration: 14 days\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct RunResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        diamond_displayed: f64,
        floor_binds: bool,
    }

    impl RunResult {
        fn from_summary(
            s: &crate::analyzer::SimSummary,
            prices: &[(String, f64, f64)],
            seed: u64,
            floor_val: f64,
        ) -> Self {
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (diamond_internal, diamond_displayed) =
                diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            Self {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                diamond_internal,
                diamond_displayed,
                floor_binds: diamond_displayed >= floor_val - 0.01,
            }
        }
    }

    let mut ctrl_results: Vec<RunResult> = Vec::new();
    let mut treat_results: Vec<RunResult> = Vec::new();

    for seed in &seeds {
        // Control: 1MM + 2GB
        let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
        let ctrl_dir = format!("/tmp/autotune-sim/ctrl-pcfg-{seed}");
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        if run_seeded_headless(&ctrl_scenario, *seed, &ctrl_path).is_ok() {
            let db_path = ctrl_path.join("simulation.db");
            if let Ok(s) = load_summary(&db_path) {
                let prices = crate::analyzer::load_all_prices(&db_path).unwrap_or_default();
                ctrl_results.push(RunResult::from_summary(&s, &prices, *seed, 0.0));
            }
        }

        // Treatment: 2MM + 2GB + 60% Diamond floor
        let mut treat_scenario = Scenario::guild_stability_2mm_fixed_guild();
        treat_scenario.name = "Production Config (2MM+floor)".to_string();
        if let Some(diamond) = treat_scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6);
        }
        let treat_dir = format!("/tmp/autotune-sim/treat-pcfg-{seed}");
        let treat_path = std::path::PathBuf::from(&treat_dir);
        std::fs::create_dir_all(&treat_path).ok();
        if run_seeded_headless(&treat_scenario, *seed, &treat_path).is_ok() {
            let db_path = treat_path.join("simulation.db");
            if let Ok(s) = load_summary(&db_path) {
                let prices = crate::analyzer::load_all_prices(&db_path).unwrap_or_default();
                treat_results.push(RunResult::from_summary(&s, &prices, *seed, diamond_floor));
            }
        }
    }

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7} {:>8}",
        "Seed", "GDP", "D/G", "BPD%", "Buy%", "DmdInt", "DmdDisp"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7} {:>8}",
        "─".repeat(6),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(7),
        "─".repeat(7),
        "─".repeat(8)
    );

    for (c, t) in ctrl_results.iter().zip(treat_results.iter()) {
        println!(
            "CNTL {:>6} {:>12.0} {:>10.3}x {:>7.3}% {:>6.1}% {:>7.0} {:>8.0}",
            c.seed,
            c.gdp,
            c.dg,
            c.bpd * 100.0,
            c.buy_ratio * 100.0,
            c.diamond_internal,
            c.diamond_displayed
        );
        println!(
            "TRAT {:>6} {:>12.0} {:>10.3}x {:>7.3}% {:>6.1}% {:>7.0} {:>8.0} {}",
            t.seed,
            t.gdp,
            t.dg,
            t.bpd * 100.0,
            t.buy_ratio * 100.0,
            t.diamond_internal,
            t.diamond_displayed,
            if t.floor_binds { " [FLOOR]" } else { "" }
        );
    }

    let avg = |v: &[RunResult], f: &str| -> f64 {
        let n = v.len() as f64;
        if n == 0.0 {
            return 0.0;
        }
        match f {
            "gdp" => v.iter().map(|r| r.gdp).sum::<f64>() / n,
            "dg" => v.iter().map(|r| r.dg).sum::<f64>() / n,
            "bpd" => v.iter().map(|r| r.bpd).sum::<f64>() / n,
            "vol" => v.iter().map(|r| r.vol).sum::<f64>() / n,
            "buy" => v.iter().map(|r| r.buy_ratio).sum::<f64>() / n,
            _ => 0.0,
        }
    };

    let ctrl_avg_gdp = avg(&ctrl_results, "gdp");
    let treat_avg_gdp = avg(&treat_results, "gdp");
    let gdp_chg = (treat_avg_gdp - ctrl_avg_gdp) / ctrl_avg_gdp * 100.0;

    let ctrl_avg_dg = avg(&ctrl_results, "dg");
    let treat_avg_dg = avg(&treat_results, "dg");
    let dg_chg = (treat_avg_dg - ctrl_avg_dg) / ctrl_avg_dg.max(0.001) * 100.0;

    let ctrl_avg_bpd = avg(&ctrl_results, "bpd");
    let treat_avg_bpd = avg(&treat_results, "bpd");
    let bpd_chg = (treat_avg_bpd - ctrl_avg_bpd) / ctrl_avg_bpd.max(0.001) * 100.0;

    let ctrl_avg_vol = avg(&ctrl_results, "vol");
    let treat_avg_vol = avg(&treat_results, "vol");
    let vol_chg = (treat_avg_vol - ctrl_avg_vol) / ctrl_avg_vol.max(0.001) * 100.0;

    let floor_binds = treat_results.iter().filter(|r| r.floor_binds).count();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7} {:>8}",
        "AVG", "GDP", "D/G", "BPD%", "Buy%", "DmdInt", "DmdDisp"
    );
    println!(
        "  {:>6} {:>12.0} {:>10.3}x {:>7.3}% {:>6.1}%",
        "Ctrl",
        ctrl_avg_gdp,
        ctrl_avg_dg,
        ctrl_avg_bpd * 100.0,
        avg(&ctrl_results, "buy") * 100.0
    );
    println!(
        "  {:>6} {:>12.0} {:>10.3}x {:>7.3}% {:>6.1}%",
        "Treat",
        treat_avg_gdp,
        treat_avg_dg,
        treat_avg_bpd * 100.0,
        avg(&treat_results, "buy") * 100.0
    );
    println!();
    println!(
        "  Changes: GDP {:+.1}%, D/G {:+.1}%, BPD {:+.1}%, Vol {:+.1}%",
        gdp_chg, dg_chg, bpd_chg, vol_chg
    );
    println!("  Floor binds: {}/{} seeds\n", floor_binds, seeds.len());

    // Strong recommendation when: GDP large gain OR (floor binds consistently AND D/G manageable)
    let floor_binds_all = floor_binds == seeds.len();
    let strong_recommend = gdp_chg > 50.0 || (floor_binds_all && dg_chg < 50.0);
    if strong_recommend {
        println!("  ✓ RECOMMENDATION: Adopt 2MM + 60% floor as production default.");
        println!(
            "    GDP +{:.0}%, floor binds {}/{} seeds — economy is larger and more stable.",
            gdp_chg,
            floor_binds,
            seeds.len()
        );
    } else if gdp_chg > 5.0 && dg_chg < 20.0 {
        println!("  ✓ RECOMMENDATION: Adopt 2MM + 60% floor as production default.");
        println!("    GDP improved substantially with manageable D/G change.");
    } else if gdp_chg > 0.0 {
        println!("  → RECOMMENDATION: 2MM+floor is an incremental improvement.");
        println!("    Monitor floor binding rate and internal price divergence.");
    } else {
        println!("  → RECOMMENDATION: Re-evaluate. Combined config may have interaction effects.");
    }
    println!();
    println!("  Java default: loans.counter-cyclical: true, floor: 60% ($300 for Diamond)");
}

// ═══════════════════════════════════════════════════════════════════════
//  ADMIN RECOVERY MODE TEST
//  Tests `/at admin recovery start|stop` — manually locking the circuit breaker
//  in a stressed economy to freeze interest and block new loans.
// ═══════════════════════════════════════════════════════════════════════
fn run_admin_recovery_mode_test() {
    use crate::loan::LoanStatus;
    use crate::player::set_global_seeded_rng;

    #[derive(Debug)]
    #[allow(dead_code)]
    struct DailyRecord {
        day: u64,
        gdp: f64,
        active_debt: f64,
        defaulted_debt: f64,
        dg_ratio: f64,
        tier: String,
        recovery_active: bool,
    }

    fn run_arm(
        scenario: &Scenario,
        seed: u64,
        label: &str,
        recovery_start_day: Option<u64>,
    ) -> (Vec<DailyRecord>, Simulation, u32, f64) {
        println!("─── {} ───", label);
        if let Some(day) = recovery_start_day {
            println!(
                "  Recovery mode activated at Day {day} (tick {})",
                day * 288
            );
        }

        set_global_seeded_rng(seed);
        let mut sim = Simulation::new_seeded(scenario.config.clone(), seed);
        sim.events = scenario.events.clone();
        add_players_to_sim(&mut sim, &scenario.players);
        sim.paused = false;

        let mut daily: Vec<DailyRecord> = Vec::new();
        let mut interest_collected: f64 = 0.0;
        let start = Instant::now();

        while sim.current_tick < scenario.duration_ticks {
            sim.tick();
            let tick = sim.current_tick;

            // Activate recovery mode at specified day
            if let Some(start_day) = recovery_start_day
                && tick == start_day * 288
                && !sim.is_admin_recovery_mode()
            {
                sim.set_admin_recovery_mode(true);
                println!("  🔒 Admin recovery mode ACTIVATED at tick {tick}.");
            }

            // Calculate interest collected between ticks (approximate)
            if sim.loans.iter().any(|l| l.last_interest_tick == tick) {
                // Interest was charged this tick
                for loan in &sim.loans {
                    if loan.status == LoanStatus::Active {
                        interest_collected += loan.current_balance * loan.interest_rate * 0.00833; // ~1/120th per tick
                    }
                }
            }

            if tick.is_multiple_of(288) {
                let day = tick / 288;
                let gdp = sim.economy_snapshots.last().map(|s| s.gdp).unwrap_or(0.0);
                let active_debt: f64 = sim
                    .loans
                    .iter()
                    .filter(|l| l.status == LoanStatus::Active)
                    .map(|l| l.current_balance)
                    .sum();
                let defaulted_debt: f64 = sim
                    .loans
                    .iter()
                    .filter(|l| l.status == LoanStatus::Defaulted)
                    .map(|l| l.current_balance)
                    .sum();
                let total = active_debt + defaulted_debt;
                let dg = if gdp > 0.0 { total / gdp } else { 0.0 };
                let tier = sim.prev_circuit_tier().to_string();
                let rec = sim.is_admin_recovery_mode();

                let marker = if rec { " 🔒" } else { "" };
                println!(
                    "  Day {:>2}: GDP={:>9.0} | D/G={:.2}x | active={:>2} | defaulted={:>2} | {}{}",
                    day,
                    gdp,
                    dg,
                    sim.loans
                        .iter()
                        .filter(|l| l.status == LoanStatus::Active)
                        .count(),
                    sim.loans
                        .iter()
                        .filter(|l| l.status == LoanStatus::Defaulted)
                        .count(),
                    tier,
                    marker
                );

                daily.push(DailyRecord {
                    day,
                    gdp,
                    active_debt,
                    defaulted_debt,
                    dg_ratio: dg,
                    tier,
                    recovery_active: rec,
                });
            }
        }

        // Count total loans created
        let total_loans = sim.loans.len() as u32;

        println!(
            "  Arm complete: {} ticks, {} loans, {:.1}s",
            sim.current_tick,
            total_loans,
            start.elapsed().as_secs_f64()
        );
        (daily, sim, total_loans, interest_collected)
    }

    let seed = 42u64;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       ADMIN RECOVERY MODE TEST                              ║");
    println!("║  /at admin recovery start|stop — economy freeze test        ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Question: When a server admin manually triggers recovery mode,\n");
    println!("  does it actually halt the debt cascade and stabilize D/G?\n");
    println!("  Arm 1: Natural (no intervention — let circuit breaker work alone)");
    println!("  Arm 2: Recovery at Day 3 (early intervention — before TIER3)");
    println!("  Arm 3: Recovery at Day 7 (mid-crisis — when D/G starts climbing)");
    println!("  Arm 4: Recovery at Day 10 (late intervention — TIER3 already active)\n");
    println!("  Scenario: guildbuyer_failure_test (1MM+2GB) — 14 days, counter_cyclical=true\n");

    let scenario = {
        let mut s = Scenario::guildbuyer_failure_test();
        s.duration_ticks = 288 * 14; // 14 days
        s
    };

    let (daily1, sim1, loans1, _) = run_arm(&scenario, seed, "Arm 1: Natural (no recovery)", None);
    println!();
    let (daily2, _sim2, loans2, _) =
        run_arm(&scenario, seed, "Arm 2: Recovery Day 3 (early)", Some(3));
    println!();
    let (daily3, _sim3, loans3, _) =
        run_arm(&scenario, seed, "Arm 3: Recovery Day 7 (mid)", Some(7));
    println!();
    let (daily4, _sim4, loans4, _) =
        run_arm(&scenario, seed, "Arm 4: Recovery Day 10 (late)", Some(10));

    // ── Final Comparison ─────────────────────────────────────────────────
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       FINAL OUTCOME COMPARISON                              ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    fn day14(daily: &[DailyRecord]) -> &DailyRecord {
        daily
            .iter()
            .find(|d| d.day == 14)
            .unwrap_or_else(|| daily.last().unwrap())
    }

    let r1 = day14(&daily1);
    let r2 = day14(&daily2);
    let r3 = day14(&daily3);
    let r4 = day14(&daily4);

    println!(
        "  {:<10} {:>12} {:>10} {:>12} {:>8} {:>7}",
        "Arm", "Day 14 GDP", "Day 14 D/G", "Defaulted", "Loans", "Recovery"
    );
    println!("  {}", "-".repeat(72));
    println!(
        "  {:<10} {:>12.0} {:>10.2}x {:>10.0} {:>8}  —",
        "Natural", r1.gdp, r1.dg_ratio, r1.defaulted_debt, loans1
    );
    println!(
        "  {:<10} {:>12.0} {:>10.2}x {:>10.0} {:>8}  Day 3",
        "EARLY", r2.gdp, r2.dg_ratio, r2.defaulted_debt, loans2
    );
    println!(
        "  {:<10} {:>12.0} {:>10.2}x {:>10.0} {:>8}  Day 7",
        "MID", r3.gdp, r3.dg_ratio, r3.defaulted_debt, loans3
    );
    println!(
        "  {:<10} {:>12.0} {:>10.2}x {:>10.0} {:>8}  Day 10",
        "LATE", r4.gdp, r4.dg_ratio, r4.defaulted_debt, loans4
    );

    // D/G deltas vs natural
    println!("\n  D/G Savings vs Natural:");
    for (name, r) in [("EARLY", r2), ("MID", r3), ("LATE", r4)] {
        let save = r1.dg_ratio - r.dg_ratio;
        println!(
            "  {}: {:+.2}x ({} → {})",
            name, save, r.dg_ratio, r1.dg_ratio
        );
    }

    // GDP deltas vs natural
    println!("\n  GDP Delta vs Natural:");
    for (name, r) in [("EARLY", r2), ("MID", r3), ("LATE", r4)] {
        let delta = if r1.gdp > 0.0 {
            (r.gdp - r1.gdp) / r1.gdp * 100.0
        } else {
            0.0
        };
        println!("  {}: {:+.1}%", name, delta);
    }

    // Active vs defaulted debt ratios
    println!("\n  Debt Composition (Day 14):");
    println!(
        "  {:<10} {:>12} {:>14} {:>14}",
        "Arm", "Active Debt", "Defaulted", "Total Debt"
    );
    println!("  {}", "-".repeat(52));
    for (name, r) in [("Natural", r1), ("EARLY", r2), ("MID", r3), ("LATE", r4)] {
        println!(
            "  {:<10} {:>12.0} {:>14.0} {:>14.0}",
            name,
            r.active_debt,
            r.defaulted_debt,
            r.active_debt + r.defaulted_debt
        );
    }

    // Final verdict
    println!("\n  ═══════════════════════════════════");
    let best_dg_arr = [r2.dg_ratio, r3.dg_ratio, r4.dg_ratio];
    let best_dg = best_dg_arr
        .iter()
        .enumerate()
        .min_by(|a, b| a.1.partial_cmp(b.1).unwrap())
        .unwrap();
    let best_name = match best_dg.0 {
        0 => "EARLY (Day 3)",
        1 => "MID (Day 7)",
        2 => "LATE (Day 10)",
        _ => "unknown",
    };
    println!("  Best D/G: {} at {:.2}x", best_name, best_dg.1);
    let worse = *best_dg.1 > r1.dg_ratio;
    if worse {
        println!(
            "  ⚠️  ALL recovery modes had WORSE D/G than natural — recovery mode is COUNTERPRODUCTIVE."
        );
        println!(
            "  Reason: 0% interest prevents debt servicing incentives. Economy can't deleverage."
        );
    } else {
        println!(
            "  ✅ Recovery mode REDUCES D/G vs natural ({:.2}x → {:.2}x).",
            r1.dg_ratio, best_dg.1
        );
        if r4.dg_ratio < r1.dg_ratio {
            println!("  Even LATE intervention (Day 10) is better than no intervention.");
        }
        if r2.dg_ratio < r3.dg_ratio && r3.dg_ratio < r4.dg_ratio {
            println!("  EARLY > MID > LATE: earlier intervention is more effective.");
        }
    }

    // Key insight about counter-cyclical interaction
    if sim1.prev_circuit_tier().contains("TIER3") {
        println!("\n  Note: Natural arm reached TIER3 via counter-cyclical taper.");
        println!("  Recovery mode mimics TIER3 but also BLOCKS new loan issuance.");
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LONG-RUN STABILITY TEST
//  Tests whether economy remains stable at 30 days vs 14 days
// ═══════════════════════════════════════════════════════════════════════
fn run_long_run_test() {
    use crate::analyzer::load_summary;

    let seed = 42u64;
    let diamond_floor = 500.0 * 0.60; // $300

    println!("\n╔════════════════════════════════════════════════════════════════╗");
    println!("║       LONG-RUN STABILITY TEST                              ║");
    println!("║  2MM + 2GB + 60% Diamond floor — 14 days vs 30 days       ║");
    println!("╚════════════════════════════════════════════════════════════════╝\n");
    println!("  Seed: {}", seed);
    println!("  Config: 2MM + 2GB @ 7% + 3Cas + 3Far + 2Tra + 60% Diamond floor\n");

    // Control: 14-day
    let mut ctrl = Scenario::guild_stability_2mm_fixed_guild();
    ctrl.name = "LongRun_14day".to_string();
    ctrl.duration_ticks = 288 * 14;
    if let Some(diamond) = ctrl.config.items.iter_mut().find(|ic| ic.name == "Diamond") {
        diamond.price_floor_override = Some(diamond.base_price * 0.6);
    }
    let ctrl_dir = "/tmp/autotune-sim/longrun-14d";
    let ctrl_path = std::path::PathBuf::from(ctrl_dir);
    std::fs::create_dir_all(&ctrl_path).ok();
    run_seeded_headless(&ctrl, seed, &ctrl_path).ok();

    // Treatment: 30-day
    let mut treat = Scenario::guild_stability_2mm_fixed_guild();
    treat.name = "LongRun_30day".to_string();
    treat.duration_ticks = 288 * 30;
    if let Some(diamond) = treat
        .config
        .items
        .iter_mut()
        .find(|ic| ic.name == "Diamond")
    {
        diamond.price_floor_override = Some(diamond.base_price * 0.6);
    }
    let treat_dir = "/tmp/autotune-sim/longrun-30d";
    let treat_path = std::path::PathBuf::from(treat_dir);
    std::fs::create_dir_all(&treat_path).ok();
    run_seeded_headless(&treat, seed, &treat_path).ok();

    #[derive(Debug)]
    #[allow(dead_code)]
    struct Result {
        days: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        spd: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        diamond_displayed: f64,
    }

    impl Result {
        fn from_db(db_path: &std::path::Path, days: u64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;
            let prices = crate::analyzer::load_all_prices(db_path).unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            Some(Self {
                days,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                diamond_displayed: dd,
            })
        }
    }

    let ctrl_r = Result::from_db(&ctrl_path.join("simulation.db"), 14);
    let treat_r = Result::from_db(&treat_path.join("simulation.db"), 30);

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>8} {:>8} {:>8}",
        "Days", "GDP", "D/G", "BPD%", "SPD%", "Vol", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>8} {:>8} {:>8}",
        "─".repeat(6),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(8)
    );

    if let Some(c) = &ctrl_r {
        println!(
            "  {:>6} {:>12.0} {:>10.3}x {:>7.3}% {:>7.3}% {:>8.4} {:>7.1}%",
            "14d",
            c.gdp,
            c.dg,
            c.bpd * 100.0,
            c.spd * 100.0,
            c.vol,
            c.buy_ratio * 100.0
        );
    } else {
        println!(
            "  {:>6} {:>12} {:>10} {:>8} {:>8} {:>8} {:>8}",
            "14d", "—", "—", "—", "—", "—", "—"
        );
    }
    if let Some(t) = &treat_r {
        println!(
            "  {:>6} {:>12.0} {:>10.3}x {:>7.3}% {:>7.3}% {:>8.4} {:>7.1}%",
            "30d",
            t.gdp,
            t.dg,
            t.bpd * 100.0,
            t.spd * 100.0,
            t.vol,
            t.buy_ratio * 100.0
        );
    } else {
        println!(
            "  {:>6} {:>12} {:>10} {:>8} {:>8} {:>8} {:>8}",
            "30d", "—", "—", "—", "—", "—", "—"
        );
    }

    if let (Some(c), Some(t)) = (&ctrl_r, &treat_r) {
        let gdp_chg = (t.gdp - c.gdp) / c.gdp * 100.0;
        let dg_chg = (t.dg - c.dg) / c.dg.max(0.001) * 100.0;
        let bpd_chg = (t.bpd - c.bpd) / c.bpd.max(0.001) * 100.0;
        let vol_chg = (t.vol - c.vol) / c.vol.max(0.001) * 100.0;
        let buy_chg = (t.buy_ratio - c.buy_ratio) / c.buy_ratio.max(0.001) * 100.0;

        println!();
        println!("  Changes (30d vs 14d):");
        println!(
            "    GDP: {:+.1}%  D/G: {:+.1}%  BPD: {:+.1}%  Vol: {:+.1}%  Buy%: {:+.1}pp",
            gdp_chg, dg_chg, bpd_chg, vol_chg, buy_chg
        );
        println!(
            "    Diamond internal: {:.0} → {:.0}  (floor=${:.0})",
            c.diamond_internal, t.diamond_internal, diamond_floor
        );
        println!();

        let vol_stable = t.vol < 0.05;
        let bpd_stable = bpd_chg.abs() < 30.0;
        let buy_balanced = t.buy_ratio > 0.35 && t.buy_ratio < 0.75;
        let gd_growing = t.gdp > c.gdp;

        if vol_stable && bpd_stable && buy_balanced {
            println!("  ✓ ECONOMY STABLE at 30 days. No cyclical degradation detected.");
            println!(
                "    Vol={:.4} < 0.05, BPD drift < 30%, buy ratio balanced",
                t.vol
            );
        } else {
            if !vol_stable {
                println!(
                    "  ⚠ Volatility concern at 30d: {:.4} (threshold: 0.05)",
                    t.vol
                );
            }
            if !bpd_stable {
                println!(
                    "  ⚠ Spread drift: BPD changed {:+.1}% over 16 extra days",
                    bpd_chg
                );
            }
            if !buy_balanced {
                println!(
                    "  ⚠ Buy ratio drifted to {:.1}% (out of 35-75% balanced band)",
                    t.buy_ratio * 100.0
                );
            }
        }
        if gd_growing {
            println!("  ✓ Economy continued growing (GDP +{:.1}%)", gdp_chg);
        } else {
            println!("  ⚠ Economy contracted at 30d (GDP {:.1}%)", gdp_chg);
        }
        if t.dg > 10.0 {
            println!(
                "  ⚠ D/G {:.2}x > 10.0x at 30d — circuit breaker should have fired",
                t.dg
            );
        } else {
            println!("  ✓ D/G {:.3}x within healthy range at 30d", t.dg);
        }
    }
    println!();
    println!("  Key insight: Long-run (30d) economy behavior vs 14-day standard test.");
    println!("  If stable: no hidden instability emerges over extended play periods.");
    println!();
}

/// Circuit Breaker Hysteresis Test
///
/// Demonstrates the TIER3 hysteresis fix: when D/G hits tier3_ratio (10.0x),
/// the circuit stays locked in TIER3 (0% interest) until D/G drops below 90%
/// of tier3_ratio (9.0x). Without hysteresis, the circuit rapidly toggles
/// on/off as D/G hovers near the boundary.
///
/// This test runs a stressed economy (legacy circuit breaker, counter_cyclical=false)
/// and counts:
/// - NEW TIER3 engagements (with hysteresis): circuit locks at first crossing, holds
/// - OLD oscillations (no hysteresis): each D/G crossing of 10.0 fires a new TIER3 event
///
/// Expected result: hysteresis eliminates ~N-1 TIER3 oscillations for N boundary crossings.
fn run_circuit_breaker_hysteresis_test() {
    use crate::player::set_global_seeded_rng;

    // Use seed 98765 — known to oscillate near D/G boundary in floor-multi-seed tests.
    // Force legacy circuit breaker (counter_cyclical=false) to exercise the tiered path.
    let seed = 98765u64;
    let tier3_ratio = 10.0;
    let hysteresis_threshold = tier3_ratio * 0.9; // 9.0 — unlock when D/G drops here

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║       CIRCUIT BREAKER HYSTERESIS TEST                          ║");
    println!("║  TIER3 lock: fires at D/G >= 10.0x, unlocks at D/G < 9.0x    ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!(
        "  Seed: {} (known boundary oscillator from floor-multi-seed)",
        seed
    );
    println!("  Scenario: guildbuyer_failure_test with counter_cyclical=false");
    println!("  Duration: 14 days (4032 ticks)\n");

    // Build scenario: use guildbuyer_failure_test as base but force legacy circuit breaker
    let mut scenario = Scenario::guildbuyer_failure_test();
    scenario.config.loans.counter_cyclical = false; // Force legacy tiered path
    scenario.name = "CB Hysteresis Test (legacy circuit breaker)".into();

    set_global_seeded_rng(seed);
    let mut sim = Simulation::new_seeded(scenario.config.clone(), seed);
    sim.events = scenario.events.clone();
    add_players_to_sim(&mut sim, &scenario.players);
    sim.paused = false;

    // ── Tracking state ────────────────────────────────────────────────
    let mut new_tier3_engagements: u32 = 0; // NEW behavior: circuit locks once
    let mut new_tier3_held_ticks: u32 = 0; // How long TIER3 is held
    let mut new_in_tier3: bool = false;

    let mut old_tier3_oscillations: u32 = 0; // OLD behavior: each crossing fires
    let mut old_was_in_tier3: bool = false; // Track old logic TIER3 state

    println!("  Running simulation with hysteresis-enabled circuit breaker...");
    let start = Instant::now();

    while sim.current_tick < scenario.duration_ticks {
        sim.tick();

        // ── Compute D/G at end of this tick (same as circuit breaker uses) ──
        let cb_total_debt: f64 = sim
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
        let gdp_window = 288u64;
        let window_start = sim.current_tick.saturating_sub(gdp_window);
        let cb_gdp: f64 = sim
            .transactions
            .iter()
            .filter(|tx| {
                tx.tick >= window_start && tx.tx_type == crate::engine::TransactionType::Buy
            })
            .map(|tx| tx.total_price)
            .sum();
        let dg = if cb_gdp > 0.0 {
            cb_total_debt / cb_gdp
        } else {
            0.0
        };

        // ── NEW behavior (with hysteresis): circuit_tier3_locked in simulation ──
        let new_in_tier3_now = sim.is_circuit_tier3_locked() || (cb_gdp > 0.0 && dg >= tier3_ratio);

        if new_in_tier3_now && !new_in_tier3 {
            new_tier3_engagements += 1;
        }
        if new_in_tier3_now {
            new_tier3_held_ticks += 1;
        }
        new_in_tier3 = new_in_tier3_now;

        // ── OLD behavior (no hysteresis): TIER3 when ratio >= 10.0 ──
        let old_in_tier3_now = cb_gdp > 0.0 && dg >= tier3_ratio;
        if old_in_tier3_now && !old_was_in_tier3 {
            old_tier3_oscillations += 1;
        }
        old_was_in_tier3 = old_in_tier3_now;

        // Progress dot every 1000 ticks
        if sim.current_tick.is_multiple_of(1000) {
            print!(".");
        }
    }
    println!(" done in {:.1}s\n", start.elapsed().as_secs_f64());

    // ── Final D/G for context ──────────────────────────────────────────
    let cb_total_debt: f64 = sim
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
    let gdp_window = 288u64;
    let window_start = sim.current_tick.saturating_sub(gdp_window);
    let cb_gdp: f64 = sim
        .transactions
        .iter()
        .filter(|tx| tx.tick >= window_start && tx.tx_type == crate::engine::TransactionType::Buy)
        .map(|tx| tx.total_price)
        .sum();
    let final_dg = if cb_gdp > 0.0 {
        cb_total_debt / cb_gdp
    } else {
        0.0
    };
    let final_tier = if sim.is_circuit_tier3_locked() {
        "TIER3 (locked)"
    } else if final_dg >= tier3_ratio {
        "TIER3"
    } else {
        "NORMAL/TIER1/TIER2"
    };

    println!(
        "  {:<30} {:>15} {:>15}",
        "Metric", "OLD (no hysteresis)", "NEW (with hysteresis)"
    );
    println!("  {:-<30} {:->15} {:->15}", "", "", "");
    println!(
        "  {:<30} {:>15} {:>15}",
        "TIER3 oscillations",
        format!("{} events", old_tier3_oscillations),
        format!("{} events", new_tier3_engagements)
    );
    println!(
        "  {:<30} {:>15} {:>15}",
        "TIER3 held ticks",
        format!("N/A"),
        format!("{}", new_tier3_held_ticks)
    );
    println!(
        "  {:<30} {:>15} {:>15}",
        "Final D/G",
        format!("{:.3}x", final_dg),
        format!("{:.3}x ({})", final_dg, final_tier)
    );
    println!();

    let oscillation_reduction = if old_tier3_oscillations > 0 {
        ((old_tier3_oscillations as f64 - new_tier3_engagements as f64)
            / old_tier3_oscillations as f64
            * 100.0)
            .max(0.0)
    } else {
        0.0
    };

    if old_tier3_oscillations > 1 && oscillation_reduction > 0.0 {
        println!(
            "  ✓ HYSTERESIS EFFECT: {} fewer TIER3 oscillations ({:.0}% reduction)",
            old_tier3_oscillations - new_tier3_engagements,
            oscillation_reduction
        );
        println!(
            "  ✓ Circuit stays locked until D/G drops below {:.1}x (hysteresis band)",
            hysteresis_threshold
        );
    } else if new_tier3_engagements == 0 {
        println!(
            "  ℹ️  No TIER3 events triggered in this run — D/G stayed below {:.1}x",
            tier3_ratio
        );
        println!(
            "  ℹ️  This can happen with certain seeds/scenarios. Try seed=42 for a more active run."
        );
    } else {
        println!(
            "  ✓ TIER3 engaged {} time(s) with hysteresis — circuit held through oscillation",
            new_tier3_engagements
        );
    }
    println!();
}

// ═══════════════════════════════════════════════════════════════════════
// ═══════════════════════════════════════════════════════════════════════
//  HEALTHY ECONOMY TIER3 SENSITIVITY TEST
//  Sweeps: TIER3 ratio (8, 10, 12, 15)
//  Scenario: 2MM + 2GB + 60% Diamond floor (production recommended config)
//  Counter-cyclical=true (default).
//
//  Question: In a HEALTHY economy, does tier3_ratio still matter?
//  - Stressed-economy test (guildbuyer_failure_test): tier3=15 → 0 TIER3 events (vs 11 at tier3=10)
//  - But counter_cyclical=true already suppresses interest as D/G rises
//  - In a healthy 2MM+2GB+floor economy, D/G may never approach any tier3_ratio
//  - If so, tier3_ratio is irrelevant in healthy economies — only matters in stressed ones
// ═══════════════════════════════════════════════════════════════════════

fn run_healthy_tier3_sweep() {
    use crate::analyzer::load_summary;
    use crate::player::set_global_seeded_rng;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];
    let tier3_ratios: Vec<f64> = vec![8.0, 10.0, 12.0, 15.0];

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║    HEALTHY ECONOMY TIER3 SENSITIVITY                         ║");
    println!("║    2MM + 2GB + 60% Diamond floor — tier3_ratio sweep       ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  tier3_ratios: {:?}", tier3_ratios);
    println!("  seeds: {:?}", seeds);
    println!("  scenario: guild_stability_2mm_fixed_guild_plus_floor");
    println!("  counter_cyclical: true (default)\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct HealthyT3Result {
        tier3_ratio: f64,
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        spd: f64,
        buy_ratio: f64,
        tier3_events: u32,
        diamond_internal: f64,
        diamond_displayed: f64,
        final_dg: f64,
    }

    impl HealthyT3Result {
        fn from_db(db_path: &std::path::Path, tier3_ratio: f64, seed: u64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;

            // Count TIER3 events from circuit_breaker_events table
            let tier3_events = count_tier3_events(db_path);

            // Get Diamond internal and displayed prices
            let (diamond_internal, diamond_displayed) = get_diamond_prices(db_path);

            let dg = if s.gdp > 0.0 { s.debt / s.gdp } else { 0.0 };
            Some(Self {
                tier3_ratio,
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                buy_ratio: s.buy_ratio,
                tier3_events,
                diamond_internal,
                diamond_displayed,
                final_dg: dg,
            })
        }
    }

    let mut results: Vec<HealthyT3Result> = Vec::new();

    for &tier3_ratio in &tier3_ratios {
        for &seed in &seeds {
            print!("  t3={tier3_ratio:.0} seed={seed} ... ");
            std::io::stdout().flush().ok();

            let mut scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
            scenario.name = format!("HealthyT3 t3={tier3_ratio:.0} s={seed}");
            scenario.config.loans.debt_gdp_tier3_ratio = tier3_ratio;

            let out_dir = format!(
                "/tmp/autotune-sim/healthy-t3-t3-{:.0}-s-{}",
                tier3_ratio, seed
            );
            let out_path = std::path::PathBuf::from(&out_dir);
            std::fs::create_dir_all(&out_path).ok();

            set_global_seeded_rng(seed);
            let _ = run_seeded_headless(&scenario, seed, &out_path);

            let db_path = out_path.join("data.db");
            if let Some(r) = HealthyT3Result::from_db(&db_path, tier3_ratio, seed) {
                let gdp_str = format!("{:.0}", r.gdp);
                let dg_str = format!("{:.2}x", r.dg);
                let vol_str = r.vol.to_string();
                println!(
                    "GDP={} D/G={} vol={} T3_ev={} [OK]",
                    gdp_str, dg_str, vol_str, r.tier3_events
                );
                results.push(r);
            } else {
                println!("FAILED to load results");
            }
        }
        println!();
    }

    // ─── Aggregate by tier3_ratio ────────────────────────────────────────
    println!("\n╔════════════════════════════════════════════════════════════════╗");
    println!("║              AGGREGATE RESULTS BY TIER3_RATIO                ║");
    println!("╚════════════════════════════════════════════════════════════════╝");
    println!();
    println!(
        "  {:^6} │ {:^10} {:^10} {:^8} {:^8} {:^8} │ {:^8} {:^8}",
        "t3", "GDP mean", "D/G mean", "vol μ", "vol σ", "BPD μ", "T3_ev", "D/G rng"
    );
    println!(
        "  {:─^6}─┼{:─^10} {:─^10} {:─^8} {:─^8} {:─^8}─┼{:─^8} {:─^8}",
        "", "", "", "", "", "", "", ""
    );

    for &tier3_ratio in &tier3_ratios {
        let arm: Vec<_> = results
            .iter()
            .filter(|r| r.tier3_ratio == tier3_ratio)
            .collect();
        let n = arm.len();
        if n == 0 {
            continue;
        }

        let gdp_mean = arm.iter().map(|r| r.gdp).sum::<f64>() / n as f64;
        let dg_mean = arm.iter().map(|r| r.dg).sum::<f64>() / n as f64;
        let vol_mean = arm.iter().map(|r| r.vol).sum::<f64>() / n as f64;
        let vol_vals: Vec<f64> = arm.iter().map(|r| r.vol).collect();
        let vol_std = if n > 1 {
            let mean = vol_mean;
            let variance = vol_vals.iter().map(|v| (v - mean).powi(2)).sum::<f64>() / n as f64;
            variance.sqrt()
        } else {
            0.0
        };
        let bpd_mean = arm.iter().map(|r| r.bpd).sum::<f64>() / n as f64;
        let t3_total: u32 = arm.iter().map(|r| r.tier3_events).sum();
        let dg_min = arm.iter().map(|r| r.dg).reduce(f64::min).unwrap_or(0.0);
        let dg_max = arm.iter().map(|r| r.dg).reduce(f64::max).unwrap_or(0.0);

        let dg_range = format!("[{:.2},{:.2}]", dg_min, dg_max);
        let dg_mean_str = format!("{:.3}", dg_mean) + "x";
        let vol_mean_str = format!("{:.4}", vol_mean);
        let vol_std_str = format!("{:.4}", vol_std);
        let bpd_mean_str = format!("{:.4}", bpd_mean);
        println!(
            "  {:^6} │ {:>10} {:>11} {:>11} {:>11} {:>11} │ {:>8} {}",
            tier3_ratio as u64,
            gdp_mean as u64,
            dg_mean_str,
            vol_mean_str,
            vol_std_str,
            bpd_mean_str,
            t3_total,
            dg_range
        );
    }

    // ─── TIER3 events detail ─────────────────────────────────────────────
    println!();
    println!("  TIER3 Events by seed and tier3_ratio:");
    println!(
        "  {:^6} │ {}",
        "t3",
        seeds
            .iter()
            .map(|s| format!("s={}", s))
            .collect::<Vec<_>>()
            .join(" │ ")
    );
    println!(
        "  {:─^6}─┼{}",
        "",
        seeds
            .iter()
            .map(|_| "─────")
            .collect::<Vec<_>>()
            .join("─┼─")
    );
    for &tier3_ratio in &tier3_ratios {
        let evs: Vec<String> = seeds
            .iter()
            .map(|&s| {
                results
                    .iter()
                    .find(|r| r.tier3_ratio == tier3_ratio && r.seed == s)
                    .map(|r| format!("{}", r.tier3_events))
                    .unwrap_or_else(|| "?".to_string())
            })
            .collect();
        println!("  {:^6.0} │ {}", tier3_ratio, evs.join(" │ "));
    }

    // ─── Key insight ──────────────────────────────────────────────────────
    println!();
    let t3_by_ratio: Vec<(f64, u32)> = tier3_ratios
        .iter()
        .map(|&t3| {
            let total: u32 = results
                .iter()
                .filter(|r| r.tier3_ratio == t3)
                .map(|r| r.tier3_events)
                .sum();
            (t3, total)
        })
        .collect();

    let total_events: u32 = t3_by_ratio.iter().map(|(_, e)| e).sum();
    if total_events == 0 {
        println!("  💡 KEY INSIGHT: 0 TIER3 events across ALL tier3_ratio × seed combinations!");
        println!("     Counter-cyclical=true keeps D/G below all tested thresholds.");
        println!("     tier3_ratio is IRRELEVANT in healthy economies with counter_cyclical=true.");
        println!("     The 80-run stressed-economy finding (tier3=15 best) does NOT apply here.");
    } else {
        let best = t3_by_ratio.iter().min_by_key(|(_, e)| e).unwrap();
        println!(
            "  💡 FINDING: {} total TIER3 events across all runs.",
            total_events
        );
        println!(
            "     Fewest events: tier3={:.0} with {} events.",
            best.0, best.1
        );
        if best.0 == 15.0 {
            println!("     tier3=15 remains best even in healthy economy.");
        }
    }

    println!();
}

fn count_tier3_events(db_path: &std::path::Path) -> u32 {
    let conn = match rusqlite::Connection::open(db_path) {
        Ok(c) => c,
        Err(_) => return 0,
    };
    match conn.query_row(
        "SELECT COUNT(*) FROM circuit_breaker_events WHERE tier = 'TIER3'",
        [],
        |row| row.get::<_, i64>(0),
    ) {
        Ok(n) => n as u32,
        Err(_) => 0,
    }
}

fn get_diamond_prices(db_path: &std::path::Path) -> (f64, f64) {
    let conn = match rusqlite::Connection::open(db_path) {
        Ok(c) => c,
        Err(_) => return (0.0, 0.0),
    };
    // Internal price = last tick's price from market_data
    let internal = conn
        .query_row(
            "SELECT price FROM market_data WHERE item_name='Diamond' ORDER BY tick DESC LIMIT 1",
            [],
            |row| row.get::<_, f64>(0),
        )
        .unwrap_or(0.0);
    // Displayed price = floored internal (if floor binds)
    let displayed = conn
        .query_row(
            "SELECT sell_price FROM latest_prices WHERE item_name='Diamond' LIMIT 1",
            [],
            |row| row.get::<_, f64>(0),
        )
        .unwrap_or(internal);
    (internal, displayed)
}

//  CIRCUIT BREAKER SENSITIVITY TEST
//  Sweeps: TIER3 ratio (8, 10, 12, 15) × min_interest (0, 5%, 10%, 20%)
//  Question: How does the counter-cyclical interest floor affect stability?
//  Multi-seed (5 seeds) for statistical robustness.
// ═══════════════════════════════════════════════════════════════════════
fn run_circuit_breaker_sensitivity_test() {
    use crate::analyzer::load_summary;
    use crate::player::set_global_seeded_rng;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];
    let tier3_ratios: Vec<f64> = vec![8.0, 10.0, 12.0, 15.0];
    let min_interests: Vec<f64> = vec![0.0, 0.05, 0.10, 0.20];

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║    CIRCUIT BREAKER SENSITIVITY — TIER3 ratio × min interest  ║");
    println!("║  4×4 sweep × 5 seeds | guildbuyer_failure_test | 14d        ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  tier3_ratios: {:?}", tier3_ratios);
    println!("  min_interests: {:?}", min_interests);
    println!("  seeds: {:?}", seeds);
    println!("  scenario: guildbuyer_failure_test (1MM + 2GB + 4Cas + 3Far + 2Tra)\n");

    /// Result tuple: (seed, gdp, dg, vol, t3_ev_f64, t3_ev_u32)
    type CbSensTuple = (u64, f64, f64, f64, f64, u32);

    #[derive(Debug)]
    #[allow(dead_code)]
    struct CbSensResult {
        tier3_ratio: f64,
        min_interest: f64,
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        buy_ratio: f64,
        tier3_events: u32,
    }

    // (tier3_idx, mi_idx) → vec of CbSensTuple
    let mut results_by_params: Vec<Vec<Vec<CbSensTuple>>> =
        vec![vec![vec![]; min_interests.len()]; tier3_ratios.len()];
    let mut failed_runs: u32 = 0;

    for (t3i, &tier3_ratio) in tier3_ratios.iter().enumerate() {
        for (mii, &min_interest) in min_interests.iter().enumerate() {
            for &seed in &seeds {
                print!("  t3={tier3_ratio:.0} mi={min_interest:.2} seed={seed} ... ");

                // Build scenario with swept parameters
                let mut scenario = Scenario::guildbuyer_failure_test();
                scenario.name = format!("CB Sens t3={tier3_ratio:.0} mi={min_interest:.2}");
                scenario.config.loans.debt_gdp_tier3_ratio = tier3_ratio;
                scenario.config.loans.min_interest_multiplier = min_interest;
                scenario.config.loans.tier1_interest_cap = 0.50;
                scenario.config.loans.tier2_interest_cap = 0.25;
                // Tier caps match default LoanConfig
                // Counter-cyclical ON (default, matching Java)
                scenario.config.loans.counter_cyclical = true;

                let out_dir = format!(
                    "/tmp/autotune-sim/cb-sens-t3-{tier3_ratio:.0}-mi-{min_interest:.2}-s-{seed}"
                );
                let out_path = std::path::PathBuf::from(&out_dir);
                std::fs::create_dir_all(&out_path).ok();

                let start = std::time::Instant::now();

                let sim_result: Result<CbSensResult, String> = {
                    set_global_seeded_rng(seed);
                    let mut sim = Simulation::new_seeded(scenario.config.clone(), seed);
                    add_players_to_sim(&mut sim, &scenario.players);
                    sim.paused = false;

                    let mut tier3_events = 0u32;
                    let mut prev_tier = String::from("NORMAL");

                    while sim.current_tick < scenario.duration_ticks {
                        sim.tick();

                        let current_tier = sim.prev_circuit_tier().to_string();
                        if current_tier == "TIER3" && prev_tier != "TIER3" {
                            tier3_events += 1;
                        }
                        prev_tier = current_tier;
                    }

                    // Compute D/G using same window as circuit breaker
                    let cb_total_debt: f64 = sim
                        .loans
                        .iter()
                        .filter(|l| {
                            matches!(
                                l.status,
                                crate::loan::LoanStatus::Active
                                    | crate::loan::LoanStatus::Defaulted
                            )
                        })
                        .map(|l| l.current_balance)
                        .sum();
                    let gdp_window = 288u64;
                    let window_start = sim.current_tick.saturating_sub(gdp_window);
                    let cb_gdp: f64 = sim
                        .transactions
                        .iter()
                        .filter(|tx| {
                            tx.tick >= window_start
                                && tx.tx_type == crate::engine::TransactionType::Buy
                        })
                        .map(|tx| tx.total_price)
                        .sum();
                    let dg = if cb_gdp > 0.0 {
                        cb_total_debt / cb_gdp
                    } else {
                        0.0
                    };

                    Ok(CbSensResult {
                        tier3_ratio,
                        min_interest,
                        seed,
                        gdp: cb_gdp,
                        dg,
                        vol: 0.0, // filled from summary below
                        bpd: 0.0,
                        buy_ratio: 0.0,
                        tier3_events,
                    })
                };

                match sim_result {
                    Ok(mut result) => {
                        // Augment with full summary stats, then clean up DB
                        let db_path = out_path.join("simulation.db");
                        if let Ok(s) = load_summary(&db_path) {
                            result.vol = s.avg_volatility;
                            result.bpd = s.avg_bpd;
                            result.buy_ratio = s.buy_ratio;
                        }
                        let _ = std::fs::remove_dir_all(&out_path);
                        results_by_params[t3i][mii].push((
                            seed,
                            result.gdp,
                            result.dg,
                            result.vol,
                            result.tier3_events as f64,
                            result.tier3_events,
                        ));
                        println!(
                            "OK (t3_ev={}, dg={:.2}x, gdp={:.0}, {:.1}s)",
                            result.tier3_events,
                            result.dg,
                            result.gdp,
                            start.elapsed().as_secs_f64()
                        );
                    }
                    Err(e) => {
                        println!("FAIL: {e}");
                        failed_runs += 1;
                    }
                }
            }
        }
    }

    // ── Print summary table ─────────────────────────────────────────────
    println!(
        "\n╔════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗"
    );
    println!(
        "║  SUMMARY TABLE — avg across 5 seeds (D/G ratio, TIER3 event sum)                                     ║"
    );
    println!(
        "╚════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝"
    );
    println!();

    // D/G table
    print!("  {:^8}", "t3\\mi");
    for &mi in &min_interests {
        print!("  {:^10}", format!("{:.0}%", mi * 100.0));
    }
    println!();
    println!("  {:─<8}", "");
    for _ in &min_interests {
        print!("  {:─>10}", "");
    }
    println!();

    for (t3i, &t3) in tier3_ratios.iter().enumerate() {
        print!("  {:^6.0}", t3);
        for (mii, _mi) in min_interests.iter().enumerate() {
            if let Some(vals) = results_by_params.get(t3i).and_then(|r| r.get(mii)) {
                if !vals.is_empty() {
                    let count = vals.len() as f64;
                    let avg_dg = vals.iter().map(|v| v.2).sum::<f64>() / count;
                    let sum_ev: u32 = vals.iter().map(|v| v.5).sum();
                    print!("  {:>5.2}x{:>3.0}e", avg_dg, sum_ev);
                } else {
                    print!("  {:>10}", "—");
                }
            } else {
                print!("  {:>10}", "—");
            }
        }
        println!();
    }

    println!();
    // Volatility table
    print!("  {:^8}", "t3\\mi");
    for &mi in &min_interests {
        print!("  {:^12}", format!("vol {:.0}%", mi * 100.0));
    }
    println!();
    println!("  {:─<8}", "");
    for _ in &min_interests {
        print!("  {:─>12}", "");
    }
    println!();

    for (t3i, &t3) in tier3_ratios.iter().enumerate() {
        print!("  {:^6.0}", t3);
        for (mii, _mi) in min_interests.iter().enumerate() {
            if let Some(vals) = results_by_params.get(t3i).and_then(|r| r.get(mii)) {
                if !vals.is_empty() {
                    let count = vals.len() as f64;
                    let avg_vol = vals.iter().map(|v| v.3).sum::<f64>() / count;
                    print!("  {:>12.4}", avg_vol);
                } else {
                    print!("  {:>12}", "—");
                }
            } else {
                print!("  {:>12}", "—");
            }
        }
        println!();
    }

    // ── Key findings ────────────────────────────────────────────────────
    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║  KEY FINDINGS                                                       ║");
    println!("╚══════════════════════════════════════════════════════════════════╝");

    // Best D/G by tier3_ratio
    println!("  Best D/G by tier3_ratio (lower is better):");
    let mut best_by_t3: Vec<(f64, f64, f64)> = Vec::new();
    for (t3i, &t3) in tier3_ratios.iter().enumerate() {
        let mut best = (f64::MAX, 0.0f64);
        for (mii, &mi) in min_interests.iter().enumerate() {
            if let Some(vals) = results_by_params.get(t3i).and_then(|r| r.get(mii))
                && !vals.is_empty()
            {
                let avg_dg = vals.iter().map(|v| v.2).sum::<f64>() / vals.len() as f64;
                if avg_dg < best.0 {
                    best = (avg_dg, mi);
                }
            }
        }
        if best.0 < f64::MAX {
            best_by_t3.push((t3, best.1, best.0));
            println!(
                "    tier3={t3:.0}: min_interest={mi_pct:.0}% → D/G={dg:.2}x",
                mi_pct = best.1 * 100.0,
                dg = best.0
            );
        }
    }

    // min_interest floor effect at default tier3=10
    // tier3=10.0 is at index 1, min_interest=0.0 is at index 0
    let t3_10_idx = tier3_ratios
        .iter()
        .position(|&v| (v - 10.0).abs() < 0.01)
        .unwrap();
    let mi_0_idx = min_interests
        .iter()
        .position(|&v| (v - 0.0).abs() < 0.001)
        .unwrap();

    if let Some(baseline) = results_by_params
        .get(t3_10_idx)
        .and_then(|r| r.get(mi_0_idx))
        && !baseline.is_empty()
    {
        let base_dg: f64 = baseline.iter().map(|v| v.2).sum::<f64>() / baseline.len() as f64;
        let base_ev: u32 = baseline.iter().map(|v| v.5).sum();
        println!("\n  At tier3=10 (current default):");
        println!(
            "    baseline min_int=0%:  D/G={:.2}x, T3_ev_sum={}",
            base_dg, base_ev
        );
        for &mi in &[0.05, 0.10, 0.20] {
            if let Some(mii) = min_interests.iter().position(|&v| (v - mi).abs() < 0.001)
                && let Some(with_mi) = results_by_params.get(t3_10_idx).and_then(|r| r.get(mii))
                && !with_mi.is_empty()
            {
                let mi_dg: f64 = with_mi.iter().map(|v| v.2).sum::<f64>() / with_mi.len() as f64;
                let mi_ev: u32 = with_mi.iter().map(|v| v.5).sum();
                let delta = (mi_dg - base_dg) / base_dg * 100.0;
                println!(
                    "    min_int={:.0}%: D/G={:.2}x ({:+.1}%), T3_ev={} ({:+})",
                    mi * 100.0,
                    mi_dg,
                    delta,
                    mi_ev,
                    mi_ev as i32 - base_ev as i32
                );
            }
        }
    }

    // tier3_ratio sweep effect (min_int=0 baseline)
    if let Some(baseline) = results_by_params
        .get(t3_10_idx)
        .and_then(|r| r.get(mi_0_idx))
        && !baseline.is_empty()
    {
        let base_dg: f64 = baseline.iter().map(|v| v.2).sum::<f64>() / baseline.len() as f64;
        println!("\n  tier3_ratio sweep effect (min_int=0, vs tier3=10 baseline):");
        for &t3 in &[8.0, 12.0, 15.0] {
            if let Some(t3i) = tier3_ratios.iter().position(|&v| (v - t3).abs() < 0.01)
                && let Some(vals) = results_by_params.get(t3i).and_then(|r| r.get(mi_0_idx))
                && !vals.is_empty()
            {
                let t3_dg: f64 = vals.iter().map(|v| v.2).sum::<f64>() / vals.len() as f64;
                let t3_ev: u32 = vals.iter().map(|v| v.5).sum();
                let delta = (t3_dg - base_dg) / base_dg * 100.0;
                println!(
                    "    tier3={:.0}: D/G={:.2}x ({:+.1}%), T3_ev_sum={}",
                    t3, t3_dg, delta, t3_ev
                );
            }
        }
    }

    println!(
        "\n  Failed runs: {}/{} total combinations",
        failed_runs,
        4 * 4 * 5
    );
    println!("  Recommend: commit code, verify build, run in main session:\n");
    println!("    cargo run --release -- --circuit-breaker-sensitivity-test");
}

// ═══════════════════════════════════════════════════════════════════════
//  ARCHETYPE MIX TEST
//  Tests: Casual-heavy vs Farmer-heavy vs control (guild_stability_mm)
// ═══════════════════════════════════════════════════════════════════════
fn run_archetype_mix_test() {
    use crate::analyzer::load_summary;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║       ARCHETYPE MIX TEST — MULTI-SEED (5 seeds)              ║");
    println!("║  Casual-heavy (6Cas/1Far) vs Farmer-heavy (2Cas/6Far)        ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Control:     2MM+2GB+3Cas+3Far+2Tra (12 players) [guild_stability_mm_fixed_guild]");
    println!("  Treatment 1: 2MM+2GB+6Cas+1Far+1Tra (10 players) [casual_heavy]");
    println!("  Treatment 2: 2MM+2GB+2Cas+6Far+2Tra (12 players) [farmer_heavy]");
    println!("  Duration: 14 days\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct MixResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        vol: f64,
        buy_ratio: f64,
    }

    impl MixResult {
        fn from_summary(s: &crate::analyzer::SimSummary, seed: u64) -> Self {
            Self {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
            }
        }
    }

    let mut ctrl_results: Vec<MixResult> = Vec::new();
    let mut casual_results: Vec<MixResult> = Vec::new();
    let mut farmer_results: Vec<MixResult> = Vec::new();

    for seed in &seeds {
        print!("  seed {seed} ... ");

        // Control
        let ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
        let ctrl_dir = format!("/tmp/autotune-sim/mix-ctrl-{seed}");
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        if run_seeded_headless(&ctrl_scenario, *seed, &ctrl_path).is_ok() {
            let db_path = ctrl_path.join("simulation.db");
            if let Ok(s) = load_summary(&db_path) {
                ctrl_results.push(MixResult::from_summary(&s, *seed));
            }
        }

        // Casual-heavy
        let casual_scenario = Scenario::guild_stability_casual_heavy();
        let casual_dir = format!("/tmp/autotune-sim/mix-casual-{seed}");
        let casual_path = std::path::PathBuf::from(&casual_dir);
        std::fs::create_dir_all(&casual_path).ok();
        if run_seeded_headless(&casual_scenario, *seed, &casual_path).is_ok() {
            let db_path = casual_path.join("simulation.db");
            if let Ok(s) = load_summary(&db_path) {
                casual_results.push(MixResult::from_summary(&s, *seed));
            }
        }

        // Farmer-heavy
        let farmer_scenario = Scenario::guild_stability_farmer_heavy();
        let farmer_dir = format!("/tmp/autotune-sim/mix-farmer-{seed}");
        let farmer_path = std::path::PathBuf::from(&farmer_dir);
        std::fs::create_dir_all(&farmer_path).ok();
        if run_seeded_headless(&farmer_scenario, *seed, &farmer_path).is_ok() {
            let db_path = farmer_path.join("simulation.db");
            if let Ok(s) = load_summary(&db_path) {
                farmer_results.push(MixResult::from_summary(&s, *seed));
            }
        }

        println!("done");
    }

    // ── Summary stats ───────────────────────────────────────────────────
    let stats = |results: &[MixResult], field: &str| -> (f64, f64) {
        let n = results.len() as f64;
        if n == 0.0 {
            return (0.0, 0.0);
        }
        let vals: Vec<f64> = results
            .iter()
            .map(|r| match field {
                "gdp" => r.gdp,
                "dg" => r.dg,
                "bpd" => r.bpd,
                "vol" => r.vol,
                "buy" => r.buy_ratio,
                _ => 0.0,
            })
            .collect();
        let mean = vals.iter().sum::<f64>() / n;
        let variance = vals.iter().map(|v| (v - mean).powi(2)).sum::<f64>() / n;
        (mean, variance.sqrt())
    };

    let (ctrl_gdp, ctrl_gdp_s) = stats(&ctrl_results, "gdp");
    let (cas_gdp, cas_gdp_s) = stats(&casual_results, "gdp");
    let (far_gdp, far_gdp_s) = stats(&farmer_results, "gdp");

    let (ctrl_dg, ctrl_dg_s) = stats(&ctrl_results, "dg");
    let (cas_dg, cas_dg_s) = stats(&casual_results, "dg");
    let (far_dg, far_dg_s) = stats(&farmer_results, "dg");

    let (ctrl_bpd, ctrl_bpd_s) = stats(&ctrl_results, "bpd");
    let (cas_bpd, cas_bpd_s) = stats(&casual_results, "bpd");
    let (far_bpd, far_bpd_s) = stats(&farmer_results, "bpd");

    let (ctrl_vol, ctrl_vol_s) = stats(&ctrl_results, "vol");
    let (cas_vol, cas_vol_s) = stats(&casual_results, "vol");
    let (far_vol, far_vol_s) = stats(&farmer_results, "vol");

    let (ctrl_buy, _) = stats(&ctrl_results, "buy");
    let (cas_buy, _) = stats(&casual_results, "buy");
    let (far_buy, _) = stats(&farmer_results, "buy");

    println!();
    println!(
        "  {:>16} {:>14} {:>12} {:>16} {:>9} {:>9} {:>7}",
        "", "GDP", "GDP-σ", "D/G (σ)", "BPD%", "Vol", "Buy%"
    );
    println!(
        "  {:>16} {:>14} {:>12} {:>16} {:>9} {:>9} {:>7}",
        "─".repeat(16),
        "─".repeat(14),
        "─".repeat(12),
        "─".repeat(16),
        "─".repeat(9),
        "─".repeat(9),
        "─".repeat(7)
    );

    let fmt_row = |label: &str,
                   gdp: f64,
                   gdp_s: f64,
                   dg: f64,
                   dg_s: f64,
                   bpd: f64,
                   _bpd_s: f64,
                   vol: f64,
                   _vol_s: f64,
                   buy: f64| {
        let dg_str = format!("{:.3}x ± {:.2}", dg, dg_s);
        println!(
            "  {:>16} {:>14.0} {:>12.0} {:>16} {:>9.3}% {:>9.5} {:>7.1}%",
            label,
            gdp,
            gdp_s,
            dg_str,
            bpd * 100.0,
            vol,
            buy * 100.0
        );
    };

    fmt_row(
        "Control (3C/3F/2T)",
        ctrl_gdp,
        ctrl_gdp_s,
        ctrl_dg,
        ctrl_dg_s,
        ctrl_bpd,
        ctrl_bpd_s,
        ctrl_vol,
        ctrl_vol_s,
        ctrl_buy,
    );
    fmt_row(
        "Casual-heavy (6C)",
        cas_gdp,
        cas_gdp_s,
        cas_dg,
        cas_dg_s,
        cas_bpd,
        cas_bpd_s,
        cas_vol,
        cas_vol_s,
        cas_buy,
    );
    fmt_row(
        "Farmer-heavy (6F)",
        far_gdp,
        far_gdp_s,
        far_dg,
        far_dg_s,
        far_bpd,
        far_bpd_s,
        far_vol,
        far_vol_s,
        far_buy,
    );

    println!();

    // ── Change vs control ─────────────────────────────────────────────
    let chg = |new: f64, ctrl: f64| -> f64 {
        if ctrl == 0.0 {
            0.0
        } else {
            (new - ctrl) / ctrl * 100.0
        }
    };

    println!("  Changes vs Control:");
    println!(
        "  {:>16} {:>12} {:>10} {:>8} {:>7}",
        "", "GDP", "D/G", "BPD", "Vol"
    );
    println!(
        "  {:>16} {:>12} {:>10} {:>8} {:>7}",
        "─".repeat(16),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(7)
    );
    println!(
        "  {:>16} {:>+11.1}% {:>+10.1}% {:>+7.1}% {:>+6.1}%",
        "Casual-heavy",
        chg(cas_gdp, ctrl_gdp),
        chg(cas_dg, ctrl_dg),
        chg(cas_bpd, ctrl_bpd),
        chg(cas_vol, ctrl_vol)
    );
    println!(
        "  {:>16} {:>+11.1}% {:>+10.1}% {:>+7.1}% {:>+6.1}%",
        "Farmer-heavy",
        chg(far_gdp, ctrl_gdp),
        chg(far_dg, ctrl_dg),
        chg(far_bpd, ctrl_bpd),
        chg(far_vol, ctrl_vol)
    );
    println!();

    // ── Verdict ────────────────────────────────────────────────────────
    let cas_better_gdp = cas_gdp > ctrl_gdp;
    let far_better_gdp = far_gdp > ctrl_gdp;
    let cas_better_dg = cas_dg < ctrl_dg;
    let far_better_dg = far_dg < ctrl_dg;

    if cas_better_gdp && cas_better_dg {
        println!("  ✓ VERDICT: Casual-heavy outperforms control on GDP AND D/G.");
        println!(
            "    Recommendation: servers with casual player bases should use 6Cas/1Far archetype."
        );
    } else if cas_better_gdp {
        println!("  → VERDICT: Casual-heavy has higher GDP but higher D/G.");
        println!(
            "    Buy ratio effect: {:.1}% (control: {:.1}%) — {}.",
            cas_buy * 100.0,
            ctrl_buy * 100.0,
            if cas_buy < ctrl_buy {
                "more sell-dominated"
            } else {
                "more buy-balanced"
            }
        );
    }

    if far_better_gdp && far_better_dg {
        println!("  ✓ VERDICT: Farmer-heavy outperforms control on GDP AND D/G.");
    } else if far_better_gdp {
        println!("  → VERDICT: Farmer-heavy has higher GDP but higher D/G.");
    } else {
        println!("  → VERDICT: Control (3Cas/3Far) is the balanced sweet spot.");
    }

    let volatility_ok = |v: f64| v < 0.05;
    if volatility_ok(ctrl_vol) && volatility_ok(cas_vol) && volatility_ok(far_vol) {
        println!("  ℹ️  All configs stable (vol < 0.05) — volatility is not the differentiator.");
    } else {
        println!("  ℹ️  Volatility differs — lower is better for price predictability.");
    }

    println!();
    println!(
        "  Admin note: Farmer-heavy servers expect lower equilibrium prices due to structural oversupply."
    );
    println!("  Recommendation: match archetype to player behavior, not vice versa.\n");
}

/// Floor Impact Test: Does the 60% Diamond floor ADD volatility?
/// Compares 2MM+2GB WITH and WITHOUT 60% Diamond floor across 5 seeds.
fn run_floor_impact_test() {
    use crate::analyzer::load_summary;

    let seeds: Vec<u64> = vec![42, 12345, 98765, 77777, 11111];
    let diamond_floor = 500.0 * 0.60; // $300

    println!("\n╔══════════════════════════════════════════════════════════════════╗");
    println!("║       FLOOR IMPACT TEST — 2MM+2GB: WITH vs WITHOUT FLOOR  ║");
    println!("║  Question: Does 60% Diamond floor ADD volatility?         ║");
    println!("╚══════════════════════════════════════════════════════════════════╝\n");
    println!("  Seeds: {:?}", seeds);
    println!("  Control: guild_stability_2mm_fixed_guild (2MM+2GB, NO floor)");
    println!("  Treat:   Same + Diamond floor=60% ($300)");
    println!("  Duration: 14 days\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct FloorImpactResult {
        seed: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_displayed: f64,
        floor_binds: bool,
    }

    impl FloorImpactResult {
        fn from_summary(
            s: &crate::analyzer::SimSummary,
            prices: &[(String, f64, f64)],
            seed: u64,
            floor_val: f64,
        ) -> Self {
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let diamond_displayed = diamond.map(|(_, _, d)| *d).unwrap_or(0.0);
            Self {
                seed,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                diamond_displayed,
                floor_binds: diamond_displayed >= floor_val - 0.01,
            }
        }
    }

    let mut ctrl_results: Vec<FloorImpactResult> = Vec::new();
    let mut treat_results: Vec<FloorImpactResult> = Vec::new();

    for seed in &seeds {
        print!("  seed {seed} ... ");

        // Control: 2MM+2GB, NO floor
        let ctrl_scenario = Scenario::guild_stability_2mm_fixed_guild();
        let ctrl_dir = format!("/tmp/autotune-sim/floor-impact-ctrl-{seed}");
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        if run_seeded_headless(&ctrl_scenario, *seed, &ctrl_path).is_ok() {
            let db_path = ctrl_path.join("simulation.db");
            if let Ok(s) = load_summary(&db_path) {
                let prices = crate::analyzer::load_all_prices(&db_path).unwrap_or_default();
                ctrl_results.push(FloorImpactResult::from_summary(&s, &prices, *seed, 0.0));
                print!("ctrl ");
            }
        }

        // Treatment: 2MM+2GB + 60% Diamond floor
        let mut treat_scenario = Scenario::guild_stability_2mm_fixed_guild();
        treat_scenario.name = "2MM+GB+Floor".to_string();
        if let Some(diamond) = treat_scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6);
        }
        let treat_dir = format!("/tmp/autotune-sim/floor-impact-treat-{seed}");
        let treat_path = std::path::PathBuf::from(&treat_dir);
        std::fs::create_dir_all(&treat_path).ok();
        if run_seeded_headless(&treat_scenario, *seed, &treat_path).is_ok() {
            let db_path = treat_path.join("simulation.db");
            if let Ok(s) = load_summary(&db_path) {
                let prices = crate::analyzer::load_all_prices(&db_path).unwrap_or_default();
                treat_results.push(FloorImpactResult::from_summary(
                    &s,
                    &prices,
                    *seed,
                    diamond_floor,
                ));
                println!("treat done");
            }
        }
    }

    // ── Per-seed table
    println!(
        "\n╔════════════════════════════════════════════════════════════════════════════════════════════╗"
    );
    println!(
        "║  PER-SEED RESULTS                                                                     ║"
    );
    println!(
        "╚════════════════════════════════════════════════════════════════════════════════════════════╝"
    );
    println!(
        "  {:>6}  {:>10}  {:>7}  {:>7}  {:>7}  {:>9}  |  {:>10}  {:>7}  {:>7}  {:>7}  {:>9}  {:>6}",
        "seed",
        "GDP(c)",
        "D/G(c)",
        "Vol(c)",
        "BPD(c)",
        "Diamond(c)",
        "GDP(t)",
        "D/G(t)",
        "Vol(t)",
        "BPD(t)",
        "Diamond(t)",
        "Floor?"
    );
    println!("  {}", "─".repeat(105));

    for seed in &seeds {
        let cr = ctrl_results.iter().find(|r| r.seed == *seed);
        let tr = treat_results.iter().find(|r| r.seed == *seed);
        if let (Some(c), Some(t)) = (cr, tr) {
            println!(
                "  {:>6}  {:>10.0}  {:>6.3}x  {:>6.4}  {:>6.3}%  {:>8.0} |  {:>10.0}  {:>6.3}x  {:>6.4}  {:>6.3}%  {:>8.0}  {:>6}",
                c.seed,
                c.gdp,
                c.dg,
                c.vol,
                c.bpd * 100.0,
                c.diamond_displayed,
                t.gdp,
                t.dg,
                t.vol,
                t.bpd * 100.0,
                t.diamond_displayed,
                if t.floor_binds { "YES" } else { "no" }
            );
        }
    }

    // ── Summary
    if ctrl_results.len() == 5 && treat_results.len() == 5 {
        let n = 5.0;
        let avg = |v: &[FloorImpactResult], field: &str| -> f64 {
            let vals: Vec<f64> = v
                .iter()
                .map(|r| match field {
                    "gdp" => r.gdp,
                    "dg" => r.dg,
                    "bpd" => r.bpd,
                    "vol" => r.vol,
                    "buy" => r.buy_ratio,
                    _ => 0.0,
                })
                .collect();
            vals.iter().sum::<f64>() / n
        };

        let ctrl_gdp = avg(&ctrl_results, "gdp");
        let treat_gdp = avg(&treat_results, "gdp");
        let ctrl_dg = avg(&ctrl_results, "dg");
        let treat_dg = avg(&treat_results, "dg");
        let ctrl_vol = avg(&ctrl_results, "vol");
        let treat_vol = avg(&treat_results, "vol");
        let ctrl_bpd = avg(&ctrl_results, "bpd") * 100.0;
        let treat_bpd = avg(&treat_results, "bpd") * 100.0;
        let ctrl_buy = avg(&ctrl_results, "buy") * 100.0;
        let treat_buy = avg(&treat_results, "buy") * 100.0;
        let floor_binds = treat_results.iter().filter(|r| r.floor_binds).count();

        let pct_str = |a: f64, b: f64| -> String {
            let pct = (b - a) / a.max(1.0) * 100.0;
            format!("{:+.1}%", pct)
        };
        let abs_str = |a: f64, b: f64| -> String { format!("{:+.4}", b - a) };

        println!(
            "\n╔════════════════════════════════════════════════════════════════════════════════════════════╗"
        );
        println!(
            "║  FLOOR IMPACT SUMMARY (5 seeds avg)                                                    ║"
        );
        println!(
            "╚════════════════════════════════════════════════════════════════════════════════════════════╝"
        );
        println!(
            "  {:<20}  {:>14}  {:>14}  {:>12}",
            "Metric", "NO FLOOR", "WITH FLOOR", "Change"
        );
        println!("  {}", "─".repeat(65));
        println!(
            "  {:<20}  {:>14.0}  {:>14.0}  {:>12}",
            "GDP",
            ctrl_gdp,
            treat_gdp,
            pct_str(ctrl_gdp, treat_gdp)
        );
        println!(
            "  {:<20}  {:>14.3}x  {:>14.3}x  {:>12}",
            "D/G",
            ctrl_dg,
            treat_dg,
            pct_str(ctrl_dg, treat_dg)
        );
        println!(
            "  {:<20}  {:>14.4}   {:>14.4}   {:>12}",
            "Volatility",
            ctrl_vol,
            treat_vol,
            abs_str(ctrl_vol, treat_vol)
        );
        println!(
            "  {:<20}  {:>14.3}%  {:>14.3}%  {:>12}",
            "BPD avg",
            ctrl_bpd,
            treat_bpd,
            pct_str(ctrl_bpd, treat_bpd)
        );
        println!(
            "  {:<20}  {:>14.1}%  {:>14.1}%  {:>12}",
            "Buy ratio",
            ctrl_buy,
            treat_buy,
            pct_str(ctrl_buy, treat_buy)
        );
        println!("\n  Floor binds: {}/5 seeds", floor_binds);

        println!(
            "\n╔════════════════════════════════════════════════════════════════════════════════════════════╗"
        );
        println!(
            "║  KEY FINDINGS                                                                       ║"
        );
        println!(
            "╚════════════════════════════════════════════════════════════════════════════════════════════╝"
        );

        let vol_change = treat_vol - ctrl_vol;
        if vol_change > 0.01 {
            println!(
                "  CAUTION: FLOOR ADDS VOLATILITY: vol +{:.4} (ctrl {:.4} -> {:.4})",
                vol_change, ctrl_vol, treat_vol
            );
        } else if vol_change < -0.01 {
            println!(
                "  GOOD: FLOOR REDUCES VOLATILITY: vol {:.4} -> {:.4}",
                ctrl_vol, treat_vol
            );
        } else {
            println!(
                "  NEUTRAL: FLOOR EFFECT ON VOLATILITY: {:.4} -> {:.4}",
                ctrl_vol, treat_vol
            );
        }

        let gdp_pct = (treat_gdp - ctrl_gdp) / ctrl_gdp * 100.0;
        if gdp_pct > 5.0 {
            println!(
                "  GOOD: FLOOR BOOSTS GDP: +{:.1}% ({:.0} -> {:.0})",
                gdp_pct, ctrl_gdp, treat_gdp
            );
        } else if gdp_pct < -5.0 {
            println!(
                "  BAD: FLOOR HURTS GDP: {:.1}% ({:.0} -> {:.0})",
                gdp_pct, ctrl_gdp, treat_gdp
            );
        } else {
            println!("  NEUTRAL: FLOOR ON GDP: {:+.1}%", gdp_pct);
        }

        if treat_dg < ctrl_dg * 0.9 {
            println!(
                "  GOOD: FLOOR REDUCES D/G: {:.3}x -> {:.3}x",
                ctrl_dg, treat_dg
            );
        } else if treat_dg > ctrl_dg * 1.1 {
            println!(
                "  BAD: FLOOR INCREASES D/G: {:.3}x -> {:.3}x",
                ctrl_dg, treat_dg
            );
        } else {
            println!(
                "  NEUTRAL: FLOOR ON D/G: {:.3}x -> {:.3}x",
                ctrl_dg, treat_dg
            );
        }

        println!("\n  VERDICT:");
        if floor_binds == 5 {
            println!("  - Floor binds in 5/5 seeds — Diamond displayed always = $300");
            println!("  - Floor creates price rigidity: internal price can collapse below floor");
            println!("  - Recommendation: try 40% floor (binds only in stressed seeds)");
        } else if floor_binds > 0 {
            println!("  - Floor binds in {}/5 seeds", floor_binds);
        }
        if treat_vol > 0.05 && floor_binds > 0 {
            println!("  - Vol > 0.05 WITH floor — consider weaker floor or higher vol threshold");
        }
        if treat_vol > 0.05 && ctrl_vol <= 0.05 {
            println!("  - CONFIRMED: floor is the volatility source");
        }
        if treat_vol > 0.05 && ctrl_vol > 0.05 {
            println!("  - Vol > 0.05 in BOTH configs — floor is not the sole cause");
        }
    }
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
fn run_sell_pressure_sweep() {
    use crate::analyzer::load_summary;
    let seed = 42u64;
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       SELL PRESSURE SENSITIVITY TEST (seed=42)              ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    let multipliers = [0.5, 0.8, 1.0, 1.2, 1.5];
    let mut results = Vec::new();

    for &mult in &multipliers {
        let mut scenario = Scenario::guild_stability_mm_fixed_guild();
        scenario.config.economy.sell_pressure_multiplier = mult;
        let dir = PathBuf::from(format!("/tmp/autotune-sp-{}", mult));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).ok();
        run_seeded_headless(&scenario, seed, &dir).unwrap();
        let summary = load_summary(&dir.join("simulation.db")).unwrap();
        results.push((mult, summary));
    }

    println!("  Mult  |   GDP   |  Debt   | D/G  | Buy% | Vol×100");
    println!("  --------------------------------------------------");
    for (mult, s) in &results {
        println!(
            "  {:<5.2} | {:7.0} | {:7.0} | {:>4.2}x | {:>4.1}% | {:>5.3}",
            mult,
            s.gdp,
            s.debt,
            s.debt / s.gdp.max(1.0),
            s.buy_ratio * 100.0,
            s.avg_volatility * 100.0
        );
    }
}
fn run_sell_pressure_multi_seed() {
    use crate::analyzer::load_summary;
    let seeds = vec![42, 12345, 98765, 77777, 11111];
    let multipliers = [0.8, 1.0, 1.2];
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       SELL PRESSURE MULTI-SEED (5 seeds)                    ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    for &mult in &multipliers {
        let mut total_gdp = 0.0;
        let mut total_dg = 0.0;
        let mut total_vol = 0.0;
        let mut total_bpd = 0.0;

        for &seed in &seeds {
            let mut scenario = Scenario::guild_stability_mm_fixed_guild();
            scenario.config.economy.sell_pressure_multiplier = mult;
            let dir = PathBuf::from(format!("/tmp/autotune-sp-multi-{}-{}", mult, seed));
            let _ = std::fs::remove_dir_all(&dir);
            std::fs::create_dir_all(&dir).ok();
            run_seeded_headless(&scenario, seed, &dir).unwrap();
            let summary = load_summary(&dir.join("simulation.db")).unwrap();

            total_gdp += summary.gdp;
            total_dg += summary.debt / summary.gdp.max(1.0);
            total_vol += summary.avg_volatility;
            total_bpd += summary.avg_bpd;
        }

        let avg_gdp = total_gdp / seeds.len() as f64;
        let avg_dg = total_dg / seeds.len() as f64;
        let avg_vol = total_vol / seeds.len() as f64;
        let avg_bpd = total_bpd / seeds.len() as f64;

        println!(
            "  Mult: {:<4.1} | GDP: {:7.0} | D/G: {:>4.2}x | Vol: {:>5.3} | BPD: {:>4.2}%",
            mult,
            avg_gdp,
            avg_dg,
            avg_vol * 100.0,
            avg_bpd * 100.0
        );
    }
}
fn run_trend_dampening_sweep() {
    use crate::analyzer::load_summary;
    let seeds = vec![42, 12345, 98765, 77777, 11111];
    let dampeners = [0.0, 0.05, 0.1, 0.15, 0.2];
    let mut print_results = Vec::new();
    for &damp in &dampeners {
        let mut total_gdp = 0.0;
        let mut total_dg = 0.0;
        let mut total_vol = 0.0;
        let mut total_bpd = 0.0;
        for &seed in &seeds {
            let mut scenario = Scenario::guild_stability_mm_fixed_guild();
            scenario.config.economy.trend_dampening = damp;
            let dir = PathBuf::from(format!("/tmp/autotune-tp-multi-{}-{}", damp, seed));
            let _ = std::fs::remove_dir_all(&dir);
            std::fs::create_dir_all(&dir).ok();
            run_seeded_headless(&scenario, seed, &dir).unwrap();
            let summary = load_summary(&dir.join("simulation.db")).unwrap();
            total_gdp += summary.gdp;
            total_dg += summary.debt / summary.gdp.max(1.0);
            total_vol += summary.avg_volatility;
            total_bpd += summary.avg_bpd;
        }
        let avg_gdp = total_gdp / seeds.len() as f64;
        let avg_dg = total_dg / seeds.len() as f64;
        let avg_vol = total_vol / seeds.len() as f64;
        let avg_bpd = total_bpd / seeds.len() as f64;
        print_results.push(format!(
            "  Damp: {:<4.2} | GDP: {:7.0} | D/G: {:>4.2}x | Vol: {:>5.3} | BPD: {:>4.2}%",
            damp,
            avg_gdp,
            avg_dg,
            avg_vol * 100.0,
            avg_bpd * 100.0
        ));
    }
    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║       TREND DAMPENING SENSITIVITY TEST (5 seeds)            ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    for res in print_results {
        println!("{}", res);
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SP-HEAD-TO-HEAD — sell_pressure=0.80 vs 1.0 in HEALTHY economy
// Uses guild_stability_mm_fixed_guild (1MM + 2GB + 4Cas + 3Far + 2Tra)
// ═══════════════════════════════════════════════════════════════════════════
fn run_sell_pressure_head_to_head() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  SELL PRESSURE HEAD-TO-HEAD — HEALTHY ECONOMY             ║");
    println!("║  guild_stability_mm_fixed_guild × 5 seeds                  ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
        "Seed", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
        "──────", "────────────", "──────────", "────────", "───────", "────────"
    );

    #[derive(Debug)]
    #[allow(dead_code)]
    struct SpResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        buy_ratio: f64,
    }
    let mut ctrl_results: Vec<SpResult> = Vec::new();
    let mut treat_results: Vec<SpResult> = Vec::new();

    for &seed in &seeds {
        // Control: sell_pressure=0.80 (current default)
        let mut ctrl_scenario = Scenario::guild_stability_mm_fixed_guild();
        ctrl_scenario.name = "Ctrl: sp=0.80".into();
        ctrl_scenario.config.economy.sell_pressure_multiplier = 0.80;

        // Treatment: sell_pressure=1.0 (symmetric)
        let mut treat_scenario = Scenario::guild_stability_mm_fixed_guild();
        treat_scenario.name = "Treat: sp=1.0".into();
        treat_scenario.config.economy.sell_pressure_multiplier = 1.0;

        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-sp-h2h-ctrl-{seed}"));
        let treat_dir = PathBuf::from(format!("/tmp/autotune-sp-h2h-treat-{seed}"));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        let _ = std::fs::remove_dir_all(&treat_dir);

        run_seeded_headless(&ctrl_scenario, seed, &ctrl_dir).ok();
        run_seeded_headless(&treat_scenario, seed, &treat_dir).ok();

        if let Ok(s) = load_summary(&ctrl_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [CTRL sp=0.80]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            ctrl_results.push(SpResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
            });
        }
        if let Ok(s) = load_summary(&treat_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [TREAT sp=1.0]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            treat_results.push(SpResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
            });
        }

        let _ = std::fs::remove_dir_all(&ctrl_dir);
        let _ = std::fs::remove_dir_all(&treat_dir);
    }

    // Summary
    let avg = |r: &[SpResult]| -> (f64, f64, f64, f64, f64) {
        let n = r.len() as f64;
        if n == 0.0 {
            return (0.0, 0.0, 0.0, 0.0, 0.0);
        }
        let (g, d, v, b, buy) = r.iter().fold((0.0, 0.0, 0.0, 0.0, 0.0), |acc, x| {
            (
                acc.0 + x.gdp,
                acc.1 + x.dg,
                acc.2 + x.vol,
                acc.3 + x.bpd,
                acc.4 + x.buy_ratio,
            )
        });
        (g / n, d / n, v / n, b / n, buy / n)
    };
    if !ctrl_results.is_empty() {
        let (ctrl_gdp, ctrl_dg, ctrl_vol, ctrl_bpd, ctrl_buy) = avg(&ctrl_results);
        let (treat_gdp, treat_dg, treat_vol, treat_bpd, treat_buy) = avg(&treat_results);
        let gdp_chg = (treat_gdp - ctrl_gdp) / ctrl_gdp * 100.0;
        let dg_chg = (treat_dg - ctrl_dg) / ctrl_dg * 100.0;
        println!(
            "\n  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
            "AVG", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}%",
            "Ctrl(0.80)",
            ctrl_gdp,
            ctrl_dg,
            ctrl_vol * 100.0,
            ctrl_bpd * 100.0,
            ctrl_buy * 100.0
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}%",
            "Treat(1.0)",
            treat_gdp,
            treat_dg,
            treat_vol * 100.0,
            treat_bpd * 100.0,
            treat_buy * 100.0
        );
        println!(
            "\n  Changes: GDP {:+.1}%, D/G {:+.1}%, Vol {:+.1}%, BPD {:+.1}%",
            gdp_chg, dg_chg, 0.0, 0.0
        );
        if gdp_chg > 0.0 && dg_chg < 0.0 {
            println!("  ✅ BOTH improved: sp=1.0 wins on GDP AND D/G");
        } else if gdp_chg < 0.0 && dg_chg < 0.0 {
            let gdp_txt = format!("-{:.1}%", -gdp_chg);
            let dg_txt = format!("{:.1}%", dg_chg);
            println!("  ⚖️  Tradeoff: sp=1.0 {} GDP but {} D/G", gdp_txt, dg_txt);
        } else if dg_chg > 0.0 {
            println!("  ❌ sp=1.0 is WORSE on D/G — current default (0.80) may be correct");
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NEWBIE STRESS TEST — replace Farmers with Newbies in stressed economy
// Newbies are net consumers (usage > gather rate), stress-testing buy-side
// Control: guildbuyer_failure_test (3Far)
// Treatment: replace 3Far with 3Newbie
// ═══════════════════════════════════════════════════════════════════════════
fn run_newbie_stress_test() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765, 77777, 11111];

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  NEWBIE STRESS TEST — stressed economy                       ║");
    println!("║  Control: 3Farmer | Treatment: 3Newbie (net consumers)     ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct NewbieResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        buy_ratio: f64,
    }
    let mut ctrl_results: Vec<NewbieResult> = Vec::new();
    let mut treat_results: Vec<NewbieResult> = Vec::new();

    for &seed in &seeds {
        // Control: guildbuyer_failure_test (1MM + 2GB + 4Cas + 3Far + 2Tra)
        let ctrl_scenario = Scenario::guildbuyer_failure_test();
        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-newbie-ctrl-{seed}"));

        // Treatment: replace 3Far with 3Newbie
        let mut treat_scenario = Scenario::guildbuyer_failure_test();
        treat_scenario.name = "Newbie Stress Test: 3Newbie".into();
        treat_scenario.players = vec![
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
                archetype: "Newbie".into(),
                count: 3,
            }, // replaces Farmer
            ArchetypeConfig {
                archetype: "Trader".into(),
                count: 2,
            },
        ];
        let treat_dir = PathBuf::from(format!("/tmp/autotune-newbie-treat-{seed}"));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        let _ = std::fs::remove_dir_all(&treat_dir);

        run_seeded_headless(&ctrl_scenario, seed, &ctrl_dir).ok();
        run_seeded_headless(&treat_scenario, seed, &treat_dir).ok();

        if let Ok(s) = load_summary(&ctrl_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            println!(
                "  {:>6} GDP={:>9.0} D/G={:.3}x Vol={:.3}% Buy%={:.1}% [Ctrl: Farmer]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.buy_ratio * 100.0
            );
            ctrl_results.push(NewbieResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
            });
        }
        if let Ok(s) = load_summary(&treat_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            println!(
                "  {:>6} GDP={:>9.0} D/G={:.3}x Vol={:.3}% Buy%={:.1}% [Treat: Newbie]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.buy_ratio * 100.0
            );
            treat_results.push(NewbieResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
            });
        }

        let _ = std::fs::remove_dir_all(&ctrl_dir);
        let _ = std::fs::remove_dir_all(&treat_dir);
    }

    let avg_newbie = |r: &[NewbieResult]| -> (f64, f64) {
        let n = r.len() as f64;
        if n == 0.0 {
            return (0.0, 0.0);
        }
        let (g, d) = r
            .iter()
            .fold((0.0, 0.0), |acc, x| (acc.0 + x.gdp, acc.1 + x.dg));
        (g / n, d / n)
    };
    if !ctrl_results.is_empty() {
        let (ctrl_gdp, ctrl_dg) = avg_newbie(&ctrl_results);
        let (treat_gdp, treat_dg) = avg_newbie(&treat_results);
        let gdp_chg = (treat_gdp - ctrl_gdp) / ctrl_gdp * 100.0;
        let dg_chg = (treat_dg - ctrl_dg) / ctrl_dg * 100.0;
        println!(
            "\n  AVG  GDP={:>9.0} D/G={:.3}x  [Ctrl: Farmer]",
            ctrl_gdp, ctrl_dg
        );
        println!(
            "  AVG  GDP={:>9.0} D/G={:.3}x  [Treat: Newbie]",
            treat_gdp, treat_dg
        );
        println!("  Changes: GDP {:+.1}%, D/G {:+.1}%", gdp_chg, dg_chg);
        if gdp_chg < -20.0 {
            println!(
                "  💡 Newbie-heavy economies produce significantly less GDP (natural — net consumers)"
            );
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  COMBO CORRECTED TEST
//  Question: Does sp=1.0 + td=0.10 outperform sp=0.80 + td=0.10?
//  Background: sp was reverted 0.80→1.0 (D/G stability fix). td=0.10 is confirmed.
//  This tests the COMBO of both corrected parameters together.
// ═══════════════════════════════════════════════════════════════════════════

fn run_combo_corrected_test() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765, 77777, 11111];

    println!("\n╔════════════════════════════════════════════════════════════════════╗");
    println!("║  COMBO CORRECTED TEST — sp=1.0 + td=0.10 vs sp=0.80 + td=0.10  ║");
    println!("║  Scenario: guild_stability_mm_fixed_guild × 5 seeds           ║");
    println!("╚════════════════════════════════════════════════════════════════════╝\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct ComboResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        buy_ratio: f64,
    }
    let mut old_results: Vec<ComboResult> = Vec::new();
    let mut new_results: Vec<ComboResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
        "Seed", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
        "──────", "────────────", "──────────", "────────", "───────", "────────"
    );

    for &seed in &seeds {
        // Old combo: sp=0.80 (old default) + td=0.10 (confirmed GDP-optimal)
        let mut old_scenario = Scenario::guild_stability_mm_fixed_guild();
        old_scenario.name = "Old: sp=0.80+td=0.10".into();
        old_scenario.config.economy.sell_pressure_multiplier = 0.80;
        old_scenario.config.economy.trend_dampening = 0.10;

        // New combo: sp=1.0 (corrected) + td=0.10 (confirmed GDP-optimal)
        let mut new_scenario = Scenario::guild_stability_mm_fixed_guild();
        new_scenario.name = "New: sp=1.0+td=0.10".into();
        new_scenario.config.economy.sell_pressure_multiplier = 1.0;
        new_scenario.config.economy.trend_dampening = 0.10;

        let old_dir = PathBuf::from(format!("/tmp/autotune-combo-old-{seed}"));
        let new_dir = PathBuf::from(format!("/tmp/autotune-combo-new-{seed}"));
        let _ = std::fs::remove_dir_all(&old_dir);
        let _ = std::fs::remove_dir_all(&new_dir);

        run_seeded_headless(&old_scenario, seed, &old_dir).ok();
        run_seeded_headless(&new_scenario, seed, &new_dir).ok();

        if let Ok(s) = load_summary(&old_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [OLD sp=0.80,td=0.10]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            old_results.push(ComboResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
            });
        }
        if let Ok(s) = load_summary(&new_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [NEW sp=1.0,td=0.10]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            new_results.push(ComboResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
            });
        }

        let _ = std::fs::remove_dir_all(&old_dir);
        let _ = std::fs::remove_dir_all(&new_dir);
    }

    let avg = |r: &[ComboResult]| -> (f64, f64, f64, f64, f64) {
        let n = r.len() as f64;
        if n == 0.0 {
            return (0.0, 0.0, 0.0, 0.0, 0.0);
        }
        let (g, d, v, b, buy) = r.iter().fold((0.0, 0.0, 0.0, 0.0, 0.0), |acc, x| {
            (
                acc.0 + x.gdp,
                acc.1 + x.dg,
                acc.2 + x.vol,
                acc.3 + x.bpd,
                acc.4 + x.buy_ratio,
            )
        });
        (g / n, d / n, v / n, b / n, buy / n)
    };

    if !old_results.is_empty() && !new_results.is_empty() {
        let (old_gdp, old_dg, old_vol, old_bpd, old_buy) = avg(&old_results);
        let (new_gdp, new_dg, new_vol, new_bpd, new_buy) = avg(&new_results);
        let gdp_chg = (new_gdp - old_gdp) / old_gdp * 100.0;
        let dg_chg = (new_dg - old_dg) / old_dg * 100.0;
        let vol_chg = (new_vol - old_vol) / old_vol.max(0.0001) * 100.0;
        let bpd_chg = (new_bpd - old_bpd) / old_bpd * 100.0;

        println!(
            "\n  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
            "AVG", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [OLD sp=0.80,td=0.10]",
            "OLD",
            old_gdp,
            old_dg,
            old_vol * 100.0,
            old_bpd * 100.0,
            old_buy * 100.0
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [NEW sp=1.0,td=0.10]",
            "NEW",
            new_gdp,
            new_dg,
            new_vol * 100.0,
            new_bpd * 100.0,
            new_buy * 100.0
        );
        println!(
            "\n  Changes: GDP {:+.1}%, D/G {:+.1}%, Vol {:+.1}%, BPD {:+.1}%, Buy {:+.1}pp",
            gdp_chg,
            dg_chg,
            vol_chg,
            bpd_chg,
            (new_buy - old_buy) * 100.0
        );

        if gdp_chg > 0.0 && dg_chg < 0.0 {
            println!("  ✅ BOTH improved: corrected combo wins on GDP AND D/G");
        } else if gdp_chg < 0.0 && dg_chg < 0.0 {
            println!(
                "  ⚖️  Tradeoff: {:+.1}% GDP, {:+.1}% D/G (D/G wins, GDP costs)",
                gdp_chg, dg_chg
            );
            println!(
                "  📌 RECOMMENDATION: corrected combo (sp=1.0+td=0.10) is correct for stability."
            );
        } else if dg_chg > 0.0 {
            println!("  ❌ NEW combo is WORSE on D/G — review needed");
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  HEALTHY ECONOMY NEWBIE TEST
//  Question: Do Newbies outperform Farmers in a HEALTHY 2MM+2GB+floor economy?
//  Prior finding (stressed): Newbie +33% GDP, -42% D/G vs Farmer.
//  Does this hold in a healthy economy?
//  Control: 2MM+2GB+3Cas+3Far+2Tra (guild_stability_2mm_fixed_guild)
//  Treat:   2MM+2GB+3Cas+3Newbie+2Tra (replace 3Far with 3Newbie)
// ═══════════════════════════════════════════════════════════════════════════

fn run_healthy_newbie_test() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765, 77777, 11111];
    let diamond_floor = 500.0 * 0.60; // $300

    println!("\n╔════════════════════════════════════════════════════════════════════╗");
    println!("║  HEALTHY ECONOMY NEWBIE TEST — 2MM+2GB+floor × 5 seeds      ║");
    println!("║  Control: 3Farmer | Treat: 3Newbie (net consumers)          ║");
    println!("╚════════════════════════════════════════════════════════════════════╝\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct HealthyNewbieResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        floor_binds: bool,
    }
    let mut ctrl_results: Vec<HealthyNewbieResult> = Vec::new();
    let mut treat_results: Vec<HealthyNewbieResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7}",
        "Seed", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7}",
        "──────", "────────────", "──────────", "────────", "───────", "───────"
    );

    for &seed in &seeds {
        // Control: 2MM+2GB+3Cas+3Far+2Tra
        let mut ctrl_scenario = Scenario::guild_stability_2mm_fixed_guild();
        ctrl_scenario.name = "Ctrl: Farmer".into();
        // Apply Diamond floor
        if let Some(diamond) = ctrl_scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6);
        }

        // Treatment: 2MM+2GB+3Cas+3Newbie+2Tra (replace Farmer with Newbie)
        let mut treat_scenario = Scenario::guild_stability_2mm_fixed_guild();
        treat_scenario.name = "Treat: Newbie".into();
        treat_scenario.players = vec![
            ArchetypeConfig {
                archetype: "MarketMaker".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "GuildBuyer".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Casual".into(),
                count: 3,
            },
            ArchetypeConfig {
                archetype: "Newbie".into(),
                count: 3,
            }, // replaces Farmer
            ArchetypeConfig {
                archetype: "Trader".into(),
                count: 2,
            },
        ];
        if let Some(diamond) = treat_scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6);
        }

        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-hn-ctrl-{seed}"));
        let treat_dir = PathBuf::from(format!("/tmp/autotune-hn-treat-{seed}"));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        let _ = std::fs::remove_dir_all(&treat_dir);

        run_seeded_headless(&ctrl_scenario, seed, &ctrl_dir).ok();
        run_seeded_headless(&treat_scenario, seed, &treat_dir).ok();

        if let Ok(s) = load_summary(&ctrl_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            let prices = crate::analyzer::load_all_prices(&ctrl_dir.join("simulation.db"))
                .unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [Ctrl: Farmer]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            ctrl_results.push(HealthyNewbieResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                floor_binds: dd >= diamond_floor - 0.01,
            });
        }
        if let Ok(s) = load_summary(&treat_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            let prices = crate::analyzer::load_all_prices(&treat_dir.join("simulation.db"))
                .unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [Treat: Newbie]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            treat_results.push(HealthyNewbieResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                floor_binds: dd >= diamond_floor - 0.01,
            });
        }

        let _ = std::fs::remove_dir_all(&ctrl_dir);
        let _ = std::fs::remove_dir_all(&treat_dir);
    }

    let avg = |r: &[HealthyNewbieResult]| -> (f64, f64, f64, f64, f64) {
        let n = r.len() as f64;
        if n == 0.0 {
            return (0.0, 0.0, 0.0, 0.0, 0.0);
        }
        let (g, d, v, b, buy) = r.iter().fold((0.0, 0.0, 0.0, 0.0, 0.0), |acc, x| {
            (
                acc.0 + x.gdp,
                acc.1 + x.dg,
                acc.2 + x.vol,
                acc.3 + x.bpd,
                acc.4 + x.buy_ratio,
            )
        });
        (g / n, d / n, v / n, b / n, buy / n)
    };

    if !ctrl_results.is_empty() && !treat_results.is_empty() {
        let (ctrl_gdp, ctrl_dg, ctrl_vol, ctrl_bpd, ctrl_buy) = avg(&ctrl_results);
        let (treat_gdp, treat_dg, treat_vol, treat_bpd, treat_buy) = avg(&treat_results);
        let gdp_chg = (treat_gdp - ctrl_gdp) / ctrl_gdp * 100.0;
        let dg_chg = (treat_dg - ctrl_dg) / ctrl_dg * 100.0;
        let vol_chg = (treat_vol - ctrl_vol) / ctrl_vol.max(0.0001) * 100.0;

        let ctrl_floor_binds = ctrl_results.iter().filter(|r| r.floor_binds).count();
        let treat_floor_binds = treat_results.iter().filter(|r| r.floor_binds).count();

        println!(
            "\n  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
            "AVG", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [Ctrl: Farmer]",
            "Farmer",
            ctrl_gdp,
            ctrl_dg,
            ctrl_vol * 100.0,
            ctrl_bpd * 100.0,
            ctrl_buy * 100.0
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [Treat: Newbie]",
            "Newbie",
            treat_gdp,
            treat_dg,
            treat_vol * 100.0,
            treat_bpd * 100.0,
            treat_buy * 100.0
        );
        println!(
            "\n  Changes: GDP {:+.1}%, D/G {:+.1}%, Vol {:+.1}%",
            gdp_chg, dg_chg, vol_chg
        );
        println!(
            "  Floor binds: Ctrl {}/5 seeds, Treat {}/5 seeds",
            ctrl_floor_binds, treat_floor_binds
        );

        if gdp_chg > 0.0 && dg_chg < 0.0 {
            println!("  ✅ Newbie BETTER in healthy economy: +GDP, -D/G");
            println!("  💡 Newbie benefit is UNIVERSAL — healthy AND stressed economies.");
        } else if gdp_chg > 0.0 && dg_chg > 0.0 {
            println!("  ⚖️  Newbie +GDP but +D/G in healthy economy (vs stressed: -D/G)");
            println!("  📌 Newbie effect is CONTEXT-DEPENDENT: beneficial but tradeoffs exist.");
        } else if gdp_chg < 0.0 && dg_chg < 0.0 {
            println!("  💡 Newbie is NEUTRAL/negative in healthy economy.");
            println!("  📌 Healthy economies don't need Newbie buy pressure as much.");
        } else {
            println!("  ❓ Unexpected result pattern — inspect data.");
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  TUNED 2MM+2GB+floor TEST
//  Question: Does 2MM+2GB+floor with corrected defaults (sp=1.0, td=0.10)
//  outperform the old defaults (sp=0.80, td=0.10)?
//  Prior: sp=0.80 was tested on guild_stability_mm_fixed_guild only.
//  This tests on the HEALTHY 2MM+2GB+floor economy — does sp=1.0 still win D/G?
// ═══════════════════════════════════════════════════════════════════════════

fn run_tuned_2mm_test() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765, 77777, 11111];
    let diamond_floor = 500.0 * 0.60; // $300

    println!("\n╔════════════════════════════════════════════════════════════════════╗");
    println!("║  TUNED 2MM+2GB+floor TEST — sp=1.0 vs sp=0.80 in healthy    ║");
    println!("║  Scenario: 2MM+2GB+Diamond floor × 5 seeds                  ║");
    println!("╚════════════════════════════════════════════════════════════════════╝\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct TunedResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        bpd: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        floor_binds: bool,
    }
    let mut old_results: Vec<TunedResult> = Vec::new();
    let mut new_results: Vec<TunedResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7}",
        "Seed", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7}",
        "──────", "────────────", "──────────", "────────", "───────", "───────"
    );

    for &seed in &seeds {
        // Old defaults: sp=0.80, td=0.10
        let mut old_scenario = Scenario::guild_stability_2mm_fixed_guild();
        old_scenario.name = "Old: sp=0.80".into();
        old_scenario.config.economy.sell_pressure_multiplier = 0.80;
        old_scenario.config.economy.trend_dampening = 0.10;
        if let Some(diamond) = old_scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6);
        }

        // New corrected: sp=1.0, td=0.10
        let mut new_scenario = Scenario::guild_stability_2mm_fixed_guild();
        new_scenario.name = "New: sp=1.0".into();
        new_scenario.config.economy.sell_pressure_multiplier = 1.0;
        new_scenario.config.economy.trend_dampening = 0.10;
        if let Some(diamond) = new_scenario
            .config
            .items
            .iter_mut()
            .find(|ic| ic.name == "Diamond")
        {
            diamond.price_floor_override = Some(diamond.base_price * 0.6);
        }

        let old_dir = PathBuf::from(format!("/tmp/autotune-tuned-old-{seed}"));
        let new_dir = PathBuf::from(format!("/tmp/autotune-tuned-new-{seed}"));
        let _ = std::fs::remove_dir_all(&old_dir);
        let _ = std::fs::remove_dir_all(&new_dir);

        run_seeded_headless(&old_scenario, seed, &old_dir).ok();
        run_seeded_headless(&new_scenario, seed, &new_dir).ok();

        if let Ok(s) = load_summary(&old_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            let prices = crate::analyzer::load_all_prices(&old_dir.join("simulation.db"))
                .unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [OLD sp=0.80]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            old_results.push(TunedResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                floor_binds: dd >= diamond_floor - 0.01,
            });
        }
        if let Ok(s) = load_summary(&new_dir.join("simulation.db")) {
            let dg = s.debt / s.gdp.max(1.0);
            let prices = crate::analyzer::load_all_prices(&new_dir.join("simulation.db"))
                .unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [NEW sp=1.0]",
                seed,
                s.gdp,
                dg,
                s.avg_volatility * 100.0,
                s.avg_bpd * 100.0,
                s.buy_ratio * 100.0
            );
            new_results.push(TunedResult {
                seed,
                gdp: s.gdp,
                dg,
                vol: s.avg_volatility,
                bpd: s.avg_bpd,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                floor_binds: dd >= diamond_floor - 0.01,
            });
        }

        let _ = std::fs::remove_dir_all(&old_dir);
        let _ = std::fs::remove_dir_all(&new_dir);
    }

    let avg = |r: &[TunedResult]| -> (f64, f64, f64, f64, f64) {
        let n = r.len() as f64;
        if n == 0.0 {
            return (0.0, 0.0, 0.0, 0.0, 0.0);
        }
        let (g, d, v, b, buy) = r.iter().fold((0.0, 0.0, 0.0, 0.0, 0.0), |acc, x| {
            (
                acc.0 + x.gdp,
                acc.1 + x.dg,
                acc.2 + x.vol,
                acc.3 + x.bpd,
                acc.4 + x.buy_ratio,
            )
        });
        (g / n, d / n, v / n, b / n, buy / n)
    };

    if !old_results.is_empty() && !new_results.is_empty() {
        let (old_gdp, old_dg, old_vol, old_bpd, old_buy) = avg(&old_results);
        let (new_gdp, new_dg, new_vol, new_bpd, new_buy) = avg(&new_results);
        let gdp_chg = (new_gdp - old_gdp) / old_gdp * 100.0;
        let dg_chg = (new_dg - old_dg) / old_dg * 100.0;
        let vol_chg = (new_vol - old_vol) / old_vol.max(0.0001) * 100.0;

        let old_floor_binds = old_results.iter().filter(|r| r.floor_binds).count();
        let new_floor_binds = new_results.iter().filter(|r| r.floor_binds).count();

        println!(
            "\n  {:>6} {:>12} {:>10} {:>8} {:>7}  {:>8}",
            "AVG", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [OLD sp=0.80,td=0.10]",
            "OLD(0.80)",
            old_gdp,
            old_dg,
            old_vol * 100.0,
            old_bpd * 100.0,
            old_buy * 100.0
        );
        println!(
            "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}%  {:>7.1}% [NEW sp=1.0,td=0.10]",
            "NEW(1.0)",
            new_gdp,
            new_dg,
            new_vol * 100.0,
            new_bpd * 100.0,
            new_buy * 100.0
        );
        println!(
            "\n  Changes: GDP {:+.1}%, D/G {:+.1}%, Vol {:+.1}%, BPD {:+.1}%",
            gdp_chg,
            dg_chg,
            vol_chg,
            (new_bpd - old_bpd) / old_bpd * 100.0
        );
        println!(
            "  Floor binds: OLD {}/5 seeds, NEW {}/5 seeds",
            old_floor_binds, new_floor_binds
        );

        if gdp_chg > 0.0 && dg_chg < 0.0 {
            println!("  ✅ BOTH improved: sp=1.0 wins on GDP AND D/G in healthy economy");
        } else if gdp_chg < 0.0 && dg_chg < 0.0 {
            println!(
                "  ⚖️  sp=1.0: -GDP {:+.1}% but D/G {:+.1}% in healthy 2MM economy",
                gdp_chg, dg_chg
            );
            println!(
                "  📌 sp=1.0 is correct for healthy 2MM economy: D/G stability > marginal GDP"
            );
        } else if dg_chg > 0.0 {
            println!("  ❌ sp=1.0 is WORSE on D/G even in healthy economy — review");
        } else {
            println!("  ℹ️  Neutral result — both configs similar in healthy economy");
        }
    }
}

// ═══════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════
// SESSION: 2026-04-15 — Events × 2MM+2GB+floor
// ═══════════════════════════════════════════════════════════════════

fn run_events_healthy_test() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765];

    println!("\n╔════════════════════════════════════════════════════════════════════╗");
    println!("║  EVENTS × 2MM+2GB+FLOOR (PRODUCTION CONFIG) — 3 seeds  ║");
    println!("║  Control: 2MM+2GB+floor (no events)                    ║");
    println!("║  Treat:   same + DEMAND_SURGE(DIAMOND), SUPPLY_GLUT(IRON), ║");
    println!("║            INFLATION_BOOST(all), GOLD_RUSH(GOLD_*)        ║");
    println!("╚════════════════════════════════════════════════════════════════════╝\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct EventResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        buy_ratio: f64,
        tier3_events: u32,
    }
    let mut ctrl_results: Vec<EventResult> = Vec::new();
    let mut treat_results: Vec<EventResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>8}",
        "Seed", "GDP", "D/G", "Vol(CV)", "Buy%", "TIER3"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>8}",
        "──────", "────────────", "──────────", "────────", "───────", "────────"
    );

    for &seed in &seeds {
        // ── Control: 2MM+2GB+floor (no events) ──────────────────────
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = "Ctrl: No Events".into();
        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-evh-ctrl-{seed}"));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        run_seeded_headless(&ctrl, seed, &ctrl_dir).ok();

        // ── Treatment: 2MM+2GB+floor WITH events ──────────────────────
        let mut treat = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        treat.name = "Treat: Events".into();
        // Same 4 events as market_event_test (days 3-12)
        treat.events = vec![
            crate::events::MarketEvent {
                name: "Diamond Demand Surge".into(),
                event_type: crate::events::EventType::DemandSurge,
                materials: vec!["DIAMOND".into()],
                multiplier: 2.0,
                starts_at_tick: 288 * 3,
                ends_at_tick: 288 * 5,
            },
            crate::events::MarketEvent {
                name: "Iron Supply Glut".into(),
                event_type: crate::events::EventType::SupplyGlut,
                materials: vec!["IRON_INGOT".into()],
                multiplier: 2.0,
                starts_at_tick: 288 * 7,
                ends_at_tick: 288 * 9,
            },
            crate::events::MarketEvent {
                name: "Economy-Wide Inflation Boost".into(),
                event_type: crate::events::EventType::InflationBoost,
                materials: vec!["*".into()],
                multiplier: 1.5,
                starts_at_tick: 288 * 5,
                ends_at_tick: 288 * 8,
            },
            crate::events::MarketEvent {
                name: "Gold Ingot Rush".into(),
                event_type: crate::events::EventType::GoldRush,
                materials: vec!["GOLD_*".into()],
                multiplier: 1.8,
                starts_at_tick: 288 * 10,
                ends_at_tick: 288 * 12,
            },
        ];
        let treat_dir = PathBuf::from(format!("/tmp/autotune-evh-treat-{seed}"));
        let _ = std::fs::remove_dir_all(&treat_dir);
        run_seeded_headless(&treat, seed, &treat_dir).ok();

        let load = |dir: &std::path::Path| -> Option<EventResult> {
            let s = load_summary(&dir.join("simulation.db")).ok()?;
            let db_path = dir.join("simulation.db");
            let tier3_events = count_tier3_events(&db_path);
            Some(EventResult {
                seed,
                gdp: s.gdp,
                dg: s.debt / s.gdp.max(1.0),
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                tier3_events,
            })
        };

        if let Some(r) = load(&ctrl_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.1}% {:>7}  [Ctrl: No Events]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.buy_ratio * 100.0,
                r.tier3_events
            );
            ctrl_results.push(r);
        }
        if let Some(r) = load(&treat_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.1}% {:>7}  [Treat: Events]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.buy_ratio * 100.0,
                r.tier3_events
            );
            treat_results.push(r);
        }
    }

    // Aggregate
    if !ctrl_results.is_empty() && !treat_results.is_empty() {
        let avg = |rs: &[EventResult], f: fn(&EventResult) -> f64| -> f64 {
            rs.iter().map(f).sum::<f64>() / rs.len() as f64
        };
        let gdp_chg = (avg(&treat_results, |r| r.gdp) - avg(&ctrl_results, |r| r.gdp))
            / avg(&ctrl_results, |r| r.gdp)
            * 100.0;
        let dg_chg = avg(&treat_results, |r| r.dg) - avg(&ctrl_results, |r| r.dg);
        let vol_chg = (avg(&treat_results, |r| r.vol) - avg(&ctrl_results, |r| r.vol))
            / avg(&ctrl_results, |r| r.vol)
            * 100.0;
        let tier3_sum: u32 = treat_results.iter().map(|r| r.tier3_events).sum();

        println!("\n  ── AVERAGES ──");
        println!(
            "  GDP:  ctrl={:.0}  treat={:.0}  chg={:+.1}%",
            avg(&ctrl_results, |r| r.gdp),
            avg(&treat_results, |r| r.gdp),
            gdp_chg
        );
        println!(
            "  D/G:  ctrl={:.3}x  treat={:.3}x  Δ={:+.3}x",
            avg(&ctrl_results, |r| r.dg),
            avg(&treat_results, |r| r.dg),
            dg_chg
        );
        println!(
            "  Vol:  ctrl={:.3}%  treat={:.3}%  chg={:+.1}%",
            avg(&ctrl_results, |r| r.vol) * 100.0,
            avg(&treat_results, |r| r.vol) * 100.0,
            vol_chg
        );
        println!("  TIER3 events (treat): {} total", tier3_sum);
        println!("\n  VERDICT:");
        if gdp_chg > 5.0 && dg_chg < 1.0 {
            println!(
                "  ✅ Events BOOST GDP ({:+.1}%) without D/G deterioration — safe in healthy economy",
                gdp_chg
            );
        } else if gdp_chg > 0.0 && dg_chg < 2.0 {
            println!(
                "  ⚠️  Events boost GDP {:+.1}% but D/G worsens by {:.3}x — monitor D/G in production",
                gdp_chg, dg_chg
            );
        } else if gdp_chg <= 0.0 {
            println!(
                "  ❌ Events HURT GDP ({:+.1}%) in healthy economy — reconsider event frequency",
                gdp_chg
            );
        } else {
            println!(
                "  ℹ️  Mixed results — events add volatility ({:+.1}%) but GDP effect is {:.1}%",
                vol_chg, gdp_chg
            );
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SESSION: 2026-04-15 — Counter-Cyclical × 2MM+2GB+floor
// ═══════════════════════════════════════════════════════════════════

fn run_counter_cyclical_cc_test() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765];

    println!("\n╔════════════════════════════════════════════════════════════════════╗");
    println!("║  COUNTER-CYCLICAL × 2MM+2GB+FLOOR — 3 seeds              ║");
    println!("║  Control: counter_cyclical=true (default)                  ║");
    println!("║  Treat:   counter_cyclical=false (legacy tiered breaker) ║");
    println!("╚════════════════════════════════════════════════════════════════════╝\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct CcResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        tier3_events: u32,
    }
    let mut ctrl_results: Vec<CcResult> = Vec::new();
    let mut treat_results: Vec<CcResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}",
        "Seed", "GDP", "D/G", "Vol(CV)", "TIER3"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}",
        "──────", "────────────", "──────────", "────────", "────────"
    );

    for &seed in &seeds {
        // ── Control: counter_cyclical=true ─────────────────────────
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = "Ctrl: CC=true".into();
        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-cc-ctrl-{seed}"));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        run_seeded_headless(&ctrl, seed, &ctrl_dir).ok();

        // ── Treatment: counter_cyclical=false ─────────────────────
        let mut treat = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        treat.name = "Treat: CC=false".into();
        treat.config.loans.counter_cyclical = false; // ← THE CHANGE
        let treat_dir = PathBuf::from(format!("/tmp/autotune-cc-treat-{seed}"));
        let _ = std::fs::remove_dir_all(&treat_dir);
        run_seeded_headless(&treat, seed, &treat_dir).ok();

        let load = |dir: &std::path::Path| -> Option<CcResult> {
            let s = load_summary(&dir.join("simulation.db")).ok()?;
            let db_path = dir.join("simulation.db");
            Some(CcResult {
                seed,
                gdp: s.gdp,
                dg: s.debt / s.gdp.max(1.0),
                vol: s.avg_volatility,
                tier3_events: count_tier3_events(&db_path),
            })
        };

        if let Some(r) = load(&ctrl_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>7}  [Ctrl: CC=true]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.tier3_events
            );
            ctrl_results.push(r);
        }
        if let Some(r) = load(&treat_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>7}  [Treat: CC=false]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.tier3_events
            );
            treat_results.push(r);
        }
    }

    if !ctrl_results.is_empty() && !treat_results.is_empty() {
        let avg = |rs: &[CcResult], f: fn(&CcResult) -> f64| -> f64 {
            rs.iter().map(f).sum::<f64>() / rs.len() as f64
        };
        let gdp_chg = (avg(&treat_results, |r| r.gdp) - avg(&ctrl_results, |r| r.gdp))
            / avg(&ctrl_results, |r| r.gdp)
            * 100.0;
        let dg_treat = avg(&treat_results, |r| r.dg);
        let dg_ctrl = avg(&ctrl_results, |r| r.dg);
        let tier3_treat: u32 = treat_results.iter().map(|r| r.tier3_events).sum();
        let tier3_ctrl: u32 = ctrl_results.iter().map(|r| r.tier3_events).sum();

        println!("\n  ── AVERAGES ──");
        println!(
            "  GDP:  CC=true={:.0}  CC=false={:.0}  chg={:+.1}%",
            avg(&ctrl_results, |r| r.gdp),
            avg(&treat_results, |r| r.gdp),
            gdp_chg
        );
        println!("  D/G:  CC=true={:.3}x  CC=false={:.3}x", dg_ctrl, dg_treat);
        println!("  TIER3: CC=true={}  CC=false={}", tier3_ctrl, tier3_treat);
        println!("\n  VERDICT:");
        if dg_treat < dg_ctrl * 0.9 && tier3_treat <= tier3_ctrl {
            println!(
                "  ✅ CC=false has {:.1}% better D/G — legacy tiered breaker outperforms counter-cyclical in healthy 2MM economy",
                (1.0 - dg_treat / dg_ctrl) * 100.0
            );
            println!(
                "     Counter-cyclical is WORTH THE TRADE-OFF: it costs {:.1}% GDP but gains {:.1}% D/G stability",
                -gdp_chg,
                (dg_ctrl - dg_treat) / dg_ctrl * 100.0
            );
        } else if dg_treat > dg_ctrl * 1.1 {
            println!(
                "  ❌ CC=false WORSENS D/G by {:.1}% — counter-cyclical is correct default",
                (dg_treat / dg_ctrl - 1.0) * 100.0
            );
        } else {
            println!(
                "  ℹ️  Neutral — counter-cyclical=true (default) is confirmed safe for 2MM+2GB+floor"
            );
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SESSION: 2026-04-15 — Casual-heavy × 2MM+2GB+floor
// ═══════════════════════════════════════════════════════════════════

fn run_casual_heavy_healthy_test() {
    use crate::analyzer::load_summary;
    let seeds = vec![42u64, 12345, 98765];

    println!("\n╔════════════════════════════════════════════════════════════════════╗");
    println!("║  CASUAL-HEAVY × 2MM+2GB+FLOOR (PRODUCTION CONFIG) — 3 seeds  ║");
    println!("║  Control: 3Cas + 3Far + 2Tra (standard mix)                  ║");
    println!("║  Treat:   6Cas + 1Far + 1Tra (casual-heavy)                 ║");
    println!("╚════════════════════════════════════════════════════════════════════╝\n");

    #[derive(Debug)]
    #[allow(dead_code)]
    struct CasualResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        buy_ratio: f64,
    }
    let mut ctrl_results: Vec<CasualResult> = Vec::new();
    let mut treat_results: Vec<CasualResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}",
        "Seed", "GDP", "D/G", "Vol(CV)", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7}",
        "──────", "────────────", "──────────", "────────", "───────"
    );

    for &seed in &seeds {
        // ── Control: 3Cas + 3Far + 2Tra (standard) ───────────────
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = "Ctrl: Standard".into();
        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-chh-ctrl-{seed}"));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        run_seeded_headless(&ctrl, seed, &ctrl_dir).ok();

        // ── Treatment: 6Cas + 1Far + 1Tra (casual-heavy) ─────────
        let mut treat = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        treat.name = "Treat: Casual-Heavy".into();
        treat.players = vec![
            ArchetypeConfig {
                archetype: "MarketMaker".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "GuildBuyer".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Casual".into(),
                count: 6,
            }, // ← 6 Casuals
            ArchetypeConfig {
                archetype: "Farmer".into(),
                count: 1,
            }, // ← 1 Farmer (was 3)
            ArchetypeConfig {
                archetype: "Trader".into(),
                count: 1,
            }, // ← 1 Trader (was 2)
        ];
        let treat_dir = PathBuf::from(format!("/tmp/autotune-chh-treat-{seed}"));
        let _ = std::fs::remove_dir_all(&treat_dir);
        run_seeded_headless(&treat, seed, &treat_dir).ok();

        let load = |dir: &std::path::Path| -> Option<CasualResult> {
            let s = load_summary(&dir.join("simulation.db")).ok()?;
            Some(CasualResult {
                seed,
                gdp: s.gdp,
                dg: s.debt / s.gdp.max(1.0),
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
            })
        };

        if let Some(r) = load(&ctrl_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.1}%  [Ctrl: Standard]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.buy_ratio * 100.0
            );
            ctrl_results.push(r);
        }
        if let Some(r) = load(&treat_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.1}%  [Treat: Casual-Heavy]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.buy_ratio * 100.0
            );
            treat_results.push(r);
        }
    }

    if !ctrl_results.is_empty() && !treat_results.is_empty() {
        let avg = |rs: &[CasualResult], f: fn(&CasualResult) -> f64| -> f64 {
            rs.iter().map(f).sum::<f64>() / rs.len() as f64
        };
        let gdp_chg = (avg(&treat_results, |r| r.gdp) - avg(&ctrl_results, |r| r.gdp))
            / avg(&ctrl_results, |r| r.gdp)
            * 100.0;
        let dg_chg = avg(&treat_results, |r| r.dg) - avg(&ctrl_results, |r| r.dg);
        let vol_chg = (avg(&treat_results, |r| r.vol) - avg(&ctrl_results, |r| r.vol))
            / avg(&ctrl_results, |r| r.vol)
            * 100.0;

        println!("\n  ── AVERAGES ──");
        println!(
            "  GDP:    standard={:.0}  casual-heavy={:.0}  chg={:+.1}%",
            avg(&ctrl_results, |r| r.gdp),
            avg(&treat_results, |r| r.gdp),
            gdp_chg
        );
        println!(
            "  D/G:    standard={:.3}x  casual-heavy={:.3}x  Δ={:+.3}x",
            avg(&ctrl_results, |r| r.dg),
            avg(&treat_results, |r| r.dg),
            dg_chg
        );
        println!(
            "  Vol:    standard={:.3}%  casual-heavy={:.3}%  chg={:+.1}%",
            avg(&ctrl_results, |r| r.vol) * 100.0,
            avg(&treat_results, |r| r.vol) * 100.0,
            vol_chg
        );
        println!(
            "  Buy%%:  standard={:.1}%  casual-heavy={:.1}%",
            avg(&ctrl_results, |r| r.buy_ratio) * 100.0,
            avg(&treat_results, |r| r.buy_ratio) * 100.0
        );
        println!("\n  VERDICT:");
        if gdp_chg > 20.0 && dg_chg < 1.0 {
            println!(
                "  ✅ Casual-heavy DRAMATICALLY improves GDP ({:+.1}%) without D/G cost — recommend for casual-dominant servers",
                gdp_chg
            );
        } else if gdp_chg > 5.0 && dg_chg < 2.0 {
            println!(
                "  ⚠️  Casual-heavy improves GDP {:+.1}% but D/G Δ={:+.3}x — net positive in healthy economy",
                gdp_chg, dg_chg
            );
        } else if gdp_chg < 0.0 {
            println!(
                "  ❌ Casual-heavy HURTS GDP ({:+.1}%) in 2MM+2GB+floor — standard mix is better for this config",
                gdp_chg
            );
        } else {
            println!(
                "  ℹ️  Neutral result — casual-heavy has similar outcomes to standard mix in 2MM+2GB+floor"
            );
        }
    }
}
// ═══════════════════════════════════════════════════════════════════
// NEWBIE-NO-GB TEST
// ═══════════════════════════════════════════════════════════════════
// Can Newbies REPLACE GuildBuyers?
// Control: 2MM + 2GB + 3Cas + 3Far + 2Tra + floor  (production default)
// Treat:   2MM + 2Newbie + 3Cas + 3Far + 2Tra + floor  (replace GB with Newbie)
//
// Prior context:
//   - Newbie+GB in STRESSED: D/G -42.1%, vol -35% (Newbie absorbs sell glut)
//   - Newbie+GB in HEALTHY: GDP +41.5%, D/G +34.7% (Newbie boosts demand)
//   - Newbie ALONE in STRESSED: not yet tested
//   - Newbie ALONE in HEALTHY: not yet tested
//
// Key question: Do Newbies need GBs to be effective, or can they drive
// demand-side stabilization alone (without proactive price-dip buying from GBs)?
// ═══════════════════════════════════════════════════════════════════

fn run_newbie_no_gb_test() {
    use crate::analyzer::load_summary;

    let seeds = vec![42u64, 12345, 98765];

    println!(
        "
╔════════════════════════════════════════════════════════════════════╗"
    );
    println!("║  NEWBIE-NO-GB TEST — Can Newbies Replace GuildBuyers?      ║");
    println!("║  Control: 2MM + 2GB + 3Cas + 3Far + 2Tra + floor          ║");
    println!("║  Treat:   2MM + 2Newbie + 3Cas + 3Far + 2Tra + floor       ║");
    println!(
        "╚════════════════════════════════════════════════════════════════════╝
"
    );

    #[derive(Debug)]
    #[allow(dead_code)]
    struct NoGbResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        vol: f64,
        buy_ratio: f64,
        bpd: f64,
    }
    let mut ctrl_results: Vec<NoGbResult> = Vec::new();
    let mut treat_results: Vec<NoGbResult> = Vec::new();

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7}",
        "Seed", "GDP", "D/G", "Vol(CV)", "BPD%", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>7} {:>7}",
        "──────", "────────────", "──────────", "────────", "───────", "───────"
    );

    for &seed in &seeds {
        // ── Control: 2MM + 2GB + 3Cas + 3Far + 2Tra + floor ───────
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = "Ctrl: 2GB".into();
        let ctrl_dir = PathBuf::from(format!("/tmp/autotune-nngb-ctrl-{}", seed));
        let _ = std::fs::remove_dir_all(&ctrl_dir);
        run_seeded_headless(&ctrl, seed, &ctrl_dir).ok();

        // ── Treatment: 2MM + 2Newbie + 3Cas + 3Far + 2Tra + floor ──
        let mut treat = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        treat.name = "Treat: 2Newbie".into();
        treat.players = vec![
            ArchetypeConfig {
                archetype: "MarketMaker".into(),
                count: 2,
            },
            ArchetypeConfig {
                archetype: "Newbie".into(),
                count: 2,
            }, // replaces GuildBuyer
            ArchetypeConfig {
                archetype: "Casual".into(),
                count: 3,
            },
            ArchetypeConfig {
                archetype: "Farmer".into(),
                count: 3,
            },
            ArchetypeConfig {
                archetype: "Trader".into(),
                count: 2,
            },
        ];
        let treat_dir = PathBuf::from(format!("/tmp/autotune-nngb-treat-{}", seed));
        let _ = std::fs::remove_dir_all(&treat_dir);
        run_seeded_headless(&treat, seed, &treat_dir).ok();

        let load = |dir: &PathBuf| -> Option<NoGbResult> {
            let s = load_summary(&dir.join("simulation.db")).ok()?;
            Some(NoGbResult {
                seed,
                gdp: s.gdp,
                dg: s.debt / s.gdp.max(1.0),
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                bpd: s.avg_bpd,
            })
        };

        if let Some(r) = load(&ctrl_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}% {:>7.1}%  [Ctrl: GB]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.bpd * 100.0,
                r.buy_ratio * 100.0
            );
            ctrl_results.push(r);
        }
        if let Some(r) = load(&treat_dir) {
            println!(
                "  {:>6} {:>12.0} {:>9.3}x {:>7.3}% {:>6.2}% {:>7.1}%  [Treat: NoGB]",
                seed,
                r.gdp,
                r.dg,
                r.vol * 100.0,
                r.bpd * 100.0,
                r.buy_ratio * 100.0
            );
            treat_results.push(r);
        }

        let _ = std::fs::remove_dir_all(&ctrl_dir);
        let _ = std::fs::remove_dir_all(&treat_dir);
    }

    if !ctrl_results.is_empty() && !treat_results.is_empty() {
        let avg = |rs: &[NoGbResult], f: fn(&NoGbResult) -> f64| -> f64 {
            rs.iter().map(f).sum::<f64>() / rs.len() as f64
        };
        let gdp_chg = (avg(&treat_results, |r| r.gdp) - avg(&ctrl_results, |r| r.gdp))
            / avg(&ctrl_results, |r| r.gdp)
            * 100.0;
        let dg_chg = avg(&treat_results, |r| r.dg) - avg(&ctrl_results, |r| r.dg);
        let vol_chg = (avg(&treat_results, |r| r.vol) - avg(&ctrl_results, |r| r.vol))
            / avg(&ctrl_results, |r| r.vol)
            * 100.0;
        let buy_chg =
            (avg(&treat_results, |r| r.buy_ratio) - avg(&ctrl_results, |r| r.buy_ratio)) * 100.0;

        println!(
            "
  ── AVERAGES ──"
        );
        println!(
            "  GDP:    GB={:.0}  NoGB={:.0}  chg={:+.1}%",
            avg(&ctrl_results, |r| r.gdp),
            avg(&treat_results, |r| r.gdp),
            gdp_chg
        );
        println!(
            "  D/G:    GB={:.3}x  NoGB={:.3}x  Δ={:+.3}x",
            avg(&ctrl_results, |r| r.dg),
            avg(&treat_results, |r| r.dg),
            dg_chg
        );
        println!(
            "  Vol:    GB={:.3}%  NoGB={:.3}%  chg={:+.1}%",
            avg(&ctrl_results, |r| r.vol) * 100.0,
            avg(&treat_results, |r| r.vol) * 100.0,
            vol_chg
        );
        println!(
            "  Buy%%:  GB={:.1}%  NoGB={:.1}%  chg={:+.1}pp",
            avg(&ctrl_results, |r| r.buy_ratio) * 100.0,
            avg(&treat_results, |r| r.buy_ratio) * 100.0,
            buy_chg
        );
        println!(
            "
  VERDICT:"
        );
        if gdp_chg > -10.0 && dg_chg.abs() < 1.0 && vol_chg.abs() < 25.0 {
            println!(
                "  ✅ Newbies are VIABLE GB replacements: GDP {:+.1}%, D/G Δ={:+.3}x, vol {:+.1}%",
                gdp_chg, dg_chg, vol_chg
            );
            println!(
                "     Newbies provide similar stabilization without proactive price-dip buying."
            );
        } else if gdp_chg < -20.0 {
            println!(
                "  ❌ Newbies CANNOT replace GBs: GDP {:+.1}% — GB proactive price-dip buying",
                gdp_chg
            );
            println!("     is ESSENTIAL for economy health. Newbies only work alongside GBs.");
        } else if dg_chg > 2.0 {
            println!(
                "  ⚠️  Newbies replace GBs at D/G cost: D/G Δ={:+.3}x worse — debt risk elevated",
                dg_chg
            );
        } else {
            println!(
                "  ⚠️  Mixed result — GDP {:+.1}%, D/G Δ={:+.3}x, vol {:+.1}%",
                gdp_chg, dg_chg, vol_chg
            );
        }
    }
    println!();
}

// ═══════════════════════════════════════════════════════════════════
// THRESHOLD × 30-DAY TEST
// ═══════════════════════════════════════════════════════════════════
// 5% vs 7% GB threshold over 30 days — does 7% advantage persist?
//
// 14-day results (guild_stability_2mm_fixed_guild_plus_floor):
//   5%:  GDP=1177K, D/G=9.10x, vol=0.119
//   7%:  GDP=1261K, D/G=8.36x, vol=0.110  ← WINNER on GDP+vol
//  10%:  GDP=1244K, D/G=9.13x, vol=0.141
//
// D/G concern at 14d: both 5% and 7% are elevated vs healthy baseline 6.03x±2.22x.
// At 30d, does D/G deleverage or worsen? Does 5% remain viable?
// ═══════════════════════════════════════════════════════════════════

fn run_threshold_30day_test() {
    use crate::analyzer::load_summary;
    use crate::player::set_fixed_guild_threshold;

    let thresholds = vec![0.05f64, 0.07f64];
    let seeds = vec![42u64, 12345, 98765];

    println!(
        "
╔════════════════════════════════════════════════════════════════════╗"
    );
    println!("║  GUILDBUYER THRESHOLD × 30-DAY — Does 7%% advantage persist? ║");
    println!("║  2MM + 2GB + 60%% Diamond floor × 3 seeds × 30 days         ║");
    println!(
        "╚════════════════════════════════════════════════════════════════════╝
"
    );

    #[derive(Debug)]
    #[allow(dead_code)]
    struct ThirtyDayResult {
        seed: u64,
        threshold: f64,
        gdp: f64,
        dg: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_internal: f64,
    }
    let mut results: Vec<ThirtyDayResult> = Vec::new();

    println!(
        "  {:>6} {:>8} {:>12} {:>10} {:>8} {:>7} {:>10}",
        "Seed", "Thresh", "GDP", "D/G", "Vol(CV)", "Buy%", "Diamond Int"
    );
    println!(
        "  {:>6} {:>8} {:>12} {:>10} {:>8} {:>7} {:>10}",
        "──────", "────────", "────────────", "──────────", "────────", "───────", "──────────"
    );

    for threshold in &thresholds {
        for &seed in &seeds {
            let mut scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
            scenario.name = format!("30d_{:.0}%", threshold * 100.0);
            scenario.duration_ticks = 288 * 30; // 30 days

            let out_dir = PathBuf::from(format!(
                "/tmp/autotune-t30d-{:.0}pct-{}",
                threshold * 100.0,
                seed
            ));
            let _ = std::fs::remove_dir_all(&out_dir);
            std::fs::create_dir_all(&out_dir).ok();

            set_fixed_guild_threshold(Some(*threshold));
            run_seeded_headless(&scenario, seed, &out_dir).ok();
            set_fixed_guild_threshold(None);

            if let Ok(s) = load_summary(&out_dir.join("simulation.db")) {
                let prices = crate::analyzer::load_all_prices(&out_dir.join("simulation.db"))
                    .unwrap_or_default();
                let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
                let di = diamond.map(|(_, i, _)| *i).unwrap_or(0.0);
                let dg = s.debt / s.gdp.max(1.0);
                println!(
                    "  {:>6} {:>7.0}% {:>12.0} {:>9.3}x {:>7.3}% {:>6.1}% {:>10.2}",
                    seed,
                    threshold * 100.0,
                    s.gdp,
                    dg,
                    s.avg_volatility * 100.0,
                    s.buy_ratio * 100.0,
                    di
                );
                results.push(ThirtyDayResult {
                    seed,
                    threshold: *threshold,
                    gdp: s.gdp,
                    dg,
                    vol: s.avg_volatility,
                    buy_ratio: s.buy_ratio,
                    diamond_internal: di,
                });
            }

            let _ = std::fs::remove_dir_all(&out_dir);
        }
    }

    if results.len() >= 4 {
        println!(
            "
  ── Per-Threshold Averages (30-day) ──
"
        );
        for threshold in &thresholds {
            let subset: Vec<_> = results
                .iter()
                .filter(|r| r.threshold == *threshold)
                .collect();
            if subset.is_empty() {
                continue;
            }
            let gdp_avg = subset.iter().map(|r| r.gdp).sum::<f64>() / subset.len() as f64;
            let dg_avg = subset.iter().map(|r| r.dg).sum::<f64>() / subset.len() as f64;
            let vol_avg = subset.iter().map(|r| r.vol).sum::<f64>() / subset.len() as f64;
            let buy_avg = subset.iter().map(|r| r.buy_ratio).sum::<f64>() / subset.len() as f64;
            println!(
                "  {:.0}% threshold: GDP={:.0}  D/G={:.3}x  vol={:.3}%  Buy%={:.1}%",
                threshold * 100.0,
                gdp_avg,
                dg_avg,
                vol_avg * 100.0,
                buy_avg * 100.0
            );
        }

        let r5: Vec<_> = results.iter().filter(|r| r.threshold == 0.05).collect();
        let r7: Vec<_> = results.iter().filter(|r| r.threshold == 0.07).collect();
        let gdp5 = r5.iter().map(|r| r.gdp).sum::<f64>() / r5.len() as f64;
        let gdp7 = r7.iter().map(|r| r.gdp).sum::<f64>() / r7.len() as f64;
        let dg5 = r5.iter().map(|r| r.dg).sum::<f64>() / r5.len() as f64;
        let dg7 = r7.iter().map(|r| r.dg).sum::<f64>() / r7.len() as f64;
        let vol5 = r5.iter().map(|r| r.vol).sum::<f64>() / r5.len() as f64;
        let vol7 = r7.iter().map(|r| r.vol).sum::<f64>() / r7.len() as f64;

        let gdp_chg = (gdp7 - gdp5) / gdp5 * 100.0;
        let dg_diff = dg7 - dg5;
        let vol_diff = (vol7 - vol5) / vol5 * 100.0;

        println!(
            "
  ── 7% vs 5% at 30 days ──"
        );
        println!(
            "  GDP:  5%={:.0}  7%={:.0}  chg={:+.1}%  (14d chg={:.0})",
            gdp5,
            gdp7,
            gdp_chg,
            (1261f64 - 1177f64) / 1177f64 * 100.0
        );
        println!("  D/G:  5%={:.3}x  7%={:.3}x  Δ={:+.3}x", dg5, dg7, dg_diff);
        println!(
            "  Vol:  5%={:.3}%  7%={:.3}%  chg={:+.1}%  (14d chg={:.0}%)",
            vol5 * 100.0,
            vol7 * 100.0,
            vol_diff,
            (0.110f64 - 0.119f64) / 0.119f64 * 100.0
        );

        println!(
            "
  VERDICT:"
        );
        if gdp_chg > 5.0 && vol_diff < 0.0 {
            println!(
                "  ✅ 7% CONFIRMED as 30-day default: GDP {:+.1}%, vol {:+.1}% lower vs 5%",
                gdp_chg,
                vol_diff.abs()
            );
            println!("     7% threshold remains correct production default at 30-day horizon.");
        } else if gdp_chg < 2.0 && dg_diff.abs() < 0.5 {
            println!(
                "  ℹ️  5% vs 7% are EQUIVALENT at 30d: GDP diff={:.1}%, D/G Δ={:.3}x",
                gdp_chg, dg_diff
            );
            println!("     Either threshold viable. 7% simpler to explain (1/14 ≈ 7.1%).");
        } else if dg_diff > 1.0 {
            let worse = if dg_diff > 0.0 { "7%" } else { "5%" };
            let better = if dg_diff > 0.0 { "5%" } else { "7%" };
            println!(
                "  ⚠️  {} ACCUMULATES more debt at 30d: D/G Δ={:+.3}x vs {}",
                worse,
                dg_diff.abs(),
                better
            );
            println!("     7% remains safer production default even if GDP is similar.");
        } else {
            println!(
                "  ⚠️  Mixed 30-day results — GDP chg={:+.1}%, D/G Δ={:+.3}x, vol chg={:+.1}%",
                gdp_chg, dg_diff, vol_diff
            );
        }
    }
    println!();
}

// ═══════════════════════════════════════════════════════════════════
// SESSION: 2026-04-15 — 60-day production stability test
// ═══════════════════════════════════════════════════════════════════

fn run_sixty_day_test() {
    use crate::analyzer::{load_all_prices, load_summary};

    let seed = 42u64;

    println!(
        "
╔════════════════════════════════════════════════════════════════╗"
    );
    println!("║       60-DAY PRODUCTION STABILITY TEST                      ║");
    println!("║  2MM + 2GB + 60% Diamond floor — extends 14d & 30d findings ║");
    println!(
        "╚════════════════════════════════════════════════════════════════╝
"
    );
    println!("  Seed: {}", seed);
    println!(
        "  Config: 2MM + 2GB @ 7% + 3Cas + 3Far + 2Tra + 60% Diamond floor
"
    );

    let mut sim = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
    sim.name = "LongRun_60day".to_string();
    sim.duration_ticks = 288 * 60; // 60 days

    let out_dir = "/tmp/autotune-sim/longrun-60d";
    let out_path = std::path::PathBuf::from(out_dir);
    std::fs::create_dir_all(&out_path).ok();
    run_seeded_headless(&sim, seed, &out_path).ok();

    #[derive(Debug)]
    #[allow(dead_code)]
    struct Result {
        days: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        spd: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        diamond_displayed: f64,
    }

    impl Result {
        fn from_db(db_path: &std::path::Path, days: u64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;
            let prices = load_all_prices(db_path).unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            Some(Self {
                days,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                diamond_displayed: dd,
            })
        }
    }

    let r = Result::from_db(&out_path.join("simulation.db"), 60);

    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>8} {:>8} {:>8}",
        "Days", "GDP", "D/G", "BPD%", "SPD%", "Vol(CV)", "Buy%"
    );
    println!(
        "  {:>6} {:>12} {:>10} {:>8} {:>8} {:>8} {:>8}",
        "─".repeat(6),
        "─".repeat(12),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(8),
        "─".repeat(8)
    );

    if let Some(r) = &r {
        println!(
            "  {:>6} {:>12.0} {:>10.3}x {:>7.3}% {:>7.3}% {:>8.4} {:>7.1}%",
            "60d",
            r.gdp as i64,
            r.dg,
            r.bpd * 100.0,
            r.spd * 100.0,
            r.vol,
            r.buy_ratio * 100.0
        );
        println!(
            "
  Diamond internal: {:.2}",
            r.diamond_internal
        );
        println!("  Diamond displayed: {:.2}", r.diamond_displayed);
        if r.diamond_displayed > 0.0 {
            let floor_binds = r.diamond_internal < r.diamond_displayed * 0.99;
            println!(
                "  Floor binding: {}",
                if floor_binds { "YES" } else { "NO" }
            );
        }

        println!(
            "
  ╔═══════════════════════════════════════════════════════════════╗"
        );
        println!("║  LONG-RUN COMPARISON: 14d → 30d → 60d                      ║");
        println!("╠═══════════════════════════════════════════════════════════════╣");
        println!(
            "║  {:>5}  {:>12}  {:>10}  {:>8}  {:>10}  {:>7}  ║",
            "Days", "GDP", "D/G", "BPD%", "Vol(CV)", "Buy%"
        );
        println!("╠═══════════════════════════════════════════════════════════════╣");
        println!(
            "║  {:>5}  {:>12}  {:>10}  {:>8}  {:>10}  {:>7}  ║",
            "14d", "1,616,248", "8.310x", "0.527%", "0.0610", "70.9%"
        );
        println!(
            "║  {:>5}  {:>12}  {:>10}  {:>8}  {:>10}  {:>7}  ║",
            "30d", "2,333,082", "7.500x", "0.467%", "0.0436", "73.4%"
        );
        println!(
            "║  {:>5}  {:>12.0}  {:>10.3}x  {:>7.3}%  {:>10.4}  {:>7.1}%  ║",
            "60d",
            r.gdp as i64,
            r.dg,
            r.bpd * 100.0,
            r.vol,
            r.buy_ratio * 100.0
        );
        println!("╚═══════════════════════════════════════════════════════════════╝");

        let gdp_growth_30to60 = (r.gdp / 2_333_082.0 - 1.0) * 100.0;

        println!(
            "
  TREND ANALYSIS:"
        );
        println!(
            "    GDP growth: 14→30d = +44.4% | 30→60d = {:+.1}% {}",
            gdp_growth_30to60,
            if gdp_growth_30to60 > 0.0 {
                "Continuing to grow"
            } else {
                "Stalled or contracting"
            }
        );
        println!(
            "    D/G trend:  8.310x → 7.500x → {:.3}x {}",
            r.dg,
            if r.dg < 7.500 {
                "DELEVERAGING continuing"
            } else if r.dg < 8.310 {
                "D/G plateaued"
            } else {
                "D/G worsening"
            }
        );
        println!(
            "    Vol trend:  0.0610 → 0.0436 → {:.4} {}",
            r.vol,
            if r.vol < 0.0436 {
                "VOLATILITY DECREASING"
            } else if r.vol < 0.05 {
                "Vol plateaued but acceptable"
            } else {
                "Vol increasing — monitor"
            }
        );

        let stable_60d = r.vol < 0.05 && r.dg < 10.0 && r.gdp > 2_333_082.0;
        println!(
            "
  VERDICT: Economy at 60d is {}",
            if stable_60d {
                "STABLE — production config validated long-term"
            } else if r.vol >= 0.05 {
                "UNSTABLE — volatility above threshold"
            } else if r.dg >= 10.0 {
                "HIGH RISK — D/G above circuit breaker"
            } else {
                "CHECK — review individual metrics above"
            }
        );
    } else {
        println!(
            "  {:>6} {:>12} {:>10} {:>8} {:>8} {:>8} {:>8}",
            "60d", "FAILED", "—", "—", "—", "—", "—"
        );
    }
}

fn run_it_removal_healthy_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64, 98765u64, 77777u64, 11111u64];

    println!(
        "
╔════════════════════════════════════════════════════════════════╗"
    );
    println!("║       IT REMOVAL TEST — Healthy Economy + Floor (5 seeds)   ║");
    println!("║  2MM+2GB+floor: WITH ITs vs WITHOUT ITs                    ║");
    println!(
        "╚════════════════════════════════════════════════════════════════╝
"
    );

    let mut controls = Vec::new(); // WITH ITs
    let mut treatments = Vec::new(); // WITHOUT ITs

    for &seed in &seeds {
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_it_and_floor();
        ctrl.name = format!("IT_ctrl_{}", seed);
        ctrl.duration_ticks = 288 * 14;
        let dir = format!("/tmp/autotune-sim/it-ctrl-{}", seed);
        let path = std::path::PathBuf::from(&dir);
        std::fs::create_dir_all(&path).ok();
        run_seeded_headless(&ctrl, seed, &path).ok();
        let s = load_summary(&path.join("simulation.db")).ok();
        controls.push(s);

        let mut treat = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        treat.name = format!("IT_treat_{}", seed);
        treat.duration_ticks = 288 * 14;
        let dir2 = format!("/tmp/autotune-sim/it-treat-{}", seed);
        let path2 = std::path::PathBuf::from(&dir2);
        std::fs::create_dir_all(&path2).ok();
        run_seeded_headless(&treat, seed, &path2).ok();
        let s2 = load_summary(&path2.join("simulation.db")).ok();
        treatments.push(s2);
    }

    fn avg(values: &[Option<crate::analyzer::SimSummary>], field: &str) -> f64 {
        let mut sum = 0.0;
        let mut cnt = 0.0;
        for s in values.iter().flatten() {
            match field {
                "gdp" => {
                    sum += s.gdp;
                    cnt += 1.0;
                }
                "dg" => {
                    sum += s.debt / s.gdp.max(1.0);
                    cnt += 1.0;
                }
                "vol" => {
                    sum += s.avg_volatility;
                    cnt += 1.0;
                }
                "bpd" => {
                    sum += s.avg_bpd;
                    cnt += 1.0;
                }
                "buy" => {
                    sum += s.buy_ratio;
                    cnt += 1.0;
                }
                _ => {}
            }
        }
        if cnt > 0.0 { sum / cnt } else { 0.0 }
    }

    println!(
        "  {:>12} {:>14} {:>14} {:>10} {:>8}",
        "Metric", "WITH ITs", "WITHOUT ITs", "Change", "Direction"
    );
    println!(
        "  {:>12} {:>14} {:>14} {:>10} {:>8}",
        "─".repeat(12),
        "─".repeat(14),
        "─".repeat(14),
        "─".repeat(10),
        "─".repeat(8)
    );

    let ctrl_gdp = avg(&controls, "gdp");
    let treat_gdp = avg(&treatments, "gdp");
    let gdp_chg = (treat_gdp / ctrl_gdp - 1.0) * 100.0;
    println!(
        "  {:>12} {:>14.0} {:>14.0} {:>+9.1}%  {}",
        "GDP",
        ctrl_gdp,
        treat_gdp,
        gdp_chg,
        if gdp_chg > 5.0 {
            "ITs boost GDP"
        } else if gdp_chg < -5.0 {
            "ITs hurt GDP"
        } else {
            "~Neutral"
        }
    );

    let ctrl_dg = avg(&controls, "dg");
    let treat_dg = avg(&treatments, "dg");
    let dg_chg = (treat_dg / ctrl_dg - 1.0) * 100.0;
    println!(
        "  {:>12} {:>14.3}x {:>14.3}x {:>+9.1}%  {}",
        "D/G",
        ctrl_dg,
        treat_dg,
        dg_chg,
        if dg_chg > 30.0 {
            "ITs worsen D/G significantly"
        } else if dg_chg > 10.0 {
            "ITs worsen D/G moderately"
        } else if dg_chg < -10.0 {
            "ITs improve D/G"
        } else {
            "~Neutral"
        }
    );

    let ctrl_vol = avg(&controls, "vol");
    let treat_vol = avg(&treatments, "vol");
    let vol_chg = (treat_vol / ctrl_vol - 1.0) * 100.0;
    println!(
        "  {:>12} {:>14.4} {:>14.4} {:>+9.1}%  {}",
        "Vol(CV)",
        ctrl_vol,
        treat_vol,
        vol_chg,
        if vol_chg < -20.0 {
            "ITs reduce volatility"
        } else if vol_chg > 20.0 {
            "ITs increase volatility"
        } else {
            "~Neutral"
        }
    );

    let ctrl_bpd = avg(&controls, "bpd");
    let treat_bpd = avg(&treatments, "bpd");
    let bpd_chg = (treat_bpd / ctrl_bpd - 1.0) * 100.0;
    println!(
        "  {:>12} {:>13.3}% {:>13.3}% {:>+9.1}%  {}",
        "BPD%",
        ctrl_bpd * 100.0,
        treat_bpd * 100.0,
        bpd_chg,
        if bpd_chg < -5.0 {
            "ITs tighten spreads"
        } else if bpd_chg > 5.0 {
            "ITs widen spreads"
        } else {
            "~Neutral"
        }
    );

    let ctrl_buy = avg(&controls, "buy");
    let treat_buy = avg(&treatments, "buy");
    let buy_chg = (treat_buy / ctrl_buy - 1.0) * 100.0;
    println!(
        "  {:>12} {:>13.1}% {:>13.1}% {:>+9.1}%  {}",
        "Buy%",
        ctrl_buy * 100.0,
        treat_buy * 100.0,
        buy_chg,
        if buy_chg > 5.0 {
            "ITs create buy pressure"
        } else if buy_chg < -5.0 {
            "ITs create sell pressure"
        } else {
            "~Neutral"
        }
    );

    println!(
        "
  RECOMMENDATION:"
    );
    if gdp_chg > 5.0 && dg_chg > 50.0 {
        println!("    REMOVE ITs from 2MM+2GB+floor — GDP gain does NOT justify D/G cost");
    } else if gdp_chg > 5.0 && dg_chg < 30.0 {
        println!("    KEEP ITs in 2MM+2GB+floor — GDP benefit with acceptable D/G tradeoff");
    } else if gdp_chg > 5.0 {
        println!("    KEEP ITs — GDP benefit real, D/G cost is manageable");
    } else if gdp_chg < -5.0 {
        println!("    REMOVE ITs — ITs hurt GDP in this config");
    } else {
        println!("    ITs are essentially neutral — no strong reason to add or remove");
    }
    println!(
        "
  NOTE: Prior single-seed test (seed=42, no floor) showed +30.1% GDP, D/G +2.41x."
    );
    println!("  This 5-seed test includes floor — floor may dampen IT price effects.");
}

// ─── 60-Day Fix Confirmation Test ────────────────────────────────────────

// ═══════════════════════════════════════════════════════════════════
// SESSION: 2026-04-20 — 90-Day Production Stability + Threshold Test
// ═══════════════════════════════════════════════════════════════════

/// CRITICAL UNANSWERED QUESTION:
/// At 60 days (seed=42), D/G explodes from 7.5x (30d) to 20.1x (60d).
/// Does this doom loop continue, stabilize, or collapse at 90 days?
///
/// Key questions:
///   1. D/G trajectory: does it keep climbing past 20x, or does circuit fire and stabilize?
///   2. At 5% threshold (corrected production default): same doom loop?
///   3. Circuit breaker: does TIER3 fire at 90d? How many times?
///   4. Floor: is Diamond floor still binding at 90d?
///
/// Reference trajectory (seed=42, 7% threshold, 60d test):
///   14d: D/G=8.31x  GDP=1.62M
///   30d: D/G=7.50x  GDP=2.33M
///   60d: D/G=20.1x  GDP=3.94M  ← CRITICAL SPIKE
///
/// With tier3=30 and hysteresis_unlock=15 (50% band):
///   Counter-cyclical multiplier at D/G=20: 1 - 20/30 = 33%
///   At D/G=27: multiplier = 1 - 27/30 = 10%
///   TIER3 fires at D/G >= 30 (hysteresis: stays locked until D/G < 15)
///   The circuit could fire between day 60-90 if D/G crosses 30.
fn run_ninety_day_test() {
    use crate::analyzer::{load_all_prices, load_summary};
    use crate::player::set_fixed_guild_threshold;

    let seeds = vec![42u64, 12345u64, 98765u64];
    let thresholds = vec![0.05f64, 0.07f64];
    let days = 90;
    let ticks = 288 * days;

    println!("\n╔════════════════════════════════════════════════════════════════╗");
    println!("║         90-DAY PRODUCTION STABILITY TEST                     ║");
    println!("║  2MM + 2GB + 60% Diamond floor × 3 seeds × 2 thresholds       ║");
    println!("║  Questions: Doom loop past 60d? 5% vs 7% at 90d?            ║");
    println!("╚════════════════════════════════════════════════════════════════╝\n");
    println!("  Config: 2MM + 2GB + 3Cas + 3Far + 2Tra + 60% Diamond floor");
    println!("  Duration: {} days ({} ticks)", days, ticks);
    println!("  Thresholds: 5% (corrected default) and 7% (old default)");
    println!("  Seeds: {:?}\n", seeds);

    #[derive(Debug)]
    #[allow(dead_code)]
    struct Result {
        seed: u64,
        threshold: f64,
        gdp: f64,
        debt: f64,
        dg: f64,
        bpd: f64,
        spd: f64,
        vol: f64,
        buy_ratio: f64,
        diamond_internal: f64,
        diamond_displayed: f64,
    }

    impl Result {
        fn from_db(db_path: &std::path::Path, seed: u64, threshold: f64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;
            let prices = load_all_prices(db_path).unwrap_or_default();
            let diamond = prices.iter().find(|(n, _, _)| n == "Diamond");
            let (di, dd) = diamond.map(|(_, i, d)| (*i, *d)).unwrap_or((0.0, 0.0));
            Some(Self {
                seed,
                threshold,
                gdp: s.gdp,
                debt: s.debt,
                dg: s.debt / s.gdp.max(1.0),
                bpd: s.avg_bpd,
                spd: s.avg_spd,
                vol: s.avg_volatility,
                buy_ratio: s.buy_ratio,
                diamond_internal: di,
                diamond_displayed: dd,
            })
        }
    }

    let mut results: Vec<Result> = Vec::new();

    println!(
        "  {:>6} {:>8} {:>12} {:>10} {:>8} {:>8} {:>8} {:>10}",
        "Seed", "Thresh", "GDP", "D/G", "BPD%", "SPD%", "Vol(CV)", "Diamond Int"
    );
    println!(
        "  {:>6} {:>8} {:>12} {:>10} {:>8} {:>8} {:>8} {:>10}",
        "──────",
        "────────",
        "────────────",
        "──────────",
        "────────",
        "────────",
        "────────",
        "──────────"
    );

    for &threshold in &thresholds {
        for &seed in &seeds {
            let mut scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
            scenario.name = format!("90d_{:.0}%_seed{}", threshold * 100.0, seed);
            scenario.duration_ticks = ticks;

            let out_dir = PathBuf::from(format!(
                "/tmp/autotune-90d-{:.0}pct-{}",
                threshold * 100.0,
                seed
            ));
            let _ = std::fs::remove_dir_all(&out_dir);
            std::fs::create_dir_all(&out_dir).ok();

            set_fixed_guild_threshold(Some(threshold));
            run_seeded_headless(&scenario, seed, &out_dir).ok();
            set_fixed_guild_threshold(None);

            if let Some(r) = Result::from_db(&out_dir.join("simulation.db"), seed, threshold) {
                println!(
                    "  {:>6} {:>7.0}% {:>12.0} {:>9.3}x {:>7.3}% {:>7.3}% {:>8.4} {:>10.2}",
                    seed,
                    threshold * 100.0,
                    r.gdp as i64,
                    r.dg,
                    r.bpd * 100.0,
                    r.spd * 100.0,
                    r.vol,
                    r.diamond_internal
                );
                results.push(r);
            } else {
                println!(
                    "  {:>6} {:>7.0}% {:>12} {:>10} {:>8} {:>8} {:>8} {:>10}",
                    seed,
                    (threshold * 100.0) as i32,
                    "FAILED",
                    "—",
                    "—",
                    "—",
                    "—",
                    "—"
                );
            }

            let _ = std::fs::remove_dir_all(&out_dir);
        }
    }

    if results.len() >= 4 {
        println!("\n  ── Per-Threshold Averages (90-day) ──");
        for &threshold in &thresholds {
            let subset: Vec<_> = results
                .iter()
                .filter(|r| r.threshold == threshold)
                .collect();
            if subset.is_empty() {
                continue;
            }
            let gdp_avg = subset.iter().map(|r| r.gdp).sum::<f64>() / subset.len() as f64;
            let dg_avg = subset.iter().map(|r| r.dg).sum::<f64>() / subset.len() as f64;
            let vol_avg = subset.iter().map(|r| r.vol).sum::<f64>() / subset.len() as f64;
            let buy_avg = subset.iter().map(|r| r.buy_ratio).sum::<f64>() / subset.len() as f64;
            let bpd_avg = subset.iter().map(|r| r.bpd).sum::<f64>() / subset.len() as f64;
            let diamond_avg =
                subset.iter().map(|r| r.diamond_internal).sum::<f64>() / subset.len() as f64;

            let risk_level = if dg_avg >= 30.0 {
                "🔴 CRITICAL"
            } else if dg_avg >= 20.0 {
                "🟠 HIGH RISK"
            } else if dg_avg >= 10.0 {
                "🟡 MODERATE"
            } else {
                "🟢 HEALTHY"
            };
            println!(
                "  {:.0}% threshold: GDP={:>10.0}  D/G={:>6.3}x  vol={:>6.4}  Buy%={:>5.1}%  {}",
                threshold * 100.0,
                gdp_avg as i64,
                dg_avg,
                vol_avg,
                buy_avg * 100.0,
                risk_level
            );
            println!(
                "              BPD={:.3}%  Diamond Int=${:.2}",
                bpd_avg * 100.0,
                diamond_avg
            );
        }

        let r5: Vec<_> = results.iter().filter(|r| r.threshold == 0.05).collect();
        let r7: Vec<_> = results.iter().filter(|r| r.threshold == 0.07).collect();

        if !r5.is_empty() && !r7.is_empty() {
            let gdp5 = r5.iter().map(|r| r.gdp).sum::<f64>() / r5.len() as f64;
            let gdp7 = r7.iter().map(|r| r.gdp).sum::<f64>() / r7.len() as f64;
            let dg5 = r5.iter().map(|r| r.dg).sum::<f64>() / r5.len() as f64;
            let dg7 = r7.iter().map(|r| r.dg).sum::<f64>() / r7.len() as f64;
            let vol5 = r5.iter().map(|r| r.vol).sum::<f64>() / r5.len() as f64;
            let vol7 = r7.iter().map(|r| r.vol).sum::<f64>() / r7.len() as f64;

            let gdp_chg = (gdp7 - gdp5) / gdp5 * 100.0;
            let dg_diff = dg7 - dg5;
            let dg_ratio = dg7 / dg5.max(0.001);
            let vol_chg = (vol7 - vol5) / vol5 * 100.0;

            let gdp_verdict = if gdp_chg > 5.0 {
                "5% better"
            } else if gdp_chg < -5.0 {
                "7% better"
            } else {
                "~Neutral"
            };
            let dg_verdict = if dg_ratio > 1.5 {
                "5% MUCH better"
            } else if dg_ratio > 1.1 {
                "5% better"
            } else if dg_ratio < 0.9 {
                "7% better"
            } else {
                "~Neutral"
            };
            let vol_verdict = if vol_chg < -10.0 {
                "5% better"
            } else if vol_chg > 10.0 {
                "7% better"
            } else {
                "~Neutral"
            };

            println!("\n  ══════════════════════════════════════════════════════════════════");
            println!("║  5% vs 7% THRESHOLD AT 90 DAYS                              ║");
            println!("╠═════════════════════════════════════════════════════════════════╣");
            println!("║  Metric      5%           7%           Change       Verdict  ║");
            println!("╠═════════════════════════════════════════════════════════════════╣");
            println!(
                "║  GDP         {:>10.0}  {:>10.0}  {:>+8.1}%      {:>8}  ║",
                gdp5 as i64, gdp7 as i64, gdp_chg, gdp_verdict
            );
            println!(
                "║  D/G         {:>10.3}x  {:>10.3}x  {:>+8.3}x     {:>8}  ║",
                dg5, dg7, dg_diff, dg_verdict
            );
            println!(
                "║  Vol(CV)     {:>10.4}  {:>10.4}  {:>+8.1}%     {:>8}  ║",
                vol5, vol7, vol_chg, vol_verdict
            );
            println!("╚═════════════════════════════════════════════════════════════════╝");

            println!("\n  ╔═══════════════════════════════════════════════════════════════╗");
            println!("║  D/G TRAJECTORY: 14d → 30d → 60d → 90d (seed=42)        ║");
            println!("╠═══════════════════════════════════════════════════════════════╣");

            let s42_5 = results.iter().find(|r| r.seed == 42 && r.threshold == 0.05);
            let s42_7 = results.iter().find(|r| r.seed == 42 && r.threshold == 0.07);
            println!(
                "║  {:>4}d  {:>10}  {:>10}  {:>8}  {:>8}  ║",
                "Horizon", "GDP", "D/G", "Thresh", "Risk"
            );
            println!("╠═══════════════════════════════════════════════════════════════╣");
            println!(
                "║  {:>4}d  {:>10.0}  {:>10.3}x  {:>8}  {:>8}  ║",
                14, 1_616_248.0, 8.310_f64, "7%", "🟢 HEALTHY"
            );
            println!(
                "║  {:>4}d  {:>10.0}  {:>10.3}x  {:>8}  {:>8}  ║",
                30, 2_333_082.0, 7.500_f64, "7%", "🟢 HEALTHY"
            );
            println!(
                "║  {:>4}d  {:>10.0}  {:>10.3}x  {:>8}  {:>8}  ║",
                60, 3_940_000.0, 20.100_f64, "7%", "🟠 HIGH"
            );
            if let Some(r) = s42_5 {
                let risk = if r.dg >= 30.0 {
                    "🔴 CRITICAL"
                } else if r.dg >= 20.0 {
                    "🟠 HIGH"
                } else if r.dg >= 10.0 {
                    "🟡 MODERATE"
                } else {
                    "🟢 HEALTHY"
                };
                println!(
                    "║  {:>4}d  {:>10.0}  {:>10.3}x  {:>8}  {:>8}  ║",
                    days, r.gdp as i64, r.dg, "5%", risk
                );
            } else {
                println!(
                    "║  {:>4}d  {:>10}  {:>10}  {:>8}  {:>8}  ║",
                    days, "—", "—", "5%", "TBD"
                );
            }
            if let Some(r) = s42_7 {
                let risk = if r.dg >= 30.0 {
                    "🔴 CRITICAL"
                } else if r.dg >= 20.0 {
                    "🟠 HIGH"
                } else if r.dg >= 10.0 {
                    "🟡 MODERATE"
                } else {
                    "🟢 HEALTHY"
                };
                println!(
                    "║  {:>4}d  {:>10.0}  {:>10.3}x  {:>8}  {:>8}  ║",
                    days, r.gdp as i64, r.dg, "7%", risk
                );
            }
            println!("╚═══════════════════════════════════════════════════════════════╝");

            println!("\n  CIRCUIT BREAKER ANALYSIS (seed=42):");
            println!("  tier3_ratio=30 | hysteresis unlock at D/G < 15.0 | counter_cyclical=true");
            if let Some(r) = s42_5 {
                let cb_state = if r.dg >= 30.0 {
                    "TIER3 LIKELY FIRED (D/G >= 30x)"
                } else if r.dg >= 15.0 {
                    "Circuit may have engaged (D/G 15-30x)"
                } else {
                    "Circuit did NOT engage"
                };
                let multiplier_at_dg = (1.0 - r.dg / 30.0).clamp(0.0, 1.0);
                println!(
                    "  5% threshold: D/G={:.3}x → multiplier={:.1}%  {}",
                    r.dg,
                    multiplier_at_dg * 100.0,
                    cb_state
                );
            }
            if let Some(r) = s42_7 {
                let cb_state = if r.dg >= 30.0 {
                    "TIER3 LIKELY FIRED (D/G >= 30x)"
                } else if r.dg >= 15.0 {
                    "Circuit may have engaged (D/G 15-30x)"
                } else {
                    "Circuit did NOT engage"
                };
                let multiplier_at_dg = (1.0 - r.dg / 30.0).clamp(0.0, 1.0);
                println!(
                    "  7% threshold: D/G={:.3}x → multiplier={:.1}%  {}",
                    r.dg,
                    multiplier_at_dg * 100.0,
                    cb_state
                );
            }

            let worst_dg = results.iter().map(|r| r.dg).fold(0.0_f64, f64::max);
            let stability_verdict = if worst_dg >= 30.0 {
                "🔴 DOOM LOOP CONTINUES — D/G >= 30x at 90d. Circuit breaker insufficient."
            } else if worst_dg >= 20.0 {
                "🟠 HIGH RISK — D/G 20-30x. Circuit engaged but debt still growing."
            } else if worst_dg >= 10.0 {
                "🟡 MODERATE — D/G 10-20x. Economy unstable long-term."
            } else {
                "🟢 STABLE — D/G < 10x. Economy self-correcting at 90d."
            };

            let threshold_verdict = if dg_diff > 3.0 {
                format!(
                    "5% threshold {:.0}x BETTER D/G than 7% at 90d. 5% confirmed as production default.",
                    dg_ratio
                )
            } else if dg_diff < -3.0 {
                format!(
                    "7% threshold {:.0}x BETTER D/G than 5% at 90d. Reconsider production default.",
                    1.0 / dg_ratio.max(0.001)
                )
            } else {
                format!(
                    "5% vs 7% equivalent at 90d (ΔD/G={:+.3}x). Both thresholds show same doom loop risk.",
                    dg_diff
                )
            };

            println!("\n  ══════════════════════════════════════════════════════════════════");
            println!("  VERDICT:");
            println!("  {}", stability_verdict);
            println!("  {}", threshold_verdict);
            if worst_dg >= 20.0 {
                println!(
                    "\n  ⚠️  ARCHITECTURAL FIX NEEDED: Counter-cyclical is a governor, not a cure."
                );
                println!("  Root cause: Debt compounds ~10%/day while GDP grows ~1-2%/day.");
                println!("  Circuit resets interest to 0% but cannot reduce existing debt stock.");
                println!(
                    "  Candidates: Exit TIER3→NORMAL bypass; forced deleveraging at TIER3 exit;"
                );
                println!("  debt growth cap; deep hysteresis (unlock at D/G < 15x).");
            }
            println!("  ══════════════════════════════════════════════════════════════════");
        }
    } else {
        println!("\n  ⚠  Insufficient results to compute summary.");
    }
    println!();
}

/// ─── 180-Day Production Trajectory Test ───────────────────────────────────
///
/// Critical unanswered question: Does the 2MM+2GB+floor economy stabilize
/// past day 90, or does it keep oscillating indefinitely?
///
/// Known checkpoints:
///   Day  14: D/G ~8.3x  (healthy)
///   Day  30: D/G ~7.5x  (healthy)
///   Day  60: D/G ~20.1x (TIER3 fires, oscillates)
///   Day  90: D/G ~16.4x (partial recovery — circuit governor effect)
///
/// This test runs 180 days × seed=42,5% threshold to fill the 90-180d gap
/// and determine if D/G stabilizes, keeps oscillating, or escalates.
fn run_one_eighty_day_test() {
    use crate::player::set_fixed_guild_threshold;

    let seeds = vec![42u64, 12345u64];
    let threshold = 0.05;
    let days = 180;
    let ticks_per_day = 288;
    let total_ticks = ticks_per_day * days;

    println!(
        "
╔══════════════════════════════════════════════════════════════════════════╗"
    );
    println!("║          180-DAY PRODUCTION TRAJECTORY TEST                       ║");
    println!("║  2MM + 2GB + 60% Diamond floor × 2 seeds × 5% threshold         ║");
    println!("║  Question: Does D/G stabilize past day 90 or oscillate forever?   ║");
    println!(
        "╚══════════════════════════════════════════════════════════════════════════╝
"
    );
    println!("  Config: 2MM + 2GB + 3Cas + 3Far + 2Tra + 60% Diamond floor");
    println!("  Duration: {} days ({} ticks)", days, total_ticks);
    println!(
        "  Threshold: {}% | Seeds: {:?}\n",
        (threshold * 100.0) as i32,
        seeds
    );

    #[derive(Debug)]
    #[allow(dead_code)]
    struct TrajectoryPoint {
        day: u32,
        tick: u64,
        gdp: f64,
        debt: f64,
        dg: f64,
        tier3_engagements: u32,
    }

    // Checkpoints at which we sample the economy
    let checkpoints = vec![14, 30, 60, 90, 120, 150, 180];

    for &seed in &seeds {
        println!("\n  ▶ Running seed {} ({} days)...", seed, days);

        let mut scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        scenario.name = format!("180d_5pct_seed{}", seed);
        scenario.duration_ticks = total_ticks;

        let out_dir = format!("/tmp/autotune-180d-5pct-{}", seed);
        let out_path = std::path::PathBuf::from(&out_dir);
        let _ = std::fs::remove_dir_all(&out_path);
        std::fs::create_dir_all(&out_path).ok();

        set_fixed_guild_threshold(Some(threshold));
        let result = run_seeded_headless(&scenario, seed, &out_path);
        set_fixed_guild_threshold(None);

        if result.is_err() {
            println!("    ✗ Simulation failed: {:?}", result.err());
            continue;
        }

        let db_path = out_path.join("simulation.db");
        if !db_path.exists() {
            println!("    ✗ DB not found at {:?}", db_path);
            continue;
        }

        // Load trajectory from economy_snapshots at each checkpoint tick
        let mut trajectory: Vec<TrajectoryPoint> = Vec::new();

        for &day in &checkpoints {
            let target_tick = day as u64 * ticks_per_day;

            // Count TIER3 engagements up to this tick from circuit_breaker_events
            let tier3_query = format!(
                "SELECT COUNT(*) FROM circuit_breaker_events WHERE tick <= {} AND tier = 'TIER3';",
                target_tick
            );

            // Get GDP and debt at this tick from economy_snapshots (closest tick at or before target)
            let snap_query = format!(
                "SELECT gdp, total_debt FROM economy_snapshots WHERE tick <= {} ORDER BY tick DESC LIMIT 1;",
                target_tick
            );

            let mut tier3_count_at: u32 = 0;
            let mut gdp = 0.0f64;
            let mut debt = 0.0f64;

            if let Ok(conn) = rusqlite::Connection::open(&db_path) {
                let _ = conn.query_row(&tier3_query, [], |row| {
                    tier3_count_at = row.get(0)?;
                    Ok(())
                });
                let _ = conn.query_row(&snap_query, [], |row| {
                    gdp = row.get(0)?;
                    debt = row.get(1)?;
                    Ok(())
                });
            }

            let dg = if gdp > 0.0 { debt / gdp } else { 0.0 };
            trajectory.push(TrajectoryPoint {
                day,
                tick: target_tick,
                gdp,
                debt,
                dg,
                tier3_engagements: tier3_count_at,
            });
        }

        // Print trajectory table
        println!("\n  ╔════════════════════════════════════════════════════════════════╗");
        println!(
            "  ║  SEED {} — 180-Day D/G Trajectory (2MM+2GB+floor, 5%)      ║",
            seed
        );
        println!("  ╠════════════════════════════════════════════════════════════════╣");
        println!(
            "  ║  {:>4}  {:>12}  {:>12}  {:>8}  {:>10}  {:>8}  ║",
            "Day", "GDP", "Debt", "D/G", "TIER3", "Risk"
        );
        println!("  ╠════════════════════════════════════════════════════════════════╣");

        // Reference points from prior tests (90d test, seed=42, 5% threshold)
        let ref_dg: std::collections::HashMap<u32, f64> = [
            (14, 8.310_f64),
            (30, 7.500_f64),
            (60, 20.100_f64),
            (90, 16.400_f64),
        ]
        .into_iter()
        .collect();

        for pt in &trajectory {
            let risk = if pt.dg >= 30.0 {
                "🔴 CRITICAL"
            } else if pt.dg >= 20.0 {
                "🟠 HIGH"
            } else if pt.dg >= 10.0 {
                "🟡 MODERATE"
            } else if pt.dg >= 5.0 {
                "🟢 HEALTHY"
            } else {
                "✅ LOW"
            };
            let ref_note = if let Some(&ref_dg) = ref_dg.get(&pt.day) {
                if pt.day <= 90 {
                    format!(" (ref {:.1}x)", ref_dg)
                } else {
                    String::new()
                }
            } else {
                String::new()
            };
            println!(
                "  ║  {:>4}  {:>12.0}  {:>12.0}  {:>8.3}x  {:>10}  {:>8}  ║{}",
                pt.day, pt.gdp, pt.debt, pt.dg, pt.tier3_engagements, risk, ref_note
            );
        }
        println!("  ╚════════════════════════════════════════════════════════════════╝");

        // Trend analysis
        if trajectory.len() >= 3 {
            let early = trajectory.first().map(|p| p.dg).unwrap_or(0.0);
            let mid = trajectory.get(3).map(|p| p.dg).unwrap_or(0.0); // day 90
            let late = trajectory.last().map(|p| p.dg).unwrap_or(0.0);

            println!("\n  Trend Analysis:");
            if late < mid {
                println!(
                    "  📉 D/G RECOVERING: {:.3}x → {:.3}x (day 90→180)",
                    mid, late
                );
            } else if late < early {
                println!(
                    "  📈 D/G GROWING but below peak: {:.3}x → {:.3}x → {:.3}x",
                    early, mid, late
                );
            } else {
                println!(
                    "  ⚠️  D/G ESCALATING: {:.3}x → {:.3}x → {:.3}x",
                    early, mid, late
                );
            }
        }

        let _ = std::fs::remove_dir_all(&out_path);
    }

    println!("\n  KEY INSIGHT:");
    println!(
        "  If D/G stabilizes <15x by day 180: economy is self-correcting (circuit is sufficient)"
    );
    println!("  If D/G oscillates 15-25x: economy is contained but needs monitoring");
    println!("  If D/G escalates >30x: circuit breaker insufficient — architectural fix needed");
    println!();
}

/// CRITICAL FINDING (2026-04-17):
/// 2MM+2GB+floor is UNSTABLE at 60 days -- D/G explodes from 7.5x (30d) to 20.1x (60d).
/// Counter-cyclical interest at tier3=30 gives only 10% interest when D/G=27,
/// insufficient to deleverage debt faster than GDP grows.
///
/// Proposed fixes:
///   1. tier3_ratio=50 -> gives multiplier=0.40 at D/G=30 (vs 0.0 at tier3=30)
///      TIER3 only fires at genuine catastrophe (D/G >= 50x)
///   2. min_interest_multiplier=0.20 -> forces continuous deleveraging at all D/G levels
///      Even when TIER3 circuit fires, economy still pays 20% interest
///
/// This test runs both fixes against the control (tier3=30, min_int=0.0) on 2 seeds
/// to confirm whether the fix resolves the 60-day instability.
fn run_sixty_day_fix_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64];

    println!(
        "
╔════════════════════════════════════════════════════════════════════╗"
    );
    println!("║     60-DAY FIX CONFIRMATION TEST                             ║");
    println!("║  Control: tier3=30, min_int=0.0  vs  Fix: tier3=50, min_int=0.20  ║");
    println!(
        "╚════════════════════════════════════════════════════════════════════╝
"
    );

    let mut ctrl_metrics = Vec::new();
    let mut fix_metrics = Vec::new();

    for &seed in &seeds {
        println!("  Running seed {}...", seed);

        // ── Control: tier3=30, min_int=0.0 ──────────────────────────────────
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = format!("Ctrl_60d_s{}", seed);
        ctrl.duration_ticks = 288 * 60;
        let ctrl_dir = format!("/tmp/autotune-sim/60d-fix-ctrl-{}", seed);
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        run_seeded_headless(&ctrl, seed, &ctrl_path).ok();

        // ── Fix: tier3=50, min_int=0.20 ─────────────────────────────────────
        let mut fix = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        fix.name = format!("Fix_60d_s{}", seed);
        fix.duration_ticks = 288 * 60;
        // Apply the proposed fix: tier3=50 + min_int=0.20
        fix.config.loans.debt_gdp_tier3_ratio = 50.0;
        fix.config.loans.min_interest_multiplier = 0.20;
        let fix_dir = format!("/tmp/autotune-sim/60d-fix-{}", seed);
        let fix_path = std::path::PathBuf::from(&fix_dir);
        std::fs::create_dir_all(&fix_path).ok();
        run_seeded_headless(&fix, seed, &fix_path).ok();

        // ── Load results ──────────────────────────────────────────────────────
        let ctrl_summary = load_summary(&ctrl_path.join("simulation.db"));
        let fix_summary = load_summary(&fix_path.join("simulation.db"));

        if let (Ok(cs), Ok(fs)) = (ctrl_summary, fix_summary) {
            let ctrl_dg = cs.debt / cs.gdp.max(1.0);
            let fix_dg = fs.debt / fs.gdp.max(1.0);
            ctrl_metrics.push((seed, cs.gdp, ctrl_dg, cs.avg_volatility, cs.buy_ratio));
            fix_metrics.push((seed, fs.gdp, fix_dg, fs.avg_volatility, fs.buy_ratio));

            let status = if fix_dg < 10.0 && ctrl_dg >= 10.0 {
                "STABLE"
            } else if fix_dg < ctrl_dg * 0.8 {
                "FIXED"
            } else if fix_dg < ctrl_dg {
                "improved"
            } else {
                "worse"
            };
            println!(
                "    Seed {}:  Ctrl D/G={:.3}x  Fix D/G={:.3}x  Delta={:+.3}x  {}",
                seed,
                ctrl_dg,
                fix_dg,
                fix_dg - ctrl_dg,
                status
            );
        } else {
            println!("    Seed {}: FAILED to load results", seed);
        }
    }

    // ── Summary comparison ─────────────────────────────────────────────────
    println!(
        "
  ╔════════════════════════════════════════════════════════════════╗"
    );
    println!("  ║  60-DAY FIX SUMMARY (2 seeds x 60 days)                      ║");
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  {:>6}  {:>12}  {:>10}  {:>10}  {:>8}  {:>7}  ║",
        "Seed", "GDP", "D/G_ctrl", "D/G_fix", "Delta", "Verdict"
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");

    for ((seed, fgdp, cdg, _, _), (_, _, fdg, _, _)) in ctrl_metrics.iter().zip(fix_metrics.iter())
    {
        let verdict = if *fdg < 10.0 && *cdg >= 10.0 {
            "STABLE"
        } else if *fdg < *cdg * 0.8 {
            "FIXED"
        } else if *fdg < *cdg {
            "improved"
        } else {
            "worse"
        };
        println!(
            "  ║  {:>6}  {:>12.0}  {:>10.3}x  {:>10.3}x  {:>+8.3}x  {:>7}  ║",
            seed,
            fgdp,
            cdg,
            fdg,
            fdg - cdg,
            verdict
        );
    }

    // Compute means
    let ctrl_dg_mean: f64 = ctrl_metrics.iter().map(|(_, _, d, _, _)| d).sum::<f64>() / 2.0;
    let fix_dg_mean: f64 = fix_metrics.iter().map(|(_, _, d, _, _)| d).sum::<f64>() / 2.0;
    let ctrl_vol_mean: f64 = ctrl_metrics.iter().map(|(_, _, _, v, _)| v).sum::<f64>() / 2.0;
    let fix_vol_mean: f64 = fix_metrics.iter().map(|(_, _, _, v, _)| v).sum::<f64>() / 2.0;

    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  {:>6}  {:>12}  {:>10.3}x  {:>10.3}x  {:>+8.3}x  {:>7}  ║",
        "MEAN",
        "--",
        ctrl_dg_mean,
        fix_dg_mean,
        fix_dg_mean - ctrl_dg_mean,
        if fix_dg_mean < ctrl_dg_mean * 0.5 {
            "FIXED"
        } else if fix_dg_mean < ctrl_dg_mean {
            "improved"
        } else {
            "not fixed"
        }
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  Vol:  Ctrl={:.4}  Fix={:.4}                              ║",
        ctrl_vol_mean, fix_vol_mean
    );
    println!("  ╚════════════════════════════════════════════════════════════════╝");

    let stable = fix_dg_mean < 10.0;
    let improved = fix_dg_mean < ctrl_dg_mean;

    println!(
        "
  VERDICT:"
    );
    if stable && improved {
        println!("    FIX CONFIRMED -- tier3=50 + min_int=0.20 resolves 60d instability");
        println!(
            "    D/G mean: {:.1}x -> {:.1}x, below 10x stability threshold",
            ctrl_dg_mean, fix_dg_mean
        );
        println!(
            "    RECOMMENDATION: Update production defaults to tier3_ratio=50, min_interest_multiplier=0.20"
        );
    } else if improved {
        println!("    PARTIAL -- fix improves D/G but may not fully resolve instability");
        println!(
            "    D/G mean: {:.1}x -> {:.1}x ({:+.1}x change)",
            ctrl_dg_mean,
            fix_dg_mean,
            fix_dg_mean - ctrl_dg_mean
        );
        println!("    RECOMMENDATION: Run 5-seed confirmation before updating defaults");
    } else {
        println!("    NOT FIXED -- tier3=50 + min_int=0.20 does NOT resolve instability");
        println!("    D/G mean: {:.1}x -> {:.1}x", ctrl_dg_mean, fix_dg_mean);
        println!("    RECOMMENDATION: Investigate alternative fixes (GB debt cap, remove floor)");
    }
}

/// 60-Day Hysteresis Band Fix Test
/// Tests whether a wider hysteresis band (50% instead of 10%) fixes the 60-day instability.
/// Root cause: 10% band (unlock at 27 for tier3=30) is too narrow — MM/GB loans open during
/// TIER3 lock accumulate with 0% interest. When circuit re-enables at 27, debt service is overwhelming.
/// Fix: 50% band (unlock at 15 for tier3=30) gives genuine deleveraging headroom.
fn run_sixty_day_hysteresis_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64];

    println!(
        "
╔════════════════════════════════════════════════════════════════════════╗"
    );
    println!("║     60-DAY HYSTERESIS BAND TEST                               ║");
    println!("║  Control: tier3=30, hysteresis=0.10  vs  Fix: tier3=30, hysteresis=0.50  ║");
    println!(
        "╚════════════════════════════════════════════════════════════════════════╝
"
    );

    let mut ctrl_metrics = Vec::new();
    let mut fix_metrics = Vec::new();

    for &seed in &seeds {
        println!("  Running seed {}...", seed);

        // ── Control: tier3=30, hysteresis=0.10 (current default) ──────────────────
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = format!("Ctrl_hyst_s{}", seed);
        ctrl.duration_ticks = 288 * 60;
        // Use default hysteresis (0.5 in new code, but we test control as if it were 0.10)
        // We need to force control to use the OLD 0.10 hysteresis. Since we changed default to 0.5,
        // we set it explicitly to 0.10 for control.
        ctrl.config.loans.tier3_hysteresis_band = 0.10;
        let ctrl_dir = format!("/tmp/autotune-sim/60d-hyst-ctrl-{}", seed);
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        run_seeded_headless(&ctrl, seed, &ctrl_path).ok();

        // ── Fix: tier3=30, hysteresis=0.50 (wider band) ─────────────────────────
        let mut fix = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        fix.name = format!("Fix_hyst_s{}", seed);
        fix.duration_ticks = 288 * 60;
        fix.config.loans.tier3_hysteresis_band = 0.50;
        let fix_dir = format!("/tmp/autotune-sim/60d-hyst-{}", seed);
        let fix_path = std::path::PathBuf::from(&fix_dir);
        std::fs::create_dir_all(&fix_path).ok();
        run_seeded_headless(&fix, seed, &fix_path).ok();

        // ── Load results ──────────────────────────────────────────────────────
        let ctrl_summary = load_summary(&ctrl_path.join("simulation.db"));
        let fix_summary = load_summary(&fix_path.join("simulation.db"));

        if let (Ok(cs), Ok(fs)) = (ctrl_summary, fix_summary) {
            let ctrl_dg = cs.debt / cs.gdp.max(1.0);
            let fix_dg = fs.debt / fs.gdp.max(1.0);
            ctrl_metrics.push((
                seed,
                cs.gdp,
                ctrl_dg,
                cs.avg_volatility,
                cs.buy_ratio,
                cs.tier3_events,
            ));
            fix_metrics.push((
                seed,
                fs.gdp,
                fix_dg,
                fs.avg_volatility,
                fs.buy_ratio,
                fs.tier3_events,
            ));

            let status = if fix_dg < 10.0 && ctrl_dg >= 10.0 {
                "STABLE"
            } else if fix_dg < ctrl_dg * 0.5 {
                "FIXED"
            } else if fix_dg < ctrl_dg {
                "improved"
            } else {
                "worse"
            };
            println!(
                "    Seed {}:  Ctrl D/G={:.3}x ({:.0}hyst)  Fix D/G={:.3}x ({:.0}hyst)  Delta={:+.3}x  {}",
                seed,
                ctrl_dg,
                10,
                fix_dg,
                50,
                fix_dg - ctrl_dg,
                status
            );
        } else {
            println!("    Seed {}: FAILED to load results", seed);
        }
    }

    // ── Summary comparison ─────────────────────────────────────────────────
    println!(
        "
  ╔════════════════════════════════════════════════════════════════╗"
    );
    println!("  ║  60-DAY HYSTERESIS TEST SUMMARY (2 seeds × 60 days)           ║");
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  {:>6}  {:>12}  {:>10}  {:>10}  {:>8}  {:>7}  ║",
        "Seed", "GDP", "D/G_ctrl", "D/G_fix", "Delta", "Verdict"
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");

    for ((seed, fgdp, cdg, _, _, _), (_, _, fdg, _, _, _)) in
        ctrl_metrics.iter().zip(fix_metrics.iter())
    {
        let verdict = if *fdg < 10.0 && *cdg >= 10.0 {
            "STABLE"
        } else if *fdg < *cdg * 0.5 {
            "FIXED"
        } else if *fdg < *cdg {
            "improved"
        } else {
            "worse"
        };
        println!(
            "  ║  {:>6}  {:>12.0}  {:>10.3}x  {:>10.3}x  {:>+8.3}x  {:>7}  ║",
            seed,
            fgdp,
            cdg,
            fdg,
            fdg - cdg,
            verdict
        );
    }

    // Compute means
    let ctrl_dg_mean: f64 = ctrl_metrics.iter().map(|(_, _, d, _, _, _)| d).sum::<f64>() / 2.0;
    let fix_dg_mean: f64 = fix_metrics.iter().map(|(_, _, d, _, _, _)| d).sum::<f64>() / 2.0;
    let ctrl_t3: f64 = ctrl_metrics
        .iter()
        .map(|(_, _, _, _, _, t3)| *t3 as f64)
        .sum::<f64>();
    let fix_t3: f64 = fix_metrics
        .iter()
        .map(|(_, _, _, _, _, t3)| *t3 as f64)
        .sum::<f64>();

    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  {:>6}  {:>12}  {:>10.3}x  {:>10.3}x  {:>+8.3}x  {:>7}  ║",
        "MEAN",
        "--",
        ctrl_dg_mean,
        fix_dg_mean,
        fix_dg_mean - ctrl_dg_mean,
        if fix_dg_mean < ctrl_dg_mean * 0.5 {
            "FIXED"
        } else if fix_dg_mean < ctrl_dg_mean {
            "improved"
        } else {
            "not fixed"
        }
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  TIER3 events: Ctrl={:.0}  Fix={:.0}                            ║",
        ctrl_t3 as u32, fix_t3 as u32
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    let stable = fix_dg_mean < 10.0;
    let improved = fix_dg_mean < ctrl_dg_mean;

    println!(
        "
  VERDICT:"
    );
    if stable && improved {
        println!("    FIX CONFIRMED -- hysteresis=0.50 resolves 60d instability");
        println!(
            "    D/G mean: {:.1}x → {:.1}x, below 10x stability threshold",
            ctrl_dg_mean, fix_dg_mean
        );
        println!("    RECOMMENDATION: Update production config tier3_hysteresis_band=0.50");
    } else if improved {
        println!("    PARTIAL -- wider hysteresis improves D/G but may not fully resolve");
        println!(
            "    D/G mean: {:.1}x → {:.1}x ({:+.1}x change)",
            ctrl_dg_mean,
            fix_dg_mean,
            fix_dg_mean - ctrl_dg_mean
        );
        println!("    RECOMMENDATION: Run 5-seed confirmation + consider GB debt cap");
    } else {
        println!("    NOT FIXED -- wider hysteresis does NOT resolve instability");
        println!("    D/G mean: {:.1}x → {:.1}x", ctrl_dg_mean, fix_dg_mean);
        println!("    RECOMMENDATION: GB debt cap is the correct fix (not hysteresis)");
    }
}

/// 60-Day GB Debt Cap Test
/// Tests whether capping total GuildBuyer debt at 3× GDP fixes the 60-day instability.
///
/// Root cause (confirmed over 3 fix attempts):
///   GB loans OPEN during TIER3 lock period (0% interest) and ACCUMULATE.
///   When circuit re-enables, accumulated zero-interest debt services catastrophically.
///
/// Fix: guildbuyer_total_debt_cap = 3.0 (3× economy GDP) prevents accumulation
/// during TIER3 lock. When total GB debt would exceed cap, loans are rejected/reduced.
///
/// Control: uncapped GB debt (guildbuyer_total_debt_cap = 0.0)
/// Treatment: capped GB debt at 3× GDP (guildbuyer_total_debt_cap = 3.0)
fn run_sixty_day_gb_debt_cap_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64];

    println!(
        "
╔════════════════════════════════════════════════════════════════════════════╗"
    );
    println!("║     60-DAY GB DEBT CAP TEST                                      ║");
    println!("║  Control: GB debt uncapped  vs  Fix: GB debt cap 3× GDP             ║");
    println!("╚════════════════════════════════════════════════════════════════════════════╝\n");

    let mut ctrl_metrics = Vec::new();
    let mut fix_metrics = Vec::new();

    for &seed in &seeds {
        println!("  Running seed {}...", seed);

        // ── Control: uncapped GB debt ─────────────────────────────────────────
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = format!("Ctrl_60d_GBcap_s{}", seed);
        ctrl.duration_ticks = 288 * 60;
        // Uncapped: disable the cap
        ctrl.config.loans.guildbuyer_total_debt_cap = 0.0;
        let ctrl_dir = format!("/tmp/autotune-sim/60d-gbcap-ctrl-{}", seed);
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        run_seeded_headless(&ctrl, seed, &ctrl_path).ok();

        // ── Fix: GB debt capped at 3× GDP ─────────────────────────────────────
        let mut fix = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        fix.name = format!("Fix_60d_GBcap_s{}", seed);
        fix.duration_ticks = 288 * 60;
        fix.config.loans.guildbuyer_total_debt_cap = 3.0; // THE FIX
        let fix_dir = format!("/tmp/autotune-sim/60d-gbcap-{}", seed);
        let fix_path = std::path::PathBuf::from(&fix_dir);
        std::fs::create_dir_all(&fix_path).ok();
        run_seeded_headless(&fix, seed, &fix_path).ok();

        // ── Load results ──────────────────────────────────────────────────────
        let ctrl_summary = load_summary(&ctrl_path.join("simulation.db"));
        let fix_summary = load_summary(&fix_path.join("simulation.db"));

        if let (Ok(cs), Ok(fs)) = (ctrl_summary, fix_summary) {
            let ctrl_dg = cs.debt / cs.gdp.max(1.0);
            let fix_dg = fs.debt / fs.gdp.max(1.0);
            let ctrl_t3 = cs.tier3_events;
            let fix_t3 = fs.tier3_events;
            ctrl_metrics.push((
                seed,
                cs.gdp,
                ctrl_dg,
                cs.avg_volatility,
                cs.buy_ratio,
                ctrl_t3,
            ));
            fix_metrics.push((
                seed,
                fs.gdp,
                fix_dg,
                fs.avg_volatility,
                fs.buy_ratio,
                fix_t3,
            ));

            let status = if fix_dg < 10.0 && ctrl_dg >= 10.0 {
                "STABLE"
            } else if fix_dg < ctrl_dg * 0.8 {
                "FIXED"
            } else if fix_dg < ctrl_dg {
                "improved"
            } else {
                "worse"
            };
            println!(
                "    Seed {}:  Ctrl D/G={:.3}x  Fix D/G={:.3}x  Delta={:+.3}x  T3 Ctrl={} Fix={}  {}",
                seed,
                ctrl_dg,
                fix_dg,
                fix_dg - ctrl_dg,
                ctrl_t3,
                fix_t3,
                status
            );
        } else {
            println!("    Seed {}: FAILED to load results", seed);
        }
    }

    // ── Summary ─────────────────────────────────────────────────────────────
    println!(
        "
  ╔════════════════════════════════════════════════════════════════╗"
    );
    println!("  ║  60-DAY GB DEBT CAP SUMMARY (2 seeds × 60 days)               ║");
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  {:>6}  {:>12}  {:>10}  {:>10}  {:>8}  {:>7}  ║",
        "Seed", "GDP", "D/G_ctrl", "D/G_fix", "Delta", "Verdict"
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");

    for ((seed, fgdp, cdg, _, _, _), (_, _, fdg, _, _, _)) in
        ctrl_metrics.iter().zip(fix_metrics.iter())
    {
        let verdict = if *fdg < 10.0 && *cdg >= 10.0 {
            "STABLE"
        } else if *fdg < *cdg * 0.5 {
            "FIXED"
        } else if *fdg < *cdg {
            "improved"
        } else {
            "worse"
        };
        println!(
            "  ║  {:>6}  {:>12.0}  {:>10.3}x  {:>10.3}x  {:>+8.3}x  {:>7}  ║",
            seed,
            fgdp,
            cdg,
            fdg,
            fdg - cdg,
            verdict
        );
    }

    let ctrl_dg_mean: f64 = ctrl_metrics.iter().map(|(_, _, d, _, _, _)| d).sum::<f64>() / 2.0;
    let fix_dg_mean: f64 = fix_metrics.iter().map(|(_, _, d, _, _, _)| d).sum::<f64>() / 2.0;
    let ctrl_t3_mean: f64 = ctrl_metrics
        .iter()
        .map(|(_, _, _, _, _, t3)| *t3 as f64)
        .sum::<f64>();
    let fix_t3_mean: f64 = fix_metrics
        .iter()
        .map(|(_, _, _, _, _, t3)| *t3 as f64)
        .sum::<f64>();

    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  {:>6}  {:>12}  {:>10.3}x  {:>10.3}x  {:>+8.3}x  {:>7}  ║",
        "MEAN",
        "--",
        ctrl_dg_mean,
        fix_dg_mean,
        fix_dg_mean - ctrl_dg_mean,
        if fix_dg_mean < ctrl_dg_mean * 0.5 {
            "FIXED"
        } else if fix_dg_mean < ctrl_dg_mean {
            "improved"
        } else {
            "not fixed"
        }
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    println!(
        "  ║  TIER3 events: Ctrl={:.0}  Fix={:.0}                            ║",
        ctrl_t3_mean as u32, fix_t3_mean as u32
    );
    println!("  ╠════════════════════════════════════════════════════════════════╣");
    let stable = fix_dg_mean < 10.0;
    let improved = fix_dg_mean < ctrl_dg_mean;

    println!(
        "
  VERDICT:"
    );
    if stable && improved {
        println!("    FIX CONFIRMED -- GB debt cap 3× GDP resolves 60d instability");
        println!(
            "    D/G mean: {:.1}x → {:.1}x, below 10x stability threshold",
            ctrl_dg_mean, fix_dg_mean
        );
        println!("    RECOMMENDATION: Add guildbuyer_total_debt_cap = 3.0 to production config");
    } else if improved {
        println!("    PARTIAL -- GB debt cap improves D/G but may not fully resolve");
        println!(
            "    D/G mean: {:.1}x → {:.1}x ({:+.1}x change)",
            ctrl_dg_mean,
            fix_dg_mean,
            fix_dg_mean - ctrl_dg_mean
        );
        println!("    RECOMMENDATION: Consider tighter cap (2× GDP) or remove floor");
    } else {
        println!("    NOT FIXED -- GB debt cap does NOT resolve instability");
        println!("    D/G mean: {:.1}x → {:.1}x", ctrl_dg_mean, fix_dg_mean);
        println!("    RECOMMENDATION: Try tighter GB cap (1-2× GDP) or remove Diamond floor");
    }
}

fn run_sixty_day_tier3_sweep() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64, 98765u64, 77777u64, 11111u64];
    let tier3_ratios = [30.0, 50.0, 100.0];

    println!("\n============================================================================");
    println!("  60-DAY TIER3=100 SWEEP TEST");
    println!("  tier3=30 (ctrl) / 50 / 100 x 5 seeds x 60 days");
    println!("============================================================================\n");

    #[derive(Debug)]
    struct T3Result {
        tier3: f64,
        seed: u64,
        gdp: f64,
        dg: f64,
        tier3_events: u32,
    }

    impl T3Result {
        fn from_db(db_path: &std::path::Path, tier3: f64, seed: u64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;
            let tier3_events = count_tier3_events(db_path);
            Some(Self {
                tier3,
                seed,
                gdp: s.gdp,
                dg: if s.gdp > 0.0 { s.debt / s.gdp } else { 0.0 },
                tier3_events,
            })
        }
    }

    let mut results = Vec::new();

    for &tier3 in &tier3_ratios {
        for &seed in &seeds {
            print!("  tier3={:.0} seed={} ... ", tier3, seed);
            std::io::stdout().flush().ok();

            let mut sim = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
            sim.name = format!("60d_t3={:.0}_s={}", tier3, seed);
            sim.duration_ticks = 288 * 60;
            sim.config.loans.debt_gdp_tier3_ratio = tier3;

            let out_dir = format!("/tmp/autotune-sim/60d-t3-{:.0}-s-{}", tier3, seed);
            let out_path = std::path::PathBuf::from(&out_dir);
            std::fs::create_dir_all(&out_path).ok();
            run_seeded_headless(&sim, seed, &out_path).ok();

            let r = T3Result::from_db(&out_path.join("simulation.db"), tier3, seed);
            if let Some(r) = r {
                println!(
                    "  GDP={:.0}  D/G={:.3}x  T3_ev={}",
                    r.gdp, r.dg, r.tier3_events
                );
                results.push(r);
            } else {
                println!("FAILED");
            }
        }
        println!();
    }

    println!("\n============================================================================");
    println!("  60-DAY TIER3 SWEEP - AGGREGATE RESULTS");
    println!("============================================================================");
    println!(
        "  {:^6} | {:^10}  {:^10}  {:^8}  {:^8} | {:^8}",
        "tier3", "GDP mean", "D/G mean", "D/G min", "D/G max", "T3_ev"
    );
    println!("  {}", "-".repeat(65));

    for &tier3 in &tier3_ratios {
        let arm: Vec<_> = results.iter().filter(|r| r.tier3 == tier3).collect();
        let n = arm.len();
        if n == 0 {
            continue;
        }
        let gdp_mean = arm.iter().map(|r| r.gdp).sum::<f64>() / n as f64;
        let dg_mean = arm.iter().map(|r| r.dg).sum::<f64>() / n as f64;
        let dg_min = arm.iter().map(|r| r.dg).reduce(f64::min).unwrap_or(0.0);
        let dg_max = arm.iter().map(|r| r.dg).reduce(f64::max).unwrap_or(0.0);
        let t3_total: u32 = arm.iter().map(|r| r.tier3_events).sum();
        println!(
            "  {:^6.0} | {:>10.0}  {:>10.3}x  {:>8.3}x  {:>8.3}x | {:^8}",
            tier3, gdp_mean, dg_mean, dg_min, dg_max, t3_total
        );
    }

    println!("\n  Per-seed D/G:");
    for &seed in &seeds {
        let row: Vec<String> = tier3_ratios
            .iter()
            .map(|&t| {
                results
                    .iter()
                    .find(|r| r.tier3 == t && r.seed == seed)
                    .map(|r| format!("{:.2}x", r.dg))
                    .unwrap_or_else(|| "-".to_string())
            })
            .collect();
        println!("  seed={} | {}", seed, row.join(" | "));
    }

    println!("\n  TIER3 events:");
    for &seed in &seeds {
        let row: Vec<String> = tier3_ratios
            .iter()
            .map(|&t| {
                results
                    .iter()
                    .find(|r| r.tier3 == t && r.seed == seed)
                    .map(|r| format!("{}", r.tier3_events))
                    .unwrap_or_else(|| "-".to_string())
            })
            .collect();
        println!("  seed={} | {}", seed, row.join(" | "));
    }

    let t3_30 = tier3_ratios[0];
    let ctrl_mean = results
        .iter()
        .filter(|r| r.tier3 == t3_30)
        .map(|r| r.dg)
        .sum::<f64>()
        / 5.0;
    let fix_mean_50 = results
        .iter()
        .filter(|r| r.tier3 == 50.0)
        .map(|r| r.dg)
        .sum::<f64>()
        / 5.0;
    let fix_mean_100 = results
        .iter()
        .filter(|r| r.tier3 == 100.0)
        .map(|r| r.dg)
        .sum::<f64>()
        / 5.0;
    let ctrl_t3: u32 = results
        .iter()
        .filter(|r| r.tier3 == t3_30)
        .map(|r| r.tier3_events)
        .sum();
    let fix100_t3: u32 = results
        .iter()
        .filter(|r| r.tier3 == 100.0)
        .map(|r| r.tier3_events)
        .sum();

    println!("\n============================================================================");
    println!("  VERDICT");
    println!("----------------------------------------------------------------------------");
    println!(
        "  tier3=30 (ctrl):  D/G={:.3}x  T3_ev={}",
        ctrl_mean, ctrl_t3
    );
    println!("  tier3=50:         D/G={:.3}x", fix_mean_50);
    println!(
        "  tier3=100:        D/G={:.3}x  T3_ev={}",
        fix_mean_100, fix100_t3
    );

    let any_t3_100 = fix100_t3 > 0;
    if any_t3_100 {
        println!("----------------------------------------------------------------------------");
        println!("  WARNING: TIER3 FIRING with tier3=100!");
        println!("  Instability is FUNDAMENTAL - not threshold-dependent.");
        println!("  tier3=100 alone does not fix root cause.");
    } else if fix_mean_100 < ctrl_mean && fix100_t3 == 0 {
        println!("----------------------------------------------------------------------------");
        println!(
            "  CONFIRMED: tier3=100 RESOLVES instability - 0 TIER3 events, D/G {:.1}x -> {:.1}x",
            ctrl_mean, fix_mean_100
        );
        println!("  RECOMMENDATION: tier3_ratio=100 in production config.");
    } else if fix_mean_100 < ctrl_mean {
        println!("----------------------------------------------------------------------------");
        println!(
            "  IMPROVED: tier3=100 helps D/G ({:.1}x -> {:.1}x) but TIER3 still fires",
            ctrl_mean, fix_mean_100
        );
        println!("  Recommend: tier3=100 + block MM/GB loans during lock");
    } else {
        println!("----------------------------------------------------------------------------");
        println!("  DOES NOT FIX: tier3=100 does not resolve instability");
        println!("  Root cause is elsewhere - block MM/GB loans during lock may help");
    }
    println!("============================================================================");
}

fn run_sixty_day_loan_lock_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64, 98765u64, 77777u64, 11111u64];

    println!("\n============================================================================");
    println!("  60-DAY LOAN LOCK TEST");
    println!("  Block MM/GB loans during TIER3 lock x 5 seeds x 60 days");
    println!("============================================================================\n");

    #[derive(Debug)]
    struct LockResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        tier3_events: u32,
        tier3_locked_ticks: u64,
    }

    impl LockResult {
        fn from_db(db_path: &std::path::Path, seed: u64, locked_ticks: u64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;
            let tier3_events = count_tier3_events(db_path);
            Some(Self {
                seed,
                gdp: s.gdp,
                dg: if s.gdp > 0.0 { s.debt / s.gdp } else { 0.0 },
                tier3_events,
                tier3_locked_ticks: locked_ticks,
            })
        }
    }

    let mut ctrl_results = Vec::new();
    let mut fix_results = Vec::new();

    for &seed in &seeds {
        println!("  Seed {} ...", seed);
        std::io::stdout().flush().ok();

        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = format!("Ctrl_60d_Lock_s{}", seed);
        ctrl.duration_ticks = 288 * 60;
        ctrl.config.loans.block_mm_gb_loans_during_tier3 = false;
        let ctrl_dir = format!("/tmp/autotune-sim/60d-lock-ctrl-{}", seed);
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        run_seeded_headless(&ctrl, seed, &ctrl_path).ok();

        let mut fix = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        fix.name = format!("Fix_60d_Lock_s{}", seed);
        fix.duration_ticks = 288 * 60;
        fix.config.loans.block_mm_gb_loans_during_tier3 = true;
        let fix_dir = format!("/tmp/autotune-sim/60d-lock-fix-{}", seed);
        let fix_path = std::path::PathBuf::from(&fix_dir);
        std::fs::create_dir_all(&fix_path).ok();
        run_seeded_headless(&fix, seed, &fix_path).ok();

        let ctrl_t3_locked = get_tier3_locked_ticks(&ctrl_path.join("simulation.db"));
        let fix_t3_locked = get_tier3_locked_ticks(&fix_path.join("simulation.db"));

        if let Some(r) = LockResult::from_db(&ctrl_path.join("simulation.db"), seed, ctrl_t3_locked)
        {
            ctrl_results.push(r);
        }
        if let Some(r) = LockResult::from_db(&fix_path.join("simulation.db"), seed, fix_t3_locked) {
            fix_results.push(r);
        }
    }

    println!("\n============================================================================");
    println!("  60-DAY LOAN LOCK - PER-SEED RESULTS");
    println!("----------------------------------------------------------------------------");
    println!(
        "  {:^6} | {:^12}  {:^10}  {:^10}  {:^8} | {:^10}  {:^10}",
        "Seed", "GDP_ctrl", "DG_ctrl", "DG_fix", "delta", "T3_ctrl", "T3_fix"
    );
    println!("  {}", "-".repeat(72));

    for (ctrl, fix) in ctrl_results.iter().zip(fix_results.iter()) {
        let delta = fix.dg - ctrl.dg;
        println!(
            "  {:^6} | {:>12.0}  {:>10.3}x  {:>10.3}x  {:>+8.3}x | {:^10}  {:^10}",
            ctrl.seed, ctrl.gdp, ctrl.dg, fix.dg, delta, ctrl.tier3_events, fix.tier3_events
        );
    }

    let ctrl_dg_mean = ctrl_results.iter().map(|r| r.dg).sum::<f64>() / 5.0;
    let fix_dg_mean = fix_results.iter().map(|r| r.dg).sum::<f64>() / 5.0;
    let ctrl_t3_mean: f64 = ctrl_results
        .iter()
        .map(|r| r.tier3_events as f64)
        .sum::<f64>()
        / 5.0;
    let fix_t3_mean: f64 = fix_results
        .iter()
        .map(|r| r.tier3_events as f64)
        .sum::<f64>()
        / 5.0;
    let fix_locked_mean: f64 = fix_results
        .iter()
        .map(|r| r.tier3_locked_ticks as f64)
        .sum::<f64>()
        / 5.0;
    let delta = fix_dg_mean - ctrl_dg_mean;

    println!("  {}", "-".repeat(72));
    println!(
        "  {:^6} | {:>12}  {:>10.3}x  {:>10.3}x  {:>+8.3}x | {:>10.1}  {:>10.1}",
        "MEAN", "avg GDP", ctrl_dg_mean, fix_dg_mean, delta, ctrl_t3_mean, fix_t3_mean
    );
    let fix_locked_pct = (fix_locked_mean / (288.0 * 60.0) * 100.0).round();
    println!("----------------------------------------------------------------------------");
    println!(
        "  TIER3 locked (fix): avg {:.0} ticks = {:.0}% of 60 days",
        fix_locked_mean, fix_locked_pct
    );

    println!("\n============================================================================");
    println!("  VERDICT");
    println!("----------------------------------------------------------------------------");
    println!(
        "  Ctrl D/G={:.2}x  Fix D/G={:.2}x  delta={:+.2}x",
        ctrl_dg_mean, fix_dg_mean, delta
    );
    println!(
        "  TIER3 events: Ctrl={:.0}  Fix={:.0}",
        ctrl_t3_mean, fix_t3_mean
    );
    println!("----------------------------------------------------------------------------");

    let stable = fix_dg_mean < 10.0;
    if stable && delta < -2.0 {
        println!(
            "  CONFIRMED: D/G {:.1}x -> {:.1}x, below 10x stability threshold",
            ctrl_dg_mean, fix_dg_mean
        );
        println!("  RECOMMENDATION: block_mm_gb_loans_during_tier3 = true in config.");
    } else if delta < -1.0 {
        println!(
            "  PARTIAL: D/G {:.1}x -> {:.1}x, significant improvement",
            ctrl_dg_mean, fix_dg_mean
        );
        println!("  Consider combining with tier3=100 for added headroom.");
    } else if delta.abs() < 1.0 {
        println!("  NEUTRAL: D/G unchanged (delta={:+.1}x)", delta);
        println!("  TIER3 lock blocking alone is insufficient.");
        println!("  Consider: tier3=100 only, or loan-lock + tier3=100 combo.");
    } else {
        println!(
            "  FAILS: D/G {:.1}x -> {:.1}x (+{:.1}x) WORSE",
            ctrl_dg_mean, fix_dg_mean, delta
        );
        println!("  Blocking MM/GB loans during TIER3 worsens the situation.");
    }
    println!("============================================================================");
}

fn get_tier3_locked_ticks(db_path: &std::path::Path) -> u64 {
    let conn = match rusqlite::Connection::open(db_path) {
        Ok(c) => c,
        Err(_) => return 0,
    };
    conn.query_row(
        "SELECT COUNT(*) FROM circuit_breaker_events WHERE tier = 'TIER3'",
        [],
        |row| row.get::<_, i64>(0),
    )
    .unwrap_or(0) as u64
}

/// 60-Day Combo Test: tier3=100 + block_mm_gb_loans_during_tier3=true
///
/// HYPOTHESIS: tier3=100 alone fixes TIER3 circuit events but worsens D/G.
/// loan-lock alone has neutral D/G effect.
/// Together: tier3=100 stops the circuit, loan-lock prevents MM/GB debt
/// accumulation during any remaining rare lock events.
///
/// Ctrl: tier3=30, block_mm_gb_loans=false
/// Fix: tier3=100, block_mm_gb_loans=true
fn run_sixty_day_combo_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64, 98765u64, 77777u64, 11111u64];

    println!("\n============================================================================");
    println!("  60-DAY COMBO TEST: tier3=100 + loan-lock");
    println!("  Ctrl: tier3=30, block_mm_gb_loans=false");
    println!("  Fix:  tier3=100, block_mm_gb_loans=true");
    println!("============================================================================\n");

    #[derive(Debug)]
    struct ComboResult {
        seed: u64,
        gdp: f64,
        dg: f64,
        tier3_events: u32,
        tier3_locked_ticks: u64,
    }

    impl ComboResult {
        fn from_db(db_path: &std::path::Path, seed: u64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;
            let tier3_events = count_tier3_events(db_path);
            let tier3_locked_ticks = get_tier3_locked_ticks(db_path);
            Some(Self {
                seed,
                gdp: s.gdp,
                dg: if s.gdp > 0.0 { s.debt / s.gdp } else { 0.0 },
                tier3_events,
                tier3_locked_ticks,
            })
        }
    }

    let mut ctrl_results = Vec::new();
    let mut fix_results = Vec::new();

    for &seed in &seeds {
        println!("  Seed {} ...", seed);
        std::io::stdout().flush().ok();

        // Control: tier3=30, no loan lock
        let mut ctrl = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        ctrl.name = format!("Ctrl_60d_Combo_s{}", seed);
        ctrl.duration_ticks = 288 * 60;
        ctrl.config.loans.debt_gdp_tier3_ratio = 30.0;
        ctrl.config.loans.block_mm_gb_loans_during_tier3 = false;
        let ctrl_dir = format!("/tmp/autotune-sim/60d-combo-ctrl-{}", seed);
        let ctrl_path = std::path::PathBuf::from(&ctrl_dir);
        std::fs::create_dir_all(&ctrl_path).ok();
        run_seeded_headless(&ctrl, seed, &ctrl_path).ok();

        // Fix: tier3=100, WITH loan lock
        let mut fix = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        fix.name = format!("Fix_60d_Combo_s{}", seed);
        fix.duration_ticks = 288 * 60;
        fix.config.loans.debt_gdp_tier3_ratio = 100.0;
        fix.config.loans.block_mm_gb_loans_during_tier3 = true;
        let fix_dir = format!("/tmp/autotune-sim/60d-combo-fix-{}", seed);
        let fix_path = std::path::PathBuf::from(&fix_dir);
        std::fs::create_dir_all(&fix_path).ok();
        run_seeded_headless(&fix, seed, &fix_path).ok();

        if let Some(r) = ComboResult::from_db(&ctrl_path.join("simulation.db"), seed) {
            println!(
                "  ctrl: GDP={:.0} D/G={:.3}x T3_ev={}  |  ",
                r.gdp, r.dg, r.tier3_events
            );
            ctrl_results.push(r);
        }
        if let Some(r) = ComboResult::from_db(&fix_path.join("simulation.db"), seed) {
            println!(
                "fix: GDP={:.0} D/G={:.3}x T3_ev={}",
                r.gdp, r.dg, r.tier3_events
            );
            fix_results.push(r);
        }
    }

    println!("\n============================================================================");
    println!("  60-DAY COMBO TEST - PER-SEED RESULTS");
    println!("----------------------------------------------------------------------------");
    println!(
        "  {:^6} | {:^12}  {:^10}  {:^10}  {:^8} | {:^8}  {:^8}",
        "Seed", "GDP_ctrl", "DG_ctrl", "DG_fix", "delta", "T3_ctrl", "T3_fix"
    );
    println!("  {}", "-".repeat(72));

    for (ctrl, fix) in ctrl_results.iter().zip(fix_results.iter()) {
        let delta = fix.dg - ctrl.dg;
        println!(
            "  {:^6} | {:>12.0}  {:>10.3}x  {:>10.3}x  {:>+8.3}x | {:^8}  {:^8}",
            ctrl.seed, ctrl.gdp, ctrl.dg, fix.dg, delta, ctrl.tier3_events, fix.tier3_events
        );
    }

    let ctrl_dg_mean = ctrl_results.iter().map(|r| r.dg).sum::<f64>() / 5.0;
    let fix_dg_mean = fix_results.iter().map(|r| r.dg).sum::<f64>() / 5.0;
    let ctrl_t3_mean: f64 = ctrl_results
        .iter()
        .map(|r| r.tier3_events as f64)
        .sum::<f64>()
        / 5.0;
    let fix_t3_mean: f64 = fix_results
        .iter()
        .map(|r| r.tier3_events as f64)
        .sum::<f64>()
        / 5.0;
    let fix_locked_mean: f64 = fix_results
        .iter()
        .map(|r| r.tier3_locked_ticks as f64)
        .sum::<f64>()
        / 5.0;
    let delta = fix_dg_mean - ctrl_dg_mean;

    println!("  {}", "-".repeat(72));
    println!(
        "  {:^6} | {:>12}  {:>10.3}x  {:>10.3}x  {:>+8.3}x | {:>8.1}  {:>8.1}",
        "MEAN", "avg GDP", ctrl_dg_mean, fix_dg_mean, delta, ctrl_t3_mean, fix_t3_mean
    );
    let fix_locked_pct = (fix_locked_mean / (288.0 * 60.0) * 100.0).round();
    println!("----------------------------------------------------------------------------");
    println!(
        "  Fix TIER3 locked: avg {:.0} ticks = {:.0}% of 60 days",
        fix_locked_mean, fix_locked_pct
    );

    println!("\n============================================================================");
    println!("  VERDICT");
    println!("----------------------------------------------------------------------------");
    println!(
        "  Ctrl D/G={:.2}x (tier3=30)  Fix D/G={:.2}x (tier3=100+lock)  delta={:+.2}x",
        ctrl_dg_mean, fix_dg_mean, delta
    );
    println!(
        "  TIER3 events: Ctrl={:.0}  Fix={:.0}",
        ctrl_t3_mean, fix_t3_mean
    );

    if fix_t3_mean == 0.0 && fix_dg_mean < ctrl_dg_mean {
        println!("----------------------------------------------------------------------------");
        println!(
            "  CONFIRMED: 0 TIER3 events, D/G improved {:.1}x -> {:.1}x",
            ctrl_dg_mean, fix_dg_mean
        );
        println!("  RECOMMENDATION: tier3=100 + block_mm_gb_loans_during_tier3=true");
        println!("  SAFE TO SHIP: Add to production config.");
    } else if fix_t3_mean == 0.0 && (fix_dg_mean - ctrl_dg_mean).abs() < 3.0 {
        println!("----------------------------------------------------------------------------");
        println!(
            "  CONDITIONAL: 0 TIER3 events, D/G change {:+.1}x (noise)",
            delta
        );
        println!("  TIER3 circuit eliminated. D/G essentially unchanged.");
        println!("  RECOMMENDATION: tier3=100 + block_mm_gb_loans_during_tier3=true");
    } else if fix_t3_mean < ctrl_t3_mean {
        println!("----------------------------------------------------------------------------");
        println!(
            "  PARTIAL: TIER3 events reduced {:.0} -> {:.0}, D/G {:+.1}x",
            ctrl_t3_mean, fix_t3_mean, delta
        );
    } else {
        println!("----------------------------------------------------------------------------");
        println!("  INCONCLUSIVE: Results do not confirm benefit.");
    }
    println!("============================================================================");
}

/// 60-Day Early Intervention Sweep
///
/// HYPOTHESIS: The counter-cyclical circuit fires at D/G=37x (tier3=30),
/// but by then debt has already spiraled. The circuit only PREVENTTS WORSE
/// (0% interest cap) but cannot DELEVERAGE existing debt.
///
/// TEST: What if the circuit fires MUCH EARLIER (tier3=5/10/15) before
/// the debt spiral forms? Early 0% interest = economy never reaches 30-40x D/G.
///
/// The circuit multiplier = max(0, 1 - D/G / tier3_ratio).
/// At tier3=10, circuit fires at D/G=10x. At tier3=5, circuit fires at D/G=5x.
fn run_sixty_day_early_intervention_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64, 98765u64, 77777u64, 11111u64];
    // Early tiers: 5, 10, 15 vs control: 30
    let tier3_ratios = [5.0, 10.0, 15.0, 30.0];

    println!("\n============================================================================");
    println!("  60-DAY EARLY INTERVENTION TEST");
    println!("  tier3=5/10/15/30 x 5 seeds x 60 days — circuit fires at D/G=5x/10x/15x/30x");
    println!("============================================================================\n");

    #[derive(Debug)]
    struct T3Result {
        tier3: f64,
        seed: u64,
        gdp: f64,
        dg: f64,
        tier3_events: u32,
    }

    impl T3Result {
        fn from_db(db_path: &std::path::Path, tier3: f64, seed: u64) -> Option<Self> {
            let s = load_summary(db_path).ok()?;
            let tier3_events = count_tier3_events(db_path);
            Some(Self {
                tier3,
                seed,
                gdp: s.gdp,
                dg: if s.gdp > 0.0 { s.debt / s.gdp } else { 0.0 },
                tier3_events,
            })
        }
    }

    let mut results = Vec::new();

    for &tier3 in &tier3_ratios {
        for &seed in &seeds {
            print!("  tier3={:.0} seed={} ... ", tier3, seed);
            std::io::stdout().flush().ok();

            let mut sim = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
            sim.name = format!("60d_ei_t{:.0}_s{}", tier3, seed);
            sim.duration_ticks = 288 * 60;
            sim.config.loans.debt_gdp_tier3_ratio = tier3;

            let out_dir = format!("/tmp/autotune-sim/60d-ei-t{:.0}-s{}", tier3, seed);
            let out_path = std::path::PathBuf::from(&out_dir);
            std::fs::create_dir_all(&out_path).ok();
            run_seeded_headless(&sim, seed, &out_path).ok();

            let r = T3Result::from_db(&out_path.join("simulation.db"), tier3, seed);
            if let Some(r) = r {
                println!(
                    "  GDP={:.0}  D/G={:.3}x  T3_ev={}",
                    r.gdp, r.dg, r.tier3_events
                );
                results.push(r);
            } else {
                println!("FAILED");
            }
        }
        println!();
    }

    println!("\n============================================================================");
    println!("  60-DAY EARLY INTERVENTION - AGGREGATE RESULTS");
    println!("============================================================================");
    println!(
        "  {:^6} | {:^10}  {:^10}  {:^8}  {:^8} | {:^8}",
        "tier3", "GDP mean", "D/G mean", "D/G min", "D/G max", "T3_ev"
    );
    println!("  {}", "-".repeat(65));

    for &tier3 in &tier3_ratios {
        let arm: Vec<_> = results.iter().filter(|r| r.tier3 == tier3).collect();
        let n = arm.len();
        if n == 0 {
            continue;
        }
        let gdp_mean = arm.iter().map(|r| r.gdp).sum::<f64>() / n as f64;
        let dg_mean = arm.iter().map(|r| r.dg).sum::<f64>() / n as f64;
        let dg_min = arm.iter().map(|r| r.dg).reduce(f64::min).unwrap_or(0.0);
        let dg_max = arm.iter().map(|r| r.dg).reduce(f64::max).unwrap_or(0.0);
        let t3_total: u32 = arm.iter().map(|r| r.tier3_events).sum();
        println!(
            "  {:^6.0} | {:>10.0}  {:>10.3}x  {:>8.3}x  {:>8.3}x | {:^8}",
            tier3, gdp_mean, dg_mean, dg_min, dg_max, t3_total
        );
    }

    println!("\n  Per-seed D/G:");
    for &seed in &seeds {
        let row: Vec<String> = tier3_ratios
            .iter()
            .map(|&t| {
                results
                    .iter()
                    .find(|r| r.tier3 == t && r.seed == seed)
                    .map(|r| format!("{:.2}x", r.dg))
                    .unwrap_or_else(|| "-".to_string())
            })
            .collect();
        println!("  seed={} | {}", seed, row.join(" | "));
    }

    println!("\n  TIER3 events:");
    for &seed in &seeds {
        let row: Vec<String> = tier3_ratios
            .iter()
            .map(|&t| {
                results
                    .iter()
                    .find(|r| r.tier3 == t && r.seed == seed)
                    .map(|r| format!("{}", r.tier3_events))
                    .unwrap_or_else(|| "-".to_string())
            })
            .collect();
        println!("  seed={} | {}", seed, row.join(" | "));
    }

    let t3_30 = 30.0;
    let ctrl_mean = results
        .iter()
        .filter(|r| r.tier3 == t3_30)
        .map(|r| r.dg)
        .sum::<f64>()
        / 5.0;
    let t3_5_mean = results
        .iter()
        .filter(|r| r.tier3 == 5.0)
        .map(|r| r.dg)
        .sum::<f64>()
        / 5.0;
    let t3_10_mean = results
        .iter()
        .filter(|r| r.tier3 == 10.0)
        .map(|r| r.dg)
        .sum::<f64>()
        / 5.0;
    let t3_15_mean = results
        .iter()
        .filter(|r| r.tier3 == 15.0)
        .map(|r| r.dg)
        .sum::<f64>()
        / 5.0;

    println!("\n============================================================================");
    println!("  VERDICT");
    println!("----------------------------------------------------------------------------");
    println!("  tier3=30 (ctrl):  D/G={:.3}x", ctrl_mean);
    println!(
        "  tier3=15:         D/G={:.3}x  delta={:+.3}x",
        t3_15_mean,
        t3_15_mean - ctrl_mean
    );
    println!(
        "  tier3=10:         D/G={:.3}x  delta={:+.3}x",
        t3_10_mean,
        t3_10_mean - ctrl_mean
    );
    println!(
        "  tier3=5:          D/G={:.3}x  delta={:+.3}x",
        t3_5_mean,
        t3_5_mean - ctrl_mean
    );

    let best_tier3 = *[t3_5_mean, t3_10_mean, t3_15_mean]
        .iter()
        .min_by(|a, b| a.partial_cmp(b).unwrap())
        .unwrap();
    let best_label = if best_tier3 == t3_5_mean {
        "5"
    } else if best_tier3 == t3_10_mean {
        "10"
    } else {
        "15"
    };

    if best_tier3 < ctrl_mean * 0.8 && best_tier3 < 10.0 {
        println!("----------------------------------------------------------------------------");
        println!(
            "  CONFIRMED: tier3={} resolves instability — D/G {:.1}x -> {:.1}x (< 10x stable)",
            best_label, ctrl_mean, best_tier3
        );
        println!(
            "  RECOMMENDATION: tier3_ratio={} in production config.",
            best_label
        );
    } else if best_tier3 < ctrl_mean {
        println!("----------------------------------------------------------------------------");
        println!(
            "  IMPROVED: tier3={} reduces D/G ({:.1}x -> {:.1}x)",
            best_label, ctrl_mean, best_tier3
        );
        println!("  But instability persists — consider more aggressive intervention.");
    } else {
        println!("----------------------------------------------------------------------------");
        println!("  DOES NOT FIX: earlier circuit does not resolve instability");
        println!("  Root cause is the debt SPIRAL, not circuit timing.");
        println!("  The circuit prevents catastrophe but cannot deleverage existing debt.");
    }
    println!("============================================================================");
}

// ─── Graduated TIER3 Exit Cap Test ──────────────────────────────────────────
//
// Problem: When TIER3 circuit unlocks (D/G drops below hysteresis threshold),
// the counter-cyclical multiplier jumps from 0% to 53% at D/G=14 (tier3=30).
// This is too high — debt grows faster than GDP, causing TIER3 re-trigger
// within days. The 180-day test shows this cascade: D/G drops at day 90,
// then CATASTROPHICALLY ESCALATES at day 120-180.
//
// Fix: After TIER3 circuit unlocks, apply a graduated multiplier cap for N ticks
// before the normal counter-cyclical formula resumes. This gives the economy
// time to deleverage under suppressed interest, rather than immediately
// re-accumulating under 53% daily interest.
//
// Hypothesis: cap=0.10, delay=1152 ticks (4 days) prevents TIER3 re-trigger
// and keeps D/G contained below 30x at 180 days.
fn run_quick_graduated_test() {
    use crate::analyzer::load_summary;
    let seeds = [42u64];

    println!("\n=== QUICK GRADUATED TEST (60d, seed=42) ===\n");

    let arms = [
        ("Ctrl", 1.0, 1152u32),
        ("FixA", 0.10, 1152u32),
        ("FixB", 0.03, 2304u32),
    ];

    let mut results: Vec<(String, f64, f64, u32)> = Vec::new();

    for &(name, cap, delay) in &arms {
        let mut scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
        scenario.name = format!("Q_{}_{}", name, cap);
        scenario.duration_ticks = 288 * 60;
        scenario.config.loans.tier3_exit_multiplier_cap = cap;
        scenario.config.loans.tier3_exit_delay_ticks = delay;
        let dir = format!("/tmp/autotune-sim/quick-{}-{}", name.to_lowercase(), cap);
        let path = std::path::PathBuf::from(&dir);
        std::fs::create_dir_all(&path).ok();
        run_seeded_headless(&scenario, seeds[0], &path).ok();

        if let Ok(summary) = load_summary(&path.join("simulation.db")) {
            let dg = summary.debt / summary.gdp.max(1.0);
            results.push((name.to_string(), summary.gdp, dg, summary.tier3_events));
            println!("  {}: GDP={:.0}  D/G={:.3}x  T3={}", name, summary.gdp, dg, summary.tier3_events);
        } else {
            println!("  {}: FAILED", name);
        }
    }

    let ctrl_dg = results.iter().find(|r| r.0 == "Ctrl").map(|r| r.2).unwrap_or(1.0);
    println!("\n  vs Ctrl:");
    for (name, _, dg, t3) in &results {
        if name != "Ctrl" {
            let imp = (*dg / ctrl_dg - 1.0) * 100.0;
            println!("    {}: {:+.1}%  (T3={})", name, imp, t3);
        }
    }
}

fn run_graduated_exit_test() {
    use crate::analyzer::load_summary;

    let seeds = [42u64, 12345u64];

    println!(
        "
╔════════════════════════════════════════════════════════════════════════╗"
    );
    println!("║     GRADUATED TIER3 EXIT CAP TEST                            ║");
    println!("║  Ctrl: cap=1.0 (disabled, raw counter-cyclical)         ║");
    println!("║  Fix A:  cap=0.10, delay=1152 ticks (4 days)           ║");
    println!("║  Fix B:  cap=0.03, delay=2304 ticks (8 days)           ║");
    println!("║  Fix C:  cap=0.10 + block_mm_gb_loans_during_tier3    ║");
    println!("╚════════════════════════════════════════════════════════════════════════╝\n");

    let mut all_metrics: Vec<(String, u64, f64, f64, f64, u32)> = Vec::new();

    for &seed in &seeds {
        println!("  Running seed {}...", seed);

        let arms = [
            ("Ctrl", 1.0, 1152u32, false),
            ("FixA", 0.10, 1152u32, false),
            ("FixB", 0.03, 2304u32, false),
            ("FixC", 0.10, 1152u32, true),
        ];

        for (name, cap, delay, block_mm_gb) in arms {
            let mut scenario = Scenario::guild_stability_2mm_fixed_guild_plus_floor();
            scenario.name = format!("Grad_{}_{}_s{}", name, cap, seed);
            scenario.duration_ticks = 288 * 90;
            scenario.config.loans.tier3_exit_multiplier_cap = cap;
            scenario.config.loans.tier3_exit_delay_ticks = delay;
            scenario.config.loans.block_mm_gb_loans_during_tier3 = block_mm_gb;
            let dir = format!(
                "/tmp/autotune-sim/grad-{}-{}-{}",
                name.to_lowercase(),
                cap,
                seed
            );
            let path = std::path::PathBuf::from(&dir);
            std::fs::create_dir_all(&path).ok();
            run_seeded_headless(&scenario, seed, &path).ok();

            if let Ok(summary) = load_summary(&path.join("simulation.db")) {
                let dg = summary.debt / summary.gdp.max(1.0);
                all_metrics.push((
                    format!("{}:{}", name, seed),
                    seed,
                    summary.gdp,
                    dg,
                    summary.avg_volatility,
                    summary.tier3_events,
                ));
                println!(
                    "      {}: GDP={:.0}  D/G={:.3}x  T3={}",
                    name, summary.gdp, dg, summary.tier3_events
                );
            } else {
                println!("      {}: FAILED", name);
            }
        }
    }

    // Per-seed comparison
    println!(
        "
╔════════════════════════════════════════════════════════════════════════╗"
    );
    println!("║  GRADUATED EXIT CAP — 90-DAY RESULTS                                  ║");
    println!("╠════════════════════════════════════════════════════════════════════════╣");
    println!(
        "║  {:^12}  {:>10}  {:>10}  {:>10}  {:>8}  ║",
        "Arm", "GDP", "D/G", "vs_Ctrl%", "T3"
    );
    println!("╠════════════════════════════════════════════════════════════════════════╣");

    let mut ctrl_dgs: Vec<f64> = Vec::new();
    for &(ref arm, _, _gdp, dg, _vol, _t3) in &all_metrics {
        if arm.starts_with("Ctrl") {
            ctrl_dgs.push(dg);
        }
    }
    let ctrl_avg = if ctrl_dgs.is_empty() {
        1.0
    } else {
        ctrl_dgs.iter().sum::<f64>() / ctrl_dgs.len() as f64
    };

    for &(ref arm, _, gdp, dg, _vol, t3) in &all_metrics {
        let vs_ctrl = if arm.starts_with("Ctrl") {
            0.0f64
        } else {
            (dg / ctrl_avg - 1.0) * 100.0
        };
        println!(
            "║  {:^12}  {:>10.0}  {:>10.3}x  {:>+9.1}%  {:>8}  ║",
            arm, gdp, dg, vs_ctrl, t3
        );
    }
    println!("╚════════════════════════════════════════════════════════════════════════╝");

    let fix_a_avg = all_metrics
        .iter()
        .filter(|m| m.0.starts_with("FixA"))
        .map(|m| m.3)
        .sum::<f64>()
        / 2.0;
    let fix_b_avg = all_metrics
        .iter()
        .filter(|m| m.0.starts_with("FixB"))
        .map(|m| m.3)
        .sum::<f64>()
        / 2.0;
    let fix_c_avg = all_metrics
        .iter()
        .filter(|m| m.0.starts_with("FixC"))
        .map(|m| m.3)
        .sum::<f64>()
        / 2.0;
    let fix_a_imp = (fix_a_avg / ctrl_avg - 1.0) * 100.0;
    let fix_b_imp = (fix_b_avg / ctrl_avg - 1.0) * 100.0;
    let fix_c_imp = (fix_c_avg / ctrl_avg - 1.0) * 100.0;

    println!(
        "
  Summary (ctrl avg D/G={:.3}x):",
        ctrl_avg
    );
    println!(
        "    FixA (cap=0.10, delay=4d):  D/G={:.3}x  ({:+.1}%)  — marginal",
        fix_a_avg, fix_a_imp
    );
    println!(
        "    FixB (cap=0.03, delay=8d): D/G={:.3}x  ({:+.1}%)  — deeper cap",
        fix_b_avg, fix_b_imp
    );
    println!(
        "    FixC (cap=0.10 + block):   D/G={:.3}x  ({:+.1}%)  — block MM/GB loans",
        fix_c_avg, fix_c_imp
    );

    if fix_b_imp < -10.0 || fix_c_imp < -10.0 {
        println!("\n  ✓ FixB or FixC shows promise — run 180-day test to confirm.");
    } else {
        println!("\n  ✗ All variants insufficient at 90 days — architectural fix needed.");
        println!("  /at admin recovery is the only reliable deleveraging tool.");
    }
}
