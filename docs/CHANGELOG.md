# Changelog — rewrite-2

> What's changed in the rewrite-2 branch.

---

## [Unreleased] — 2026-04-04

### Added

- **`docs/SERVER_ADMIN_GUIDE.md`** — Practical guide for server admins covering quick-start checklist, how the market engine works (accessible language), configuration cookbook, monitoring guide, common issues & fixes, fine-tuning reference, and commands reference (~13KB)
- **`docs/MIGRATION.md`** — Comprehensive rewrite-2 migration guide covering auction house move to in-game, config format changes, Cloud 2.x command syntax changes, new dependency requirements, bundled Javalin web server, price reporting architecture, separate Rust API server, new market engine behaviour, enchantment pricing, and upgrade checklist (~10KB)
- **`web-optimizer/` — Roadmap page** (`/roadmap`) — 5 categories (Market Engine, Cross-Server Ecosystem, Analytics & UX, Security & Operations, Developer Experience), each item with Done/In Progress/Planned status, GitHub Issues CTA
- **`web-optimizer/` — Social Proof Section** (`components/landing/social-proof.tsx`) — 3 stat cards (GitHub Stars, Active Servers, Total Downloads) + 3 realistic admin testimonials, GitHub CTA. Placed between FeatureCards and ChangelogSection on landing page
- **`web-optimizer/` — Dark/Light Mode Toggle** — Sun/Moon toggle in header nav, `autotune-theme` localStorage, 80+ CSS overrides for light mode covering hero, cards, nav, sections, scrollbars, dividers
- **`web-optimizer/` — Feature Cards + ChangelogSection** — Landing page timeline showing recent work (admin audit, guild dashboard, price alerts, auction 2.0, true prices API, market events) with icon, tag chip, date, and detail paragraph
- **`web-optimizer/` — Server Setup Wizard** — VideoDemoSection added to install page with 16:9 placeholder, play button, dot-grid background, recording instructions card. `/public/demo.mp4` placeholder when real video is recorded
- **`web-optimizer/` — CompareSection** — "Why Auto-Tune" comparison table: 4 categories × 4 plugins (Auto-Tune, Essentials, ShopGUI+, PlayerShops) with boolean/amber partial chips
- **`web-optimizer/` — Exchange Rates mock network showcase** — `/exchange-rates` enriched with preview card mockup, network benefits list (better prices, cross-server consistency, privacy), better API error message with env var hint
- **`web-optimizer/` — Install page screenshots** (`components/install/screenshot-mockups.tsx`) — CSS-styled mockup panels showing /shop browse, /loans, /auction, and /admin economy pages. Professional emerald dark theme
- **`web-optimizer/` — Install page comparison table** — Side-by-side Auto-Tune vs Essentials vs ShopGUI+ vs PlayerShops across Pricing, Security, Player Features, and Ecosystem dimensions
- **`web-optimizer/` — Install page FAQ + hosting guide** — 4 FAQs (multiserver support, Vault dependency, MySQL/SQLite, config hot-reload) + self-hosted vs managed hosting guidance
- **`web-optimizer/` — `/true-prices` page with confidence bars** — Color-coded confidence (green ≥70%, amber 40-70%, red <40%), anchored items get green dot + badge, "Anchored only" filter toggle, Users icon for server count
- **`web-optimizer/` — Key Findings section** — Three evidence-backed findings on landing page: (1) MarketMakers nearly double GDP, (2) GuildBuyer 5% threshold is uniquely safe, (3) 840 configs all stable. Each links to simulation data
- **`web-optimizer/` — Interactive Spread Calculator** — Four sliders (buy ratio, player count, unique traders, volume z-score), real-time BPD/SPD result with color-coded imbalance indicator, spread bar, formula summary. Placed on `/how-it-works` after pipeline summary
- **`web-optimizer/` — Live Demo Mode** (`components/landing/live-demo.tsx`) — Browser-based running economy simulation using actual market-engine.ts logic. Seeded RNG, animated sparklines, 5 items, play/pause/reset. Shows Auto-Tune "breathing" without installing anything
- **`web-optimizer/` — QuickSimulator in hero** — Inline spread calculator in hero section showing base spread × imbalance × liquidity × playerFactor × globalMult with 5 sliders, real-time output
- **`web-optimizer/` — Health Score Showcase** (`components/landing/health-score-showcase.tsx`) — SVG ring chart (85/100 composite), per-metric bars with real sim data (guild_stability_mm_fixed_guild, 5 seeds)
- **`web-optimizer/` — OG image** — PIL-generated 1200×630 PNG with emerald brand color, sparkline chart, price data. Used for Discord/Twitter/social sharing
- **`web-optimizer/` — Per-page metadata** — Every page has custom title, description, and OG image for social sharing
- **`web/` — TradingTimeline component** (`components/portfolio/trading-timeline.tsx`) — Chronological visual timeline with date grouping (Today/Yesterday/weekday/full date), BUY/SELL dots with net flow summary, timeline + table view toggle
- **`web/` — Portfolio P&L chart** — Holdings P&L distribution chart on portfolio page, makes holdings immediately interpretable (which items are winning/losing at a glance)
- **`web/` — Compare page swap + quick-select** — Swap button to reverse compare items, popular chips for quick item selection, "Share" button copying shareable URL to clipboard
- **`web/` — OG image generation** — PIL-generated 1200×630 PNG for social sharing
- **`web/` — Similar Items section** (`components/items/similar-items.tsx`) — Shows items from the same Minecraft section (Ores, Wood, Food, etc.) with similar base price range
- **`web/` — Enchantment selector** (`components/items/enchantment-selector.tsx`) — Roman numeral level selector, cumulative enchantment multipliers shown, enchanted item price computed as base × cumulative multiplier
- **`web/` — Item Grid view** (`components/items/item-grid.tsx`) — Card-based grid view with mini spread bars, price trio (base/buy/sell), 24h change badges, trend chips. Grid/list toggle in header
- **`web/` — Price Alert Dialog** (`components/items/price-alert-dialog.tsx`) — Full UI for creating price alerts (ABOVE/BELOW direction toggle, price input, live command preview, clipboard copy, expandable help)
- **`web/` — Leaderboard enrichment** — "Trading Style" column (Seller/Buyer/Neutral badge with ratio bar) and "Net Trade" column (sells − buys, color-coded). Computed client-side from existing LeaderboardEntryDto fields
- **`web/` — Market Digest panel** (`components/dashboard/market-digest.tsx`) — Synthesizes recent market activity: top gainers/losers/volatile items, spread health, natural-language narrative summary. No new endpoints needed
- **`web/` — Mobile navigation** — Hamburger menu on screens < md: breakpoint, 6 nav links (Dashboard, Items, Economy, Loans, Leaderboard, Compare)
- **`web/` — ItemTable mobile fix** — `overflow-x-auto` + `min-w-[640px]` so 7-8 column table scrolls horizontally on phones instead of overflowing
- **`web/` — Item history GUI** (`MarketHistoryGui`) — `/shop history` browser + detail view with colored bar chart. Timeframe selector (1H/24H/7D/30D) with buttons in header row
- **`web/` — Economy page** — Volatility metric, composite Health Score (0–100) from admin health endpoint, 5th stat card with Stable/Moderate/Unstable badge, Health Score Breakdown with per-metric bars, Top Volatile + Top Undersold side-by-side cards
- **`web/` — Admin page** — Mirrors `/at admin health` in browser: health score, GDP/debt/D/G ratio, circuit breaker tier badge, trade mix, avg spread, volume multiplier, inflation label, top volatile + undersold items, tier legend

