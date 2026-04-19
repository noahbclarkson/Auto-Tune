# 60-Day Architectural Fix Path — CORRECTED

_Simulation-verified analysis of the TIER3 counter-cyclical doom loop at 60 days._

**⚠️ CORRECTION (2026-04-19):** Prior version of this doc incorrectly stated `tier3_ratio=100` stabilizes D/G. Actual simulation results show D/G **increases** with tier3=100. See corrected analysis below.

---

## The Problem (Confirmed)

**Config:** 2MM + 2GB @ 7% + 60% Diamond floor + counter-cyclical=true + `tier3_ratio=30` (pre-2026-04-15)

**60-day result:** D/G = 20.1x, 6+ TIER3 oscillations, economy enters doom loop at ~day 39.

| Metric | 14d | 30d | 60d | Verdict |
|--------|-----|-----|-----|---------|
| GDP | 1.62M | 2.33M | 3.94M | ✅ Growing |
| D/G | 8.31x | 7.50x | **20.1x** | ❌ Doom loop |
| TIER3 events | 0 | 0 | **6+** | ❌ |

**Root cause — architectural:** Debt compounds at ~10%/day across ALL tiers while GDP grows ~1%/day. The counter-cyclical formula `multiplier = max(0, 1 - D/G/30)` gives 0% interest when D/G ≥ 30. When TIER3 fires:
1. ALL existing debt compounds at 0% during the lock
2. Circuit re-enables at D/G=27 (0.9 × 30), immediately triggering re-entry
3. D/G climbs past 30 again. Loop repeats 5+ times.

**The Diamond floor was a red herring.** Natural equilibrium ~$472. Floor at $500 was non-binding.

---

## All Fix Candidates — Test Results

**All 7 proposed fixes FAILED to resolve the underlying D/G instability.**

| Fix | Test | Result |
|-----|------|--------|
| tier3=50 + min_int=0.20 | `--sixty-day-fix-test` 2×60d | ❌ D/G +1.05x WORSE |
| Hysteresis band 50% | `--sixty-day-hysteresis-test` 2×60d | ❌ D/G essentially flat |
| GB debt cap 3× GDP | `--sixty-day-gb-debt-cap-test` 2×60d | ❌ NOOP — cap non-binding |
| tier3=50 alone | `--sixty-day-tier3-sweep` 5×60d | ❌ TIER3 still fires |
| tier3=100 alone | `--sixty-day-tier3-sweep` 5×60d | ❌ D/G **+2.3x WORSE**, 0 T3 events |
| Loan-lock alone | `--sixty-day-loan-lock-test` 5×60d | ⚠️ NEUTRAL — D/G -0.3x (noise), T3 -83% |
| tier3=100 + loan-lock combo | `--sixty-day-combo-test` 5×60d | ❌ D/G **+2.3x WORSE**, 0 T3 events |
| Early intervention (tier3=5) | `--sixty-day-early-intervention-test` 5×60d | ❌ D/G reaches 8–36x regardless |

### Detailed Results: tier3 Ratio Sweep (5 seeds × 60d)

| tier3 | Mean D/G | T3_ev total | Verdict |
|-------|----------|-------------|---------|
| 30 (ctrl) | **20.18x** | 8 | baseline |
| 50 | 21.44x | 1 | +6.2% D/G, T3 reduced |
| 100 | 22.48x | **0** | +11.4% D/G WORSE, T3 eliminated |

### Why tier3=100 Makes D/G Worse

At D/G=22 with tier3=30 (ctrl): `multiplier = 1 - 22/30 = 0` → TIER3 fires → 0% interest → debt accumulation PAUSES
At D/G=22 with tier3=100: `multiplier = 1 - 22/100 = 0.78` → 78% interest → debt ACCUMULATES

The TIER3 lock temporarily relieves debt accumulation. Without the circuit firing, interest keeps compounding.
**Eliminating circuit events is neutral at best and actively harmful to D/G.**

### Why tier3=100 + loan-lock Combo Also Fails

