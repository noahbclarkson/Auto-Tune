//! Regression detection for the Rust market simulation engine.
//!
//! Runs a deterministic simulation and compares outputs against a stored baseline.
//! Use this to detect drift when engine parameters or logic are changed.
//!
//! Usage:
//!   cargo run --release -- --regression          # compare against baseline
//!   cargo run --release -- --regression --update # update baseline
//!   cargo run --release -- --regression --baseline # print current baseline

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

use crate::engine::{ItemState, PriceTrendDirection};
use crate::loan::LoanStatus;
use crate::player::Archetype;
use crate::simulation::Simulation;

/// Signature of a single item at a checkpoint tick.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ItemSignature {
    pub name: String,
    pub base_price: f64,
    pub price: f64,
    pub bpd: f64,
    pub spd: f64,
    pub trend_direction: String,
    pub avg_volatility: f64,
}

/// Signature of the full simulation at a checkpoint.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CheckpointSignature {
    pub tick: u64,
    pub item_signatures: Vec<ItemSignature>,
    pub gdp: f64,
    pub total_debt: f64,
    pub debt_gdp_ratio: f64,
    pub online_players: i32,
    pub total_players: i32,
    pub active_loans: usize,
    pub defaulted_loans: usize,
    pub total_transactions: usize,
    pub buy_ratio: f64,
    pub avg_volatility: f64,
    pub global_vol_mult: f64,
}

/// Complete regression baseline for one scenario.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScenarioBaseline {
    pub scenario_name: String,
    pub engine_git_rev: String,
    pub config_hash: String,
    pub checkoints: Vec<CheckpointSignature>,
    pub final_summary: FinalSignature,
}

/// Final summary signature (computed from last checkpoint).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FinalSignature {
    pub avg_price_displacement_pct: f64,
    pub avg_volatility: f64,
    pub avg_final_bpd: f64,
    pub avg_final_spd: f64,
    pub final_gdp: f64,
    pub final_debt: f64,
    pub debt_gdp_ratio: f64,
    pub total_transactions: usize,
    pub buy_ratio: f64,
    pub stable: bool,
}

impl ScenarioBaseline {
    pub fn new(scenario_name: String, engine_git_rev: String, config_hash: String) -> Self {
        Self {
            scenario_name,
            engine_git_rev,
            config_hash,
            checkoints: Vec::new(),
            final_summary: FinalSignature {
                avg_price_displacement_pct: 0.0,
                avg_volatility: 0.0,
                avg_final_bpd: 0.0,
                avg_final_spd: 0.0,
                final_gdp: 0.0,
                final_debt: 0.0,
                debt_gdp_ratio: 0.0,
                total_transactions: 0,
                buy_ratio: 0.0,
                stable: false,
            },
        }
    }

    pub fn to_json(&self) -> String {
        serde_json::to_string_pretty(self).unwrap()
    }

    pub fn from_json(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }
}

/// Checkpoint ticks to record signatures at.
fn checkpoint_ticks(duration: u64) -> Vec<u64> {
    let n_checkpoints = 5;
    (0..n_checkpoints)
        .map(|i| (duration * (i as u64 + 1) / n_checkpoints as u64).min(duration))
        .collect()
}

fn compute_item_signature(item: &ItemState, base_price: f64) -> ItemSignature {
    let volatility = if item.price_history.len() > 10 {
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
            variance.sqrt() / mean.max(0.01)
        } else {
            0.0
        }
    } else {
        0.0
    };

    let trend_str = match item.trend_direction {
        PriceTrendDirection::Up => "Up",
        PriceTrendDirection::Down => "Down",
        PriceTrendDirection::Stable => "Stable",
    };

    ItemSignature {
        name: item.name.clone(),
        base_price,
        price: item.price,
        bpd: item.spread.bpd,
        spd: item.spread.spd,
        trend_direction: trend_str.to_string(),
        avg_volatility: volatility,
    }
}

