//! Parameter sweep tool for Auto-Tune market simulation.
//!
//! Runs the standard scenario across a grid of parameter values and emits
//! a CSV with stability and performance metrics for each combination.
//!
//! Usage: cargo run --release -- --sweep [--output sweep-results.csv]

use std::collections::HashMap;
use std::io::Write;

use crate::config::{ArchetypeConfig, SimConfig};
use crate::loan::LoanStatus;
use crate::player::Archetype;
use crate::simulation::Simulation;

/// Parameter grid to sweep
#[derive(Clone, Debug)]
pub struct SweepConfig {
    /// sell_pressure_multiplier range (min, max, step)
    pub sell_pressure: (f64, f64, f64),
    /// base_spread range (min, max, step)
    pub base_spread: (f64, f64, f64),
    /// max_price_change_percent range (min, max, step)
    pub max_change: (f64, f64, f64),
    /// trend_dampening range (min, max, step)
    pub trend_dampening: (f64, f64, f64),
    /// number of ticks to run per scenario (default: 288 * 7 = 7 days)
    pub duration_ticks: u64,
}

impl Default for SweepConfig {
    fn default() -> Self {
        Self {
            // Yesterday's findings: sell_pressure 0.7-0.8 helps prevent underselling
            sell_pressure: (0.5, 1.0, 0.1),
            // Yesterday's findings: base_spread 0.15-0.20 is sweet spot
            base_spread: (0.10, 0.30, 0.05),
            // Yesterday: max_change 1.0-1.5 is stable
            max_change: (0.5, 2.0, 0.25),
            // New parameter to test: trend_dampening
            trend_dampening: (0.0, 0.15, 0.05),
            duration_ticks: 288 * 7,
        }
    }
}

impl SweepConfig {
    /// Generate all parameter combinations as a flat list
    pub fn grid(&self) -> Vec<HashMap<String, f64>> {
        let mut combos = Vec::new();
        let mut sp = self.sell_pressure.0;
        while sp <= self.sell_pressure.1 + 1e-9 {
            let mut bs = self.base_spread.0;
            while bs <= self.base_spread.1 + 1e-9 {
                let mut mc = self.max_change.0;
                while mc <= self.max_change.1 + 1e-9 {
                    let mut td = self.trend_dampening.0;
                    while td <= self.trend_dampening.1 + 1e-9 {
                        let mut m = HashMap::new();
                        m.insert("sell_pressure_multiplier".into(), sp);
                        m.insert("base_spread".into(), bs);
                        m.insert("max_price_change_percent".into(), mc);
                        m.insert("trend_dampening".into(), td);
                        combos.push(m);
                        td = (td * 100.0 + self.trend_dampening.2 * 100.0).round() / 100.0;
                    }
                    mc = (mc * 100.0 + self.max_change.2 * 100.0).round() / 100.0;
                }
                bs = (bs * 100.0 + self.base_spread.2 * 100.0).round() / 100.0;
            }
            sp = (sp * 100.0 + self.sell_pressure.2 * 100.0).round() / 100.0;
        }
        combos
    }
}

#[derive(Debug, Clone)]
pub struct SweepResult {
    pub sell_pressure_multiplier: f64,
    pub base_spread: f64,
    pub max_price_change_percent: f64,
    pub trend_dampening: f64,
    /// Average price displacement from base across all items (% below or above base)
    pub avg_price_displacement: f64,
    /// Average volatility (stddev/mean) across items
    pub avg_volatility: f64,
    /// Average BPD spread in last 10 ticks
    pub avg_final_bpd: f64,
    /// Average SPD spread in last 10 ticks
    pub avg_final_spd: f64,
    /// Final GDP
    pub final_gdp: f64,
    /// Final total debt
    pub final_debt: f64,
    /// Debt to GDP ratio (warn if > 10x)
    pub debt_gdp_ratio: f64,
    /// Total transactions
    pub total_tx: usize,
    /// Buy/sell ratio
    pub buy_ratio: f64,
    /// Stable (1.0) or not (0.0)
    pub stable: f64,
    /// Active loans at end
    pub active_loans: usize,
    /// Defaulted loans
    pub defaulted_loans: usize,
}

impl SweepResult {
    pub fn header_csv() -> &'static str {
        "sell_pressure_multiplier,base_spread,max_price_change_percent,trend_dampening,avg_price_displacement_pct,avg_volatility,avg_final_bpd_pct,avg_final_spd_pct,final_gdp,final_debt,debt_gdp_ratio,total_tx,buy_ratio,stable,active_loans,defaulted_loans"
    }

    pub fn to_csv(&self) -> String {
        format!(
            "{:.2},{:.2},{:.2},{:.3},{:.2},{:.4},{:.3},{:.3},{:.2},{:.2},{:.3},{},{:.3},{:.0},{},{}",
            self.sell_pressure_multiplier,
            self.base_spread,
            self.max_price_change_percent,
            self.trend_dampening,
            self.avg_price_displacement,
            self.avg_volatility,
            self.avg_final_bpd * 100.0,
            self.avg_final_spd * 100.0,
            self.final_gdp,
            self.final_debt,
            self.debt_gdp_ratio,
            self.total_tx,
            self.buy_ratio,
            self.stable,
            self.active_loans,
            self.defaulted_loans,
        )
    }
}

