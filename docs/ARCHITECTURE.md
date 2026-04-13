# Architecture Guide

> Auto-Tune rewrite-2. How all the pieces fit together.

---

## System Overview

Auto-Tune is a Minecraft economy engine. At its core, it watches player trades and adjusts item prices so supply and demand naturally drive the market. No manual price updates required.

The system spans three environments:

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Java Plugin** | Paper 1.21.4, Guice, JDBI | In-game economy, commands, GUIs |
| **Rust API Server** | Actix-web, PostgreSQL | Cross-server price solving, true prices |
| **TypeScript Frontends** | Next.js (2 apps) | Admin dashboard + public optimizer site |

```
┌─────────────────────────────────────────────────────────────┐
│  Minecraft Server (Paper 1.21.4)                            │
│                                                             │
│  MarketEngine ──► SQLite ──► Javalin (:8989) ──► web/       │
│  ShopManager        ┌───────────────────────────────────────► Player browser
│  LoanManager        │  (bundled Next.js dashboard)         │
│  AuctionHouse       │                                       │
│  PriceReporter ─────┼──► HTTP POST ratio matrix              │
│                     │                                       │
└─────────────────────┼───────────────────────────────────────┘
                      │ :8080
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  Rust API Server (Actix-web)                                │
│                                                             │
│  Price solver ◄──► PostgreSQL                               │
│  Exchange rates                                             │
│  Rate limiting (token bucket per IP)                        │
└─────────────────────┬───────────────────────────────────────┘
                      │ :3000 (standalone Next.js)
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  web-optimizer/ (public site)                               │
│                                                             │
│  True prices  ·  Exchange rates  ·  Simulator              │
│  Server explorer  ·  How-it-works                          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  scripts/market-simulation/ (Rust, standalone)               │
│                                                             │
│  Headless market simulation — same engine math as Java      │
│  Scenarios, parameter sweeps, SQLite result DBs             │
│  --analyze <db> for single-run reports                      │
│  --analyze-dir <dir> for cross-run comparison              │
└─────────────────────────────────────────────────────────────┘
```

---

## Java Plugin

### Packages

| Package | Classes | Responsibility |
|---------|---------|---------------|
| `manager/` | `MarketEngine`, `ShopManager`, `EconomyManager`, `LoanManager`, `PriceAlertManager`, `DatabaseCleanupManager` | Core business logic |
| `auction/` | `AuctionMatchingEngine`, `AuctionManager`, `AuctionRepository` | In-game auction house |
| `web/` | `WebServer`, resources under `resources/web/` | Javalin REST + bundled Next.js |
| `command/` | `ShopCommand`, `SellCommand`, `AutosellCommand`, `LoanCommand`, `AlertCommand`, `AuctionCommand` | Cloud 2.0 command tree |
| `gui/` | `ShopGui`, `SellGui`, `AutosellGui`, `TrendsGui`, `AuctionGui` | InventoryFramework GUIs |
| `db/` | `DatabaseManager`, `*Repository` JDBI DAOs | SQLite/MariaDB persistence |

### Market Engine

The brain. Recalculates prices every `update-interval` ticks (default: 6000 = 5 min).

**Price update pipeline (7 steps):**

1. **Trade collection** — gather all transactions within `trade-window-days` (default 7d), recency-weighted (recent = more influence)
2. **Trade ratio** — `(weightedBuys − weightedSells) / totalWeighted`
3. **Player scaling** — `tanh(onlineCount × atanh(0.99) / fullEffectPlayers)` — dampens price changes when few players online
4. **Price change cap** — `tradeRatio × playerScaling × maxPriceChangePercent`
5. **Sell pressure asymmetry** — `sellPressureMultiplier` (default 1.0, symmetric). Evidence (5-seed, 2026-04-13): sp=0.80 → GDP +5.2% but D/G +39.9% WORSE. Set to 1.0 for stable economies; 0.80 for growth-oriented admins. Exposed in config as `sell-pressure-multiplier`.
6. **Trend dampening** — reduces continued-direction moves (`trendDampening`, default 0.10)
7. **Sector correlation** — nudges related items in the same direction (`sectorCorrelation`, default 0.05)

No hard min/max bounds. Prices are purely market-driven through the above mechanisms.

**Spread pipeline (5 steps, outputs BPD and SPD):**

1. Base half-spread: `baseSpread / 2`
2. Volume imbalance: shifts BPD or SPD depending on dominant trade direction
3. Liquidity reduction: high-volume items get tighter spreads
4. Player count reduction: more players → tighter spreads
5. Global volume multiplier: z-score of recent activity widens/narrows spreads

