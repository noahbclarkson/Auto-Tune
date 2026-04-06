# Economy Concepts — Understanding Auto-Tune's Engine

> A plain-English guide to the economics underneath Auto-Tune. Written for server admins who want to understand *why* the engine behaves the way it does — not just *how* to configure it.

**Prerequisites:** None. This doc stands alone.

---

## What Auto-Tune Is actually doing

Auto-Tune watches every buy and sell transaction, then nudges prices up or down based on whether players are buying more than selling (demand) or selling more than buying (supply). That's it. No fixed prices, no admin intervention.

The engine runs every **5 minutes** (configurable). Each tick:
1. Collect all trades in the rolling window (default: 7 days)
2. Weight recent trades more heavily than old ones
3. Calculate: are players buying more than selling? By how much?
4. Move the price — small step, never a jump

---

## GDP — What "Gross Domestic Product" Means in Minecraft

In the real world, GDP is the total value of all goods and services produced in a year. Auto-Tune's GDP is the same idea: **the total value of all items traded in your server's economy over the tracking period.**

```
GDP = Σ(quantity_traded × current_price) for all trades
```

A healthy server economy might see **$1M–$2M GDP per day** at scale (100+ players, active trading). A quiet server might see $50K–$200K.

GDP tells you: **how active is the economy?** It doesn't tell you if it's healthy — a server where players are only selling (no one is buying) could still have high GDP from desperation selling at low prices.

**What to watch:** GDP *trend* matters more than absolute value. A server that went from $500K/day to $200K/day has a problem — activity is collapsing. A server at $200K/day that has been stable at $200K/day for weeks is fine.

---

## Why Prices Settle 40–70% Below Base

This is the #1 question admins ask. "I set Diamond to $500 and it settled at $200. Why?"

**Short answer: Minecraft economies are naturally seller-heavy.** Here's why:

Players who gather resources (Farmers) produce items much faster than players who spend them (Casuals who consume). Imagine 10 players on a server: 6 are gathering diamonds, 4 are using them. Diamond supply outpaces demand 6:4. Prices fall to clear the excess supply.

This isn't a bug — it's the natural behavior of a supply/demand engine. The equilibrium price settles wherever supply meets demand, not where the admin set the base price.

**The simulation evidence (from 840-config parameter sweep):**
- Every single parameter combination tested produced prices 29–65% below base
- Player archetype mix matters 10x more than engine tuning for price levels
- The only structural fix is having enough buyers relative to sellers

**What this means for admins:**
- Set base prices as *starting points*, not targets
- A Diamond base of $500 doesn't mean Diamond will be $500 — it's where the price *starts*
- The price will settle wherever supply and demand push it

**The buy ratio** (see below) is the key indicator of whether your player mix is balanced.

---

## Buy Ratio — Your Economy Health Meter

**Buy ratio = buys ÷ (buys + sells)**

A buy ratio of **70%** means 70 out of every 100 trades were purchases. The rest were sales.

| Buy Ratio | What it means | Status |
|-----------|---------------|--------|
| 60–80% | Balanced — enough buyers and sellers | ✅ Healthy |
| >80% | Sellers are rare — hoarding? scarcity? | ⚠️ Supply squeeze |
| <50% | Buyers are rare — oversupply, prices falling | ⚠️ Distressed |
| <30% | Collapse — almost no one is buying | 🔴 Critical |

**The ideal is 50–60%** (near-balanced). In practice, most Minecraft servers run 65–85% because Farmers produce more than Casuals consume.

**How to improve buy ratio:**
- Add more MarketMaker archetypes (they always post buy and sell orders)
- Add GuildBuyer archetypes (they buy to maintain guild stock)
- Reduce Farmer gather rates (less supply)
- Add reasons for Casuals to spend (donation perks, rank upgrades)

**The simulation finding:** The recommended archetype config (2MM + 2GB @ 7% threshold) achieves ~50–55% buy ratio. A server with 6 Farmers and 1 Casual will have 80%+ buy ratio — scarcity, not a healthy economy.

---