fn compute_checkpoint(items: &[ItemState], sim: &Simulation, tick: u64) -> CheckpointSignature {
    let item_sigs: Vec<ItemSignature> = items
        .iter()
        .map(|item| {
            let base = sim
                .config
                .items
                .iter()
                .find(|i| i.name == item.name)
                .map(|i| i.base_price)
                .unwrap_or(item.base_price);
            compute_item_signature(item, base)
        })
        .collect();

    let total_tx = sim.transactions.len();
    let buy_tx = sim
        .transactions
        .iter()
        .filter(|t| matches!(t.tx_type, crate::engine::TransactionType::Buy))
        .count();
    let buy_ratio = buy_tx as f64 / total_tx.max(1) as f64;

    let active_loans = sim
        .loans
        .iter()
        .filter(|l| l.status == LoanStatus::Active)
        .count();
    let defaulted = sim
        .loans
        .iter()
        .filter(|l| l.status == LoanStatus::Defaulted)
        .count();

    let (gdp, total_debt, online_players) = sim
        .economy_snapshots
        .last()
        .map(|s| (s.gdp, s.total_debt, s.online_players))
        .unwrap_or((0.0, 0.0, 0));

    let debt_gdp_ratio = if gdp > 0.0 { total_debt / gdp } else { 0.0 };

    let avg_vol = if !item_sigs.is_empty() {
        item_sigs.iter().map(|s| s.avg_volatility).sum::<f64>() / item_sigs.len() as f64
    } else {
        0.0
    };

    CheckpointSignature {
        tick,
        item_signatures: item_sigs,
        gdp,
        total_debt,
        debt_gdp_ratio,
        online_players,
        total_players: sim.players.len() as i32,
        active_loans,
        defaulted_loans: defaulted,
        total_transactions: total_tx,
        buy_ratio,
        avg_volatility: avg_vol,
        global_vol_mult: sim.engine.global_volume_multiplier,
    }
}

fn compute_final_summary(baseline: &mut ScenarioBaseline) {
    let last = baseline.checkoints.last();
    if let Some(cp) = last {
        let avg_disp: f64 = cp
            .item_signatures
            .iter()
            .map(|item| {
                if item.base_price > 0.0 {
                    (item.price - item.base_price) / item.base_price * 100.0
                } else {
                    0.0
                }
            })
            .sum::<f64>()
            / cp.item_signatures.len().max(1) as f64;

        baseline.final_summary = FinalSignature {
            avg_price_displacement_pct: avg_disp,
            avg_volatility: cp.avg_volatility,
            avg_final_bpd: cp.item_signatures.iter().map(|i| i.bpd).sum::<f64>()
                / cp.item_signatures.len().max(1) as f64,
            avg_final_spd: cp.item_signatures.iter().map(|i| i.spd).sum::<f64>()
                / cp.item_signatures.len().max(1) as f64,
            final_gdp: cp.gdp,
            final_debt: cp.total_debt,
            debt_gdp_ratio: cp.debt_gdp_ratio,
            total_transactions: cp.total_transactions,
            buy_ratio: cp.buy_ratio,
            stable: cp.avg_volatility < 0.05,
        };
    }
}

/// Tolerance for floating point comparisons.
#[derive(Debug, Clone)]
pub struct Tolerance {
    pub spread_abs: f64,    // absolute tolerance for spread comparisons
    pub pct_threshold: f64, // % difference to flag as regression
}

impl Default for Tolerance {
    fn default() -> Self {
        Self {
            spread_abs: 0.0001,  // 0.01% spread tolerance
            pct_threshold: 0.05, // flag if any single item differs by >5%
        }
    }
}

/// Result of comparing one item.
#[derive(Debug)]
#[allow(dead_code)]
pub struct ItemDelta {
    pub name: String,
    pub price_pct_diff: f64,
    pub bpd_abs_diff: f64,
    pub spd_abs_diff: f64,
    pub flagged: bool,
}

/// Result of comparing two checkpoints.
#[derive(Debug)]
#[allow(dead_code)]
pub struct CheckpointDelta {
    pub tick: u64,
    pub item_deltas: Vec<ItemDelta>,
    pub gdp_pct_diff: f64,
    pub debt_pct_diff: f64,
    pub tx_count_diff: i64,
    pub flagged: bool,
}

