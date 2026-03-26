# Contributing to Auto-Tune

> Auto-Tune rewrite-2. Thanks for helping build this.

## Quick Start

```bash
# Clone the repo
git clone https://github.com/noahbclarkson/Auto-Tune.git
cd Auto-Tune
git checkout rewrite-2

# Build the plugin
./gradlew build

# Run a local Paper server for testing
./gradlew runServer
```

## Repository Layout

```
autotune/
├── src/main/java/.../autotune/   # Java plugin source
│   ├── manager/                   # MarketEngine, ShopManager, etc.
│   ├── economy/                  # EconomyManager, LoanManager
│   ├── command/                  # Cloud command definitions
│   ├── config/                   # Config loading and records
│   ├── database/                 # JDBI repositories, migrations
│   ├── model/                    # Data models (Java records)
│   ├── ui/                       # InventoryFramework GUIs
│   ├── listener/                 # Bukkit event listeners
│   └── web/                      # Javalin REST + WebSocket server
├── web/                          # Bundled Next.js dashboard (plugin-local)
├── web-optimizer/               # Standalone public Next.js site
├── api-server/                  # Rust/Actix API server (price solver)
├── scripts/market-simulation/   # Rust market simulation (egui GUI)
├── docs/                        # Architecture, API, config, changelog docs
└── src/main/resources/
    ├── db/                       # SQL migrations (V1__, V2__, etc.)
    └── web/                      # web/ static export output (auto-generated)
```

## Tech Stack

| Component | Language | Framework | Notes |
|-----------|----------|-----------|-------|
| Minecraft plugin | Java 21 | Paper 1.21.4, Guice, JDBI | Shadow JAR bundles everything |
| Bundled dashboard | TypeScript | Next.js 14 | Static export, served by Javalin |
| Public optimizer site | TypeScript | Next.js 14 | Static export, hosted separately |
| API server | Rust | Actix-web 4 | Cross-server price solver |
| Market simulation | Rust | egui | Parameter exploration and stress testing |

## Market Engine — Staying in Sync

Auto-Tune has **three market engine implementations** that must stay in sync:

1. **Java** — `manager/MarketEngine.java` — production, in the plugin
2. **Rust** — `scripts/market-simulation/src/engine.rs` — simulation lab
3. **TypeScript** — `web-optimizer/src/lib/market-engine.ts` — public simulator

When you change pricing logic in any one:
- Verify the other two produce the same outputs for the same inputs
- Update all three default constants simultaneously
- Run the Rust simulation regression test: `cargo run --release -- --regression`

Default values that must stay in sync across all three:

| Constant | Java | Rust | TypeScript | Notes |
|----------|------|------|------------|-------|
| baseSpread | 0.20 | 0.20 | 0.20 | |
| volumeImpact | 0.8 | 0.8 | 0.8 | |
| playerImpact | 0.6 | 0.6 | 0.6 | |
| liquidityCoeff | 0.01 | 0.01 | 0.01 | |
| fullEffectPlayers | 10 | 10 | 10 | |

## Code Standards

### Java
- Use **Java records** for data models (immutable, minimal)
- **Guice DI** for dependency injection — do not `new` managers directly
- All database operations go through **JDBI repositories** — no raw JDBC
- Async DB ops via `DatabaseManager.supplyAsync()` / `runAsync()` — never block the main thread
- Vault economy calls (`withdrawPlayer`/`depositPlayer`) must run on Bukkit main thread via `Bukkit.getScheduler().runTask()`
- Cloud 2.0.0-beta.14: `@Argument` has **no `defaultValue`**. Use `Optional<T>` for optional args.

### Rust
- `cargo clippy --all-targets --all-features -- -D warnings` must pass
- `cargo fmt` must be run before committing
- All public APIs must have doc comments
- Error handling: use `?` with `anyhow::Result<T>` for app-level errors, `thiserror` for domain errors

### TypeScript / Next.js
- Server Components by default; `'use client'` only when needed
- `market-engine.ts` is the canonical TypeScript spec — keep it well-documented
- Static export (`output: 'export'` in next.config.js) — no server-side features in `web-optimizer`

## Branch Policy

- **`rewrite-2`** — active development. All work happens here.
- **`main`** — stable release. Do not push directly.
- Feature branches from `rewrite-2`, PR back into `rewrite-2`.

## Commit Convention

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add per-item max price limits
fix: credit seller vault balance in auction fill
docs: add API reference for Rust server
style: apply rustfmt to price-solver crate
refactor: consolidate database schema to single V1 migration
test: add correlation test for sector co-movement
```

## Pull Request Checklist

- [ ] `./gradlew build` succeeds (Java plugin)
- [ ] `cargo clippy --all-targets --all-features -- -D warnings` passes (Rust)
- [ ] Both web apps build: `cd web && npm run build && cd ../web-optimizer && npm run build`
- [ ] If you changed market engine math: run regression test (`cargo run --release -- --regression`)
- [ ] If you changed database schema: test with existing + fresh database
- [ ] Update `docs/CHANGELOG.md` with your change

## Testing Strategy

**Java unit tests** (`src/test/java/`): Use JUnit 5 + Mockito 4. `MarketEngine` has 36 tests covering spread and price pipelines. Note: `JavaPlugin` is `final` — Mockito can't mock it on JDK 17+. Use the `PluginAdapter` interface pattern (see `MarketEngineTest.java` for examples).

**Rust tests**: `cargo test` in `api-server/` and `price-solver/`. The market simulation has `--regression` mode which verifies deterministic output.

**Manual testing**: Run `./gradlew runServer` with a Paper 1.21.4 server. The plugin starts with an in-memory SQLite database. Run some trades, check the web dashboard at `localhost:8989`.

## Getting Help

- Discord: https://discord.gg/bNVVPe5
- See `docs/ARCHITECTURE.md` for system overview
- See `docs/API.md` for API reference
- See `docs/CONFIG_GUIDE.md` for configuration cookbook

## Important Caveats

- **Paper `JavaPlugin` is `final`** — can't Mockito-mock it. If your test setup takes >15 minutes, stop, commit production code, and skip the test. Write a `// TODO: test` comment.
- **`Cloud @Argument` has no `defaultValue`** — use `Optional<T>` instead.
- **InventoryFramework**: `ChestGui.addPane(Pane)` works; `HopperGui` does NOT have `addPane`. Use `ChestGui` for programmatic pane-based GUIs.
- **Kyori Adventure**: `NamedTextColor` ≠ `Component`. Use `TextColor` for color variables. `Component.text(String, TextColor, TextDecoration...)` DOES NOT EXIST — use builder pattern.
