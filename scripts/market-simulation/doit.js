const fs = require('fs');
let c = fs.readFileSync('src/main.rs', 'utf8');
const old = '/// This test runs both fixes against the control (tier3=30, min_int=0.0) on 2 seeds\n/// to confirm whether the fix resolves the 60-day instability.\nfn run_sixty_day_fix_test() {';
const replacement = `// ═══════════════════════════════════════════════════════════════════
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

    println!(
        "
╔════════════════════════════════════════════════════════════════╗"
    );
    println!("║         90-DAY PRODUCTION STABILITY TEST                     ║");
    println!("║  2MM + 2GB + 60% Diamond floor × 3 seeds × 2 thresholds       ║");
    println!("║  Questions: Doom loop past 60d? 5% vs 7% at 90d?            ║");
    println!(
        "╚════════════════════════════════════════════════════════════════╝\n"
    );
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
        "──────", "────────", "────────────", "──────────", "────────", "────────", "────────", "──────────"
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
                    "FAILED", "—", "—", "—", "—", "—"
                );
            }

            let _ = std::fs::remove_dir_all(&out_dir);
        }
    }

    if results.len() >= 4 {
        println!(
            "
  ── Per-Threshold Averages (90-day) ──"
        );
        for &threshold in &thresholds {
            let subset: Vec<_> = results.iter().filter(|r| r.threshold == threshold).collect();
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

            println!(
                "
  ══════════════════════════════════════════════════════════════════"
            );
            println!("║  5% vs 7% THRESHOLD AT 90 DAYS                              ║");
            println!(
                "╠═════════════════════════════════════════════════════════════════╣"
            );
            println!(
                "║  Metric      5%           7%           Change       Verdict  ║"
            );
            println!(
                "╠═════════════════════════════════════════════════════════════════╣"
            );
            println!(
                "║  GDP         {:>10.0}  {:>10.0}  {:>+8.1}%      {:>8}  ║",
                gdp5 as i64,
                gdp7 as i64,
                gdp_chg,
                gdp_chg > 5.0 ? "5% better" : gdp_chg < -5.0 ? "7% better" : "~Neutral"
            );
            println!(
                "║  D/G         {:>10.3}x  {:>10.3}x  {:>+8.3}x     {:>8}  ║",
                dg5,
                dg7,
                dg_diff,
                dg_ratio > 1.5 ? "5% MUCH better"
                    : dg_ratio > 1.1 ? "5% better"
                    : dg_ratio < 0.9 ? "7% better"
                    : "~Neutral"
            );
            println!(
                "║  Vol(CV)     {:>10.4}  {:>10.4}  {:>+8.1}%     {:>8}  ║",
                vol5,
                vol7,
                vol_chg,
                vol_chg < -10.0 ? "5% better" : vol_chg > 10.0 ? "7% better" : "~Neutral"
            );
            println!(
                "╚═════════════════════════════════════════════════════════════════╝"
            );

            println!(
                "
  ╔═══════════════════════════════════════════════════════════════╗"
            );
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
                let risk = r.dg >= 30.0 ? "🔴 CRITICAL"
                    : r.dg >= 20.0 ? "🟠 HIGH"
                    : r.dg >= 10.0 ? "🟡 MODERATE"
                    : "🟢 HEALTHY";
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
                let risk = r.dg >= 30.0 ? "🔴 CRITICAL"
                    : r.dg >= 20.0 ? "🟠 HIGH"
                    : r.dg >= 10.0 ? "🟡 MODERATE"
                    : "🟢 HEALTHY";
                println!(
                    "║  {:>4}d  {:>10.0}  {:>10.3}x  {:>8}  {:>8}  ║",
                    days, r.gdp as i64, r.dg, "7%", risk
                );
            }
            println!("╚═══════════════════════════════════════════════════════════════╝");

            println!(
                "
  CIRCUIT BREAKER ANALYSIS (seed=42):"
            );
            println!(
                "  tier3_ratio=30 | hysteresis unlock at D/G < 15.0 | counter_cyclical=true"
            );
            if let Some(r) = s42_5 {
                let cb_state = r.dg >= 30.0 ? "TIER3 LIKELY FIRED (D/G >= 30x)"
                    : r.dg >= 15.0 ? "Circuit may have engaged (D/G 15-30x)"
                    : "Circuit did NOT engage";
                let multiplier_at_dg = (1.0 - r.dg / 30.0).max(0.0).min(1.0);
                println!(
                    "  5% threshold: D/G={:.3}x → multiplier={:.1}%  {}",
                    r.dg,
                    multiplier_at_dg * 100.0,
                    cb_state
                );
            }
            if let Some(r) = s42_7 {
                let cb_state = r.dg >= 30.0 ? "TIER3 LIKELY FIRED (D/G >= 30x)"
                    : r.dg >= 15.0 ? "Circuit may have engaged (D/G 15-30x)"
                    : "Circuit did NOT engage";
                let multiplier_at_dg = (1.0 - r.dg / 30.0).max(0.0).min(1.0);
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

            println!(
                "
  ══════════════════════════════════════════════════════════════════"
            );
            println!("  VERDICT:");
            println!("  {}", stability_verdict);
            println!("  {}", threshold_verdict);
            if worst_dg >= 20.0 {
                println!(
                    "
  ⚠️  ARCHITECTURAL FIX NEEDED: Counter-cyclical is a governor, not a cure."
                );
                println!(
                    "  Root cause: Debt compounds ~10%/day while GDP grows ~1-2%/day."
                );
                println!(
                    "  Circuit resets interest to 0% but cannot reduce existing debt stock."
                );
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

///   This test runs both fixes against the control (tier3=30, min_int=0.0) on 2 seeds
/// to confirm whether the fix resolves the 60-day instability.
fn run_sixty_day_fix_test() {`;

const new_content = c.replace(old, replacement);
fs.writeFileSync('src/main.rs', new_content);
console.log('DONE');