/// Compare two baselines and return deltas.
pub fn compare_baselines(
    current: &ScenarioBaseline,
    baseline: &ScenarioBaseline,
    tol: &Tolerance,
) -> RegressionReport {
    let mut report = RegressionReport {
        scenario_name: current.scenario_name.clone(),
        current_rev: current.engine_git_rev.clone(),
        baseline_rev: baseline.engine_git_rev.clone(),
        checkpoint_deltas: Vec::new(),
        final_delta: None,
        passed: true,
        flagged_items: Vec::new(),
    };

    for (curr_cp, base_cp) in current.checkoints.iter().zip(baseline.checkoints.iter()) {
        if curr_cp.tick != base_cp.tick {
            continue;
        }
        let mut cp_delta = CheckpointDelta {
            tick: curr_cp.tick,
            item_deltas: Vec::new(),
            gdp_pct_diff: 0.0,
            debt_pct_diff: 0.0,
            tx_count_diff: 0,
            flagged: false,
        };

        for (curr_item, base_item) in curr_cp
            .item_signatures
            .iter()
            .zip(base_cp.item_signatures.iter())
        {
            let price_pct = if base_item.price > 0.001 {
                (curr_item.price - base_item.price).abs() / base_item.price
            } else {
                0.0
            };

            let bpd_abs = (curr_item.bpd - base_item.bpd).abs();
            let spd_abs = (curr_item.spd - base_item.spd).abs();

            let flagged = price_pct > tol.pct_threshold
                || bpd_abs > tol.spread_abs * 10.0
                || spd_abs > tol.spread_abs * 10.0;

            if flagged {
                report.flagged_items.push(format!(
                    "  tick {:5} | {:20} | price {:8.4}→{:8.4} ({:+.2}%) | BPD {:.5}→{:.5} ({:+.5}) | SPD {:.5}→{:.5} ({:+.5})",
                    curr_cp.tick,
                    curr_item.name,
                    base_item.price,
                    curr_item.price,
                    price_pct * 100.0,
                    base_item.bpd,
                    curr_item.bpd,
                    bpd_abs,
                    base_item.spd,
                    curr_item.spd,
                    spd_abs,
                ));
            }

            cp_delta.item_deltas.push(ItemDelta {
                name: curr_item.name.clone(),
                price_pct_diff: price_pct,
                bpd_abs_diff: bpd_abs,
                spd_abs_diff: spd_abs,
                flagged,
            });

            if flagged {
                cp_delta.flagged = true;
                report.passed = false;
            }
        }

        cp_delta.gdp_pct_diff = if base_cp.gdp > 0.001 {
            (curr_cp.gdp - base_cp.gdp).abs() / base_cp.gdp
        } else {
            0.0
        };
        cp_delta.debt_pct_diff = if base_cp.total_debt > 0.001 {
            (curr_cp.total_debt - base_cp.total_debt).abs() / base_cp.total_debt
        } else {
            0.0
        };
        cp_delta.tx_count_diff =
            curr_cp.total_transactions as i64 - base_cp.total_transactions as i64;

        if cp_delta.gdp_pct_diff > 0.10 || cp_delta.debt_pct_diff > 0.10 {
            cp_delta.flagged = true;
            report.passed = false;
        }

        report.checkpoint_deltas.push(cp_delta);
    }

    // Final summary comparison
    if let Some(curr_final) = current.checkoints.last()
        && let Some(base_final) = baseline.checkoints.last()
    {
        let avg_disp_curr = curr_final
            .item_signatures
            .iter()
            .map(|i| {
                if i.base_price > 0.0 {
                    (i.price - i.base_price) / i.base_price * 100.0
                } else {
                    0.0
                }
            })
            .sum::<f64>()
            / curr_final.item_signatures.len().max(1) as f64;
        let avg_disp_base = base_final
            .item_signatures
            .iter()
            .map(|i| {
                if i.base_price > 0.0 {
                    (i.price - i.base_price) / i.base_price * 100.0
                } else {
                    0.0
                }
            })
            .sum::<f64>()
            / base_final.item_signatures.len().max(1) as f64;

        let avg_bpd_curr = curr_final
            .item_signatures
            .iter()
            .map(|i| i.bpd)
            .sum::<f64>()
            / curr_final.item_signatures.len().max(1) as f64;
        let avg_bpd_base = base_final
            .item_signatures
            .iter()
            .map(|i| i.bpd)
            .sum::<f64>()
            / base_final.item_signatures.len().max(1) as f64;

        let avg_spd_curr = curr_final
            .item_signatures
            .iter()
            .map(|i| i.spd)
            .sum::<f64>()
            / curr_final.item_signatures.len().max(1) as f64;
        let avg_spd_base = base_final
            .item_signatures
            .iter()
            .map(|i| i.spd)
            .sum::<f64>()
            / base_final.item_signatures.len().max(1) as f64;

        let vol_curr = curr_final.avg_volatility;
        let vol_base = base_final.avg_volatility;

        report.final_delta = Some(FinalDelta {
            avg_displacement_pct: (avg_disp_curr - avg_disp_base).abs(),
            avg_bpd_diff: (avg_bpd_curr - avg_bpd_base).abs(),
            avg_spd_diff: (avg_spd_curr - avg_spd_base).abs(),
            volatility_diff: (vol_curr - vol_base).abs(),
        });
    }

    report
}

