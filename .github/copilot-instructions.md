# GitHub Copilot Instructions

## Build & Run Commands

```bash
# Full build (compiles web frontend, then shadow JAR)
./gradlew build

# Build and spin up a local Paper 1.21.4 test server
./gradlew runServer

# Web frontend only (Next.js → static export → copied into JAR resources)
cd web && npm run export

# Rust market simulation GUI (mirrors the Java MarketEngine exactly)
cd scripts/market-simulation && cargo run --release

# Market curve visualizations (requires numpy, matplotlib)
python scripts/market_curves.py
```

There are **no unit tests**. Validation is done via the Rust simulation and manual testing on a Paper server. CI runs `gradle test build` but the test suite is empty.

> **CI/CD Note:** `.github/workflows/gradle.yml` uses JDK 17, but the project targets Java 21. Build locally with JDK 21.

## Architecture

Auto-Tune is a **Minecraft Paper 1.21.4 plugin** that implements a supply-and-demand economy. The codebase has three distinct layers:

1. **Java plugin** (`src/main/java/com/noahblclarkson/autotune/`) — the Paper plugin with Guice DI, JDBI repositories, Javalin REST/WebSocket server, and InventoryFramework GUIs.
2. **Next.js dashboard** (`web/`) — built as a static export and bundled inside the shadow JAR under `src/main/resources/web/`.
3. **Rust simulation** (`scripts/market-simulation/`) — a standalone egui app that mirrors the Java `MarketEngine` formulas exactly for parameter testing.

### Plugin Lifecycle

```
onEnable:  ConfigManager → Vault hook → DatabaseManager → Guice injector
           → managers/commands/listeners → TaskScheduler → WebServer

onDisable: WebServer → TaskScheduler → DatabaseManager
```

### Key Packages

| Package | Purpose |
|---------|---------|
| `manager/` | `MarketEngine` (pricing, spreads, trends), `ShopManager` (item cache), `AutosellManager`, `EconomyMetricsManager` |
| `database/` | `DatabaseManager` (HikariCP + async executor) + 6 JDBI repositories |
| `economy/` | `EconomyManager` (buy/sell via Vault), `LoanManager` |
| `command/` | Incendo Cloud annotation-based commands: `/shop`, `/sell`, `/autosell`, `/loan`, `/transactions` |
| `config/` | `ConfigManager` loads YAML into `AutoTuneConfig` — a nested record hierarchy |
| `model/` | Immutable Java records with manual builder pattern |
| `ui/` | InventoryFramework GUIs: `ShopGui`, `SellGui`, `AutosellGui`, `TrendsGui`, `TransactionHistoryGui` |
| `listener/` | `SellGuiListener`, `AutosellListener`, `PlayerListener` |
| `web/` | Javalin REST API (`/api/items`, `/api/prices`, `/api/spreads`, `/api/economy/*`) + WebSocket |
| `task/` | `TaskScheduler` — market tick (5 min), loan interest (24 h), overdue check (1 h), economy snapshot (5 min) |

### Database

SQLite (default) or MariaDB. Schema is **manually versioned** via an `at_schema_version` table — there is no Flyway or Liquibase. Migration SQL lives in `src/main/resources/db/`. All async DB work goes through `DatabaseManager.supplyAsync()` / `runAsync()`; results return to the main thread via `runOnMain()`. SQLite uses a single-thread executor; MariaDB uses a pool-sized executor.

### MarketEngine (most complex component)

- **Spreads:** asymmetric BPD/SPD pipeline — base → volume imbalance shift → liquidity reduction → player count reduction → global volume multiplier.
- **Price change:** `tradeRatio * playerScaling * maxPriceChangePercent`, with sell-pressure multiplier and trend dampening.
- **Player scaling:** `tanh(onlineCount * atanh(0.99) / fullEffectPlayers)` — reaches 99% effect at `fullEffectPlayers`.
- **Trade window:** recency-weighted transactions; adaptive mode scales 1–7 days targeting 100 tx/day.
- **Caching:** `ConcurrentHashMap` caches for prices, spreads, trend direction/streak, per-tick volumes — all cleared on `reload()`.

The Rust simulation (`scripts/market-simulation/`) uses the **same constants and formulas** as the Java engine. When changing MarketEngine math, update both.

## Key Conventions

### Models are Java records with manual builders

```java
public record ShopItem(int id, Material material, BigDecimal price, ...) {
    public static Builder builder() { ... }
    public Builder toBuilder() { ... }
}
```

No Lombok. No mutable POJOs for domain objects.

### Financial values use `BigDecimal`; timestamps use `Instant`

Never use `double`/`float` for prices or monetary amounts. Never use `Date` or `LocalDateTime` — use `java.time.Instant` (UTC).

### Async database pattern

```java
// Correct pattern for all DB work
dbManager.supplyAsync(() -> repo.findById(id))
         .thenAccept(result -> dbManager.runOnMain(() -> handleResult(result)));
```

### Dependency injection via Guice

All managers are `@Singleton` and injected via `@Inject`. New managers must be bound in `AutoTuneModule`. Do not use `new` to instantiate managers.

### Shadow JAR dependency relocation

Every new `implementation` dependency that could conflict with other plugins **must** be added to the `relocate(...)` block in `build.gradle.kts`. Relocated packages live under `com.noahblclarkson.autotune.lib.*`. JDBC drivers and Javalin/Jetty are excluded from minimization.

### Config is a nested record hierarchy

`AutoTuneConfig` contains `StorageConfig`, `WebConfig`, `EconomyConfig`, `LoanConfig`, `GuiConfig`, `DebugConfig` as nested records mapped directly from YAML. Add new config fields as record components, not as mutable fields.

### Web frontend is bundled into the JAR

The `processResources` task depends on `buildWeb`, which runs `npm run export` and copies `web/out/` → `src/main/resources/web/`. Changes to the frontend require a full `./gradlew build` to be reflected in the plugin JAR.

### Annotations

Use `@NotNull` / `@Nullable` from `org.jetbrains:annotations` on all public API parameters and return types. These are `compileOnly` and do not affect runtime.
