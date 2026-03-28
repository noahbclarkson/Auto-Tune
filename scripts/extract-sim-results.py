#!/usr/bin/env python3
"""Extract structured JSON from all simulation DBs in sim-output/"""

import sqlite3
import json
import os
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent.resolve()
# scripts/ is at: <workspace>/autotune/scripts/
# workspace root is 2 levels up from scripts/: scripts -> autotune -> workspace
WORKSPACE = SCRIPT_DIR.parent.parent
SIM_OUTPUT = WORKSPACE / "sim-output"
OUTPUT_FILE = WORKSPACE / "autotune" / "web-optimizer" / "public" / "simulation-results.json"
OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)


def analyze_db(db_path: Path) -> dict | None:
    try:
        conn = sqlite3.connect(str(db_path))
        conn.row_factory = sqlite3.Row
    except Exception as e:
        print(f"  ERROR opening {db_path}: {e}", flush=True)
        return None

    try:
        # Final economy snapshot
        eco = conn.execute(
            "SELECT gdp, total_debt, tick FROM economy_snapshots ORDER BY tick DESC LIMIT 1"
        ).fetchone()
        if not eco:
            return None
        gdp = eco["gdp"]
        debt = eco["total_debt"]

        # Session info
        session = conn.execute(
            "SELECT started_at, config_json FROM sessions ORDER BY id DESC LIMIT 1"
        ).fetchone()

        # Spread averages
        avg_bpd_row = conn.execute("SELECT AVG(bpd) as v FROM item_states").fetchone()
        avg_spd_row = conn.execute("SELECT AVG(spd) as v FROM item_states").fetchone()
        avg_bpd = avg_bpd_row["v"] if avg_bpd_row else 0
        avg_spd = avg_spd_row["v"] if avg_spd_row else 0

        # Buy ratio
        buy_row = conn.execute(
            "SELECT "
            "  COUNT(*) as total,"
            "  SUM(CASE WHEN action='Buy' THEN 1 ELSE 0 END) as buys "
            "FROM decisions"
        ).fetchone()
        total_decisions = buy_row["total"] or 1
        buys = buy_row["buys"] or 0
        buy_ratio = buys / total_decisions

        # Loan events
        loan_row = conn.execute(
            "SELECT "
            "  COUNT(*) as total,"
            "  SUM(CASE WHEN event_type='InterestApplied' THEN 1 ELSE 0 END) as interest_cnt,"
            "  SUM(CASE WHEN event_type='Defaulted' THEN 1 ELSE 0 END) as defaulted_cnt,"
            "  SUM(CASE WHEN event_type='InterestApplied' THEN amount ELSE 0 END) as interest_total "
            "FROM loan_events"
        ).fetchone()
        total_loans = loan_row["total"] or 0
        default_rate = (loan_row["defaulted_cnt"] or 0) / max(total_loans, 1) * 100

        # Tick range
        tick_row = conn.execute(
            "SELECT MIN(tick) as mn, MAX(tick) as mx, online_players "
            "FROM ticks ORDER BY tick DESC LIMIT 1"
        ).fetchone()
        max_tick = tick_row["mx"] or 0
        duration_days = max_tick / 288.0

        # Price stability (top 10 by range)
        max_tick_val = conn.execute("SELECT MAX(tick) FROM item_states").fetchone()[0] or 0
        price_stability = []
        for row in conn.execute(f"""
            SELECT item_name, base_price,
                   MIN(price) as min_price,
                   MAX(price) as max_price,
                   AVG(price) as avg_price,
                   (SELECT price FROM item_states i2
                    WHERE i2.item_name = item_states.item_name
                    ORDER BY tick ASC LIMIT 1) as first_price,
                   (SELECT trend FROM item_states i2
                    WHERE i2.item_name = item_states.item_name
                    ORDER BY tick DESC LIMIT 1) as last_trend
            FROM item_states
            WHERE tick % 288 = 0 OR tick = {max_tick_val}
            GROUP BY item_name, base_price
            ORDER BY (MAX(price) - MIN(price)) / MAX(0.001, MIN(price)) DESC
            LIMIT 10
        """):
            price_stability.append({
                "item": row["item_name"],
                "basePrice": row["base_price"],
                "minPrice": row["min_price"],
                "maxPrice": row["max_price"],
                "avgPrice": row["avg_price"],
                "firstPrice": row["first_price"] or row["base_price"],
                "lastTrend": row["last_trend"] or "FLAT",
            })

        # Most active items
        most_active = []
        for row in conn.execute("""
            SELECT COALESCE(
                (SELECT i2.item_name FROM item_states i2
                 WHERE i2.session_id = d.session_id
                   AND i2.item_index = d.item_index
                 ORDER BY i2.tick DESC LIMIT 1),
                'Item[' || d.item_index || ']'
            ) as item_name, COUNT(*) as tx_count
            FROM decisions d
            GROUP BY d.item_index
            ORDER BY tx_count DESC
            LIMIT 8
        """):
            most_active.append({
                "item": row["item_name"],
                "trades": row["tx_count"],
            })

        # Archetype summary
        archetypes = []
        for row in conn.execute("""
            SELECT archetype,
                   COUNT(DISTINCT player_id) as players,
                   SUM(total_trades) as trades
            FROM player_states
            WHERE tick = (SELECT MAX(tick) FROM player_states)
            GROUP BY archetype
        """):
            archetypes.append({
                "archetype": row["archetype"],
                "players": row["players"],
                "trades": row["trades"],
            })

        conn.close()

        name = db_path.parent.name
        return {
            "name": name,
            "gdp": gdp,
            "debt": debt,
            "debtGdp": debt / gdp if gdp > 0 else 0,
            "avgBpd": avg_bpd,
            "avgSpd": avg_spd,
            "avgVolatility": 0.0,  # Would need compute_avg_volatility() from Rust port
            "buyRatio": buy_ratio,
            "defaultRate": default_rate,
            "totalInterestPaid": loan_row["interest_total"] or 0,
            "interestEvents": loan_row["interest_cnt"] or 0,
            "defaultedEvents": loan_row["defaulted_cnt"] or 0,
            "durationDays": round(duration_days, 1),
            "maxTick": max_tick,
            "startedAt": session["started_at"] if session else "",
            "configJson": session["config_json"] if session else "{}",
            "priceStability": price_stability,
            "mostActiveItems": most_active,
            "archetypes": archetypes,
        }

    except Exception as e:
        print(f"  ERROR analyzing {db_path}: {e}", flush=True)
        import traceback
        traceback.print_exc()
        return None
    finally:
        try:
            conn.close()
        except Exception:
            pass


def main():
    print(f"Scanning: {SIM_OUTPUT}", flush=True)

    results = []
    for db_path in sorted(SIM_OUTPUT.glob("*/simulation.db")):
        print(f"  Processing: {db_path}", flush=True)
        result = analyze_db(db_path)
        if result:
            results.append(result)

    print(f"\nFound {len(results)} simulation runs", flush=True)

    # Sort by name
    results.sort(key=lambda r: r["name"])

    OUTPUT_FILE.write_text(json.dumps(results, indent=2))
    print(f"Written: {OUTPUT_FILE}", flush=True)

    # Print summary table
    print(f"\n{'Scenario':<30} {'GDP':>12} {'Debt':>12} {'D/G':>8} {'BPD':>7} {'Buy%':>7}")
    print("-" * 80)
    for r in results:
        print(
            f"{r['name']:<30} "
            f"{r['gdp']:>12.0f} "
            f"{r['debt']:>12.0f} "
            f"{r['debtGdp']:>7.2f}x "
            f"{r['avgBpd']*100:>6.2f}% "
            f"{r['buyRatio']*100:>6.1f}%"
        )


if __name__ == "__main__":
    main()
