mod analyzer;
mod config;
mod engine;
mod gui;
mod loan;
mod player;
mod recorder;
mod regression;
mod simulation;
mod sweep;

use std::path::PathBuf;
use std::time::Instant;

use crate::config::{ArchetypeConfig, SimConfig};
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
            stress_events: vec![],
            duration_ticks: 288 * 7,
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

fn run_headless(scenario: &Scenario, output_dir: Option<PathBuf>) -> Result<(), String> {
    use crate::recorder::DataRecorder;

    println!("=== Running Scenario: {} ===", scenario.name);
    println!(
        "Duration: {} ticks ({} days)",
        scenario.duration_ticks,
        scenario.duration_ticks / 288
    );
    println!("Speed: {} ticks/sec", scenario.speed_ticks_per_sec);

    let mut sim = Simulation::new(scenario.config.clone());

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

    for player_cfg in &scenario.players {
        let archetype = archetype_map
            .get(&player_cfg.archetype)
            .ok_or_else(|| format!("Unknown archetype: {}", player_cfg.archetype))?;
        for _ in 0..player_cfg.count {
            sim.add_player(*archetype);
        }
    }
    println!(
        "Players: {} (Casual:{}, Farmer:{}, Trader:{}, Hoarder:{}, Exploiter:{}, Newbie:{}, AFKFarmer:{}, GuildBuyer:{}, MarketMaker:{})",
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
        println!("  standard         - Normal economy with 11 players (default)");
        println!("  stressed         - Exploit, low players, loan cascade injected");
        println!("  high-activity    - High activity, 20 players, 7 days");
        println!("  low-player       - 3 players, 14 days");
        println!("  spread-stability - Farmer/Trader mix, 10 days");
        println!("  sp08-moderate    - Tiered breaker test: sp=0.80, cascade at day 6, 14d");
        println!("  buyer-heavy     - Buyer-heavy mix: 2 GuildBuyer + 3 Hoarder + 3 Casual");
        println!("  correlation      - Sector correlation test (treatment vs control)");
        println!("  all              - Run all scenarios and compare");
        println!("  sweep            - Parameter sweep across engine parameter space");
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
            Scenario::guild_stability(),
            Scenario::marketmaker_test(),
        ];
        crate::regression::run_regression_test(&scenarios, &baseline_dir, update);
        return Ok(());
    }

    if args.len() > 1 && args[1] == "--headless" {
        // Headless mode
        let scenario_name = args.get(2).map(|s| s.as_str()).unwrap_or("standard");
        let output_dir = {
            let output_idx = args.iter().position(|s| s == "--output");
            output_idx.and_then(|i| args.get(i + 1)).map(PathBuf::from)
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
                Scenario::marketmaker_test(),
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
                "marketmaker-test" | "marketmaker_test" => Scenario::marketmaker_test(),
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
            if let Err(e) = run_headless(&scenario, out_dir) {
                eprintln!("Error: {}", e);
                std::process::exit(1);
            }
        }
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
