//! Simulation result analyzer — reads SQLite DB files produced by the simulator
//! and emits structured analysis reports.
//!
//! Usage: cargo run --release -- --analyze <path-to-simulation.db>
//!        cargo run --release -- --analyze-dir <path-to-sim-output-dir>

use rusqlite::{Connection, params};
use std::path::Path;

/// Load an analyzer for a simulation DB.
pub fn analyze_db(path: &Path) -> Result<(), String> {
    let conn = Connection::open(path).map_err(|e| format!("Cannot open DB: {e}"))?;

    let session_row: (String, String) = conn
        .query_row(
            "SELECT started_at, config_json FROM sessions ORDER BY id DESC LIMIT 1",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .map_err(|e| format!("No session found: {e}"))?;

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║           SIMULATION RESULT ANALYZER                           ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  DB: {}", path.display());
    println!("  Session started: {}", session_row.0);
    if session_row.1.len() > 200 {
        println!("  Config: {}...[truncated]", &session_row.1[..200]);
    } else {
        println!("  Config: {}", session_row.1);
    }

    // ── Tick range ──────────────────────────────────────────────
    let (min_tick, max_tick, total_ticks): (i64, i64, i64) = conn
        .query_row(
            "SELECT MIN(tick), MAX(tick), COUNT(DISTINCT tick) FROM ticks",
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )
        .unwrap_or((0, 0, 0));
    let duration_days = max_tick as f64 / 288.0;
    println!(
        "\n  Simulation: {} – {} ticks ({:.1} days, {} snapshots)",
        min_tick, max_tick, duration_days, total_ticks
    );

    // ── Economy snapshots ────────────────────────────────────────
    let (final_gdp, final_debt, avg_price_change): (f64, f64, f64) = conn
        .query_row(
            "SELECT gdp, total_debt, avg_price_change
             FROM economy_snapshots
             ORDER BY tick DESC LIMIT 1",
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )
        .unwrap_or((0.0, 0.0, 0.0));
    let debt_gdp = if final_gdp > 0.0 {
        final_debt / final_gdp
    } else {
        0.0
    };
    println!("\n─── Economy Health ───────────────────────────────────────────");
    println!("  Final GDP:          {:>12.2}", final_gdp);
    println!("  Final Debt:        {:>12.2}", final_debt);
    println!(
        "  Debt / GDP:        {:>12.3}x  {}",
        debt_gdp,
        debt_health_note(debt_gdp)
    );
    println!("  Avg price change:  {:>+12.4}%", avg_price_change * 100.0);

    // GDP over time (early vs late)
    if let Some((early_gdp, late_gdp)) = gdp_trajectory(&conn) {
        let gdp_change = if early_gdp > 0.0 {
            (late_gdp - early_gdp) / early_gdp * 100.0
        } else {
            0.0
        };
        println!(
            "  GDP change:        {:>+12.1}%  (early={:.0} → late={:.0})",
            gdp_change, early_gdp, late_gdp
        );
    }

    // ── Loan analysis ────────────────────────────────────────────
    let (total_loans, interest_events, defaulted_events, total_interest_paid): (
        i64,
        i64,
        i64,
        f64,
    ) = conn
        .query_row(
            "SELECT COUNT(*),
                    SUM(CASE WHEN event_type = 'InterestApplied' THEN 1 ELSE 0 END),
                    SUM(CASE WHEN event_type = 'Defaulted' THEN 1 ELSE 0 END),
                    SUM(CASE WHEN event_type = 'InterestApplied' THEN amount ELSE 0 END)
             FROM loan_events",
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
        )
        .unwrap_or((0, 0, 0, 0.0));
    println!("\n─── Loan Activity ──────────────────────────────────────────");
    println!("  Total loan events:  {}", total_loans);
    println!("  Interest events:    {}", interest_events);
    println!("  Default events:    {}", defaulted_events);
    println!("  Total interest paid: {:>10.2}", total_interest_paid);
    if defaulted_events > 0 {
        let default_rate = defaulted_events as f64 / total_loans.max(1) as f64 * 100.0;
        println!("  Default rate:      {:>10.1}%  ⚠️", default_rate);
    }

    // ── Price stability ──────────────────────────────────────────
    println!("\n─── Price Stability (all items) ───────────────────────────");
    let mut stmt = conn
        .prepare(
            "SELECT item_name, base_price,
                    MIN(price) as min_price,
                    MAX(price) as max_price,
                    AVG(price) as avg_price,
                    -- First and last prices
                    (SELECT price FROM item_states i2
                     WHERE i2.item_name = item_states.item_name
                     ORDER BY tick ASC LIMIT 1) as first_price,
                    (SELECT price FROM item_states i2
                     WHERE i2.item_name = item_states.item_name
                     ORDER BY tick DESC LIMIT 1) as last_price
             FROM item_states
             WHERE tick % 288 = 0 OR tick = (SELECT MAX(tick) FROM item_states)
             GROUP BY item_name, base_price",
        )
        .map_err(|e| format!("Query failed: {e}"))?;

    let rows: Vec<(String, f64, f64, f64, f64, f64, f64)> = stmt
        .query_map([], |row| {
            Ok((
                row.get(0)?,
                row.get(1)?,
                row.get(2)?,
                row.get(3)?,
                row.get(4)?,
                row.get(5)?,
                row.get(6)?,
            ))
        })
        .map_err(|e| format!("Query failed: {e}"))?
        .filter_map(|r| r.ok())
        .collect();

    let mut table_rows: Vec<(String, f64, f64, f64, f64, f64, f64)> = Vec::new();
    for (name, base, min_p, max_p, avg, _first, last) in rows {
        let pct_range = if base > 0.0 && min_p > 0.0 {
            (max_p - min_p) / min_p * 100.0
        } else {
            0.0
        };
        let pct_final = if base > 0.0 {
            (last - base) / base * 100.0
        } else {
            0.0
        };
        table_rows.push((name, base, min_p, max_p, avg, pct_range, pct_final));
    }

    // Sort by volatility (pct_range descending)
    table_rows.sort_by(|a, b| b.5.partial_cmp(&a.5).unwrap_or(std::cmp::Ordering::Equal));

    println!(
        "  {:18} {:>9} {:>9} {:>9} {:>9} {:>9} {:>9}",
        "Item", "Base", "Min", "Max", "Avg", "Range%", "Final%"
    );
    println!(
        "  {:18} {:>9} {:>9} {:>9} {:>9} {:>9} {:>9}",
        "────", "────", "────", "────", "────", "──────", "───────"
    );
    for (name, base, min_p, max_p, avg, pct_range, pct_final) in &table_rows {
        println!(
            "  {:18} {:>9.2} {:>9.2} {:>9.2} {:>9.2} {:>+8.1}% {:>+8.1}%",
            format!("{:.18}", name),
            base,
            min_p,
            max_p,
            avg,
            pct_range,
            pct_final
        );
    }

    // ── Spread analysis ──────────────────────────────────────────
    println!("\n─── Spread Health ──────────────────────────────────────────");
    let mut stmt = conn
        .prepare(
            "SELECT item_name,
                    AVG(bpd) as avg_bpd,
                    MAX(bpd) as max_bpd,
                    MIN(bpd) as min_bpd,
                    AVG(spd) as avg_spd,
                    MAX(spd) as max_spd,
                    MIN(spd) as min_spd
             FROM item_states
             GROUP BY item_name",
        )
        .map_err(|e| format!("Query failed: {e}"))?;

    let spread_rows: Vec<(String, f64, f64, f64, f64, f64, f64)> = stmt
        .query_map([], |row| {
            Ok((
                row.get(0)?,
                row.get(1)?,
                row.get(2)?,
                row.get(3)?,
                row.get(4)?,
                row.get(5)?,
                row.get(6)?,
            ))
        })
        .map_err(|e| format!("Query failed: {e}"))?
        .filter_map(|r| r.ok())
        .collect();

    let avg_all_bpd: f64 =
        spread_rows.iter().map(|r| r.1).sum::<f64>() / spread_rows.len().max(1) as f64;
    let avg_all_spd: f64 =
        spread_rows.iter().map(|r| r.4).sum::<f64>() / spread_rows.len().max(1) as f64;
    let max_bpd_seen: f64 = spread_rows.iter().map(|r| r.2).fold(0.0, f64::max);
    let max_spd_seen: f64 = spread_rows.iter().map(|r| r.5).fold(0.0, f64::max);

    println!(
        "  Average BPD:  {:.2}%   Average SPD:  {:.2}%",
        avg_all_bpd * 100.0,
        avg_all_spd * 100.0
    );
    println!(
        "  Max BPD seen: {:.2}%   Max SPD seen: {:.2}%",
        max_bpd_seen * 100.0,
        max_spd_seen * 100.0
    );

    if max_bpd_seen > 0.15 {
        println!("  ⚠️  BPD widens >15% under stress — circuit breaker working as expected");
    } else if max_bpd_seen < 0.05 {
        println!("  ⚠️  BPD very tight (<5%) — may indicate thin liquidity or price manipulation");
    } else {
        println!("  ✓  Spread behavior is healthy");
    }

    // ── Transaction volume ───────────────────────────────────────
    println!("\n─── Transaction Volume ─────────────────────────────────────");
    let (buy_count, sell_count, total_decisions): (i64, i64, i64) = conn
        .query_row(
            "SELECT
                SUM(CASE WHEN action = 'Buy' THEN 1 ELSE 0 END),
                SUM(CASE WHEN action = 'Sell' THEN 1 ELSE 0 END),
                COUNT(*)
             FROM decisions",
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )
        .unwrap_or((0, 0, 0));
    let buy_ratio = buy_count as f64 / total_decisions.max(1) as f64;
    println!("  Total decisions: {}", total_decisions);
    println!("  Buys:  {:>6} ({:.1}%)", buy_count, buy_ratio * 100.0);
    println!(
        "  Sells: {:>6} ({:.1}%)",
        sell_count,
        (1.0 - buy_ratio) * 100.0
    );
    if (buy_ratio - 0.5).abs() < 0.1 {
        println!("  ✓  Economy is balanced (near 50/50 split)");
    } else if buy_ratio > 0.6 {
        println!("  ℹ  Buyer-heavy economy — may indicate supply shortage or high demand");
    } else {
        println!("  ℹ  Seller-heavy economy — natural in player-driven economies (farm > buy)");
    }

    // ── Volume per item ─────────────────────────────────────────
    println!("\n─── Most-Active Items ─────────────────────────────────────");
    // decisions.item_index references item_states.item_index (per session)
    let mut stmt = conn
        .prepare(
            "SELECT COALESCE(
                (SELECT i2.item_name FROM item_states i2
                 WHERE i2.session_id = d.session_id
                   AND i2.item_index = d.item_index
                 ORDER BY i2.tick DESC LIMIT 1),
                'Item[' || d.item_index || ']'
             ) as item_name, COUNT(*) as tx_count
             FROM decisions d
             GROUP BY d.item_index
             ORDER BY tx_count DESC
             LIMIT 8",
        )
        .map_err(|e| format!("Query failed: {e}"))?;
    let item_volume: Vec<(String, i64)> = stmt
        .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
        .map_err(|e| format!("Query failed: {e}"))?
        .filter_map(|r| r.ok())
        .collect();
    for (name, count) in item_volume {
        println!("  {:20} {:>6} decisions", name, count);
    }

    // ── Player archetype activity ───────────────────────────────
    println!("\n─── Player Activity by Archetype ──────────────────────────");
    let mut stmt = conn
        .prepare(
            "SELECT archetype, COUNT(DISTINCT player_id) as players, SUM(total_trades) as trades
             FROM player_states
             WHERE tick = (SELECT MAX(tick) FROM player_states)
             GROUP BY archetype",
        )
        .map_err(|e| format!("Query failed: {e}"))?;
    let archetype_rows: Vec<(String, i64, i64)> = stmt
        .query_map([], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)))
        .map_err(|e| format!("Query failed: {e}"))?
        .filter_map(|r| r.ok())
        .collect();
    for (arch, players, trades) in archetype_rows {
        println!("  {:15} {} players, {} total trades", arch, players, trades);
    }

    // ── Trend direction summary ─────────────────────────────────
    println!("\n─── Final Price Trends ────────────────────────────────────");
    let mut stmt = conn
        .prepare(
            "SELECT item_name, trend, trend_pct, price
             FROM item_states i1
             WHERE tick = (SELECT MAX(tick) FROM item_states)
             ORDER BY ABS(trend_pct) DESC",
        )
        .map_err(|e| format!("Query failed: {e}"))?;
    let trend_rows: Vec<(String, String, f64, f64)> = stmt
        .query_map([], |row| {
            Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?))
        })
        .map_err(|e| format!("Query failed: {e}"))?
        .filter_map(|r| r.ok())
        .collect();
    for (name, trend, trend_pct, price) in trend_rows {
        let arrow = match trend.as_str() {
            "UP" => "↑",
            "DOWN" => "↓",
            _ => "→",
        };
        println!(
            "  {:18} {} {:>+7.2}%  ({:.2})",
            name,
            arrow,
            trend_pct * 100.0,
            price
        );
    }

    // ── Stability verdict ───────────────────────────────────────
    let avg_volatility = compute_avg_volatility(&conn);
    println!("\n─── STABILITY VERDICT ──────────────────────────────────────");
    println!("  Average volatility: {:.4}", avg_volatility);
    if avg_volatility < 0.05 {
        println!("  ✓  STABLE — economy is well-regulated by market engine");
    } else if avg_volatility < 0.15 {
        println!("  ⚠  MODERATE — some price oscillation but within acceptable bounds");
    } else {
        println!("  ✗  UNSTABLE — prices are oscillating significantly; parameter review needed");
    }

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║  ANALYSIS COMPLETE                                           ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");

    Ok(())
}