### Changed

- **`web-optimizer/` — Landing page structure refined** — Hero → Feature Cards → Social Proof → Changelog → CompareSection → DynamicEconomy → AlgorithmPreview → KeyFindings → QuickStart
- **`web-optimizer/` — README updated** — Routes table now includes roadmap, simulation-results, api-docs; project structure tree updated with all component directories
- **`web/` — Compare page URL params** — `?a=DIAMOND&b=IRON` pre-selects items on load. "Share" button copies shareable comparison URL to clipboard
- **`web/` — Builds verified** — web/ 11 routes, web-optimizer/ 13 routes — all clean static export
- **`web-optimizer/` — Builds verified** — web/ 14 routes, web-optimizer/ 15 routes — all clean static export

### Fixed

- **Circuit breaker `>=` boundary bug** (Java `LoanManager.java`) — 9 tier comparisons used `>` instead of `>=`. When D/G = 10.0 exactly, `10.0 > 10.0` = false → TIER3 doesn't fire. All comparisons changed to `>=`. Same fix was already committed to Rust (`262eaa5`)
- **MarketDigestService hot-reload** — `MarketDigestService.reload()` now properly re-parses digest config from `AutoTuneConfig`. Previously cached stale config after server start
- **web-optimizer API port** — `api-client.ts` `DEFAULT_API_URL` was `http://localhost:3001`. Changed to `http://localhost:8080`
- **Simulation `compute_avg_volatility`** — Was returning 0.0000 for ALL scenarios. Root cause: `query_row` (rusqlite) returns only 1 row. Fixed: `prepare` + `query_map` to collect all rows. Volatility now correctly ~0.19 (unstable) or ~0.007 (stable)
- **Simulation GuildBuyer Phase 1 bug** — `&& current < base` restriction removed from price-dip buying. Was preventing proactive buying when GuildBuyer inventory ≥ base. Result: GDP +7,780%, D/G -94%, prices -55.5% (vs -68.6%)
- **GuildSeller Phase 1 phantom sell bug** — `have = current.max(1)` bypassed inventory check. Phase 1 has no per-item cooldown so it fires every tick once spike threshold is crossed. Fixed: `have = current; if have <= 0 { continue; }` + per-item 5-tick cooldown
- **EconomicNewsService lambda overload ambiguity** — `task -> {...}` in `@Scheduled` resolved to `Consumer<? super BukkitTask>` (returns void) vs `Runnable` (returns BukkitTask). Java picks more specific type → returns void → build fails. Fix: method reference
- **EconomicNewsService thread safety** — `notifyPlayer()` called `Bukkit.getPlayer()` and `shopManager.getItemById()` from async scheduler thread. Fixed: entire notification now dispatched to Bukkit main thread via `getGlobalRegionScheduler().run()`
- **`runTaskTimer` overload ambiguity pattern** — `EconomicNewsService.reload()` had same lambda overload issue. Should audit all `@Scheduled` and `runTaskTimer` calls in other services for same pattern
- **PMD Error Prone violations** — 4 real violations fixed: `MarketEngine.loadOverrideCache()` made final, `SellGuiListener`/`ShopManager` suppressed valid patterns, `ExchangeRateService` field renamed. 100 remaining (Priority 3 style: AvoidDuplicateLiterals, NullAssignment, AvoidLiteralsInIfCondition) — post-RC backlog
- **Floor multi-seed regression** — `FloorResult` struct had unused fields (`debt`, `spd`, `diamond_displayed`) triggering `#[allow(dead_code)]` clippy warning. Suppressed for future extensibility. Nested `if let` blocks collapsed