#[derive(Debug)]
pub struct FinalDelta {
    pub avg_displacement_pct: f64,
    pub avg_bpd_diff: f64,
    pub avg_spd_diff: f64,
    pub volatility_diff: f64,
}

#[derive(Debug)]
pub struct RegressionReport {
    pub scenario_name: String,
    pub current_rev: String,
    pub baseline_rev: String,
    pub checkpoint_deltas: Vec<CheckpointDelta>,
    pub final_delta: Option<FinalDelta>,
    pub passed: bool,
    pub flagged_items: Vec<String>,
}

impl RegressionReport {
    pub fn print(&self) {
        println!("\n=== Regression Report: {} ===", self.scenario_name);
        println!("Current rev:  {}", self.current_rev);
        println!("Baseline rev: {}", self.baseline_rev);
        println!("Result: {}", if self.passed { "PASS ✓" } else { "FAIL ✗" });

        if let Some(fd) = &self.final_delta {
            println!("\n--- Final Summary Delta ---");
            println!("  Avg displacement: {:+.3}%", fd.avg_displacement_pct);
            println!("  Avg BPD:          {:+.5}", fd.avg_bpd_diff);
            println!("  Avg SPD:          {:+.5}", fd.avg_spd_diff);
            println!("  Avg volatility:   {:+.5}", fd.volatility_diff);
        }

        if !self.flagged_items.is_empty() {
            println!("\n--- Flagged Items (price > 5% different) ---");
            for item in &self.flagged_items {
                println!("{}", item);
            }
        }

        println!();
    }
}