pub fn analyze_dir(dir_path: &Path) -> Result<(), String> {
    let entries = std::fs::read_dir(dir_path)
        .map_err(|e| format!("Cannot read directory {}: {e}", dir_path.display()))?;

    let mut dbs: Vec<_> = entries
        .filter_map(|e| e.ok())
        .filter(|e| e.path().is_dir())
        .collect();

    if dbs.is_empty() {
        println!("No simulation directories found in {}", dir_path.display());
        return Ok(());
    }

    dbs.sort_by_key(|d| d.file_name());

    println!("\n╔══════════════════════════════════════════════════════════════╗");
    println!("║           SIMULATION DIRECTORY SUMMARY                       ║");
    println!("╚══════════════════════════════════════════════════════════════╝\n");
    println!("  Directory: {}", dir_path.display());
    println!("  Runs found: {}\n", dbs.len());

    let mut summaries: Vec<SimSummary> = Vec::new();

    for entry in &dbs {
        let db_path = entry.path().join("simulation.db");
        if !db_path.exists() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_string();
        match load_summary(&db_path) {
            Ok(s) => summaries.push(s),
            Err(e) => println!("  ✗ {}: {}", name, e),
        }
    }

    // Print comparison table
    println!(
        "  {:22} {:>10} {:>10} {:>10} {:>10} {:>8} {:>8}",
        "Scenario", "GDP", "Debt", "Debt/GDP", "BPD avg", "Vol", "Buy%"
    );
    println!(
        "  {:22} {:>10} {:>10} {:>10} {:>10} {:>8} {:>8}",
        "─".repeat(22),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(10),
        "─".repeat(8),
        "─".repeat(8)
    );

    for s in &summaries {
        println!(
            "  {:22} {:>10.0} {:>10.0} {:>10.2}x {:>9.2}% {:>7.4} {:>7.1}%",
            s.name,
            s.gdp,
            s.debt,
            s.debt / s.gdp.max(0.01),
            s.avg_bpd * 100.0,
            s.avg_volatility,
            s.buy_ratio * 100.0,
        );
    }

    // Highlight best/worst
    if !summaries.is_empty() {
        let most_stable = summaries
            .iter()
            .min_by(|a, b| a.avg_volatility.partial_cmp(&b.avg_volatility).unwrap())
            .unwrap();
        let highest_vol = summaries
            .iter()
            .max_by(|a, b| a.avg_volatility.partial_cmp(&b.avg_volatility).unwrap())
            .unwrap();
        let most_buy_heavy = summaries
            .iter()
            .max_by(|a, b| a.buy_ratio.partial_cmp(&b.buy_ratio).unwrap())
            .unwrap();
        let most_sell_heavy = summaries
            .iter()
            .min_by(|a, b| a.buy_ratio.partial_cmp(&b.buy_ratio).unwrap())
            .unwrap();

        println!("\n─── Highlights ─────────────────────────────────────────────");
        println!(
            "  Most stable:   {:22} vol={:.4}",
            most_stable.name, most_stable.avg_volatility
        );
        println!(
            "  Highest vol:   {:22} vol={:.4}",
            highest_vol.name, highest_vol.avg_volatility
        );
        println!(
            "  Most buy-heavy:{:22} {:.1}% buys",
            most_buy_heavy.name,
            most_buy_heavy.buy_ratio * 100.0
        );
        println!(
            "  Most sell-heavy:{:21} {:.1}% buys",
            most_sell_heavy.name,
            most_sell_heavy.buy_ratio * 100.0
        );
    }

    println!("\n  Use --analyze <path> to see full detail for a specific run.\n");

    Ok(())
}

