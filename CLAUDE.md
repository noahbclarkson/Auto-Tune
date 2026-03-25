# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build the plugin (produces shadow JAR in build/libs/)
./gradlew build

# Build and run a local Paper 1.21.4 test server
./gradlew runServer

# Build only the web frontend (Next.js static export)
cd web && npm run export

# Run the Rust market simulation GUI
cd scripts/market-simulation && cargo run --release

# Generate market curve visualizations (requires numpy, matplotlib)
python scripts/market_curves.py
```

The `build` task depends on `shadowJar`, which relocates all dependencies under `com.noahblclarkson.autotune.lib.*`. The `buildWeb` task runs `npm run export` in `web/` and copies the static output into `src/main/resources/web/` before `processResources`.

There are no unit tests in this project. Validation is done via the Rust market simulation and manual testing on a Paper server. All three market engine implementations (Java `MarketEngine.java`, Rust `scripts/market-simulation/src/engine.rs`, TypeScript `web-optimizer/src/lib/market-engine.ts`) must stay in sync — same default values, same formulas.

## Architecture

**Minecraft Paper plugin** (Java 21, Paper 1.21.4) with Guice DI, JDBI for database access, Javalin embedded web server, and a Next.js dashboard frontend.

**Package:** `com.noahblclarkson.autotune`

### Plugin Lifecycle (`AutoTune.java`)

onEnable: ConfigManager (YAML) -> Vault economy hook -> DatabaseManager (HikariCP + migrations) -> Guice injector (`AutoTuneModule`) -> managers/commands/listeners -> TaskScheduler -> WebServer

onDisable: WebServer stop -> TaskScheduler stop -> DatabaseManager shutdown

### Key Packages

| Package | Purpose |
|---------|---------|
| `manager/` | Core domain logic: `MarketEngine` (pricing/spreads/trends), `ShopManager` (item cache, buyable logic), `AutosellManager`, `EconomyMetricsManager` |
| `database/` | `DatabaseManager` (HikariCP, migrations, async executor) + 6 JDBI repositories |
| `economy/` | `EconomyManager` (buy/sell processing via Vault), `LoanManager` (interest, defaults) |
| `command/` | Cloud (Incendo) annotation-based commands: `/shop`, `/sell`, `/autosell`, `/loan`, `/transactions` |
| `config/` | `ConfigManager` loads YAML; `AutoTuneConfig` is a nested record hierarchy (StorageConfig, WebConfig, EconomyConfig, LoanConfig, GuiConfig, DebugConfig) |
| `model/` | Immutable Java records with builder pattern: ShopItem, Transaction, Loan, PlayerData, EconomySnapshot, PriceHistory, etc. |
| `ui/` | InventoryFramework GUIs: ShopGui, SellGui, AutosellGui, TrendsGui, TransactionHistoryGui |
| `listener/` | SellGuiListener (sell on inventory close), AutosellListener (inventory change triggers), PlayerListener (join/leave) |
| `web/` | `WebServer` - Javalin REST API (`/api/items`, `/api/prices`, `/api/spreads`, `/api/economy/*`) + WebSocket + static file serving |
| `task/` | `TaskScheduler` - Paper async scheduler: market tick (5min), loan interest (24h), overdue check (1h), economy snapshot (5min) |

### Database

SQLite (default) or MariaDB. Schema versioned manually via `at_schema_version` table (not Flyway). Migrations in `src/main/resources/db/` (V1 = initial schema, V2 = per-item market engine overrides). All async DB ops go through `DatabaseManager.supplyAsync()`/`runAsync()` with main-thread callbacks via `runOnMain()`. SQLite uses a single-thread executor; MySQL uses pool-sized executor.

### Market Engine (`MarketEngine.java`)

The most complex component. Core concepts:

- **Asymmetric BPD/SPD spreads**: buyPrice = base * (1 + BPD), sellPrice = base * (1 - SPD)
- **Spread pipeline**: base spread -> volume imbalance shift -> liquidity reduction -> player count reduction -> global volume multiplier
- **Price changes**: tradeRatio * playerScaling * maxPriceChangePercent, with sell pressure multiplier and trend dampening
- **Player scaling**: `tanh(onlineCount * atanh(0.99) / fullEffectPlayers)` - reaches 99% at `fullEffectPlayers`
- **Trade window**: recency-weighted transactions over configurable window (adaptive mode scales 1-7 days targeting 100 tx/day)
- **Caching**: ConcurrentHashMap caches for prices, spreads, trend direction/streak, per-tick volumes; cleared on `reload()`

### Web Frontend (`web/`)

Next.js 14 + TypeScript + Tailwind + Recharts. Built as static export. Dashboard components in `web/src/components/dashboard/` (price-chart, economy-panel, item-table, stats-cards, transaction-feed). Item detail components in `web/src/components/items/` (item-detail-header with material/metadata, item-stats-row with spread-bar visualization, item-transactions-table, price-chart with OHLC candlesticks). The Gradle `buildWeb` task compiles and copies output before JAR packaging.

### Rust Market Simulation (`scripts/market-simulation/`)

Standalone egui GUI app that mirrors the Java MarketEngine exactly (same constants, same formulas). Used for testing parameter changes visually. Features 5 player archetypes (Casual, Farmer, Trader, Hoarder, Exploiter) with distinct trading behaviors. Includes SQLite recording for data export. Build with `cargo build --release`, run with `cargo run --release`.

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

Key gap: The auction house in the Rust API server (`api-server/`) must eventually move to an in-game Java GUI feature. Remove from API once the Java version is built.