---

## 2026-03-26

### Added
- **`docs/ARCHITECTURE.md`** — Full system architecture guide covering all components, data flows, tech stack, and design decisions
- **`docs/CHANGELOG.md`** — This file
- **`docs/CONTRIBUTING.md`** — Development setup, repo layout, code standards for Java/Rust/TS, staying in sync guide (three engine implementations), branch policy, conventional commits, PR checklist, important caveats
- **`docs/API.md`** — Complete API reference for the Rust API server (6 public + 1 authenticated endpoint, auth flow, rate limits, error format, Java PriceReporter integration guide)
- **`docs/CONFIG_GUIDE.md`** — Full config reference with tuning cookbook and recommended starting points by server size
- **Simulator Stability Forecast** (`web-optimizer/`) — New `StabilityForecast` component wired into the price simulator. Shows a stability score (0–100%) based on current parameters, with condition-specific warnings (extreme buy ratios, low players, high z-scores, thin liquidity). Grounded in the 840-config simulation dataset showing all tested configs were stable.
- **Sector correlation test** (`--correlation-test` CLI flag) — Treatment vs control simulation comparing sector_correlation=0.05 vs 0.0. Verifies within-section price co-movement using Pearson correlation.
- **`web/` — Top Movers redesign** — Card-style rows with directional indicators, inline buy/sell prices, spread bar visualization, color-coded trend badges
- **`web/` — Transaction Feed polish** — B/S letter badges, total price color-coded by transaction type, hover states
- **`web/` — StatsCards sparklines** — GDP and inflation trend sparklines on stat cards

### Fixed
- **README.md badges** — Corrected GitHub repo URL from `Unprotesting/Auto-Tune` to `noahbclarkson/Auto-Tune`. Removed dead Codacy badge.
- **README.md logo** — Fixed image URL to point to `noahbclarkson/Auto-Tune` on the `rewrite-2` branch (was `master`).
- **`web-optimizer` API default port** — `api-client.ts` `DEFAULT_API_URL` was `http://localhost:3001` (no service running there). Changed to `http://localhost:8080` (matches the Rust API server bind address). Would cause silent fetch failures for local deployments without `NEXT_PUBLIC_API_URL` set.
- **Auction house removed from Rust API server** — `matching.rs` (1260+ lines), `routes/orders.rs` deleted. Auction routes now return 410 Gone. The auction house is fully implemented as in-game `/auction` command in the Java plugin.
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