/// Run a deterministic regression test for a scenario and return its baseline.
pub fn run_regression_scenario(scenario: &crate::Scenario, git_rev: &str) -> ScenarioBaseline {
    let archetype_map: HashMap<String, Archetype> = [
        ("Casual".into(), Archetype::Casual),
        ("Farmer".into(), Archetype::Farmer),
        ("Trader".into(), Archetype::Trader),
        ("Hoarder".into(), Archetype::Hoarder),
        ("Exploiter".into(), Archetype::Exploiter),
        ("Newbie".into(), Archetype::Newbie),
        ("AFKFarmer".into(), Archetype::AFKFarmer),
        ("GuildBuyer".into(), Archetype::GuildBuyer),
        ("MarketMaker".into(), Archetype::MarketMaker),
    ]
    .into_iter()
    .collect();

    let config_json = serde_json::to_string(&scenario.config).unwrap();
    let config_hash = format!("{:x}", md5_hash(&config_json));

    let mut baseline =
        ScenarioBaseline::new(scenario.name.clone(), git_rev.to_string(), config_hash);

    // Use a fixed seed for deterministic, reproducible regression runs.
    // Changing this seed will produce a different simulation trajectory.
    #[allow(clippy::unusual_byte_groupings)]
    const REGRESSION_SEED: u64 = 0xC0FF_EE_DEAD_BEEF_u64;
    let mut sim = Simulation::new_seeded(scenario.config.clone(), REGRESSION_SEED);

    for player_cfg in &scenario.players {
        let archetype = *archetype_map.get(&player_cfg.archetype).unwrap_or_else(|| {
            panic!(
                "regression scenario references unknown archetype: '{}'. Known archetypes: {:?}",
                player_cfg.archetype,
                archetype_map.keys().collect::<Vec<_>>()
            )
        });
        for _ in 0..player_cfg.count {
            sim.add_player(archetype);
        }
    }

    sim.paused = false;

    let checkpoints: Vec<u64> = checkpoint_ticks(scenario.duration_ticks);
    let mut next_checkpoint = 0;

    while sim.current_tick <= scenario.duration_ticks {
        sim.tick();

        while next_checkpoint < checkpoints.len()
            && sim.current_tick >= checkpoints[next_checkpoint]
        {
            let cp = compute_checkpoint(&sim.engine.items, &sim, sim.current_tick);
            baseline.checkoints.push(cp);
            next_checkpoint += 1;
        }

        if sim.current_tick >= scenario.duration_ticks {
            break;
        }
    }

    compute_final_summary(&mut baseline);
    baseline
}

fn md5_hash(input: &str) -> u128 {
    // Simple but deterministic — just fold chars into u128
    let mut h: u128 = 0;
    for (i, b) in input.bytes().enumerate() {
        h = h
            .wrapping_mul(31)
            .wrapping_add((b as u128).wrapping_mul((i as u128).wrapping_add(1)));
    }
    h
}

/// Regression test runner.
pub fn run_regression_test(
    scenarios: &[crate::Scenario],
    baseline_dir: &std::path::Path,
    update: bool,
) {
    let git_rev = get_git_rev();
    let tol = Tolerance::default();

    println!("=== Regression Test ===");
    println!("Git rev: {}", git_rev);
    println!("Baseline dir: {}", baseline_dir.display());
    println!(
        "Tolerance: price > {:.1}% or spread > {:.4} flagged as regression",
        tol.pct_threshold * 100.0,
        tol.spread_abs * 10.0
    );
    println!();

    let mut all_passed = true;

    for scenario in scenarios {
        let baseline_path = baseline_dir.join(format!(
            "{}.json",
            scenario.name.to_lowercase().replace(' ', "_")
        ));

        println!("\n--- Scenario: {} ---", scenario.name);
        let current = run_regression_scenario(scenario, &git_rev);

        if update {
            // Write baseline
            std::fs::create_dir_all(baseline_dir).ok();
            std::fs::write(&baseline_path, current.to_json()).ok();
            println!("  Updated baseline: {}", baseline_path.display());
            continue;
        }

        // Load baseline
        let baseline_json = match std::fs::read_to_string(&baseline_path) {
            Ok(s) => s,
            Err(_) => {
                println!(
                    "  No baseline found at {}. Run with --update to create one.",
                    baseline_path.display()
                );
                continue;
            }
        };

        let baseline = match ScenarioBaseline::from_json(&baseline_json) {
            Ok(b) => b,
            Err(e) => {
                println!("  Failed to parse baseline: {}", e);
                continue;
            }
        };

        let report = compare_baselines(&current, &baseline, &tol);
        report.print();

        if !report.passed {
            all_passed = false;
        }
    }

    println!(
        "\n=== Overall: {} ===",
        if all_passed {
            "ALL PASSED ✓"
        } else {
            "REGRESSIONS FOUND ✗"
        }
    );
}

fn get_git_rev() -> String {
    std::process::Command::new("git")
        .args(["rev-parse", "--short=8", "HEAD"])
        .output()
        .ok()
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|| "unknown".to_string())
}
