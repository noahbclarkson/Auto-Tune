# Changelog — rewrite-2

> What's changed in the rewrite-2 branch.

---

## [Unreleased] — 2026-03-26

### Added
- **`docs/ARCHITECTURE.md`** — Full system architecture guide covering all components, data flows, tech stack, and design decisions
- **`docs/CHANGELOG.md`** — This file
- **Simulator Stability Forecast** (`web-optimizer/`) — New `StabilityForecast` component wired into the price simulator. Shows a stability score (0–100%) based on current parameters, with condition-specific warnings (extreme buy ratios, low players, high z-scores, thin liquidity). Grounded in the 840-config simulation dataset showing all tested configs were stable.
- **Sector correlation test** (`--correlation-test` CLI flag) — Treatment vs control simulation comparing sector_correlation=0.05 vs 0.0. Verifies within-section price co-movement using Pearson correlation.

### Fixed
- **README.md badges** — Corrected GitHub repo URL from `Unprotesting/Auto-Tune` to `noahbclarkson/Auto-Tune`. Removed dead Codacy badge.
- **Auction house removed from Rust API server** — `matching.rs` (1260+ lines), `routes/orders.rs` deleted. Auction routes now return 410 Gone. Auction house is fully implemented as in-game `/auction` command in the Java plugin.

---

## 2026-03-26

### Added
- **`docs/API.md`** — Complete API reference for the Rust API server (6 public + 1 authenticated endpoint, auth flow, rate limits, error format, Java PriceReporter integration guide)
- **`docs/CONFIG_GUIDE.md`** — Full config reference with tuning cookbook and recommended starting points by server size
- **`web/` — Top Movers redesign** — Card-style rows with directional indicators, inline buy/sell prices, spread bar visualization, color-coded trend badges
- **`web/` — Transaction Feed polish** — B/S letter badges, total price color-coded by transaction type, hover states
- **`web/` — StatsCards sparklines** — GDP and inflation trend sparklines on stat cards

### Fixed
- **Auction `fillSellOrder` escrow bug** — Buyer's money was escrowed but seller's Vault balance never credited. `processFill()` and `recordFillAsync()` now credit seller's Vault and deliver items to buyer on Bukkit main thread.
- **`V2__add_price_floor_ceiling` + `V3__remove_price_ceiling_floor` migrations** — Consolidated into single `V1__Initial_Schema.sql` (rewrite-2 is not live; no need for incremental add-then-remove migrations)

### Changed
- **Both `web/` and `web-optimizer/` builds verified** — 9 routes (web/), 7 routes (web-optimizer/) — all clean static export

---

## 2026-03-25

### Added
- **`scripts/market-simulation/src/analyzer.rs`** — SQLite result analyzer. `--analyze <db>` for single-run deep reports; `--analyze-dir <dir>` for cross-run comparison. Reports economy health, loan activity, price stability, spread health, volume, archetype activity, trends, stability verdict.
- **`PriceShock` stress event** — Direct price multiplier injection for testing sector correlation
- **`--correlation-test` CLI** — Comparative sector correlation testing (Pearson correlation within/between sections)
- **`/alert` command** — Price alert system with ABOVE/BELOW alert types, 1-min check interval, in-game notifications
- **`DatabaseCleanupManager`** — Automatic pruning of `at_transactions` (14d), `at_market_history` (7d), `at_economy_snapshots` (30d) with SQLite VACUUM

### Fixed
- **`processSellImmediate` data loss** — Was fire-and-forget on DB insert. Now awaits write before returning. Player items removed first, then money deposited, then DB — safe rollback on failure.
- **Auction sell order item removal atomicity** — Items now removed before async `placeSellOrderAsync`, restored on async failure
- **Auction GUI fill persistence** — `processFillAsync` now persists updated remaining quantities to DB
- **Loan circuit breaker in simulation** — Rust sim was missing the circuit breaker. Stressed scenario debt: 11M → 21k after fix

### Changed
- **Regression test infrastructure** — All player archetype factory RNG now routed through `SeededRng` (seeded thread-local). All scenarios deterministic: 0.000% delta between runs.
- **`web/` Economy page overhaul** — Color-coded inflation trends, Debt/GDP ratio bar with circuit breaker annotation, health badge, per-loan average display
- **`web/` Compare page overhaul** — Side-by-side item stats, ratio chart + spread chart with toggle, BPD vs SPD stacked bar view

---

## 2026-03-24

### Added
- **`price-solver/src/solver.rs` — Residual-based confidence scoring** — `compute_prices_with_quality()` returns `SolveResult` with normalized RMS residual from LS fit. quality = `exp(-5 × residual_rms)`. `confidence()` combines quality + server/item coverage bonuses.
- **`api-server/src/rate_limit.rs`** — Token-bucket per-IP rate limiter (submit tier: 6 req/min, fast tier: 10 burst). X-Forwarded-For support. 1 MiB body size limit.
- **Enchantment pricing** — `EnchantmentPricing.java` with cumulative multipliers per enchantment level (Sharpness I=1.25×, Efficiency I=1.30×, etc.). Falls back to base material if no exact shop entry.

### Fixed
- **`./gradlew build` on VPS** — Removed `errorprone` plugin and `foojay-resolver-convention` from `settings.gradle.kts`. System JDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64` is used directly.
- **`V1__Initial_Schema.sql` stale V2 reference** — `DatabaseManager` referenced `V2__add_price_floor_ceiling` which only existed on `rewrite-2`. Schema version check now uses `V1__Initial_Schema` which is the canonical baseline.