## Spread — The Cost of Trading

When you buy an item, you pay the **buy price** (higher). When you sell an item, you receive the **sell price** (lower). The difference is the **spread**.

**BPD = Buy-Price Difference** — how much more you pay vs the mid-market price, expressed as a percentage.
**SPD = Sell-Price Difference** — how much less you receive vs the mid-market price, expressed as a percentage.

```
Diamond mid-market price: $200
Buy price: $202 (BPD = 1.0%)
Sell price: $198 (SPD = 1.0%)
```

A **1% spread** means for every $200 Diamond trade, $2 goes to the spread ($1 to each side). On a server with $1M daily GDP, that's $10K absorbed by the spread every day.

**Tight spreads = liquid market.** Wide spreads = illiquid, expensive to trade.

**What drives spread width:**
- **Volume:** High trade volume → tighter spreads (more liquidity)
- **Player count:** More players → tighter spreads (more competition on both sides)
- **MarketMaker archetypes:** MMs explicitly post tight-spread orders, reducing spreads by 50–70%
- **Crisis:** Low volume, few players → spreads blow out to 8–16%

**Target spreads:** 1–4% BPD/SPD in normal conditions. >6% BPD means your market is illiquid.

---

## Debt/GDP Ratio — The Loan Health Metric

**Debt/GDP = total outstanding loan principal ÷ daily GDP**

This is the single most misunderstood metric in Auto-Tune.

**Healthy range:** 0.5x – 8x for a mature economy with 2 MarketMakers.

**Why it matters:** If your economy has $1M in daily GDP and $3M in outstanding loans, those loans represent 3 days of total economic output. The economy *can* service that debt (if GDP stays healthy), but a shock could cascade.

**The critical warning about D/G post-default:**
After a loan defaults, the defaulted principal is *written off* but still counts in total debt. A server with massive defaults from a cascade can show D/G of 20x+ even though the economy has fully recovered — the defaulted loans are stale numbers sitting in the database.

**Read D/G together with:**
1. **GDP trend** — is the economy growing or shrinking?
2. **Volatility** — is the economy stable or oscillating?
3. **Buy ratio** — is trading balanced?

Never read D/G in isolation.

---

## Volatility — The Most Important Health Metric

**Volatility measures how much prices jump around from tick to tick.** Auto-Tune tracks average absolute price change percentage across all items per tick.

| Volatility | Meaning | Status |
|-----------|---------|--------|
| < 0.010 | Stable — prices move smoothly | ✅ Healthy |
| 0.010–0.050 | Moderate — normal market movement | ⚠️ Watch |
| > 0.050 | Unstable — price swings, hard to predict | 🔴 Problem |

**Why volatility matters more than D/G:** A server can have high D/G (lots of loans) but low volatility (prices stable, economy predictable). That's manageable. Low D/G but high volatility means prices are chaotic — players can't plan, merchants can't price, the economy feels random.

**The simulation finding:** Across 23 tested scenarios, the standard economy had volatility of ~0.19 (unstable) regardless of parameters. Only economies with MarketMakers achieved <0.010 volatility. The 2MM + 2GB config reduces volatility by **95%** vs standard archetype mixes.

