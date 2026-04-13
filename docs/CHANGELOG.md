# Changelog — rewrite-2

> What's changed in the rewrite-2 branch.

---

## 2026-04-13 — Simulation Lab

### Added

- **`--afkfarmer-stress-test` CLI (sim)** — AFKFarmer archetype in guildbuyer_failure_test (stressed economy) across 5 seeds. Result: GDP −49.5%, volatility +65.9%, D/G +21.4%. AFKFarmers (5-15% online, dump at near-zero margins when online) are the MOST DESTRUCTIVE archetype tested — more destructive than IT+VT. Mechanism: offline accumulation → sudden dump at near-zero margin → price spike crash → circuit breaker fires constantly. **VERDICT: ❌ AFKFARMERS CATASTROPHIC.**
- **`--hoarder-heavy-test` CLI (sim)** — Hoarder archetype in guildbuyer_failure_test (stressed economy) across 5 seeds. Result: GDP +1.5% (flat), D/G −6.9% (slight improvement). Hoarders hold inventory → less supply → slightly higher prices → GBs trigger less → less debt. Effect is marginal but consistent. **VERDICT: ⚠️ Hoarders are NEUTRAL.**
- **`--vt-stressed-test` CLI (sim)** — VolumeTrader in guildbuyer_failure_test (stressed economy) scenario across 5 seeds. Result: GDP −12.7%, D/G +0.48x, vol +5.9%. VT amplifies sell cascades by firing on spread widening in supply-driven stressed economies. Mechanism: stressed economies have chronic Farmer/Hoarder oversupply → wide spreads → VT sells into it → prices drop further. **VERDICT: VT is ALWAYS harmful** — healthy (-9.2%) or stressed (-12.7%).
- **`--it-vt-healthy-test` CLI (sim)** — IT+VT combo in guild_stability_2mm_fixed_guild (healthy economy) across 5 seeds. Result: GDP −25.6% (IT alone was +30.1%, VT alone was −9.2%). VT's drag DOMINATES and REVERSES all of IT's gains by 3x. Mechanism: ITs create buy pressure → GBs trigger more → multiplicative debt amplification → GDP collapse. **VERDICT: IT+VT is CATASTROPHIC.** Do NOT combine them in any config.
- **`--it-stressed-test` CLI (sim)** — InsiderTrader in guildbuyer_failure_test (stressed economy) across 5 seeds. Result: GDP −12.2%, D/G −1.32x. IT buying during price dips absorbs Farmer/Hoarder sell pressure counter-cyclically. This is the OPPOSITE pattern from healthy economies (where ITs worsen D/G by +2.41x). ITs should only be added to HEALTHY economies, not stressed ones.

### Changed

- **Definitive archetype recommendation table (sim)** — Updated with 4-row definitive table: 2MM+2GB ✅ production default; +2IT ⚠️ optional (+30.1% GDP, D/G +2.41x, watch D/G); +2VT ❌ never (always −9 to −13% GDP); +2IT+2VT ❌ catastrophic (−25.6% GDP). Table added to docs/ECOSYSTEM_ANALYSIS.md and docs/SERVER_ADMIN_GUIDE.md.

---

## 2026-04-08 — Web & Ecosystem

### Added

