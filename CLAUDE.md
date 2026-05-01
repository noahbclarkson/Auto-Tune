# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build the plugin (produces shadow JAR in build/libs/)
./gradlew build

# Build and run a local Paper 1.21.4 test server
./gradlew runServer

# Build only the web frontend (Next.js static export)
cd web && npm run build

# Build the standalone web-optimizer (public Auto-Tune Page)
cd web-optimizer && npm run build

# Run the Rust market simulation GUI
cd scripts/market-simulation && cargo run --release

# Generate market curve visualizations (requires numpy, matplotlib)
python scripts/market_curves.py
```

The `build` task depends on `shadowJar`, which relocates all dependencies under `com.noahblclarkson.autotune.lib.*`. The `buildWeb` task runs `npm run export` in `web/` and copies the static output into `src/main/resources/web/` before `processResources`.

There are unit tests in `src/test/java/` using JUnit 5 + Mockito 4. MarketEngine has comprehensive tests (36 tests). See `MarketEngineTest.java`.

Validation is also done via the Rust market simulation and manual testing on a Paper server. All three market engine implementations (Java `MarketEngine.java`, Rust `scripts/market-simulation/src/engine.rs`, TypeScript `web-optimizer/src/lib/market-engine.ts`) must stay in sync — same default values, same formulas.

## Architecture

**Minecraft Paper plugin** (Java 21, Paper 1.21.4) with Guice DI, JDBI for database access, Javalin embedded web server, and a Next.js dashboard frontend.

**Package:** `com.noahblclarkson.autotune`

### Plugin Lifecycle (`AutoTune.java`)

onEnable: ConfigManager (YAML) -> Vault economy hook -> DatabaseManager (HikariCP + migrations) -> Guice injector (`AutoTuneModule`) -> managers/commands/listeners -> TaskScheduler -> WebServer

onDisable: WebServer stop -> TaskScheduler stop -> DatabaseManager shutdown

### Key Packages

| Package | Purpose |
|---------|---------|
| `manager/` | Core domain logic: `MarketEngine` (pricing/spreads/trends), `ShopManager` (item cache, buyable logic), `AutosellManager`, `EconomyMetricsManager`, `TreasuryService` (tax collection) |
| `database/` | `DatabaseManager` (HikariCP, migrations, async executor) + JDBI repositories |
| `economy/` | `EconomyManager` (buy/sell processing via Vault), `LoanManager` (interest, defaults, circuit breaker, and manual admin recovery mode that freezes interest and pauses new loans) |
| `command/` | Cloud (Incendo) annotation-based commands: `/shop`, `/sell`, `/autosell`, `/loan`, `/transactions`, `/auction`, `/treasury`, `/autotune admin` |
| `config/` | `ConfigManager` loads YAML; `AutoTuneConfig` is a nested record hierarchy (StorageConfig, WebConfig, EconomyConfig, LoanConfig, AutosellConfig, TreasuryConfig, GuiConfig, DebugConfig) |
| `model/` | Immutable Java records with builder pattern: ShopItem, Transaction, Loan, PlayerData, EconomySnapshot, PriceHistory, etc. |
| `ui/` | InventoryFramework GUIs: ShopGui, SellGui, AutosellGui, TrendsGui, TransactionHistoryGui |
| `listener/` | SellGuiListener (sell on inventory close), AutosellListener (inventory change triggers), PlayerListener (join/leave) |
| `web/` | `WebServer` - Javalin REST API (`/api/items`, `/api/prices`, `/api/spreads`, `/api/economy/*`) + WebSocket + static file serving |
| `task/` | `TaskScheduler` - Paper async scheduler: market tick (5min), loan interest (24h), overdue check (1h), economy snapshot (5min) |

### Database

SQLite (default) or MariaDB. Schema versioned manually via `at_schema_version` table (not Flyway). Migrations in `src/main/resources/db/` now include the consolidated V1 plus incremental rewrite-2 repair/feature migrations through V7. Schema includes: `at_items`, `at_market_history`, `at_players`, `at_autosell_items` (per-item autosell + min price), `at_loans`, `at_item_ratios`, `at_transactions`, `at_sections`, `at_economy_snapshots`, `at_price_alerts`, auction tables, watched auctions, admin audit log, and `at_circuit_events` for circuit-breaker/admin-recovery timeline transitions. All async DB ops go through `DatabaseManager.supplyAsync()`/`runAsync()` with main-thread callbacks via `runOnMain()`. SQLite uses a single-thread executor; MySQL uses pool-sized executor.

### Market Engine (`MarketEngine.java`)

> **Unit testing note:** `MarketEngine` depends on `PluginAdapter` (not directly on `AutoTune`) so it can be tested without a live server. See `PluginAdapter.java` and `DefaultPluginAdapter.java`. Run tests with `./gradlew test`.

The most complex component. Core concepts:

- **Asymmetric BPD/SPD spreads**: buyPrice = base * (1 + BPD), sellPrice = base * (1 - SPD)
- **Spread pipeline**: base spread -> volume imbalance shift -> liquidity reduction -> player count reduction -> global volume multiplier
- **Price changes**: tradeRatio * playerScaling * maxPriceChangePercent, with sell pressure multiplier and trend dampening
- **Player scaling**: `tanh(onlineCount * atanh(0.99) / fullEffectPlayers)` - reaches 99% at `fullEffectPlayers`
- **Trade window**: recency-weighted transactions over configurable window (adaptive mode scales 1-7 days targeting 100 tx/day)
- **Caching**: ConcurrentHashMap caches for prices, spreads, trend direction/streak, per-tick volumes; cleared on `reload()`

### Auction House

The auction house matching engine and in-game GUI are fully implemented in the Java plugin (rewrite-2). The Rust API server no longer has auction functionality — that concern is handled entirely by the plugin.

Components:
- `AuctionMatchingEngine.java` — price-time priority, maker price execution, fill generation
- `AuctionManager.java` — order placement, fill processing, escrow for buy orders
- `AuctionRepository.java` — JDBI CRUD + audit queries for `at_auction_orders` + `at_auction_fills` tables. Note: SQLite JDBC stores bound `Timestamp` values as integer epoch milliseconds, so daily fill aggregation must use `DATE(filled_at / 1000, 'unixepoch')` on SQLite and `DATE(filled_at)` on MySQL/MariaDB; active/expired order queries should bind `Instant.now()` as a `Timestamp` instead of comparing against SQL `CURRENT_TIMESTAMP`.
- `AuctionCommand.java` — Cloud command `/auction` (browse, sell, buy, my, cancel, history)
- `AdminCommand.java` + `WebServer.java` expose auction integrity health (`/at admin auction`, `/api/admin/auction-audit`): status counts, 7d churn, self-trade fills, thin books, and large sell-wall warnings.
- `AuctionGui.java` — 6-row chest GUI with sell/buy columns, click-to-fill, cancel
- `MarketHistoryGui.java` — in-game price history chart (JFreeChart rendered to BufferedImage)

> ✅ **Fixed (2026-03-26):** `processFill()` now credits the seller's Vault balance (`economy.depositPlayer(seller, ...)`) and gives the buyer their items (`player.getInventory().addItem(...)`) on the Bukkit main thread. Buyer's funds were already withdrawn in `placeBuyOrderAsync`; this closes the escrow gap.

### Web Frontend (`web/`)

Next.js 14 + TypeScript + Tailwind + Recharts. Built as static export. Dashboard components in `web/src/components/dashboard/` (price-chart, economy-panel, item-table, stats-cards, transaction-feed). Item detail components in `web/src/components/items/` (item-detail-header with material/metadata, item-stats-row with spread-bar visualization, item-transactions-table, price-chart with OHLC candlesticks). The Gradle `buildWeb` task compiles and copies output before JAR packaging.

Brand colors: `--primary` is emerald (HUSL 160°), not blue. The light/dark theme toggle persists via localStorage.

**Live price updates:** The `useWebSocket` hook connects to `/ws/market` on the Javalin server and receives `price_update` messages with item prices. It is wired into the app context (`livePrices` Map + `isWsConnected`). The home page Top Movers section consumes `livePrices` — when a WebSocket price arrives, it updates the displayed buy/sell prices in real-time (between 30s polls) and flashes the updated row green. The header's `LiveIndicator` shows the WebSocket connection status.

### Web-Optimizer Frontend (`web-optimizer/`)

Standalone Next.js 14 app (not bundled in the plugin). Used by server admins worldwide to explore true prices, compare servers, and simulate spread scenarios. Routes:
- `/` — Landing page (hero, algorithm preview, dynamic economy section, feature cards, how-it-works CTA)
- `/simulator` — Spread/price simulator with 5-factor market engine, real-time chart, and preset scenarios
- `/true-prices` — Live true-price feed with server filtering and material browser
- `/exchange-rates` — Per-server price multiplier vs true-price baseline (bar chart + table)
- `/servers` — Network overview of registered servers
- `/how-it-works` — Full technical breakdown with formulas

Uses dark emerald theme (emerald-400 primary accent). Public — no auth required by default (set `NEXT_PUBLIC_API_URL` for live data).

### Rust Market Simulation (`scripts/market-simulation/`)

Standalone Rust/egui GUI app that mirrors the Java MarketEngine exactly (same constants, same formulas). Used for testing parameter changes visually. Features 8 player archetypes: Casual, Farmer, Trader, Hoarder, Exploiter, Newbie, AFKFarmer, GuildBuyer — each with distinct trading behaviors. GuildBuyer is inventory-targeting (buys to fill guild stock, sells surplus above 2x target). Headless mode: `cargo run --release -- --headless <scenario>`. CLI tools: `--sweep` (840-config grid), `--regression` (determinism check), `--analyze <db>` (result analysis), `--correlation-test` (sector correlation). SQLite session recording. Build with `cargo build --release`, run with `cargo run --release`.

## Dependency Relocation

The shadow JAR relocates: Cloud, HikariCP, JDBI, Javalin, Jetty, InventoryFramework, Guice, and their transitive dependencies to `com.noahblclarkson.autotune.lib.*`. This prevents classpath conflicts with other plugins. When adding new dependencies, check `build.gradle.kts` to see if relocation is needed.

## CI/CD

GitHub Actions (`.github/workflows/gradle.yml`): triggers on push/PR to `master`, runs `gradle test build`, uploads JAR artifact. Note: CI uses JDK 21. The project targets Java 21. Ensure `openjdk-21-jdk-headless` is used in CI/build environments.

## Branch Policy

- **`rewrite-2`** — active development branch. Do NOT merge to `main` until stable.
- All work in this branch. Feature flags for incomplete work.
- Schema migrations: since `rewrite-2` is not live yet, consolidate migrations into a single clean V1 schema rather than incremental add/remove migrations. The `dc4fa21` commit demonstrates this consolidation.

## Ecosystem Overview

```
Player trades → Java Plugin (EconomyManager)
                         ↓
              MarketEngine.tick() (5 min)
                         ↓
              SQLite/MariaDB (at_items, at_transactions, etc.)
                         ↓
              Javalin WebServer (:8989) → bundled Next.js dashboard (web/)
                         ↓
              PriceReporter → API Server (Rust/Actix) :8080
                         ↓
              Price Solver (log-space least-squares)
                         ↓
              True Prices → web-optimizer/ (public landing + simulator)
```

## Key Integration Points

- **Plugin → API Server**: `PriceReporter` HTTP POST pushes item prices to the Rust API server every 5 min. Submissions go through a bounded retry queue (5-entry cap, 3 attempts, 1-min drain task) so transient API downtime doesn't silently drop submissions.
- **API Server → True Prices**: Rust `market_server` computes log-space least-squares true prices
- **API Server → web-optimizer**: `web-optimizer/` fetches via `lib/api-client.ts` (fetch with error/resilience)
- **Plugin → web/**: Bundled static Next.js dashboard served by Javalin on port 8989
- **Auction house**: Implemented in Java plugin (rewrite-2). No auction functionality remains in Rust API server.
- **Tax system**: `TreasuryService` collects buy/sell/auction/loan-interest taxes into the server treasury. `/treasury` command for balance, deposit, withdraw. Dynamic tax rates configurable per transaction type.
- **Circuit timeline**: `LoanManager` records transitions between `NORMAL`, `TIER1`, `TIER2`, `TIER3`, and `ADMIN_RECOVERY` into `at_circuit_events`; `WebServer` exposes `/api/economy/circuit-events`; bundled `web/` annotates `/economy` history with those state changes and renders D/G, interest multiplier, and admin action guidance on recent event cards.

## Active Engineering Roles

- **Plugin Engineer** — Java Paper plugin, market engine, economy logic
- **Simulation Lab** — Rust market simulation (egui) and parameter exploration
- **Web & Ecosystem** — `web/` dashboard polish, `web-optimizer/` public frontend, documentation, ecosystem integration