struct SimSummary {
    name: String,
    gdp: f64,
    debt: f64,
    avg_bpd: f64,
    avg_volatility: f64,
    buy_ratio: f64,
}

fn load_summary(db_path: &Path) -> Result<SimSummary, String> {
    let conn = Connection::open(db_path).map_err(|e| e.to_string())?;

    let name = db_path
        .parent()
        .and_then(|p| p.file_name())
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| "unknown".to_string());

    let (gdp, debt): (f64, f64) = conn
        .query_row(
            "SELECT gdp, total_debt FROM economy_snapshots ORDER BY tick DESC LIMIT 1",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .unwrap_or((0.0, 0.0));

    let avg_bpd: f64 = conn
        .query_row("SELECT AVG(bpd) FROM item_states", [], |row| row.get(0))
        .unwrap_or(0.0);

    let (buy_count, total_decisions): (i64, i64) = conn
        .query_row(
            "SELECT SUM(CASE WHEN action='Buy' THEN 1 ELSE 0 END), COUNT(*) FROM decisions",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .unwrap_or((0, 0));
    let buy_ratio = buy_count as f64 / total_decisions.max(1) as f64;

    let avg_vol = compute_avg_volatility(&conn);

    Ok(SimSummary {
        name,
        gdp,
        debt,
        avg_bpd,
        avg_volatility: avg_vol,
        buy_ratio,
    })
}

fn gdp_trajectory(conn: &Connection) -> Option<(f64, f64)> {
    let early: Option<f64> = conn
        .query_row(
            "SELECT gdp FROM economy_snapshots ORDER BY tick ASC LIMIT 1",
            [],
            |row| row.get(0),
        )
        .ok();
    let late: Option<f64> = conn
        .query_row(
            "SELECT gdp FROM economy_snapshots ORDER BY tick DESC LIMIT 1",
            [],
            |row| row.get(0),
        )
        .ok();
    early.zip(late)
}

fn compute_avg_volatility(conn: &Connection) -> f64 {
    // Per-item: for each item with enough history, compute stddev/mean
    // Then average across items
    let mut stmt = match conn.prepare("SELECT item_name FROM item_states GROUP BY item_name") {
        Ok(s) => s,
        Err(_) => return 0.0,
    };

    let items: Vec<String> = stmt
        .query_map([], |row| row.get(0))
        .ok()
        .map(|rows| rows.filter_map(|r| r.ok()).collect())
        .unwrap_or_default();

    let mut total_vol = 0.0;
    let mut count = 0;

    for item_name in items {
        let prices: Vec<f64> = conn
            .query_row(
                "SELECT price FROM item_states WHERE item_name = ?1
                 AND tick % 288 = 0 ORDER BY tick",
                params![&item_name],
                |row| row.get(0),
            )
            .ok()
            .map(|p| vec![p])
            .unwrap_or_default();

        // Use last 14 samples (bi-hourly snapshots × 14 days)
        let recent: Vec<f64> = prices.into_iter().rev().take(14).collect();
        if recent.len() < 3 {
            continue;
        }
        let mean = recent.iter().sum::<f64>() / recent.len() as f64;
        if mean < 0.01 {
            continue;
        }
        let variance = recent
            .iter()
            .map(|p| {
                let d = p - mean;
                d * d
            })
            .sum::<f64>()
            / recent.len() as f64;
        let vol = variance.sqrt() / mean;
        total_vol += vol;
        count += 1;
    }

    if count > 0 {
        total_vol / count as f64
    } else {
        0.0
    }
}

fn debt_health_note(ratio: f64) -> &'static str {
    if ratio < 0.5 {
        "✓ healthy"
    } else if ratio < 2.0 {
        "✓ moderate"
    } else if ratio < 5.0 {
        "⚠ elevated"
    } else if ratio < 10.0 {
        "⚠ high"
    } else {
        "✗ critical"
    }
}