/// Run a single simulation with given parameters and return key metrics.
fn run_single(
    sell_pressure: f64,
    base_spread: f64,
    max_change: f64,
    trend_dampening: f64,
    duration_ticks: u64,
) -> SweepResult {
    let mut config = SimConfig::default();
    config.economy.sell_pressure_multiplier = sell_pressure;
    config.spread.base_spread = base_spread;
    config.economy.max_price_change_percent = max_change;
    config.economy.trend_dampening = trend_dampening;

    let mut sim = Simulation::new(config.clone());

    // Standard player mix
    let archetype_map: HashMap<String, Archetype> = [
        ("Casual".into(), Archetype::Casual),
        ("Farmer".into(), Archetype::Farmer),
        ("Trader".into(), Archetype::Trader),
        ("Hoarder".into(), Archetype::Hoarder),
    ]
    .into_iter()
    .collect();

    let players = vec![
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
    ];

    for player_cfg in &players {
        let archetype = *archetype_map.get(&player_cfg.archetype).unwrap_or_else(|| {
            panic!(
                "sweep基准配置引用了未知原型: '{}'. 已知原型: {:?}",
                player_cfg.archetype,
                archetype_map.keys().collect::<Vec<_>>()
            )
        });
        for _ in 0..player_cfg.count {
            sim.add_player(archetype);
        }
    }

    sim.paused = false;

    while sim.current_tick < duration_ticks {
        sim.tick();
    }

    // Compute metrics
    let mut avg_displacement = 0.0;
    let mut avg_volatility = 0.0;
    let mut avg_final_bpd = 0.0;
    let mut avg_final_spd = 0.0;

    let item_count = sim.engine.items.len();
    for item in &sim.engine.items {
        let displacement = if item.base_price > 0.0 {
            (item.price - item.base_price) / item.base_price * 100.0
        } else {
            0.0
        };
        avg_displacement += displacement;

        // Volatility from price history
        if item.price_history.len() > 10 {
            let window = item.price_history.len().min(100);
            let recent: Vec<f64> =
                item.price_history[item.price_history.len().saturating_sub(window)..].to_vec();
            if !recent.is_empty() {
                let mean = recent.iter().sum::<f64>() / recent.len() as f64;
                let variance = recent
                    .iter()
                    .map(|p| {
                        let d = p - mean;
                        d * d
                    })
                    .sum::<f64>()
                    / recent.len() as f64;
                avg_volatility += variance.sqrt() / mean.max(0.01);
            }
        }

        avg_final_bpd += item.spread.bpd;
        avg_final_spd += item.spread.spd;
    }

    if item_count > 0 {
        avg_displacement /= item_count as f64;
        avg_volatility /= item_count as f64;
        avg_final_bpd /= item_count as f64;
        avg_final_spd /= item_count as f64;
    }

    let total_tx = sim.transactions.len();
    let buy_tx = sim
        .transactions
        .iter()
        .filter(|t| t.tx_type == crate::engine::TransactionType::Buy)
        .count();
    let buy_ratio = buy_tx as f64 / total_tx.max(1) as f64;

    let (final_gdp, final_debt) = sim
        .economy_snapshots
        .last()
        .map(|s| (s.gdp, s.total_debt))
        .unwrap_or((0.0, 0.0));

    let debt_gdp_ratio = if final_gdp > 0.0 {
        final_debt / final_gdp
    } else {
        0.0
    };

    let active_loans = sim
        .loans
        .iter()
        .filter(|l| l.status == LoanStatus::Active)
        .count();
    let defaulted_loans = sim
        .loans
        .iter()
        .filter(|l| l.status == LoanStatus::Defaulted)
        .count();

    let stable = if avg_volatility < 0.05 { 1.0 } else { 0.0 };

    SweepResult {
        sell_pressure_multiplier: sell_pressure,
        base_spread,
        max_price_change_percent: max_change,
        trend_dampening,
        avg_price_displacement: avg_displacement,
        avg_volatility,
        avg_final_bpd,
        avg_final_spd,
        final_gdp,
        final_debt,
        debt_gdp_ratio,
        total_tx,
        buy_ratio,
        stable,
        active_loans,
        defaulted_loans,
    }
}