**The floor paradox:** A price floor ($300 minimum for Diamond) *appears* to reduce volatility (prices can't fall below $300). But internally, the engine is forcing more trades at the floor price, which can accumulate debt. The floor masks volatility; it doesn't eliminate it.

---

## The Loan System — Why It Doesn't Destroy Economies

Auto-Tune's loan system is designed to be **self-limiting.** Here's how:

**1. Circuit Breaker** — Three tiers protect against cascade:

| Tier | D/G Threshold | Interest Cap |
|------|--------------|-------------|
| TIER 1 | >3x | 50% of normal |
| TIER 2 | >5x | 25% of normal |
| TIER 3 | >15x | 0% (paused entirely) |

The circuit breaker fires based on *total* unpaid debt (active + defaulted), not just active loans.

**2. Post-Default Cooldown (168 hours)** — After a player defaults, they can't take new loans for 7 days. This prevents cascade borrowing (default → immediately re-borrow at even larger scale).

**3. Counter-Cyclical Interest** — Instead of a cliff at TIER3, interest is continuously reduced as D/G rises. At D/G=10 in a tier3=15 system, interest is already at 33% of normal. The circuit breaker is a last resort, not the primary mechanism.

**4. Single-Loan GDP Cap** — No single loan can exceed 100% of the economy's daily GDP. A $1M/day economy can't have a single $5M loan.

**Why the loan system is safe:**
- MarketMakers (the primary borrowers) are professional liquidity providers — they earn more from spread trading than they pay in interest
- GuildBuyers borrow to maintain stock — their debt is backed by items with real value
- The circuit breaker ensures debt growth is self-limiting
- Post-default cooldown prevents cascade failure

**What can break it:** A mass player exodus (80%+ quit overnight) with no circuit breaker → debt compounds on a shrinking economy → cascade. The circuit breaker (TIER3 at 15x) prevents this from being catastrophic. A 50% exodus with circuit breaker: GDP drops 3–5%, D/G spikes but circuit fires, economy self-corrects within days.

---

## Price Floor and Ceiling — External Price Guards

Admins can set a **minimum price (floor)** and **maximum price (ceiling)** for any item.

**Floor use case:** "Diamond should never drop below $100 — that would make mining worthless." Set a $100 floor.

**Ceiling use case:** "Emerald should never exceed $1,000 — that breaks the villager economy." Set a $1,000 ceiling.

**How it works:**
- The floor/ceiling applies to *displayed* prices (what players see)
- The internal price update still uses natural supply/demand
- When the internal price tries to go below the floor, the floor "binds" — it holds the displayed price at the floor level

**The floor paradox:** When Diamond's natural price falls to $80 but the floor is $300, the *displayed* price is $300. But internally, the engine knows Diamond is "worth" $80. Players gather Diamond because the *displayed* price is $300 — but they're actually overproducing relative to true demand. This can accumulate debt.

**Recommendation:** Use floors conservatively. A floor of 50–60% of base price is protective without being distortive. A floor of 90%+ chokes the economy.

**Production finding (simulation):** 60% floor ($300 for Diamond) is the sweet spot — binds in 100% of tested economies, improves GDP by ~10%, without causing the floor paradox at scale.

---

## The Cross-Server Ecosystem

When multiple servers opt into Auto-Tune's cross-server network, something interesting happens: the API server collects *ratio matrices* from each server (not prices, not player data). These ratios describe the relative value between items on each server.

The **price solver** uses weighted least-squares to find "true prices" — the set of prices consistent with all submitted ratios. This gives new servers a sensible starting point instead of arbitrary base prices.

**Exchange rates** are computed as: `server_item_price ÷ true_price`. A server with exchange rate 1.2 has prices 20% above the cross-server average. Players on that server effectively have more purchasing power for imported goods.

**Security:** The API server uses server keys (anti-Sybil), 3σ outlier filtering on ratio submissions, and per-server reputation weighting. A single malicious server can't meaningfully corrupt true prices — it would need to represent a majority of submitted trade volume.

---

## Quick Reference

| Concept | Healthy | Warning | Critical |
|---------|---------|---------|---------|
| **Buy Ratio** | 60–80% | >80% or <50% | <30% |
| **BPD Spread** | 1–4% | 4–6% | >6% |
| **Debt/GDP** | 0.5–8x | 8–12x | >12x |
| **Volatility** | <0.010 | 0.010–0.050 | >0.050 |
| **GDP Trend** | Stable or growing | Declining <10% | Declining >20% |

---

## Further Reading

- **[SERVER_ADMIN_GUIDE.md](./SERVER_ADMIN_GUIDE.md)** — Practical admin tasks, commands, and config
- **[CONFIG_GUIDE.md](./CONFIG_GUIDE.md)** — Every config parameter with recommended ranges
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** — How the engine components interact
- **[QUICKSTART.md](./QUICKSTART.md)** — 5 decisions before you launch