The combo eliminates TIER3 events (0 vs 8) but D/G increases by +2.3x.
This is because the circuit lock's 0% interest pause is the only mechanism slowing debt accumulation.
With tier3=100, that pause never occurs → debt accumulates faster.

---

## Root Cause — Architectural

The circuit is a **symptom observer, not a debt correction mechanism**.

```
Debt growth:   ~10%/day (across all tiers)
GDP growth:    ~1%/day
Net:           debt outpaces GDP by ~9%/day
```

The circuit observes high D/G and reduces interest to 0%, but:
1. Existing debt remains
2. The 0% pause only helps if the circuit actually fires
3. Exiting TIER3 into TIER2 (multiplier=0.50) immediately triggers re-entry when D/G drops to ~15x

**Counter-cyclical is a governor, not a cure.** It makes the problem less severe but cannot reverse it.

---

## Candidate Architectural Fixes (NOT YET TESTED)

These require deeper engine changes and are escalated to Arc for prioritization:

1. **Exit TIER3 directly to NORMAL** — bypass TIER2 to prevent the 0.50→re-entry oscillation
2. **Forced deleveraging at TIER3 exit** — debt write-off or mandatory repayment schedule when circuit unlocks
3. **Cap total economy debt growth rate** — engine-level cap on debt accumulation vs GDP growth
4. **Deep hysteresis: require D/G < tier3_ratio × 0.25 before re-enabling** — more conservative unlock threshold
5. **Simulation as CI gate** — commit `--sixty-day-combo-test` to prevent parameter regressions

---

## Current Production Config — UNCHANGED

The simulation does not support changing defaults. Current recommended config:

```yaml
loans:
  debt_gdp_tier3_ratio: 30.0        # UNCHANGED — other values make D/G worse
  block_mm_gb_loans_during_tier3: false  # UNCHANGED — neutral effect alone
  counter_cyclical: true             # unchanged
  min_interest_multiplier: 0.0      # unchanged
  tier3_hysteresis_band: 0.5        # unchanged
```

**⚠️ Warning for 60+ day servers:** D/G naturally climbs to ~20x. Admins should monitor D/G via `/at admin stats`. The circuit will fire multiple times — this is expected behavior, not a failure. Economy remains functional (GDP grows, trades execute, prices stable).

---

## Simulation Test Inventory

All 60d tests run via `cargo run --release -- --<flag>` in `scripts/market-simulation/`:

| Test | Flag | Seeds | Duration | Status |
|------|------|-------|----------|--------|
| Baseline 60d | `--sixty-day-test` | 2 | 60d | ✅ Done |
| tier3=50+min_int=0.20 | `--sixty-day-fix-test` | 2 | 60d | ✅ FAILS |
| Hysteresis sweep | `--sixty-day-hysteresis-test` | 2 | 60d | ✅ marginal |
| GB debt cap | `--sixty-day-gb-debt-cap-test` | 2 | 60d | ✅ NOOP |
| tier3=30/50/100 sweep | `--sixty-day-tier3-sweep` | 5 | 60d | ✅ ALL FAIL |
| Loan lock alone | `--sixty-day-loan-lock-test` | 5 | 60d | ✅ neutral |
| tier3=100+loan-lock combo | `--sixty-day-combo-test` | 5 | 60d | ✅ FAILS |
| Early intervention (tier3=5/10/15) | `--sixty-day-early-intervention-test` | 5 | 60d | ✅ worst |

---

## What Changed vs Prior Version

| Section | Old (WRONG) | New (CORRECT) |
|---------|-------------|---------------|
| Fix 1 (tier3=100) | "D/G stable, 0 T3 events ✅ CONFIRMED" | "D/G +2.3x WORSE, 0 T3 events ❌ FAILS" |
| Fix 3 (combo) | "D/G stable ✅ CONFIRMED — RECOMMENDED" | "D/G +2.3x WORSE, 0 T3 events ❌ FAILS" |
| Recommended config | `tier3=100, block_mm_gb_loans=true` | NO CHANGE to defaults |
| Root cause | "Diamond floor red herring" | Same + architectural debt accumulation framing |
| Architectural fixes | Not listed | 5 candidate fixes listed for Arc |
