
## Simulation Insight (2026-05-26) — Trend Dampening Cannot Fix Loan Circuit Instability

**Key distinction:** `trend_dampening` (engine.rs:489-500) caps price momentum overshoot when a price streak is continuing (streak dampened to `1/(1+streak*damp)`, floored at 0.25). This is a PRICE-level mechanism. It has zero effect on loan/debt circuit cycling.

**5-seed guild_stability** (4Cas+3Far+2Tra+2GB, 14d) — avg D/G 4.11x, 4/5 seeds hit TIER3, but individual item vol averaged only 0.0036 (below STABLE threshold). The D/G instability is invisible to the volatility metric.

**5-seed low_player** (2Cas+1Far, 14d) — avg vol 0.0178–0.0299 (passes STABLE), but every seed cycles TIER3 repeatedly. GDP collapses to near-zero. This is chronic debt-circuit pathology, not price oscillation.

**Stressed** (15 players + Exploiters + 3 stress events) — all 5 seeds collapse to near-zero GDP with catastrophic debt (6–33M).

**Implication for volatility param sweep:** Spread and trend-dampening tuning is the wrong lever for guild_stability/low_player/stressed. These scenarios fail at the loan circuit level. The relevant parameter families are:
1. `tier3_hysteresis_band` — reduce TIER3 re-entry spam (most impactful for low_player chronic cycling)
2. `min_interest_multiplier` — faster deleveraging push during counter-cyclical mode
3. `guildbuyer_threshold` + MM presence — prior finding: MM presence drops avg_vol 0.19→0.006; without MM, thin GG+GB markets oscillate

**Actionable next step:** Run hysteresis sweep at multiple tier3_ratio × hysteresis_band combinations × 90d × fresh seeds. `--tier3-40-hysteresis-test` already exists in the CLI — needs to be extended for multi-seed.

---

## Repo Health (2026-05-26) — Rust CLI headless flag missing --trend-dampening argument

The `--headless SCENARIO [--seed N] [--duration N]` path does not parse `--trend-dampening VALUE`. The only way to run the trend dampening sweep is via the dedicated `--trend-dampening-multi` flag which invokes `run_trend_dampening_sweep()` internally with hardcoded seeds. This is fine for the existing sweep use case but limits ad-hoc exploration.

Low priority. Not committed.

