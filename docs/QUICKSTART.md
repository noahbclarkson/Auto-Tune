# Admin Quickstart: Launching Your Auto-Tune Economy

> **5 decisions before you go live.** Backed by thousands of simulation runs across 5 seeds.

---

## Visual Decision Guide

Answer these questions in order. Your answers determine the right config:

```
Q1: Do you want player loans enabled?
│
├─ NO  →  ⚡ No-loans mode
│         → Loans disabled. Prices still work.
│         → Use 2 MarketMakers (no GuildBuyers needed — no debt to service)
│         → Still set price floors (60%) for seller protection
│         → Run: set loans.enabled: false
│
└─ YES →  Q2: What's your archetype mix?
          │
          ├─ 2+ MarketMakers + 2+ GuildBuyers
          │   → Recommended. GDP +101%, vol -48%, spreads -22% vs 1MM+1GB
          │   → Set loans: 5% threshold, counter-cyclical: true, tier3_ratio: 30
          │   → Set price floor: 60% on Diamond-type items
          │   → ✅ You're ready
          │
          ├─ 1 MarketMaker + 1 GuildBuyer
          │   → Acceptable for small/low-traffic servers
          │   → GDP will be lower, spreads wider
          │   → Set price floor: 50-60% on Diamond-type items
          │   → Plan to scale to 2MM+2GB as player count grows
          │
          └─ No MarketMakers or GuildBuyers
              → ⚠️ High risk. Prices will settle 50-70% below base.
              → Casual-heavy servers (6Cas/1Far/...) are more stable than Farmer-heavy
              → Do NOT disable loans — players need credit to absorb Farmer oversupply
              → Strongly recommend adding at least 1MM + 1GB
```

**The short answer for most servers:** 2MM + 2GB + loans ON + 60% Diamond floor + market events.

---

## The 5 Decisions

### 1. Archetype Mix: 2 MarketMakers + 2 GuildBuyers

The recommended mix prevents supply gluts while keeping spreads tight.

- **MarketMakers** anchor prices and provide two-sided liquidity (buy + sell simultaneously)
- **GuildBuyers** buy on dips to prevent runaway price crashes
- Result: GDP +101%, volatility -48%, spreads -22% vs. underprovisioned configs

In `config.yml`, configure your economy to attract or designate players as MarketMakers and GuildBuyers. See [SERVER_ADMIN_GUIDE.md → Archetypes](SERVER_ADMIN_GUIDE.md#player-archetypes) for how to tune player behavior.

> **Why not more players?** More MMs/GBs improves stability up to a point. Beyond 2 of each, diminishing returns. Beyond 4 of each, D/G starts rising without GDP benefit.

### 2. Loans: Enabled (Default: ON)

Loans are **required** for a healthy economy. Without credit, GuildBuyer purchases fail and prices crash.

- **Circuit Breaker:** Enabled automatically. If Debt/GDP exceeds 15×, interest pauses until D/G drops to 13.5× (10% hysteresis band)
- **Post-default cooldown:** Players who default are blocked from new loans for **7 days** (`post-default-cooldown-hours: 168`)
- **Counter-cyclical interest:** Interest rate reduces as D/G rises (enabled by default). Prevents most cascade scenarios before the circuit breaker fires.
- **TIER3 ratio:** Set to `15` (not 10). Prevents TIER3 noise in healthy economies. At 15×, counter-cyclical handles debt before the circuit breaker is needed.

> **Disable loans only if:** Your economy has no borrowable capital and no need for credit. Most SMPs benefit from loans.

```yaml
loans:
  enabled: true
  interest:
    base-rate: 0.07          # 7% annual (default)
    counter-cyclical: true   # reduces interest as D/G rises
  tier3_ratio: 30.0         # circuit breaker fires at 30× D/G (D/G < 15× to unlock with 50% hysteresis band)
  post-default-cooldown-hours: 168  # 7 days
```

### 3. Price Floor: 60% of Base ($300 Diamond)

A hard price floor protects sellers — especially new players — during oversupply. Without a floor, prices can crash to near-zero when a single player dominates supply.

The **60% floor** ($300 on a $500 Diamond base) is the sweet spot:
- Binds in **5/5 simulation seeds** — consistently active
- GDP +10.7% vs. no floor (sellers feel confident enough to keep trading)
- D/G +22% — floor paradox cost is real but manageable
- Floor paradox: displayed prices stay at floor but *internal* prices drop further — still worth it for new-player protection

```yaml
market:
  items:
    DIAMOND:
      price-floor-enabled: true
      price-floor-percent: 0.60   # 60% of base price
    DIAMOND_PICKAXE:
      price-floor-enabled: true
      price-floor-percent: 0.60
    GOLD_INGOT:
      price-floor-enabled: true
      price-floor-percent: 0.60
```

Or use the command:
```
/at admin item floor DIAMOND 300
/at admin item floor GOLD_INGOT 150
```

> **Warning:** 70%+ floors are destructive. Simulation shows -10.7% GDP at 70%, near-collapse at 80%. 60% is the maximum recommended.

### 4. Market Events: Enabled

Events (Diamond Rush, Gold Glut, Inflation Boost) temporarily alter price velocity for specific items. They make the economy feel **alive** and give admins a lever for seasonal content.

```
/at event templates/invoke DIAMOND_RUSH
```

Boss bars announce events to all online players automatically. See [SERVER_ADMIN_GUIDE.md → Market Events](SERVER_ADMIN_GUIDE.md#market-events) for template creation.

### 5. Economy Digest: Weekly Discord Report

Configure a Discord webhook to receive automated weekly economy summaries — GDP trend, top movers, circuit breaker transitions, D/G health.

```yaml
market-digest:
  enabled: true
  schedule: "0 9 * * MON"   # Every Monday at 9am
  webhook-url: "https://discord.com/api/webhooks/..."
  sections:
    health: true
    top-movers: true
    loans: true
```

Run `/at admin digest config` to see current settings. Run `/at admin digest` to send immediately.

---

## Post-Launch Checklist

```
□ Run /at admin health    — verify GDP > $0, D/G in expected range
□ Run /at admin item list — check floor prices are set on Diamond/Gold
□ Verify /shop works      — browse as a test player, buy and sell one item
□ Check floor binds       — Diamond floor at $300 should be visible in /shop
□ Enable cleanup          — set cleanup.enabled: true (removes old data)
□ Set up Discord webhook  — weekly digest keeps you informed
□ Review 7-day data       — check prices didn't crash unexpectedly
□ Adjust if needed        — see FAQ "Prices aren't doing what I expected"
```

Run `/at admin reload` after any config change. Run `/at admin health` to confirm the economy is healthy.

---

## What "Healthy" Looks Like (2MM + 2GB + 60% Floor)

From 5-seed, 30-day simulation:

| Metric | Value | What It Means |
|--------|-------|---------------|
| GDP | > $500K/day | Active trading economy |
| D/G | 7–8× | Normal loan exposure |
| Buy % | 65–75% | Slightly buy-heavy, not oversold |
| BPD | 0.8–1.2% | Tight spreads, good liquidity |
| Volatility | < 0.015 | Stable prices |

See [ECONOMY_CONCEPTS.md](ECONOMY_CONCEPTS.md) for the full metric guide.