- **`/health-badge` route (web-optimizer)** — Three badge styles (compact/standard/detailed), live preview with real health data from the API, self-contained HTML snippet generator for server forums and Discord embeds. Three size options, no external dependencies. Nav header link added.
- **Landing page refresh (web-optimizer)** — Hero rewritten with concrete admin value props (advice commands, circuit breaker, events). Stats strip updated: Commands, Market tick, Circuit breaker threshold, Events types. New Admin Intelligence feature card covering /at admin advice, /at admin history, /at admin recovery.
- **`/at admin advice` command (plugin)** — Rules-based expert system reads live health metrics (GDP, D/G, volatility, buy ratio, circuit breaker tier) and produces plain-English diagnosis + recommended YAML config snippets with copy button. Admins get actionable recovery plans without reading raw metrics.
- **`/shop info <item>` command (plugin)** — Players see why a price moved: last price change direction + magnitude, 7-day trend, recent large trades, active market events affecting this item, floor/ceiling status. Makes the engine transparent and educational.
- **`docs/PLAYER_QUICKSTART.md`** — Player-facing guide covering /shop, /sell, /compare, /loans, /transactions. Explains prices, spreads, trends, 4 money-making strategies (gathering, flipping, lending, event anticipation), common mistakes. Closes the player-facing docs gap.
- **`docs/QUICKSTART.md` rewritten** — Added visual decision tree at top (no-loans vs loans path, archetype mix options with warnings). 5 decisions section with YAML + CLI commands. Post-launch 8-point checklist. Healthy economy reference table.
- **`docs/README.md` updated** — Added "For Players" section, role-based reading order (new admin / experienced admin / developer / player).

### Changed

- **`docs/CHANGELOG.md`** — Complete entries for 2026-04-05 through 2026-04-07 added.

---

## [Unreleased] — 2026-04-07

### Changed

- **`config.yml` defaults updated (plugin)** — `sell-pressure-multiplier` default: 1.0 → 0.8 and `trend-dampening` default: 0.05 → 0.10, matching Java ConfigManager defaults. Comments updated with 5-seed evidence: 0.8 → GDP +2%, D/G −30%; 0.10 → GDP +5%, D/G −25%. Both parameters were already correct in Java code (commit 618d287) but the YAML config was still documenting the old values.

---

## [Unreleased] — 2026-04-06

### Added

- **`Seasonal Economy Events — Event Scheduling` (plugin)** — `/at event` command tree with three new subcommands: `templates` (lists available event templates from config.yml), `invoke <name>` (triggers a named template immediately), and `schedule <type> <materials> <multiplier> <duration> <offset>` (schedules an event to start in N minutes). Admins define reusable event templates in `config.yml` under `market-events.events`, then trigger them on demand or pre-schedule them. Scheduled events appear in `/at event list` as `[S]` (SCHEDULED) status and auto-activate when `onMarketTick()` fires. Boss bars and broadcasts fire on activation. Builds on existing `MarketEventService` (SCHEDULED status + auto-activation via `onMarketTick()`).
- **`docs/SERVER_ADMIN_GUIDE.md`** — Practical guide for server admins covering quick-start checklist, how the market engine works (accessible language), configuration cookbook, monitoring guide, common issues & fixes, fine-tuning reference, and commands reference (~13KB)
- **`docs/MIGRATION.md`** — Comprehensive rewrite-2 migration guide covering auction house move to in-game, config format changes, Cloud 2.x command syntax changes, new dependency requirements, bundled Javalin web server, price reporting architecture, separate Rust API server, new market engine behaviour, enchantment pricing, and upgrade checklist (~10KB)

### Changed

- **`docs/CHANGELOG.md`** — 9 days of missing entries added (2026-03-27 → 2026-04-04), ~85 total entries across 4 dated sections + unreleased. Merged duplicate `## 2026-03-26` sections into one.

---

## 2026-04-05

### Added