/// Run the full parameter sweep and print results as CSV.
pub fn run_sweep(sweep_config: &SweepConfig) {
    let combos = sweep_config.grid();
    let total = combos.len();

    println!("=== Parameter Sweep ===");
    println!("  Configurations: {}", total);
    println!(
        "  sell_pressure: {:.2} – {:.2} (step {:.2})",
        sweep_config.sell_pressure.0, sweep_config.sell_pressure.1, sweep_config.sell_pressure.2
    );
    println!(
        "  base_spread: {:.2} – {:.2} (step {:.2})",
        sweep_config.base_spread.0, sweep_config.base_spread.1, sweep_config.base_spread.2
    );
    println!(
        "  max_change: {:.2} – {:.2} (step {:.2})",
        sweep_config.max_change.0, sweep_config.max_change.1, sweep_config.max_change.2
    );
    println!(
        "  trend_dampening: {:.3} – {:.3} (step {:.3})",
        sweep_config.trend_dampening.0,
        sweep_config.trend_dampening.1,
        sweep_config.trend_dampening.2
    );
    println!(
        "  Duration: {} ticks ({} days)",
        sweep_config.duration_ticks,
        sweep_config.duration_ticks / 288
    );
    println!();
    println!("{}", SweepResult::header_csv());

    let mut results = Vec::new();
    for (i, params) in combos.iter().enumerate() {
        let sp = *params
            .get("sell_pressure_multiplier")
            .expect("sweep grid: missing 'sell_pressure_multiplier' — grid generator and read site are out of sync");
        let bs = *params.get("base_spread").expect(
            "sweep grid: missing 'base_spread' — grid generator and read site are out of sync",
        );
        let mc = *params
            .get("max_price_change_percent")
            .expect("sweep grid: missing 'max_price_change_percent' — grid generator and read site are out of sync");
        let td = *params.get("trend_dampening").expect(
            "sweep grid: missing 'trend_dampening' — grid generator and read site are out of sync",
        );

        eprint!(
            "\r  [{:3}/{:3}] sp={:.2} bs={:.2} mc={:.2} td={:.3}",
            i + 1,
            total,
            sp,
            bs,
            mc,
            td
        );
        std::io::stderr().flush().ok();

        let result = run_single(sp, bs, mc, td, sweep_config.duration_ticks);
        println!();
        println!("{}", result.to_csv());
        results.push(result);
    }

    eprintln!("\n  Sweep complete. {} configurations tested.", total);

    // Print analysis
    print_sweep_analysis(&results);
}

fn print_sweep_analysis(results: &[SweepResult]) {
    println!("\n=== Sweep Analysis ===");

    // Stable configurations
    let stable: Vec<_> = results.iter().filter(|r| r.stable > 0.5).collect();
    println!("\n--- Stable configs (volatility < 0.05) ---");
    if stable.is_empty() {
        println!("  (none)");
    } else {
        // Sort by buy_ratio descending (want configs with healthy buy demand)
        let mut sorted = stable.clone();
        sorted.sort_by(|a, b| {
            b.buy_ratio
                .partial_cmp(&a.buy_ratio)
                .unwrap_or(std::cmp::Ordering::Equal)
        });
        for r in sorted.iter().take(5) {
            println!(
                "  sp={:.2} bs={:.2} mc={:.2} td={:.3} | displacement={:+.1}% buy_ratio={:.1}% vol={:.4}",
                r.sell_pressure_multiplier,
                r.base_spread,
                r.max_price_change_percent,
                r.trend_dampening,
                r.avg_price_displacement,
                r.buy_ratio * 100.0,
                r.avg_volatility,
            );
        }
    }

    // Configs with least underselling (displacement closest to 0)
    let mut by_displacement = results.to_vec();
    by_displacement.sort_by_key(|r| (r.avg_price_displacement.abs() * 100.0) as i32);
    println!("\n--- Least price displacement (closest to base) ---");
    for r in by_displacement.iter().take(5) {
        println!(
            "  sp={:.2} bs={:.2} mc={:.2} td={:.3} | displacement={:+.1}% buy_ratio={:.1}% vol={:.4} {}",
            r.sell_pressure_multiplier,
            r.base_spread,
            r.max_price_change_percent,
            r.trend_dampening,
            r.avg_price_displacement,
            r.buy_ratio * 100.0,
            r.avg_volatility,
            if r.stable > 0.5 { "STABLE" } else { "UNSTABLE" },
        );
    }

    // Configs with best buy ratio (balanced economy)
    let mut by_buy_ratio = results.to_vec();
    by_buy_ratio.sort_by(|a, b| {
        b.buy_ratio
            .partial_cmp(&a.buy_ratio)
            .unwrap_or(std::cmp::Ordering::Equal)
    });
    println!("\n--- Best buy ratios (balanced economy) ---");
    for r in by_buy_ratio.iter().take(5) {
        println!(
            "  sp={:.2} bs={:.2} mc={:.2} td={:.3} | buy_ratio={:.1}% displacement={:+.1}% vol={:.4} {}",
            r.sell_pressure_multiplier,
            r.base_spread,
            r.max_price_change_percent,
            r.trend_dampening,
            r.buy_ratio * 100.0,
            r.avg_price_displacement,
            r.avg_volatility,
            if r.stable > 0.5 { "STABLE" } else { "UNSTABLE" },
        );
    }
}