**Data flow per tick:**
```
Player trade
  → EconomyManager.processBuy / processSell
    → TransactionRepository.insert()   ← persisted immediately
    → MarketEngine.recordTrade()         ← in-memory only
  → TaskScheduler.marketTick()
    → MarketEngine.recalculateAll()
      → ShopManager.getShopItems()
      → for each item: MarketEngine.calculateNewPrice()
      → ItemRepository.update()          ← batched, async (supplyAsync + join)
      → PriceReporter.submitIfReady()   ← ratio matrix to API server
```

### Database Schema (V1 — consolidated 2026-03-26)

Single migration `V1__Initial_Schema.sql`. Key tables:

| Table | Purpose | Growth |
|-------|---------|--------|
| `at_items` | Material, price, section, overrides | +1 per new item seen |
| `at_market_history` | 5-min OHLCV snapshots | 288 rows/item/day — **pruned after 7d** |
| `at_transactions` | Every trade | unbounded — **pruned after 14d** |
| `at_players` | UUID, credit score, totals | +1 per unique player |
| `at_loans` | Active + historical loans | moderate |
| `at_autosell_items` | Per-player per-item autosell settings | player × items |
| `at_economy_snapshots` | 5-min GDP/debt/inflation | 288/day — **pruned to last 30d** |
| `at_price_alerts` | Player price alerts | player × alerts |
| `at_sections` | Item category metadata | ~10 rows |

**Cleanup:** `DatabaseCleanupManager` runs on startup then every `cleanup.interval-hours` (default 24). Each table has independent retention config.

### PriceReporter

Submits ratio matrix to the Rust API server every `report-interval` ticks (default 60000 = 5 min).

**Current risk:** If the API server is down, submissions are silently dropped. No retry queue or offline buffering. **This is a known gap** — fix before production.

### Auction House

Fully in the Java plugin (migrated from Rust API on 2026-03-25). Not a cross-server feature.

- **Matching:** `AuctionMatchingEngine` — price-time priority, maker-price execution
- **Orders:** `AuctionManager.placeBuyOrderAsync` / `placeSellOrderAsync` — async DB write
- **Fills:** `AuctionManager.processFill` (command path) and `recordFillAsync` (GUI path) — both credit the seller's Vault balance and give items to the buyer on Bukkit main thread via scheduler
- **GUI:** `AuctionGui` — 6-row chest, sell/buy columns, click-to-fill, cancel own orders

---

## Rust API Server

**Purpose:** Cross-server price discovery. Combines ratio matrices from multiple servers, solves a constrained least-squares system in log-space to produce "true" universal prices.

### Routes

```
GET  /health                       — health check
POST /api/servers/register         — register server (fast tier rate limit)
GET  /api/servers                  — list registered servers
PUT  /api/servers/{id}             — update server metadata
DELETE /api/servers/{id}           — deregister server
GET  /api/prices/true             — get solved true prices + confidence
POST /api/servers/{id}/prices      — submit ratio matrix (submit tier rate limit)
GET  /api/prices/history/{item}    — price history for an item
GET  /api/servers/exchange-rates   — per-server exchange rates vs true-price baseline
```

### Price Solver

1. Collect ratio matrices from all servers
2. For each server: for every pair (i,j), log-ratio = `ln(price_i / price_j)` — shared across servers
3. Weighted geometric mean across servers (weighted by server activity)
4. LU decomposition in log-space via `nalgebra` to solve for relative prices
5. Anchor one item to an absolute value to scale the whole graph

**Confidence scoring:** `SolveResult` from `compute_prices_with_quality()`. quality = `exp(-5 × rms_residual)` where RMS residual measures how well the solved prices fit the observed ratios. Perfect fit → 1.0. Combined with server-count and item-coverage bonuses.

**Rate limiting:** Token-bucket per IP.
- `submit_prices`: 6 requests/min, 1 token/sec refill
- `register_server`: 10 burst, 5 tokens/sec refill
- 1 MiB max request body (ratio matrices can be large)

---

## Market Simulation (`scripts/market-simulation/`)

Standalone Rust app that mirrors the Java MarketEngine exactly. Used to:

- **Validate** the engine is mathematically stable across parameter ranges
- **Stress-test** edge cases (market crash, exploit, hyperinflation, loan cascade)
- **Run parameter sweeps** (840 configs) to find unexpected behavior
- **Calibrate** parameters before deployment

**Key finding (2026-03-25):** ALL 840 parameter combinations tested produced stable markets (avg volatility < 0.05). The engine is robust across the full parameter space. Underselling (prices 30–65% below base) is structural — player archetypes (farmer-heavy) drive it, not engine parameters.

**Loan circuit breaker:** Rust sim now mirrors Java LoanManager — pauses interest when `debt / GDP > 10.0`. Verified in stressed scenario: debt went from catastrophic to manageable.

**Analyzer CLI:**
```
cargo run --release -- --analyze <sim.db>     — single run deep report
cargo run --release -- --analyze-dir <dir>    — cross-run comparison
```

---

## Web Frontends