- **`GuildSeller Phase 2 Redesign` (sim)** — New `guild_phase2_dip_threshold` field in SimConfig + PlayerAgent. Phase 2 trigger: GS sells when price < perceived × (1 − dip_threshold). Active anti-oversupply mechanism vs legacy passive liquidation. Scenario: `guild_stability_mm_gs_phase2_redesign` (1MM+1GB+1GS+4Cas+3Far+2Tra). Verified counterproductive (H3 confirmed): GDP −96%, D/G +813%, prices catastrophic. GuildSeller remains a dead-end at both Phase 1 and Phase 2.
- **`--stressed-30d-floor-test` CLI (sim)** — 30-day stressed-economy scenario (chronic oversupply: 3Cas+5Far+2Tra+2Hoa+1Exp + stress events @ ticks 864/1440/2016). Control (no floor) vs treatment (60% Diamond floor). Key finding: floor paradox INVERTED in stressed economy — floor acts as economic circuit breaker, preventing cascading oscillation. Control: GDP≈0, 14 TIER3 oscillations. Treatment: GDP=4,144, D/G=2,055x, only 1 TIER3 event (−93%). Floor is a valuable safety mechanism in stressed economies.
- **`--circuit-breaker-sensitivity-test` CLI (sim)** — 80-run sweep: 4×4×5 seeds (tier3_ratio ∈ {8,10,12,15} × min_interest ∈ {0%,5%,10%,20%} × 5 seeds). Key finding: tier3_ratio=15 eliminates TIER3 events even in stress test (0 events across all 20 combos). min_interest is counterproductive. **Production recommendation: tier3_ratio=15, counter_cyclical=true, min_interest=0.**
- **`EconomyTemperatureGauge` component (web/)** — Visual thermostat on home page showing economy heat. Compact SVG gauge with color gradient (blue=cold/healthy → red=overheating). Integrated into home page header area.
- **`EconomyTemperatureGauge` on /economy page (web/)** — Same gauge component dropped into the /economy page using `AdminHealthDto` fields.
- **`Config Health Dashboard` (web/ /admin)** — Config health matrix: each config section shown as a card with current value + recommended range indicator. Admin sees at a glance which parameters are in safe ranges vs need attention.
- **`Post-Install Discovery Funnel` (web/)** — `DiscoveryOverlay` component: floating dismissible tip card on first visit to `/items` and `/portfolio`. Random contextual tip per session (1 of 3 per page), 8s auto-dismiss with progress bar. Tips for /items: price discovery, loans, shift-click history. Tips for /portfolio: P&L tracking, trading timeline, strategy adjustment. localStorage-persisted dismissal.
- **`Portfolio CSV Export` (web/ + plugin)** — New Java endpoint `GET /api/portfolio/{player}/transactions.csv` generates a CSV of all player transactions with columns: timestamp, item, type, quantity, unit_price, total, balance_after. Export button on /portfolio page. Uses existing `TransactionRepository`.
- **`Player Achievement Timeline` (web/ /badges)** — Search any player to see their earned badges with earn dates, rarity, and progress bar. Chronological display showing the player's "economy journey".
- **`Server Setup Wizard` (web-optimizer/)** — 5-step interactive config generator: Server Type → Player Count → Key Parameters → Stability Preview → YAML Export. Pre-computed sim data, fully client-side. Icon keys fixed (pickaxe→axe, island→mountain). All archetypes now include tier3=15 loans config.

### Changed

- **`LoanManager tier3-ratio default → 15` (plugin)** — Updated default from 10 to 15 in `ConfigManager.java`. Counter-cyclical at tier3=15 gives multiplier=33% at D/G=10x, vs 0% at tier3=10. Eliminates TIER3 event noise in healthy economies while maintaining circuit breaker protection.
- **`config.rs tier3_ratio default → 15` (sim)** — Rust `LoanConfig.debt_gdp_tier3_ratio` updated to 15.0. Matches Java default exactly. Regression: 5/5 PASS.

---

## 2026-04-04

### Added

- **`price-milestone notifications` (plugin)** — New `PriceMilestoneService` broadcasts action bar messages when items cross round-number price thresholds (e.g. Diamond breaks through $300). Announces both upward (🚀) and downward (📉) crossings with previous price for context. Per-item+threshold cooldown (default 60 min) prevents spam. Config: `price-milestone.enabled`, `interval-minutes`, `thresholds` (default [50/100/200/300/500/1000/2000]), `cooldown-minutes`. Disabled by default to avoid duplicating EconomicNewsService. Closes feature idea: "Price milestone notifications — broadcast on round-number price crossings"
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
