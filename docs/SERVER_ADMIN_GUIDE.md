# Server Admin Guide

_A practical guide to running Auto-Tune on your Minecraft server. Written for admins who aren't developers._

---

## Table of Contents

1. [What Auto-Tune Does](#what-auto-tune-does)
2. [Quick Start](#quick-start)
3. [How the Market Engine Works](#how-the-market-engine-works)
4. [Configuration Cookbook](#configuration-cookbook)
5. [Monitoring Your Economy](#monitoring-your-economy)
6. [Common Issues & Fixes](#common-issues--fixes)
7. [Fine-Tuning Reference](#fine-tuning-reference)

---

## What Auto-Tune Does

Auto-Tune creates a **dynamic supply-and-demand economy** for your Minecraft server. Instead of setting prices manually, Auto-Tune watches what players buy and sell, then adjusts prices automatically:

- Players buy an item a lot → price goes **up** (to reduce demand and encourage selling)
- Players sell an item a lot → price goes **down** (to encourage buying)
- Few players trade an item → spreads **widen** (less liquidity = more risk for traders)
- Many players trade an item → spreads **tighten** (healthier market)

The plugin runs entirely on your server. The bundled web dashboard shows live prices, trends, and economy health at `http://your-server:8989`.

---

## Quick Start

### Requirements

- **Paper 1.21.4+** (or a fork — Purpur, Folia, etc.)
- **Vault** + any economy plugin (EssentialsX, Reserve, etc.)
- **Java 21** on your server machine

### Installation

1. Download the latest `.jar` from the [releases page](https://github.com/noahbclarkson/Auto-Tune/releases)
2. Drop it into your server's `plugins/` folder
3. Start/restart your server
4. Auto-Tune creates its default configuration at `plugins/Auto-Tune/config.yml`
5. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) for economy placeholders like `%autotune_gdp%`

### First Run Checklist

- [ ] Vault and your economy plugin are installed and working
- [ ] `/autotune` command works (or `/at` alias)
- [ ] `/shop` opens the GUI shop
- [ ] `/sell` opens the sell panel
- [ ] Web dashboard loads at port 8989
- [ ] Players can buy and sell items and prices move

### Enabling the Web Dashboard

The dashboard is on by default. If it doesn't load:

```yaml
# In config.yml:
web:
  enabled: true
  port: 8989       # change if 8989 is taken
  host: "0.0.0.0"  # use "127.0.0.1" to restrict to localhost
```

Restart, then visit `http://your-server-ip:8989`.

---

## How the Market Engine Works

Understanding these three concepts will help you tune the plugin effectively.

### 1. Price Updates (every 5 minutes by default)

Auto-Tune collects all trades in a **trade window** (default: 7 days). Each trade is weighted by recency — trades this week matter more than trades from last week.

The trade ratio determines the price direction:
```
tradeRatio = (weightedBuys − weightedSells) / (weightedBuys + weightedSells)
```
- `+0.5` → strong buying pressure → price goes up
- `−0.5` → strong selling pressure → price goes down
- `0` → balanced → price barely moves

Price changes are capped at `maxPriceChangePercent` per tick, so the market can't flip instantly.

### 2. The Spread (Buy vs Sell Price)

Every item has a **buy price** (what players pay) and a **sell price** (what players receive). The difference is the spread — it represents market maker profit and risk.

The spread widens when:
- One side dominates trading (heavy buying → buy spread widens)
- Few players are trading (low liquidity)
- Trading volume is unusually high or low

The spread tightens when:
- Many unique traders are active (high liquidity)
- More players are online

### 3. Player Scaling

Price changes are amplified by online player count using a smooth curve. At `fullEffectPlayers` (default: 10), the market engine is at 99% responsiveness. Solo players still have ~26% impact even when alone.

---

## Configuration Cookbook

### Tight Spreads (Small, Active Server)

For servers with 5–15 active players who trade frequently:

```yaml
economy:
  max-price-change-percent: 1.0   # slower price moves (was 1.5)
  trade-window-days: 5             # shorter window = faster reactions (was 7)

spread:
  base-spread: 0.10               # 10% total spread — tighter (was 0.20)
  volume-impact: 0.6             # less aggressive widening (was 0.8)
  player-impact: 0.7             # more player count sensitivity (was 0.6)
```

**Result:** Prices move more gradually. Spreads are tighter when the market is healthy. Better for survival servers where players need affordable goods.

---

### Wide Spreads (Large Server, 50+ Players)

```yaml
economy:
  max-price-change-percent: 2.0   # faster market reaction (was 1.5)
  trade-window-days: 14           # longer window for more data (was 7)

spread:
  base-spread: 0.30               # 30% total spread — wider (was 0.20)
  volume-impact: 1.0             # aggressive spread widening (was 0.8)
  player-impact: 0.4             # less player sensitivity (was 0.6)
```

**Result:** Prices move more dramatically. Spreads absorb big imbalances. Better for servers with lots of farmers and bulk traders.

---

### Preventing Deflation (Prices Always Falling)

If your prices keep settling below base prices (natural seller-heavy economy):

```yaml
economy:
  # Reduce downward pressure from sells:
  sell-pressure-multiplier: 1.0    # symmetric; 0.80 sacrifices D/G stability (+40%) for +5% GDP — admin choice only

  # Also: raise base prices in shops.yml above what players should "fairly" pay.
  # The engine will settle toward base, so start higher than your ideal.
```

> **Why this happens:** Minecraft economies tend to be seller-heavy. Players gather and sell resources far more than they buy manufactured goods. This is a game design issue, not an Auto-Tune bug. Raising base prices or tuning `sellPressureMultiplier` addresses it.

---

### Tight Loans (Prevent Debt Accumulation)

```yaml
loans:
  base-interest-rate: 0.03        # 3% instead of 5%
  max-loan-multiplier: 1.5        # cap loans at 1.5× trading history (was 2.0)
  min-term-days: 5                # longer minimum term (was 3)
  default-penalty: 75             # bigger credit penalty for defaulting (was 50)
```

The loan circuit breaker is **on by default** — it pauses interest if total debt exceeds the `debt-gdp-tier3-ratio` multiplier times the economy's GDP (default: **30×**). Don't disable it.

---

### Making the Market React Faster

```yaml
economy:
  update-interval: 3000           # 2.5 minutes instead of 5 (was 6000)
  adaptive-window: true           # automatically shrinks window when busy
  min-window-days: 1              # minimum 1 day window (was 2)
  max-window-days: 3              # maximum 3 days (was 7)
```

**Tradeoff:** Faster reactions = more volatile prices. Use with `maxPriceChangePercent: 1.0` to keep swings controlled.

---

### Preventing Spam from Cheap Autosell

By default autosell sells everything. On servers with huge quantities of cobblestone or dirt:

```yaml
autosell:
  minimum-price: 0.10   # Don't autosell items worth less than $0.10
```

Players holding cheap items will have to manually sell them at `/sell` instead of getting action bar spam every pickup.

---

## Monitoring Your Economy

### Web Dashboard (`/web`)

Access at `http://your-server:8989` (or your configured port):

| Page | What it shows |
|------|--------------|
| **Home** | Live prices, top movers, transaction feed, economy health |
| **Items** | Full sortable/searchable item list with buy/sell/spread |
| **Compare** | Side-by-side price comparison of two items |
| **Economy** | GDP, inflation, debt, loan stats with historical charts |
| **Leaderboard** | Top traders by volume, biggest borrowers |
| **Loans** | Active loans with status |

### Key Indicators to Watch

**Volatility** (on the home page): Average absolute % price change per day. Above 10% means prices are swinging wildly — consider reducing `maxPriceChangePercent`.

**Debt / GDP ratio**: Should stay below 1×. A ratio above 5× means players are borrowing too much relative to economic activity. The circuit breaker kicks in at **30×** by default (`debt-gdp-tier3-ratio`).

**Spread width** (BPD + SPD): Above 15% total spread means low market liquidity — players are being gouged on buy/sell prices. Add more tradeable items or reduce `baseSpread`.

**Online players**: The plugin adapts automatically, but very low player counts (< 3) can make prices jumpy. `fullEffectPlayers: 10` means solo players still have ~26% market impact.

### Economy Health Signals

| Signal | Likely cause | Fix |
|--------|-------------|-----|
| Prices all 50–70% below base | Natural seller-heavy economy | Raise base prices, tune `sellPressureMultiplier` |
| GDP growing but debt growing faster | Loans too attractive | Raise interest rate or reduce `maxLoanMultiplier` |
| Spreads 15–20%+ | Low liquidity / few traders | Reduce `baseSpread`, encourage more trading |
| Prices oscillating wildly | Too fast adaptation | Reduce `maxPriceChangePercent` or extend `tradeWindowDays` |

---

## Common Issues & Fixes

### "Prices are too high / too low"

The most common cause is **base prices set incorrectly in shops.yml**. Auto-Tune's engine works by drifting prices toward market equilibrium — if your base prices are way off, equilibrium will be way off too.

Start with realistic base prices based on how much players actually value items.

### "Players say buying is too expensive"

1. Reduce `baseSpread` (e.g., 0.15 instead of 0.20)
2. Ensure enough items are **buyable** (items with no sell history need `/shop admin setbuyable <item> true`)
3. Check the spread in the web dashboard — if BPD > 15%, the buy side is gouged

### "Players say selling is worthless"

Same fix as above but check SPD (sell price deviation) instead of BPD. Also check `autosell.minimumPrice` — items below the threshold are skipped.

### "Economy collapsed after a player took huge loans"

The **loan circuit breaker** should have prevented this (it pauses interest when debt > GDP × `debt-gdp-tier3-ratio`). Check:
- Is `loans.enabled: true`?
- Is `loans.debt-gdp-tier3-ratio` at a reasonable level (default: 30.0)?
- Did the player default and lose credit score? Default penalty is `defaultPenalty: 50`.

### "Database is getting huge"

Auto-Tune now has automatic cleanup. Check your `cleanup:` section in config.yml:

```yaml
cleanup:
  cleanup-interval-hours: 24    # run cleanup every 24h
  transactions:
    retention-days: 14         # keep 14 days of trades
  market-history:
    retention-days: 7          # keep 7 days of price history
  economy-snapshots:
    retention-days: 30          # keep 30 days of GDP/inflation snapshots
```

### "Web dashboard won't load"

1. Check `web.enabled: true` in config.yml
2. Check the port isn't taken: `web.port: 8989`
3. Check your firewall allows the port: `sudo ufw allow 8989`
4. Check the server console for Javalin startup errors
5. Try `host: "0.0.0.0"` instead of `127.0.0.1` if binding to all interfaces

---

## Fine-Tuning Reference

### What Each Parameter Does

| Parameter | Default | Range | Effect |
|-----------|---------|-------|--------|
| `maxPriceChangePercent` | 1.5% | 0.5–5% | Max price movement per 5-min tick. Higher = more volatile. |
| `baseSpread` | 0.20 | 0.05–0.50 | Total spread at equilibrium. Higher = bigger gap between buy/sell. |
| `volumeImpact` | 0.8 | 0–1 | How much trade imbalance widens spread. Higher = more responsive. |
| `playerImpact` | 0.6 | 0–1 | How much player count compresses spread. Higher = bigger city effect. |
| `fullEffectPlayers` | 10 | Any | Player count for ~99% market responsiveness. |
| `tradeWindowDays` | 7 | 1–30 | How far back trades affect prices. Longer = more stable but slower. |
| `sellPressureMultiplier` | 1.0 | 0.5–1.5 | Extra downward pressure when players sell. >1 = faster deflation. |
| `sectorCorrelation` | 0.05 | 0–0.2 | Cross-item price influence within a section. Subtle effect. |
| `trendDampening` | 0.05 | 0–0.2 | Prevents runaway momentum in price streaks. Higher = more stable. |

### Starting Points by Server Size

| Size | `fullEffectPlayers` | `baseSpread` | `maxPriceChangePercent` |
|------|--------------------|--------------|------------------------|
| 1–5 players | 3 | 0.15 | 1.0 |
| 5–20 players | 10 | 0.20 | 1.5 |
| 20–50 players | 20 | 0.25 | 2.0 |
| 50–200 players | 30 | 0.30 | 2.5 |

### Commands Reference

| Command | Description |
|---------|-------------|
| `/shop` | Open the GUI shop |
| `/sell` | Open the sell panel (sells items in hand or opens inventory sell) |
| `/autosell` | Configure autosell settings |
| `/autosell toggle` | Enable/disable autosell for your account |
| `/autosell minprice <item> [price]` | Set per-item minimum sell price (anvil GUI) |
| `/loan` | Loan management — take, repay, list |
| `/transactions` | View your transaction history |
| `/auction` | Auction house — browse, sell, buy, manage orders |
| `/badges` | View your earned achievement badges |
| `/treasury` | Server economy treasury — balance, deposit, withdraw |
| `/pricealert <item> <above|below> <price>` | Set a price alert for an item |
| `/at admin` | Admin commands — market freeze, price override, item config, prices management |
| `/at event templates` | List available event templates from config.yml |
| `/at event invoke <name>` | Trigger a named event template immediately |
| `/at event schedule <type> <mats> <mult> <dur> <offset>` | Schedule an event to start in N minutes |
| `/at event list` | Show active and scheduled market events |
| `/at event cancel <id>` | Cancel a scheduled event |

For per-item price tuning: `/at admin item spread <material> <value>` to set a custom spread for a specific item, or `/at admin item reset <material>` to clear the override.

### Price Recovery After Exploits

If an item becomes completely mispriced due to a bug or exploit:

```
/at admin prices reset <material>
```

This resets the item's floating price to its `shops.yml` base price, clears all market history for that item (so stale trade data can't bias recovery), and evicts it from the price cache. Takes effect immediately.

For bulk price operations: `/at admin prices export` saves all current prices to a file; `/at admin prices import <file>` loads them back. Useful for migrating prices between servers.

### Shop Tooltips — Personal P&L

When browsing `/shop`, each item's tooltip shows your personal trading history:

```
Last bought: $245.90 (now -$15.80)   ← green = price fell since you bought
Last sold: $238.40 (now +$2.20)      ← green = price rose since you sold
```

This is computed from your transaction history — no extra data stored. It gives players personal anchoring in the market and creates P&L awareness without a full portfolio system.

### Price Alerts

Players can set price alerts to be notified when an item crosses a threshold:

```
/pricealert add DIAMOND below 200
/pricealert list
/pricealert remove 1
/pricealert toggle     ← pause/resume all alerts
```

Alerts trigger when the next market tick crosses the threshold. Alerts persist across restarts.

### Auction House

The auction house provides a traditional order-book marketplace — players place limit orders, and trades execute when buy/sell orders cross. Unlike the instant `/shop`, the auction lets players set their own prices and sizes.

```
/auction browse        ← view buy/sell orders (market depth)
/auction sell <price> <qty> ← list item in hand for sale (limit order)
/auction buy <material> <price> <qty> ← place a buy order (fills automatically if price ≥ best ask)
/auction my            ← view your active orders
/auction cancel <id>   ← cancel your order
/auction history       ← your fill history
/auction info <id>     ← inspect an order in detail
```

**Screens:** the bundled web dashboard at `/auction` provides 4 tabs — Active Orders (with material filter), Recent Fills, Materials Book, and My Orders — plus a depth chart showing bid/ask ladder depth. Access it at `http://your-server:8989/auction`.


**Thin book warnings:** when an item's order book has very low open interest (few orders, small sizes), admins see a warning in `/at admin auction` and in the `/admin` dashboard. Thin books let large orders move prices significantly. The warning appears when the book has fewer than 2 bid orders or fewer than 2 ask orders.


**Order expiry:** orders expire after 72 hours by default (configurable via `auction.default-duration-hours`). When an order expires:
- Buy orders: escrowed funds are **automatically refunded**
- Sell orders: items are returned to online players when possible; otherwise they are saved for `/auction reclaim`
- Filled buy orders: if items cannot be delivered because the buyer is offline or their inventory is full, they are saved as pending returns for `/auction reclaim`

**Integrity monitoring:** `/at admin auction` shows 7-day cancellation churn, fill/cancel/expire rates, self-trade fills, and large sell-wall warnings. Use this to detect manipulation patterns such as cancellation spoofing or spoofed bid walls.


**Watching orders:** players can run `/auction watch <id>` to receive an in-game notification when a watched order fills. Watch state persists across restarts and works for offline players via pending login notifications.

### Achievement Badges

Players earn badges through market activity:

| Badge | How to earn |
|-------|-------------|
| First Sale | First item sold |
| First Buyer | First item bought |
| Loan Shark | Repaid a loan of 100K+ |
| Loan Taker | Taken your first loan |
| Big Spender | Bought 1M+ worth in one transaction |
| Centurion | Traded 100+ times |
| Market Maker | Provided liquidity across 10+ items |
| Hoarder | Autosell collected 50+ items |
| Diversified | Traded 20+ different materials |
| Stable Hand | Held the same item for 7+ days without selling |
| Trend Spotter | Had a price alert fire |

Run `/badges` to see your earned badges. Use `/badges gui` for a visual showcase.

### Treasury System

The server maintains a treasury funded by transaction taxes:

```
/treasury balance       ← view current treasury balance
/treasury deposit <amt> ← deposit from your balance to treasury
/treasury withdraw <amt>← withdraw from treasury to your balance (admin only)
/treasury status        ← tax rates and collection summary
```

Taxes are collected on every buy, sell, auction fill, and loan interest compound. Configure rates in `config.yml` under `treasury.*`.

### Market Events

Scheduled server-wide events that temporarily influence prices. Events affect price velocity (how fast prices change), not absolute prices — exploits are not possible.

Define reusable event templates in `config.yml` under `market-events.events`:

```yaml
market-events:
  events:
    diamond-rush:
      type: DEMAND_SURGE
      materials: [DIAMOND, DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE]
      multiplier: 2.0
      duration-minutes: 60
```

Then trigger them via command:


```
/at event templates              ← list available templates from config.yml
/at event invoke <name>           ← trigger a named template immediately
/at event schedule <type> <mats> <mult> <duration> <offset-mins>
                                ← schedule event to start in N minutes
/at event list                   ← show active/scheduled events
/at event cancel <id>            ← cancel a scheduled event
```

Event types: `DEMAND_SURGE` (buy pressure), `SUPPLY_GLUT` (sell pressure), `INFLATION_BOOST`, `DEFLATION_DROP`, `GOLD_RUSH`, `CUSTOM`. When an event activates, all online players see a boss bar announcing it. Events auto-activate when the server's market tick fires (every 5 minutes by default).

### Admin Digest

Configure a scheduled economy digest:

```
/at admin digest         ← send digest now
/at admin digest config  ← show current digest settings
```

The digest posts to a Discord webhook (configured in `config.yml`) with economy health stats, top movers, and notable events. Admins get daily/weekly summaries without checking the web dashboard.

### Per-Item Price Bounds

Admins can set hard floor and ceiling prices per item:

```
/at admin item floor <material> <value>    ← minimum buy/sell price
/at admin item ceiling <material> <value>  ← maximum buy/sell price
/at admin item info <material>              ← show floor/ceiling if set
/at admin item reset <material>             ← clear all overrides including floor/ceiling
```

Floor prevents items from being given away; ceiling prevents price gouging on essential items.

---

## Player Economy Design

Auto-Tune's economy health depends heavily on your **player archetype mix** — the types of trading behaviors your players exhibit. The Rust market simulation (`scripts/market-simulation/`) models these as AI player archetypes so you can test configurations before deploying.

### Archetypes and Their Effects

| Archetype | Role | Effect on Economy |
|---|---|---|
| **Casual** | Balanced buyer/seller | Baseline normal activity |
| **Farmer** | Heavy seller | Natural supply; can cause underselling |
| **GuildBuyer** | Proactive buyer at dips | Buy pressure; counteracts farmer oversupply |
| **MarketMaker** | Two-sided liquidity | Tightens spreads dramatically; stabilizes prices |
| **InsiderTrader** | Mean-reversion | Healthy: +30.1% GDP, D/G +2.41x (watch D/G). Stressed: −12.2% GDP, D/G −1.32x (counter-cyclical benefit — only add to HEALTHY economies) |
| **Newbie** | Net consumer, high buy rate | **Healthy: +41.5% GDP, D/G +34.7% (watch borrowing), volatility −68%. Stressed: +33.3% GDP, D/G −42.1%, volatility −35%. ALWAYS dramatically reduces volatility — best archetype for high-volatility servers. High D/G in healthy economies (they borrow to fund buying).** |
| **VolumeTrader** | Spread compressor | Always harmful — −9% to −13% GDP; never recommended |

### Recommended Archetype Config (2MM + 2GB)

Simulation testing across 5 seeds confirms: **2 MarketMakers + 2 GuildBuyers** produces the healthiest economy.

> ⚠️ **Note on GuildBuyer threshold:** This is a Rust simulation parameter (fixed threshold override), not a Java plugin config. The simulation's GuildBuyer archetypes randomize between 15–30% per-player in the base scenario. The "5% vs 7%" finding applies when the simulation forces a uniform threshold across all GuildBuyers. Java plugin GuildBuyer behavior is not directly configurable — it is emergent from the `sell_pressure_multiplier` and MarketEngine parameters.

**GuildBuyer threshold in simulation (updated 2026-04-20):** 5% is recommended. At 14d: 7% wins (+7% GDP). At 30d: 7% and 5% produce EQUAL GDP (+0.7%) but 7% D/G is +2.89× WORSE (17.8× vs 14.9×). The 7% GDP advantage is a 14d artifact. Use 7% only for servers <14 days. At 10%+, GuildBuyers accumulate dangerous debt on single purchases.

### Tuning for Your Server Size

- **Small server (5–10 players):** 1 MarketMaker + 1 GuildBuyer. More MMs than players causes over-trading.
- **Medium server (10–20 players):** 2 MarketMaker + 2 GuildBuyer. This is the validated recommended config.
- **Large server (20–50 players):** 2 MarketMaker + 2 GuildBuyer. Do NOT add VolumeTraders — they are always harmful in any economy condition (-9% to -13% GDP).
- **Avoid:** InsiderTrader + VolumeTrader combination — catastrophic (-25.6% GDP, IT's gains reversed by VT). GuildSellers (confirmed dead-end). AFKFarmers (catastrophic: -49.5% GDP, +66% volatility).

### The Floor Percent

**⚠️ Updated recommendation (90-day sim):** The floor is NOT a long-run health mechanism.
- **Short-term servers (<30 days):** 50-60% floor is fine — seller protection benefit outweighs long-run cost
- **Long-running servers (>60 days):** Set to 30% or disable. Floor at 60% → GDP -19.1% and D/G +1.6x worse at 90d vs no floor.
- **70%+:** destructive regardless of horizon — internal prices collapse while displayed prices stay high

_For full config documentation, see [CONFIG_GUIDE.md](./CONFIG_GUIDE.md). For architecture internals, see [ARCHITECTURE.md](./ARCHITECTURE.md)._