### `web/` — Admin Dashboard (bundled in plugin JAR)

Served by Javalin on `:8989`. Built as Next.js static export, copied to `resources/web/`, included in shadow JAR.

Shows the **local server economy** — prices, spreads, GDP, debt, loans, transactions, top movers.

Pages:
- `/` — Main dashboard: StatsCards, MarketHealthBar, Top Movers, Transaction Feed
- `/compare` — Side-by-side item comparison with ratio + spread chart
- `/economy` — GDP, debt, inflation, volume charts
- `/items` — Searchable item list with price/spread stats
- `/items/detail` — Item detail with OHLC chart + transaction history
- `/leaderboard` — Top traders
- `/loans` — Active loans table

WebSocket (`ws://`) for live price updates — polls every 30s fallback.

### `web-optimizer/` — Public Auto-Tune Page (standalone)

Deployed separately. Shows **cross-server true prices** from the Rust API server.

Pages:
- `/` — Landing: Hero, FeatureCards, DynamicEconomy preview, AlgorithmPreview
- `/true-prices` — Live solved prices with confidence scores + local calculator
- `/exchange-rates` — Per-server exchange rates
- `/servers` — Registered server explorer
- `/simulator` — Real-time price simulator (matches Java engine math exactly)
- `/how-it-works` — Explainer page

The simulator (`/simulator`) is particularly useful — it runs the identical spread/price math as the Java plugin via `market-engine.ts`, so admins can predict behavior before changing config.

---

## market-engine.ts

TypeScript port of the core market engine math. Lives in `web-optimizer/src/lib/market-engine.ts`. Serves as the canonical reference for the spread pipeline.

**Must stay in sync** with:
1. Java: `src/main/java/.../manager/MarketEngine.java`
2. Rust: `scripts/market-simulation/src/engine.rs`

**Synced defaults (2026-03-25):**
```
baseSpread: 0.20
volumeImpact: 0.8
playerImpact: 0.6
liquidityCoeff: 0.01
liquidityFullEffectTraders: 10
fullEffectPlayers: 10
maxPriceChangePercent: 1.5
tradeWindowDays: 7
```

**TS-only extras:**
- `getSpreadFactors()` — returns per-factor breakdown (imbalance, liquidity, player, globalVolume)
- `generateSpreadCurve()` — pre-computes spread curves for visualization
- `simulatePrice()` — visualization helper (not in Java)

---

## Configuration System

`config.yml` → `ConfigManager.java` → nested record hierarchy:

```
AutoTuneConfig
├── spread: SpreadConfig
├── playerScaling: PlayerScalingConfig
├── loans: LoanConfig
├── enchantments: EnchantmentConfig
├── web: WebConfig
├── autosell: AutosellConfig
├── cleanup: CleanupConfig
└── (others)
```

**Key tuning knobs:**
- `base-spread` — controls absolute spread width (bs=0.10 → BPD~1.5%, bs=0.30 → BPD~7%)
- `sell-pressure-multiplier` — reduces downward pressure on sell transactions (1.0 = symmetric, recommended; 0.80 = growth-oriented, worsens D/G ~40%)
- `trade-window-days` — longer = smoother prices, slower reaction; shorter = faster adaptation, more volatile
- `max-price-change-percent` — per-tick price change cap (higher = faster adaptation, more volatility)
- `debt-gdp-circuit-breaker-ratio` — essential safeguard (10.0 default)

See `docs/CONFIG_GUIDE.md` for full reference.

---

## Key Risks & Known Gaps

| Risk | Severity | Status |
|------|----------|--------|
| PriceReporter silent drop on API down | High | **Known gap** — no retry queue |
| Underselling (prices 30–65% below base) | Medium | Structural — fix via player mix, not params |
| No Java unit tests | Medium | Validated via Rust simulation instead |
| Manual engine sync (no automated validation) | Medium | TS/Java/Rust must be kept in sync by convention |
| PMD debt (2200+ style warnings) | Low | Non-blocking, deferred post-rewrite-2 |

---

## Technology Choices

| Component | Choice | Why |
|-----------|--------|-----|
| Plugin platform | Paper 1.21.4 | Modern API, wide server adoption |
| DI | Guice | Clean constructor injection |
| DB | SQLite (default), MariaDB (production) | JDBI for portable SQL |
| Commands | Cloud 2.0.0-beta.14 | Fluent API, annotation-driven |
| GUI | InventoryFramework 0.11.6 | ChestGui + HopperGui pattern |
| Economy | Vault | Universal interface |
| Web server | Javalin | Lightweight, embeddable |
| API server | Actix-web | Fast, async Rust |
| Price solver | nalgebra (LU decomposition) | Proven linear algebra |
| Frontend | Next.js (static export for web/, standalone for optimizer) | SSR + static, easy deployment |
| Simulation | Egui + custom Rust engine | Reproducible, deterministic |
