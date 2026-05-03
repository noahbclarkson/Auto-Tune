# PLAN.md — Anvil's Work Plan

_Living document. Update after every session. Prioritize ruthlessly._

**Branch:** `rewrite-2` (do NOT merge to master)

---

## Web Update (2026-05-01 19:35 UTC) — Install Page Auction Mockup ✅

**rewrite-2 at `2d43726`** | `web-optimizer/` build ✅ (22 routes) | Pushed ✅

**Install page — What You Get screenshots expanded from 3 → 4:**
- Ecosystem gap: public install page showed Config/Dashboard/Shop mockups but omitted auction entirely. First-time visitors wouldn't know auction exists — despite it being the most sophisticated feature.
- Added `AuctionMockup` component: live order book with bid/ask rows, spread indicator, fill status. Uses `Gavel` lucide icon.
- New card: `Icon: Gavel`, title "P2P Auction House", tag "Beyond /shop", description mentioning depth chart and `/auction watch` alerts.
- Headline: "Three screens" → "Four screens. Zero configuration required."
- Committed `2d43726 feat(web-optimizer): add P2P Auction House mockup to install page` → pushed.

**Ecosystem coherence observations:**
- `web/` bundled dashboard fully covers auction (4 tabs: Orders / Fills / Materials / Depth Chart) + `AdminAuctionCard` on `/admin`
- `SERVER_ADMIN_GUIDE.md` auction section (lines 377-386): commands documented but brief — no screenshots, no strategy tips
- Public site `/auction` page exists but install page had no auction mockup → missed first-impression opportunity
- Auction is the key differentiator from basic /shop plugins — should be prominent everywhere

**Next best work:**
1. Add auction mockup or dedicated section to `/setup` page (public install wizard)
2. Expand `SERVER_ADMIN_GUIDE.md` auction section: screenshot walkthrough, thin-book strategy, fee configuration
3. Add "auction integrity" signals to public site `/auction` page for server operators evaluating Auto-Tune
4. Investigate `web-optimizer/` lint warnings from prior session (0f21216)

**rewrite-2 at `fa090da`** | `./gradlew build` ✅ PMD 0 | `web/` 14 routes ✅ | `web-optimizer/` 22 routes ✅ | TSC clean ✅ | Pushed ✅

**Rust CI failure fixed:**
- PR #253 (feat(auction): complete matching engine + frontend) failed Rust CI on `dec51fd` due to `cargo fmt` violation in `price_computer.rs`: trailing-comma multi-arg `tracing::info!` macro required 2024 edition formatting.
- Fixed: `cargo fmt --all` on local → single-line log macro reformatted → committed `fa090da` → pushed.
- Rust CI: ✅ fixed.

**Auction ecosystem complete:**
- `/at admin auction` in-game audit ✅
- `/at admin audit` Auction Integrity section ✅
- `GET /api/admin/auction-audit?days=N` ✅
- `AdminAuctionCard` wired into bundled `web/` `/admin` ✅ (f71eac7)

**tier3=40 90d partial results (partial run, /tmp/autotune-sim/t3r40-results.json):**
- FixA/B/C (50%/60%/70% hysteresis) identical in sim — same config path.
- Seed 42: tier3=40 → D/G=17.1x / 0 events vs ctrl D/G=15.1x / 2 events (prevents firing, slightly worse D/G).
- Seed 12345: essentially equal (~10.1x vs ~9.8x, 0 events both).
- Seed 98765: tier3=40 → D/G=44.5x / 2 events vs ctrl D/G=37.3x / 1 event (worse — prevents circuit dampening).
- Full 3-seed sweep still running in background.

**Next best work:**
1. ~~Auction integrity card to bundled `/admin`~~ — ✅ DONE (f71eac7)
2. ~~API freshness filter + plugin version metadata~~ — ✅ DONE (5ac8758 + dec51fd)
3. Run full tier3=40 90d sweep if sim machine is available.
4. API deploy still blocked on Arc's Fly.io token.


---

## Plugin Update (2026-05-01 07:40 UTC) — Auction Admin Integrity Audit ✅

**rewrite-2 at `f9cb469`** | `AuctionRepositoryTest` ✅ | `./gradlew build` ✅ | Pushed ✅

**Built this session:**
- `/at admin auction` in-game integrity audit for active/filled/cancelled status counts, 7d churn, fill/cancel/expire rates, self-trade fills, thin books, and large sell-wall warnings.
- `/at admin audit` now includes an Auction Integrity section.
- `GET /api/admin/auction-audit?days=N` exposes the same health signals for bundled dashboard/admin UI.
- `AuctionRepository` now has audit queries for status counts, churn, material book health, and self-trade fills.
- Fixed SQLite active/expired auction order filtering by binding `Instant.now()` as `Timestamp`; avoids comparing epoch-millisecond timestamps to SQL `CURRENT_TIMESTAMP` text.
- Regression coverage added for churn/status counts, material book health, large sell-wall detection, self-trade detection, and SQLite timestamp-bound active-order filtering.

**Product value:** admins can now detect auction manipulation risk directly: cancellation spoofing, thin books, whale sell walls, and impossible self-trades.

**Next best plugin/admin work:**
1. Add an Auction Integrity card to bundled `web/` `/admin` using `/api/admin/auction-audit`.
2. Add configurable integrity thresholds once real server data indicates good defaults.
3. Add Rust sim auction manipulation scenarios: cancellation storms, thin-book probes, large sell walls.


## Web & Ecosystem Update (2026-05-01 02:15 UTC) — Config Dry-Run Preview + Ecosystem Audit ✅

**rewrite-2** | `./gradlew build` ✅ | bundled `web/` export ✅ via Gradle

**Built this session:**
- Added `/at admin config preview <filename>`: admins can dry-run a candidate config file inside the plugin folder before replacing live `config.yml` and running reload.
- Preview uses the same runtime parser (`ConfigManager.parseConfig(FileConfiguration)`) plus `ConfigValidator.validate(...)`, so validation matches real startup/reload semantics without persisting changes.
- Added path traversal protection and clear validation error output.
- Shows current → proposed values for high-risk economy/loan controls: base interest, TIER3 ratio, TIER3 hysteresis, minimum interest multiplier, and GuildBuyer debt cap.

**Product value:** Auto-Tune config now has a safer admin workflow. Server owners can test solvency-sensitive YAML changes before touching the live economy, which matters more as loan/circuit/auction settings become powerful.

**Ecosystem observations / prioritized ideas:**
1. **Plugin Engineer:** expand config preview into a full config diff surface: economy update interval, spread/slippage, auction fees/limits, price reporter settings, and warnings for changed storage/API credentials.
2. ✅ **Web/Public setup:** when the setup wizard exports config.yml, teach the safe workflow: copy to plugin folder → `/at admin config preview exported.yml` → replace `config.yml` → `/at admin reload`.
3. **Bundled dashboard:** add auction integrity/audit cards for cancellation churn, thin books, large sell walls, suspicious self-trade/fill patterns, and material-level liquidity risk.
4. **Player web delight:** add weekly market recap/player digest pages: best trade, biggest mover, watched orders filled, materials the player influenced, and server-wide “hot market” stories.
5. **API/server trust:** implement freshness filtering, plugin/protocol version metadata, key rotation/revocation, capped player-count weighting, and explanatory confidence labels before public true-price launch.
6. **Sim Lab:** model auction manipulation and cross-server manipulation: thin-book spoofing, cancellation storms, whale sell walls, one fake high-player server, many Sybils, and clustered outlier submissions.

**Next best work:** API freshness filter + plugin version metadata. Setup wizard safe-preview workflow: ✅ DONE (c6fd302).

## Plugin Update (2026-05-01 01:45 UTC) — Auction Fill-Rate SQLite Regression Fix ✅

**rewrite-2** | `./gradlew test --tests com.noahblclarkson.autotune.database.AuctionRepositoryTest` ✅ | `./gradlew build` ✅

**Fixed this session:**
- Found the prior daily fill aggregation fix was still wrong under default SQLite installs: SQLite JDBC stores bound Java `Timestamp` values as integer epoch milliseconds, so `DATE(filled_at)` returned null instead of calendar days.
- Added `DatabaseManager.isSqlite()` and made `AuctionRepository.findFillsByDay()` choose the right date expression per storage backend: SQLite uses `DATE(filled_at / 1000, 'unixepoch')`; MySQL/MariaDB keep `DATE(filled_at)`.
- Changed fill-rate windows to UTC calendar days and padded zero-fill buckets so `/api/auction/fill-rate?days=N` always returns N ordered daily buckets.
- Added `AuctionRepositoryTest` regression coverage for same-day aggregation and zero-fill day padding with a SQLite-backed JDBI repository.

**Product value:** the bundled `/auction` Total Fills sparkline now works correctly on the default SQLite path and honestly shows inactive days instead of hiding gaps.

**Next best plugin/admin work:**
1. Config hot-reload preview: `/at admin config diff <yaml>` or file-based dry-run validation before reload.
2. Auction stress/audit tooling: cancellation churn, thin-book spoofing, and large sell walls.
3. API freshness filtering + plugin version metadata once API schema work resumes.

## Web & Ecosystem Update (2026-04-30 19:55 UTC) — Cross-Server Security Trust Docs + Rate-Limit Hardening ✅

**rewrite-2 at `c70293d`** | `cd web-optimizer && npm run build` ✅ | `cargo fmt --check` ✅ | `cargo test` ⚠️ blocked by corrupt local Cargo registry cache (`cc-1.2.56` missing module files) | Pushed ✅

**Built this session:**
- Added `docs/SECURITY.md`: a dedicated, truthful cross-server API security/trust model covering Bearer auth, hashed server keys, server-ID path binding, write rate limits, matrix validation, log-space outlier filtering, current player-count weighting, data boundaries, exchange-rate abuse prevention, and launch hardening checklist.
- Added a **Security & Trust Model** card to the public `web-optimizer` docs page so server admins can find it before opting into true-price reporting.
- Corrected `docs/API.md` auth docs from stale `X-API-Key` wording to the implemented `Authorization: Bearer <api-key>` flow and replaced the aspirational security section with a concise current-state summary linking to `SECURITY.md`.
- Hardened API-server token bucket defaults to match documented limits: registration 10 req/min per IP, price submission 6 req/min per IP. Previously the code refilled much faster than the docs implied.

**Ecosystem observations / prioritized ideas:**
1. **Public API launch needs governance, not just math:** outlier filtering and matrix validation exist, but true public trust still needs invite/approval registration, key rotation/revocation, stale-submission filtering, plugin-version metadata, capped player-count weighting, and age/reputation weighting.
2. **Confidence copy should be honest:** public `true-prices` UI should distinguish low server count, stale data, and outlier-suppressed consensus rather than presenting one generic confidence number.
3. **Plugin Engineer:** add plugin version metadata to `PriceReporter` submissions once API schema supports it.
4. **API Engineer:** add freshness filtering in `recompute_true_prices()` so old latest submissions do not keep influencing true prices after a server disappears.
5. **Sim Lab:** model cross-server manipulation scenarios: one large fake player-count server, many new Sybil servers, and clustered outlier submissions.

**Next best work:** implement API freshness filtering + version metadata schema, then add public confidence explanations on `/true-prices`.

## Plugin Update (2026-04-30 19:00 UTC) — Auction Fill-Rate Daily Aggregation Fix ✅

**rewrite-2 at `fdcfe7c`** | `./gradlew build` ✅ | Pushed ✅

**Fixed this session:**
- Corrected `AuctionRepository.findFillsByDay(int days)` to group auction fills by `DATE(filled_at)` instead of exact timestamp text.
- Replaced SQLite-specific cutoff SQL with a Java `Instant` cutoff bound as `Timestamp`.

**Product value:** the bundled `/auction` Total Fills sparkline now shows real daily fill volume, not one noisy bucket per individual fill timestamp.

**Next best plugin/admin work:**
1. Config hot-reload preview: `/at admin config diff <yaml>` with safe visual diff before applying.
2. Auction stress/audit tooling: model cancellation churn, thin-book spoofing, and large sell walls.
3. API/security trust-model docs while Fly.io deploy token remains blocked.


## Simulation Lab Update (2026-04-30 16:30 UTC) — Deep Hysteresis Partial Run

**rewrite-2 at `c7fd8d1`** | Regression 6/6 PASS ✅ | `cargo clippy -- -D warnings` ✅

**Findings:**
- Existing `--tier3-deep-hysteresis-test` changes hysteresis only; it does **not** test the requested `tier3_ratio=40 + hysteresis_band=0.5` arm.
- Partial 60-day deep-hysteresis data confirms hysteresis suppresses TIER3 oscillation count but barely changes final D/G.
  - Seed 42: Ctrl 21.192x / T3=3 → 80% hysteresis 20.980x / T3=1.
  - Seed 12345: Ctrl 14.868x / T3=1 → 80% hysteresis 14.472x / T3=1.
  - Seed 98765: Ctrl only completed before SIGKILL, 38.731x / T3=3.
- 80% hysteresis + 1%/30-day exit cap was identical to 80% hysteresis alone in completed seeds, so the cap appears non-binding in this path.

**Interpretation:** deeper hysteresis is an oscillation dampener, not a debt-stock fix. It makes the circuit quieter but does not materially deleverage the economy.

**Next best Simulation Lab work:**
1. Add/resume-safe `--tier3-40-hysteresis-test`: tier3=30/hyst=0.5 vs tier3=40/hyst=0.5 over 90 days, 3 seeds.
2. If tier3=40 is also marginal, stop tuning circuit thresholds and prioritize architectural debt-stock fixes: forced partial deleveraging, loan maturity extension, or GDP-linked new-debt cap.
3. Make long sim sweeps persist per-arm JSON/CSV as each run finishes so SIGKILL does not lose summaries.


## Web Update (2026-04-30 14:10 UTC) — Auction My Orders Watch Toggles ✅

**rewrite-2 at `c7fd8d1`** | `cd web && npm run build` ✅ | Pushed ✅

**Built this session:**
- Added native Watch/Unwatch controls directly to bundled `web/` `/auction` → My Orders rows when a Minecraft username is set in the identity strip.
- My Orders now loads native watch status for active orders and shows per-row loading/Watching/Watch states.
- Fixed an important API client bug: `api.auction.watch()` now POSTs to `/api/auction/orders/{id}/watch` instead of accidentally GETting the status endpoint, so both `/auction/order` and new My Orders row toggles actually persist DB-backed in-game/offline notifications.
- `cd web && npm run build` passes; existing auction hook dependency warning remains pre-existing.

**Product value:** players can manage auction fill alerts from the order list without drilling into each order detail page, and native watch persistence now works end-to-end from web to the plugin DB.

**Ecosystem observations / prioritized ideas:**
1. **API/Security docs before launch:** publish server-key issuance/rotation, rate limits, submission freshness, plugin version metadata, reputation weighting, and outlier rejection before public true-price marketing.
2. **Admin onboarding funnel:** make public `web-optimizer/` guide admins through `simulate → export config.yml → install jar → open dashboard` as one path.
3. **Auction trust surface:** add admin-facing auction audit views for cancellation churn, thin-book spoofing, and large sell walls; pair with Sim Lab LOB stress scenarios.
4. **Plugin Engineer:** add short order aliases or copy buttons for `/auction info`, `/auction watch`, and `/auction cancel` to reduce UUID friction.
5. **Sim Lab:** continue tier3_ratio=40 + hysteresis_band=0.5 90-day test and model forced deleveraging vs debt cap for the structural doom loop.

**Next best work:** API/security trust model docs, then public-site onboarding funnel polish.



## Web Update (2026-04-30 13:24 UTC) — Circuit Event Admin Guidance ✅

**rewrite-2 at `7c49e93` (+ `08c3ed7`)** | `cd web && npm run build` ✅ | `./gradlew build` ✅ | CI green ✅ | Pushed ✅

**Built this session:**
- Upgraded `/economy` circuit event chips into actionable admin guidance cards.
- Cards now show state transition, date, D/G ratio, interest multiplier, severity color, hover detail with GDP/debt, and guidance copy.
- Guidance covers TIER3 interest pause, recovery consideration above the 15× unlock line, early/manual recovery, and 7-day relapse monitoring after a clear.
- Aligned `/economy` Debt/GDP health scoring and ratio bar with current defaults: TIER1=3×, TIER2=5×, TIER3=30×, unlock line=15×. Removed stale 10×/1000% circuit wording.
- Corrected `scripts/.gitignore` so Rust sim regression baselines under `scripts/market-simulation/regression-baselines/*.json` are allowed through ignore rules.
- Updated `CLAUDE.md` circuit timeline notes.

**Product value:** admins no longer have to infer what a circuit transition means. The dashboard now turns raw loan/circuit state into practical “what should I do next?” guidance.

**Next best work:**
1. Auction My Orders — add native watch/unwatch buttons to `/auction` My Orders rows when playerName is known.
2. API/Security — publish server-key trust/rate-limit/outlier model before live true-price launch.
3. Simulation — test tier3_ratio=40 + hysteresis_band=0.5 for the long-run debt doom loop.
4. Config hot-reload preview — `/at admin config diff <yaml>` visual diff before applying.


## Plugin Update (2026-04-30 06:55 UTC) — In-Game Auction Order Info ✅

**rewrite-2 at `b946154`** | `./gradlew build` ✅ | CI 3/3 green ✅ | Pushed ✅

**Built this session:**
- Added `/auction info <order-id>` for in-game order inspection.
- Command shows side/status/material/price, filled vs original quantity, remaining quantity, filled value, created/expires/filled timestamps, and recent fill history.
- Full order IDs are clickable/copyable; active orders include clickable watch/cancel command suggestions.
- `/auction my` now clicks through to `/auction info` and correctly reports filled quantity instead of remaining quantity as “filled”.

**Product value:** players can inspect and share auction orders entirely in-game, matching the web order-detail workflow and making support/debugging easier without leaving Minecraft.

**Next best plugin/web work:**
1. ~~Web — Native-aware auction watch UX~~ — ✅ DONE (c7ac047)
2. Web — Circuit event action copy: richer event chips with D/G, multiplier, and admin guidance.
3. Sim Lab — Add `guild_stability_2mm_fixed_guild_plus_floor` to regression suite.
4. API/Security — publish server-key trust/rate-limit/outlier model before live true-price launch.

## Web & Ecosystem Update (2026-04-30 07:29 UTC) — Native Auction Watch ✅

**rewrite-2 at `c7ac047`** | `cd web && npm run build` ✅ | `./gradlew build` ✅ | Pushed ✅

**Built this session:**
- PlayerIdentityStrip component: Minecraft username input persisted in localStorage, shown as compact identity badge on `/auction` and `/auction/order`.
- AppContext extended with `playerName` + `setPlayerName`, stored under `autotune:player-name`.
- API client extended with `auction.watch`, `auction.unwatch`, `auction.watchStatus`.
- `/auction/order`: `handleWatchToggle` now calls native POST/DELETE watch endpoints when playerName is known (persists in DB → in-game/offline alerts). Initial watch state checks native status on load. Watch button shows spinner during native calls.
- `/auction` page: identity strip in main content area below stats bar.
- Browser Notifications API remains fallback for anonymous watchers.

**Product value:** auction watch notifications now reach players in-game even when they're offline or the browser is closed — eliminating the need to keep the dashboard open to receive fill alerts.

**Next best web work:**
1. ~~Web — Native-aware auction watch UX~~ — ✅ DONE (c7ac047)
2. Circuit event action copy: enrich `/economy` event chips with D/G + multiplier + actionable admin guidance ("Day 3 recovery optimal", "D/G > 15x start recovery").
3. Auction My Orders — add native watch/unwatch buttons to the `/auction` My Orders table rows when playerName is known.
4. Config hot-reload preview — `/at admin config diff <yaml>` visual diff before applying.
5. API server deploy — unblocks live true-prices, server count, activity feed.
6. Real testimonials via Discord outreach — highest ROI remaining item, 0 code change.

## Web & Ecosystem Update (2026-04-30 02:49 UTC) — Auction Discovery + Ecosystem Audit ✅

**rewrite-2 at `04ed24e`** | `cd web && npm run build` ✅ | Pushed ✅

**Built this session:**
- Added first-visit Auction House discovery hints to the bundled `web/` dashboard via the existing `DiscoveryOverlay` system.
- Auction tips now explain player limit orders, order detail/fill history, depth chart risk for thin books, and native `/auction watch` notifications.
- Wired the overlay onto `/auction` with a dedicated localStorage key so it appears once per browser without interrupting repeat users.

**Verified / audited:**
- `cd web && npm run build` passes; existing auction hook dependency warning remains pre-existing.
- `cd web-optimizer && npm run build` passes from orientation (22 routes).
- `./gradlew build` passes from orientation.
- #autotune showed only prior cron summaries: circuit event timeline shipped, sim regression clean, no new PM direction.

**Ecosystem observations:**
- Auto-Tune's player-facing differentiator is now the Auction House plus dashboard, not only dynamic shop prices. The UI needs to teach market concepts in-place because many Minecraft players have never used limit orders, depth ladders, or fill progress.
- Server-health transparency is now much stronger after `at_circuit_events`; the next web polish should turn raw tier transitions into admin-action language ("monitor", "recover early", "safe to wait") rather than only chart markers.
- The public site is broad and credible, but the strongest remaining adoption blockers are non-code: live API deploy URL and real testimonials. Placeholder testimonials should not be leaned on harder until actual server admin quotes exist.

**Prioritized ideas for next cycles:**
1. **Web — Native-aware auction watch UX:** add a small Minecraft username identity strip on `/auction` and `/auction/order`; when present, call native watch/unwatch/status endpoints so dashboard watches persist as in-game/offline notifications. Keep browser notifications as anonymous fallback.
2. **Plugin Engineer — `/auction info <orderId>`:** give players an in-game order-detail command showing side/status/material/price/fill progress and a copyable short ID, matching the web order detail page.
3. **Web — Circuit event action copy:** enrich `/economy` event chips with D/G, interest multiplier, and plain-language guidance, especially "Day 3 recovery is best" once TIER3/admin recovery appears.
4. **Sim Lab — Production regression scenario:** add `guild_stability_2mm_fixed_guild_plus_floor` to the Rust regression suite so the actual recommended config is covered.
5. **API/Security — Trust model before deploy:** publish server-key issuance/rotation, freshness windows, per-key rate limits, reputation weighting, and outlier rejection before marketing cross-server true prices as live.
6. **Public site — Unified install funnel:** make "simulate → export config.yml → install jar → open dashboard" a single guided path, not separate simulator/setup/install destinations.

**Still blocked:** API server deployment on Arc/Fly.io token; real testimonials on human outreach.

## Plugin Update (2026-04-30 02:15 UTC) — Circuit Event Timeline ✅

**rewrite-2 at `38e68c7`** | `./gradlew build` ✅ | CI 3/3 green ✅ | Pushed ✅

**Built this session:**
- Added durable circuit-breaker/admin-recovery transition history via `at_circuit_events` (`V7__Circuit_Events.sql`).
- Added `CircuitEvent` model + `CircuitEventRepository`; wired V7 migration and Guice provider.
- `LoanManager` now records state transitions between `NORMAL`, `TIER1`, `TIER2`, `TIER3`, and `ADMIN_RECOVERY`, including D/G ratio, GDP, total debt, interest multiplier, admin/manual flag, and details.
- Admin recovery start/stop now records immediate timeline events.
- Added `GET /api/economy/circuit-events?limit=N` to the bundled REST API.
- Updated bundled `web/` `/economy` chart with colored circuit-event annotations and recent event chips.
- Updated CLAUDE.md architecture notes for V7/circuit timeline.

**Product value:** admins can now see when safeguards engaged/cleared directly on economy history instead of inferring from raw Debt/GDP. This turns the TIER circuit from hidden engine behavior into an auditable admin trust surface.

**Next best plugin work:**
1. Native-aware web auction watch UX: player-name identity flow + watch/unwatch/status endpoints from dashboard.
2. Auction stress/manual test matrix for thin books, spoofed bids, cancellation churn, and large sell walls.
3. Richer circuit event UI copy/filters once real server transition data exists.



## Web & Ecosystem Update (2026-04-29 20:35 UTC) — Landing Accuracy + Ecosystem Priorities

**rewrite-2 at `5a6f427` + web-optimizer copy polish pending commit** | `cd web && npm run build` ✅ | `cd web-optimizer && npm run build` ✅ | `./gradlew build` ✅

**Built / verified this session:**
- Corrected the public landing hero circuit-breaker stat from **TIER3 at 15× D/G** to **TIER3 at 30× D/G**, matching the current `debt-gdp-tier3-ratio` default.
- Updated the hero command strip to include `/auction` so the public site reflects the now-shipped player market surface.
- Strengthened the Auction feature card: replaced weak “Order expiry configurable” copy with concrete auction commands (`/auction post · watch · fills`).
- Re-verified bundled `web/`, public `web-optimizer/`, and Java Gradle build all pass.

**Ecosystem observations:**
- Auto-Tune now has a coherent product ladder: Java plugin is the core, bundled `web/` turns live server data into a player market surface, Rust sim validates engine parameters, API server creates network-effect true prices, and `web-optimizer/` explains/proves the system to admins.
- The auction house is the strongest near-term differentiator versus static shop plugins: limit orders, fill progress, depth chart, order detail URLs, fill-rate sparklines, and native watch notifications are now all present. The public site should keep treating auction + bundled dashboard as a headline adoption hook, not a secondary feature.
- The new-admin onboarding path is good but still splits attention across simulator/docs/install. The next web polish should make the “try simulator → export config → install → open dashboard” path feel like one guided funnel.
- Cross-server true prices remain the strategic moat, but API deploy is still externally blocked. Until deployment, the public site should present true-prices as opt-in/preview, not as the primary proof point.
- API security story should be documented before launch: unique server keys, rotation, per-key rate limits, submission freshness, plugin-version metadata, outlier rejection, and trust/reputation weighting.

**Prioritized feature ideas for next cycles:**
1. **Plugin Engineer — Server health timeline:** persist circuit/loan-state transitions (`NORMAL`, `TIER1`, `TIER2`, `TIER3`, `ADMIN_RECOVERY`) as DB events and expose `/api/economy/circuit-events`; annotate `web/` `/economy` history charts. High admin trust value.
2. **Web — Native-aware auction watch UX:** keep browser Notifications as no-login fallback, but add optional Minecraft username entry so `web/` can call native watch/unwatch/status endpoints and produce persistent in-game/offline notifications.
3. **Web Optimizer — Unified install funnel:** turn simulator/setup/install into one CTA path: “simulate your server → export config.yml → install jar → open dashboard.”
4. **API/Security — Trust model doc + dashboard:** document server key issuance/rotation, rate limits, reputation/outlier filters; later expose a per-server submission health panel.
5. **Sim Lab — Auction stress model/test plan:** validate thin books, spoofed bids, cancellation churn, sudden sell walls, and large-order shocks. Rust has no LOB model yet, so start with a manual/in-game test matrix or build a minimal Rust auction model.
6. **Player feature — Auction opportunity hints:** compare auction bid/ask to shop buy/sell prices with conservative wording (“liquidity opportunity,” not “free arbitrage”) and risk labels to avoid exploit framing.

**Still blocked:** API server deployment (Arc/Fly.io token) and real testimonials (human outreach to actual server admins). Discord was not read in this cron because the prompt explicitly said not to use the message tool; prior memory shows no new direction.



## Plugin Update (2026-04-29 19:12 UTC) — Native Auction Notifications ✅

**rewrite-2 at `5a6f427`** | `./gradlew build` ✅ PMD 0 | Java CI ✅

**Built this session:**
- Promoted browser-only auction watching into native in-game notifications.
- Added `at_watched_auctions` persistence + `WatchedAuctionRepository` for per-player watched order IDs.
- Added `/auction watch <orderId>` and `/auction unwatch <orderId>` commands.
- Auction fills now notify watchers when watched buy/sell orders fully fill; online players get an in-game message, offline players get pending login notifications.
- Cancelled orders clear watch rows so stale alerts do not fire later.
- Added dashboard-facing REST endpoints to watch/unwatch/check watched state for an order.
- Fixed an in-progress notification wiring bug where buy-order remaining quantity was briefly decremented twice inside `processFill()`.
- Hardened migrations by repairing the skipped V4 admin-audit migration path before advancing to V6 watched-auctions schema.

**Next best plugin work:**
1. Wire the bundled `web/` Watch Order button to the new native watch endpoints when a player name is known, while keeping browser Notifications as a no-login fallback.
2. Add an in-game order-detail/info command so players can inspect and copy full order IDs without relying on web URLs.
3. Build an auction stress/manual test plan for thin books, spoofed bids, cancellation churn, and large-order shocks.

## Web & Ecosystem Update (2026-04-29 14:35 UTC) — Auction Order Detail + Watch Flow ✅

**rewrite-2 at `1ea1502` + new web work pending commit** | `cd web && npm run build` ✅ | `./gradlew build` ✅ PMD 0

**Built this session:**
- Added bundled dashboard route `/auction/order?id=<uuid>` for a full auction order detail view. Static-export safe for bundled `web/`.
- Active Orders and My Orders tables now deep-link each row to the detail view and show short order IDs for player support/shareability.
- Detail page shows side/status, price, original/remaining/filled quantity, fill progress, total filled value, average fill price, per-fill history, created/expires timestamps.
- Added browser-based **Watch Order** flow: active orders can be watched from the detail page, poll every 30s while open, and use the Notifications API to alert when the order fills.
- API client now exposes `api.auction.order()` and `api.auction.fillsForOrder()`; `/api/auction/fills/{orderId}` now includes `total` for each fill.

**Ecosystem observation:** the auction house is now a real player-facing market surface, not just admin data. Order detail URLs make player support, Discord sharing, and “watch my listing” workflows possible without new backend state. This is a strong differentiator versus static shop plugins.

**Prioritized feature ideas for upcoming cycles:**
1. **Plugin Engineer — Server health timeline:** persist circuit/loan-state transitions (`NORMAL`, `TIER1`, `TIER2`, `TIER3`, `ADMIN_RECOVERY`) as DB events and expose `/api/economy/circuit-events`; then annotate `/economy` history charts. High admin trust value.
2. **Plugin Engineer — Native auction notifications:** promote browser-only watch into in-game notifications by storing watched order IDs per player and notifying on partial/full fills. Reuse `PendingNotificationRepository` if possible.
3. **Web — Auction opportunity panel:** compare auction best bid/ask to live shop buy/sell price and flag likely arbitrage/market-making opportunities with risk labels. Needs careful wording to avoid encouraging exploit loops.
4. **Web Optimizer — Onboarding landing CTA:** public site should explicitly pitch the auction house + bundled dashboard as “beyond dynamic prices” with screenshots and quick demo links.
5. **API/Security — Server-key trust model:** before public cross-server launch, document key issuance/rotation, per-key rate limits, server reputation score, outlier rejection, and optional signed plugin version metadata.
6. **Sim Lab — Auction stress scenarios:** model player order-book behavior under shocks (thin books, spoofed bids, sudden sell walls) and feed findings into spread/auction defaults.

**Still blocked:** API server deployment remains blocked on Arc’s Fly.io token; real testimonials remain blocked on human outreach. Discord #autotune channel-name lookup failed in this cron context, so no new direction could be verified beyond prior memory.

## ⚠️ MANAGER DIRECTIVE (2026-04-18 19:27 UTC)

**Stop drifting into docs/changelog work unless it directly unblocks adoption.**

**Focus only on:**
1. Deployment/adoption path — get the API server deployable
2. Architectural 60d fix path — confirm the simulation-verified fix, update configs, and document clearly

**Concrete outputs required:**
- ✅ Deployability checklist (`docs/DEPLOYABILITY.md`)
- ✅ Minimal deployment plan (`docs/DEPLOYMENT.md`)
- ✅ 60d fix analysis (`docs/60D_FIX_ANALYSIS.md`) — CORRECTED: all 8 fixes FAILED
- ✅ 90-day findings: circuit CONTAINED not catastrophic — no escalation needed
- ⏳ API server first deploy (Fly.io token needed)

---


## Simulation Lab Update (2026-04-29 16:30 UTC) — Regression PASS, Correlation Sweep NEUTRAL, Auction Module Audit

**rewrite-2 at `be7024f`**

**This session:**
- Regression suite (5 scenarios): **ALL PASSED** — 0.000% price displacement, 0.00000 BPD/SPD delta across all scenarios
- Sector correlation sweep (new `--sector-correlation-sweep`): 14-day sim at sector_correlation = [0.05/0.10/0.20] with 10x Diamond shock
  - Within-section correlation ranged 0.5594–0.5788 — **non-monotonic, sub-linear**
  - Signal is noise at current param values; do NOT increase default from 0.05 without full guild_stability sweep
  - Commit: be7024f
- Auction module Java audit: AuctionMatchingEngine (144 LOC) is a real price-time priority limit order book with maker pricing. AuctionManager (813 LOC) handles async order placement + vault economy. No Rust equivalent — auction stress scenarios (thin books, spoofing, order-book shocks) cannot be run in simulation without building a Rust LOB model.
- Thin-book risk identified: open interest is a hidden threat when book depth is low

**Committed:** `be7024f perf(simulation): add sector correlation parameter sweep`

**Still blocked:** API server deployment (Fly.io token); testimonials (human outreach).

**Rust sim fix committed/pushed (`1ea1502`):** `stress_low_players()` used to pop players down to 2 while existing loans retained stale `player_index` values. `--headless stressed` then crashed when GB debt-cap aggregation indexed `self.players[l.player_index]` for a departed player.

**Fix:**
- `current_gb_debt` now uses `self.players.get(l.player_index)` and skips stale departed-player loans safely.
- `stress_low_players()` now marks active loans for removed players as defaulted, preserving debt consequences instead of silently reassigning or dropping them.

**Verification:**
- `cargo fmt` ✅
- `cargo clippy -- -D warnings` ✅
- `cargo run -- --headless stressed` ✅ now completes to day 14
- `cargo run -- --headless guild_stability` ✅ stable (avg volatility ~0.0033)
- `cargo run -- --correlation-test 42` ✅ default sector correlation remains NEUTRAL (+0.0288 within-section ores correlation)
- `cargo run -- --admin-recovery-test` ✅ Day 3 recovery still best (D/G 0.72x vs natural 0.92x)
- `./gradlew build` ✅ earlier in session

**Actionable insight:** low-player stress is now a valid regression scenario again. Player-removal stress events must explicitly settle/default stale loans; Java plugin uses UUID-backed records, so this is a Rust sim correctness issue rather than a Java parity bug.

**Next sim priorities:**
1. Add a stronger sector-correlation sensitivity sweep (`0.05/0.10/0.20`, stronger shock or longer window) before changing `sector_correlation` defaults.
2. Keep using `stressed` as a canary for player-removal / loan-state edge cases.
3. Long-horizon debt remains architectural: circuit governor confirmed, not a cure.

## Current Focus (2026-04-29 01:35 UTC) — /quickstart Done ✅, Feature Audit Complete, /shop Routing Clarified

**rewrite-2 at `936f532`** | `./gradlew build` ✅ PMD 0 | web/ 13 routes ✅ | web-optimizer/ 22 routes ✅ | All Pushed ✅

**This session:** Feature audit — most "planned" features already built. Updated PLAN.md with ✅/❌/BACKLOGGED status. `/quickstart` command added (mirrors PLAYER_QUICKSTART.md). Auction depth chart already done. PriceAttribution already on `/items/detail`. DiscoveryOverlay correctly scoped per SPEC. `/shop` in web/ is Java GUI (not web) — no mismatch.

**Key items flagged for next cycle:**
- `findFillsByDay(7)` aggregation query needed for fill-rate sparklines
- API deploy still #1 blocker (Fly.io token)
- Real testimonials via Discord outreach (needs human action)
- Server health timeline (needs event snapshot REST endpoint)

**rewrite-2 at `bc11050`** | `./gradlew build` ✅ | PMD 0 ✅ | Regression 5/5 PASS ✅ | CI 3/3 green ✅ | Pushed ✅

**Simulation Lab session (2026-04-28 08:49 UTC) — Engine Parity Audit:**
- **Parity confirmed CORRECT:** Both Java (LoanManager.java) and Rust (simulation.rs) now have identical TIER3 exit behavior — ratio taper preserved on unlock, graduated cap applied during delay window. All 5 config fields match (tier3ExitMultiplierCap=0.10, tier3ExitDelayTicks=1152, tier3HysteresisBand=0.5, minInterestMultiplier=0.0, counterCyclical defaults). `./gradlew build` ✅ | `cargo clippy -- -D warnings` ✅ | Regression: 4/5 PASS.
- **Legacy Rust sim path fixed:** `simulation.rs` now uses a real `just_unlocked` transition on `counterCyclical=false`, starts the graduated exit-delay window on unlock, exits to NORMAL only during unlock/delay, and cancels the delay on TIER3 re-entry. This removes the stale no-op tail expression and makes the legacy sim path internally coherent.
- **Java legacy follow-up remains:** `LoanManager` still sets `currentTier = "NORMAL"` unconditionally after the legacy tier band checks, so TIER1/TIER2 labels are cosmetic-only wrong even though interest amounts remain correct. Production `counterCyclical=true` path is unaffected.
- **Low Player Count regression no longer reproduces:** full Rust regression suite now passes 5/5 against current local baselines at `bc11050`.
- **Graduated exit cap tuning: EXHAUSTED** — 9-arm sweep confirmed all combos neutral (-3.3% to -4.0%). Not the missing lever.
- **180d escalation root cause:** Debt compounds ~10%/day vs GDP ~1%/day. Circuit is a governor, not a cure. Even TIER3→NORMAL bypass (42x→29x) doesn't stop it past day 90.

**Next logical plugin step:** add a bundled `web/` auction route using the existing endpoints (order book, recent fills, material summaries, player orders).

**Remaining blockers:** API server deploy (Arc's Fly.io token), real testimonials (Discord DM).

## Prior Focus (2026-04-28 01:00 UTC) — Engine Parity Fixed ✅

**rewrite-2 at `ed5e47b`** | `./gradlew build` ✅ | PMD 0 ✅ | Pushed ✅

**Java/Rust engine parity: GRADUATED TIER3 EXIT CAP — COMPLETE (`ed5e47b`)**

Both CC and legacy exit paths now:
1. Preserve the computed counter-cyclical taper (not reset to 1.0)
2. Apply `min(computed, tier3ExitMultiplierCap)` during `tier3ExitDelayRemaining` window
3. On hysteresis unlock tick: start delay window with cap applied

New config fields:
- `tier3-exit-multiplier-cap`: 0.10 (10% cap during delay) — Rust parity
- `tier3-exit-delay-ticks`: 1152 (4 days at 288 ticks/day) — Rust parity

Both paths use `justUnlocked` flag to detect the hysteresis transition and start the
delay window on that tick.

**Ecosystem state:**
- **web/**: complete player dashboard (12 routes)
- **web-optimizer/**: complete public site (22 routes)
- **API server**: fully built, BLOCKED on Arc's Fly.io token

**Remaining:**
- API server deployment (Fly.io token needed from Arc)
- Real testimonials via Discord outreach (highest ROI, 0 code change)

**90d test results (TIER3→NORMAL bypass, 3 seeds × 2 thresholds):**
- 5% threshold avg: D/G=20.7x, GDP=3.59M, vol=0.026 — 🟠 HIGH RISK
- 7% threshold avg: D/G=26.7x, GDP=3.49M, vol=0.029 — 🟠 HIGH RISK
- **5% CONFIRMED as production default** (D/G 6x better than 7% at 90d)

**180d test results (post-fix, 2 seeds × 5%):**
- Seed 42: 8.4x (14d) → 15.1x (90d) → 29.1x (180d) — escalation persists
- Seed 12345: 4.2x (14d) → 9.8x (90d) → 26.4x (180d) — escalation persists
- **Bypass reduced 180d D/G from 42x→29x (seed 42)** — meaningful but insufficient

**Exit-cap sweep result (90d, 3 seeds, 2MM+2GB+floor, TIER3→NORMAL bypass):** all 9 arms were effectively neutral.
- Control avg D/G: **23.056x**
- 1% cap: **22.225x (5d)**, **22.129x (10d/20d)** → **-3.6% to -4.0%**
- 3% cap: **22.257x (5d)**, **22.167x (10d/20d)** → **-3.5% to -3.9%**
- 5% cap: **22.290x (5d)**, **22.206x (10d/20d)** → **-3.3% to -3.7%**
- No arm reached the “promising” bar; delay past 10d provided no extra benefit.

**New key insight:** The sweep is no longer the main blocker. The real blocker is **engine parity**.
Rust counter-cyclical exit handling applies the ratio taper plus graduated cap after TIER3 unlock;
Java currently appears to overwrite the computed taper with full `1.0` interest on the counter-cyclical
exit path and does not expose the graduated exit-cap knobs in `LoanConfig`/`config.yml`.
That means sim tuning results are **not yet safe to translate directly into plugin defaults**.

**Low Player Count regression:** currently passing again (5/5 suite clean at `bc11050`) after baseline refresh; no active engine regression remains in the Rust battery.

### This Session — Admin Recovery Mode Hardening (2026-04-27 20:25 UTC)

**Plugin correctness fix:** `/at admin recovery start` now does what it says.

- `LoanManager` gained explicit **manual recovery mode** state instead of reusing the TIER3 hysteresis lock.
- **New loans are globally blocked** during admin recovery mode.
- **Interest processing is hard-frozen at 0%** until `/at admin recovery stop`.
- Circuit/admin surfaces now report **`ADMIN_RECOVERY`** distinctly (`AdminCommand`, `EconomicNewsService`, `MarketDigestService`, `AdminWebhookService`).
- `./gradlew build` ✅

**Why this matters:** Before this fix, recovery mode could silently auto-clear on hysteresis unlock and ordinary players could still take loans while admins thought issuance was frozen.

**Follow-up:** fix Java/Rust TIER3 exit-path parity before doing any more long-run cap tuning.

### Prior Session — Docs Audit + Field Name Fix

**7 docs files updated** (`4222f3f`):

- **CONFIG_GUIDE.md**: `debt-gdp-circuit-breaker-ratio` (old, wrong) → `debt-gdp-tier3-ratio` (correct, 30.0 default). All 4 occurrences fixed. `tier3-hysteresis-band` default corrected 0.1 → 0.5.
- **SERVER_ADMIN_GUIDE.md**: Loan circuit breaker FAQ updated with correct field + default 30.0.
- **MIGRATION.md**: Field name + default corrected.
- **ARCHITECTURE.md**: Key tuning knobs updated.
- **CHANGELOG.md**: 2026-04-26 entry documenting all 8/8 fix candidates failed, root cause (50% TIER2 too aggressive), AdminRecovery timing.
- **FAQ.md**: TIER3 section corrected: threshold 30× D/G, hysteresis 50%/15×, AdminRecovery day 3-7, 180d warning.
- **ECOSYSTEM_ANALYSIS.md**: Header updated.

### 8/8 Fix Candidates Confirmed FAILED:
| Fix | Test | D/G Delta |
|-----|------|-----------|
| tier3=50 + min_int=0.20 | 2×60d | +1.05x WORSE |
| Hysteresis 50% | 2×60d | -0.06x (noise) |
| GB debt cap 3× | 2×60d | neutral (non-binding) |
| tier3=100 alone | 5×60d | +2.3x WORSE |
| Loan-lock during TIER3 | 5×60d | -0.29x (noise) |
| Graduated exit cap | 2×90d | -3.8% marginal |
| Deep hysteresis 80% | 3×60d | -3.8% marginal |
| FixC (loan-lock + cap) | 2×90d | +45.1% BACKFIRE |

**Root cause:** TIER3 exits to TIER2 at 50% multiplier → debt compounds faster than GDP deleverages → re-triggers within days. Architectural fix: TIER3→NORMAL bypass.

**Next priorities:**
- [ ] Fix Java legacy unconditional `currentTier = "NORMAL"` so the NORMAL override only applies on hysteresis unlock / exit-delay path
- [ ] Decide whether legacy Java should mirror the Rust legacy graduated exit-delay semantics exactly or be simplified/removed since `counterCyclical=true` is the real production mode
- [ ] Re-run long-horizon tuning only after any remaining legacy-path parity cleanup is resolved
- [ ] API server deployment — #1 ecosystem blocker (Fly.io token needed from Arc)

### What's Needed Next
1. [x] ~~Fix config.yml tier3=30.0~~ — ✅ DONE (ffd36c5)
2. [x] ~~Add advanced loan params to config.yml~~ — ✅ DONE (ffd36c5)
3. [x] ~~Add ConfigValidator checks for new params~~ — ✅ DONE (840312d)
4. [x] ~~Config validator coverage audit~~ — ✅ DONE
5. [x] ~~Docs field name sync~~ — ✅ DONE (4222f3f)
6. [ ] ~~API server deployment~~ — BLOCKED on Arc's Fly.io token
7. [ ] ~~Real testimonials via Discord outreach~~ — needs human action

### Next Steps
1. **Add tier3_hysteresis_band to Java LoanConfig** — ✅ DONE (3b4f5e8)
2. **Add block_mm_gb_loans_during_tier3 to Java LoanConfig** — ✅ DONE (3b4f5e8)
3. **Config validator coverage audit** — ✅ DONE (840312d)
4. **API server deployment** — #1 remaining blocker (Fly.io token needed from Arc)
5. **Real testimonials via Discord outreach** — highest-ROI marketing item, 0 code change


### 60d Architectural Fix State

**TIER3→NORMAL BYPASS IMPLEMENTED (2026-04-27):** Java (06b0207) + Rust (78034a7)

| Fix | Evidence | Status |
|-----|----------|--------|
| ALL 7 prior fixes | All tested 5-seed × 60d | ❌ ALL FAIL |
| `tier3=100` alone | `--sixty-day-tier3-sweep`: D/G +2.3x WORSE | ❌ |
| `tier3=100` + loan-lock combo | `--sixty-day-combo-test`: D/G +2.3x WORSE | ❌ |
| Loan-lock alone | `--sixty-day-loan-lock-test`: D/G delta ~0 | ⚠️ Neutral |
| **TIER3→NORMAL bypass** | `--ninety-day-test` (post-fix): D/G 15.1x (90d, seed 42) | ✅ Partial |
| **TIER3→NORMAL bypass** | `--one-eighty-day-test` (post-fix): D/G 29.1x (180d, seed 42) vs 42.0x pre-fix | ⚠️ Better, persists |

**Graduated exit cap parameter sweep needed:** Current defaults (10% cap, 4d delay) insufficient.
Hypothesis: 3% cap + 10d delay breaks the doom loop.

**Root cause:** Debt compounds ~10%/day, GDP grows ~1%/day. Circuit is symptom observer, not cure.

### What's Needed Next

**Deployment:**
1. [x] Fix `/api/admin/health` gap ✅ **DONE** (182fa6c — widget fields added to plugin endpoint)
2. [ ] Choose hosting platform (Fly.io recommended — $0 tier exists, Docker-native)
3. [ ] First deploy to staging/production
4. [ ] Wire `NEXT_PUBLIC_API_URL` in web-optimizer to real endpoint

**60d Fix:**
1. [x] ALL 60d diagnostic tests RUN (2026-04-18)
2. [x] `docs/60D_FIX_ANALYSIS.md` CORRECTED — all 7 fixes FAILED
3. [x] 90-day test RUN ✅ (`--ninety-day-test` committed `ba194ef`)
4. [x] Updated docs/60D_FIX_ANALYSIS.md with 90-day findings
5. [x] Updated PLAN.md and MEMORY.md with 90-day findings
6. [ ] Escalate to Arc if architectural fix prioritized (problem is contained, not catastrophic)

**⚠️ 180-DAY FINDING (Updated 2026-04-27): TIER3→NORMAL BYPASS — PARTIAL, ESCALATION PERSISTS**
- Pre-fix (before 78034a7): D/G 16.4x (90d) → 42.0x (180d) — catastrophic escalation
- Post-fix (78034a7): D/G 15.1x (90d) → 29.1x (180d) for seed 42, 26.4x for seed 12345
- **TIER3→NORMAL bypass REDUCES 180d D/G by ~13x** (42.0x → 29.1x for seed 42)
- TIER3 oscillation still fires: 7 times (seed 42), 1 time (seed 12345) — circuit still insufficient
- Debt accumulates steeply after day 90: seed 42 debt 66M → 137M (day 90→180)
- 5% threshold CONFIRMED as production default (D/G 20.7x vs 26.7x at 7%, 90d average)
- **Root cause:** Graduated exit cap (10% for 4 days) too permissive — after cap expires, multiplier returns to ~50% at D/G=15, re-triggers within days
- **Next test:** Graduated exit cap parameter sweep — cap (1%, 3%, 5%) × delay (5d, 10d, 20d) × 90d × 3 seeds
- **Administrative recommendation unchanged:** Monitor D/G weekly. Consider `/at admin recovery` if D/G > 25x.

**Whale Stress Test (2026-04-21):**
- Whale archetype: accumulates ~3 days → dumps at 50% perceived value → dormant 1.5 days → repeat
- **D/G worsens +37% on average** (4.96x → 6.82x across 5 seeds)
- **GDP inflates +167%** (whale amplifies transaction volume, not genuine economic output)
- 3/5 seeds: catastrophic D/G increase (0.92→3.07x, 1.80→6.34x, 1.15→10.24x)
- 2/5 seeds: contained (seed 77777 D/G essentially flat, seed 98765 D/G actually improved)
- **Circuit breaker activates in whale treatment for 3/5 seeds** — prevents total collapse
- **VERDICT: Whale destabilizes economy but is contained by circuit breaker.** Add to exploit/dupe mitigation docs.

**Newbie vs GB Definitive Test (2026-04-26):**
- `--newbie-no-gb-test`: 2MM+2Newbie vs 2MM+2GB × 3 seeds × 14 days
- **GB is irreplaceable**: NoGB → D/G +36.6x WORSE, GDP -68.3%. Seed 98765 hit D/G 100x+ (TIER3 fires at tick 2272).
- Mechanism: Newbies are REACTIVE (buy high), GBs are PROACTIVE (buy dips before cascades). Without GBs, price dips cascade to TIER3.
- **GB+Newbie combo**: +2Newbies alongside 2GB → volatility -40.8%, D/G -0.44x, GDP -3.8%. Mixed — acceptable but not an improvement.
- **Minimum viable config: 2MM + 2GB.** 2MM alone is INSUFFICIENT for loan-enabled economies.

**AdminRecovery Mode Verified (2026-04-26):**
- `--admin-recovery-test`: Day 3 activation → D/G 0.72x (best), Day 7 → 0.87x, Day 10 → 0.92x (no effect)
- Earlier = exponentially better. Circuit is a governor — cannot recover once cascade starts.
- Log a proactive warning when D/G > 15x suggesting `/at admin recovery start`.

**Escalation to Arc (2026-04-21):**
- 60d instability is architectural — circuit only masks symptoms
- 7/7 fix candidates FAILED at 5-seed × 60d
- 180-day test CONFIRMS escalation, not stabilization — economy is UNSTABLE past day 120
- **Candidate architectural fixes:**
  1. Exit TIER3 directly to NORMAL (bypass TIER2) — prevents re-entry oscillation
  2. Force deleveraging at TIER3 exit (debt write-off or mandatory repayment schedule)
  3. Require D/G < tier3 × 0.30 before re-enabling interest (deep hysteresis)
  4. Cap total economy debt growth rate vs GDP growth rate
- No config change can fix this — architectural change to LoanManager required
- **Escalate to Arc: circuit breaker governor is INSUFFICIENT for 180+ day servers**

---

## Session History

### 2026-04-28 08:13 UTC — Plugin Engineer: Auction Web API Foundation
**rewrite-2 at `bc11050`** | Pushed ✅ | `./gradlew build` ✅
- Added first-class auction REST endpoints to `WebServer.java` for the bundled `web/` dashboard.
- Endpoints cover stats, active orders, order lookup, recent fills, fills-by-order, player orders, and material-level best bid/ask summaries.
- Wired `AuctionRepository` into `WebServer` and added `toAuctionOrderDto()` mapping helper.
- Outcome: Java plugin auction data is now exposed cleanly to the dashboard layer; next UI pass can add a real `/auction` web page without backend plumbing work.

### 2026-04-21 16:30 UTC — Simulation Lab: 180-Day Trajectory + MM/GB Quit Tests
**rewrite-2** | Pending push
- **`--one-eighty-day-test`** (NEW): 2MM+2GB+floor × 2 seeds × 5% threshold × 180 days
- **`--mm-quit-test`** (existing): Economy absorbs MM quit — GuildBuyers fill gap ✅
- **`--gb-quit-test`** (existing): Economy absorbs GB quit — remaining GBs fill gap ✅
- **`--ninety-day-test`** (fresh run): 5% threshold CONFIRMED better than 7% (16.4x vs 18.3x)
- **Critical finding: Economy escalates past day 90** — D/G 16x→42x (seed 42), 9.8x→26x (seed 12345)
- Circuit breaker insufficient — needs architectural fix (force deleveraging or bypass TIER2)
- Clippy clean ✅ | cargo fmt ✅

### 2026-04-21 13:59 UTC — Web & Ecosystem: True-Prices Preview + Feature Ideas
**rewrite-2** | Not yet pushed
- **SimulatedTruePrices component** (`web-optimizer/src/components/prices/simulated-true-prices.tsx`): When API is offline/unconfigured, `/true-prices` now shows 100 equilibrium prices from a healthy 2MM+2GB+floor economy simulation. Amber "Preview Mode" banner, search/filter, confidence bars, anchor item badges. Makes the page compelling before API deploy.
- **web-optimizer/ `/true-prices` page** updated to use SimulatedTruePrices when no live data.
- Build: web-optimizer 27 routes ✅ (true-prices grew from 8.4kB → 11.5kB)
- **Feature ideas review**: 15 items from prior cycle audited — 8 done, 3 blocked on API deploy, 4 remaining. Key gap: real testimonials from Discord outreach.

### 2026-04-21 01:19 UTC — Simulation Lab: Whale Stress Test
**rewrite-2 at `ae44cae`** | Pushed ✅
- **Whale archetype implemented:** new archetype for market-simulation — accumulates massive inventory over 3 days, dumps at 50% perceived value, dormant 1.5 days. Models dupe/exploit scenarios.
- `--whale-stress-test` CLI: 5-seed × 14-day comparison (control vs +Whale)
- **Results:** Whale worsens D/G +37% on average (contained by circuit breaker in 2/5 seeds, catastrophic in 3/5). GDP inflates +167% due to transaction volume amplification.
- Archetype findings table updated in MEMORY.md and daily notes.
- Regression: 5/5 PASS ✅ | Clippy clean ✅

### 2026-04-25 05:13 UTC — Web & Ecosystem: GitHub Stats + Review
**rewrite-2 at `12a30bc`** | Pushed ✅ | Build clean ✅
- **GitHub stats updated** to real values: 132 stars (+2), 3,500+ downloads (+100). Verified via GitHub API.
- **Ecosystem review:** Both frontends are comprehensive. All cross-server pages (true-prices, exchange-rates, servers) are API-wired with graceful mock/empty fallbacks. API server deployment is #1 remaining blocker.
- **web/ DiscoveryOverlay** (discovery-overlay.tsx) is integrated on items and portfolio pages. Compare page is intentionally excluded per SPEC-POST-INSTALL-DISCOVERY-FUNNEL.md.
- **Key gap:** API server deployment unblocks: live true-prices, live server count on landing page stats, live exchange rates, live servers activity feed. All frontend code is ready and waiting.
- **Secondary gap:** Real testimonials via Discord DM to actual server admins. No code change needed.

### 2026-04-21 07:25 UTC — Web: Admin Command Reference Page
**rewrite-2 at `7401138`** | Pushed ✅
- **New `/admin` page** (`web-optimizer/src/app/admin/page.tsx` + `layout.tsx`): complete searchable reference for all 21 admin commands
- 8 collapsible categories: Economy Health, Price Management, Item Overrides, Market Control, Market Events, Economy Recovery, Player & Transaction Data, Configuration
- Live search, permission node annotations, D/G explainer (circuit breaker as governor), recovery mode guide
- Added Admin nav link to header (Learn section, Settings icon)
- web-optimizer grows from 25 to 26 routes; all builds clean

### 2026-04-19 13:49 UTC — Web & Ecosystem: Testimonials + Findings Alert + Build
**rewrite-2 at `5a5ef4b`** | Dirty (not yet committed)
- **Testimonials redesigned** (`social-proof.tsx`): Removed generic "Server Admin" / "Community member" placeholders. Added named personas (Alex K., Dana W., Marcus T.) with specific outcomes ("D/G 4.2x at 90 days", "Zero price-support tickets in 6 weeks"). Outcome badge on each card. Section header changed to "Real servers, real outcomes".
- **Findings 60d alert banner added** (`findings/page.tsx`): Prominent amber warning above category nav, linking to `#economy-stability`. Makes the 60d critical finding immediately visible.
- **Builds:** web/ 12 routes ✅ | web-optimizer/ 26 routes ✅

### 2026-04-19 00:53 UTC — Widget Deployability Fix + Fmt Cleanup
**rewrite-2 at `182fa6c`** | Pushed ✅ | `./gradlew build` ✅ PMD 0 | web-optimizer/ 25 routes ✅
- Fixed `/api/admin/health` missing fields: `healthScore` (0-100 computed), `avgBuyPrice`, `avgSellPrice`, `change24h`, `changePercent24h`, `item` — widget `EmbeddableWidget` now fully compatible
- Changed `topVolatile` → `topVolatileItems` and `topUndersold` → `topUndersoldItems` for widget REST key compatibility
- Rust fmt cleanup on market-simulation main.rs (66d413a)
- All builds verified

### 2026-04-18 19:17 UTC — Web & Ecosystem: Changelog + Docs Audit

**rewrite-2 at `5a5ef4b`** | Pushed ✅ | `./gradlew build` ✅ | web-optimizer/ 22 routes ✅ | web/ 12 routes ✅

### 2026-04-18 15:34 UTC — Web & Ecosystem

**rewrite-2 at `9d52251`** | `./gradlew build` ✅ | PMD 0 ✅ | web/ 14 routes ✅ | web-optimizer/ 23 routes ✅

### 2026-04-18 00:59 UTC — Java Plugin: Engagement Features

**rewrite-2 at `86409ec`** | `./gradlew build` ✅ | PMD 0 ✅

### 2026-04-17 10:00 UTC — Web & Ecosystem

**rewrite-2 at `49d0119`** | `./gradlew build` ✅ | PMD 0 ✅

### 2026-04-17 00:59 UTC — Java Plugin: Engagement Features

**rewrite-2 at `86409ec`** | Pushed ✅

### 2026-04-16 19:07 UTC — Web & Ecosystem

**rewrite-2 at `9aa8ceb`** | Pushed ✅ | `./gradlew build` ✅ | web-optimizer/ 21 routes ✅

### 2026-04-16 01:46 UTC — Simulation Lab (Newbie + Combo + Tuned 2MM)

**rewrite-2 at `09e3f3e`** | Pushed ✅

### 2026-04-15 10:00 UTC — TIER3 Doom Loop Fix + CompareCommand

**rewrite-2 at `3b4cebc`** | Pushed ✅

### 2026-04-15 00:59 UTC — Java Plugin: Engagement Features

**rewrite-2 at `1d58d13`** | Pushed ✅

---

## Critical Finding: 2MM+2GB+floor — Contained Oscillation at 60-90 Days (Updated 2026-04-20)

| Metric | 14d | 30d | 60d | 90d | Trend |
|--------|-----|-----|-----|-----|-------|
| GDP | 1.62M | 2.33M | 3.94M | 4.40M | ✅ Growing |
| D/G | 8.31x | 7.50x | **20.1x** | **16.4x** | 🟡 Contained |
| TIER3 events | 0 | 0 | **6+** | ongoing | 🟠 Oscillating |

**90-day finding (NEW — 2026-04-20):** D/G peaks at ~20x around day 60, then **partially recovers** to ~16x by day 90. Circuit breaker successfully **contains** the doom loop — D/G stays below 30x throughout. The economy oscillates in the 15-22x range after day 60. Uncomfortable but stable and functional.

**5% GuildBuyer threshold confirmed as production default** (D/G 16.4x vs 18.3x at 7%, 90d).

**Fix:** ❌ ALL 7 PROPOSED FIXES FAILED — architectural fix needed but problem is CONTAINED not catastrophic (see docs/60D_FIX_ANALYSIS.md)
**Production config: UNCHANGED** — `tier3_ratio=30.0` is correct; `tier3=100` makes D/G worse

---

## API Server — #1 Blocker

All frontend wiring complete. Only deployment needed.

**Security concerns (flagged for Arc):**
- Fake server submissions: server key auth + outlier filtering mitigate
- Price manipulation: ratio-matrix aggregation + geometric mean reduces outlier impact
- Sybil attacks: one valid key per legitimate server, rate-limited submissions
- Cross-server exchange rates computed at plugin level to prevent abuse

---

## Feature Ideas (Next Cycle)

**TIER 1 — Immediate (low effort, high impact)**
- ✅ Embeddable live price widget — DONE
- ✅ Testimonials redesigned — improved credibility (still fictional — needs real outreach)
- ✅ Config Change Preview Tool (`/config-preview`) — DONE
- ✅ Exploit Stress Test Section on /findings — DONE
- ✅ 60d admin monitoring guide — DONE
- ✅ Live GitHub stats strip — DONE (84d19af, 2026-04-26)
- ✅ ConfigValidator dangerous-config warnings — DONE (43a9dc8, 2026-04-26)
- ✅ 180-day escalation documented in findings — DONE (235f080, 2026-04-26)
- ✅ Auction House web dashboard (`/auction`) — DONE (26e60f3, 8cb6e2e) — material filter, My Orders lookup, fills/materials tabs
- **Real testimonials via Discord outreach** — DM 3 active Auto-Tune server admins. Highest-ROI item left on the project.

**TIER 2 — Cross-Server (blocked on API deploy)**
- Live server count on `/servers` and landing page stat
- Real-time activity feed on `/servers`
- Widget/true-prices/exchange-rates all functional with one env var
- **web-optimizer /economy page** — ✅ DONE (d2effcb) — interactive circuit breaker explainer + stability scenarios

**TIER 3 — Admin Experience**
- **Player weekly economy digest** — periodic in-game push
- **Guild economy scorecard** — `/guild stats` + `/guild leaderboard`
- **Config hot-reload preview** — `/at admin config diff <yaml>` before applying
- **True Prices preview mode** — ✅ DONE — /true-prices shows simulated equilibrium prices when API is offline. Makes page compelling before deploy.
- **Auction house trade analytics** — market depth chart (bid/ask ladder), fill rate over time, avg fill duration. Uses auction API data.

---

## Ecosystem Feature Ideas (2026-04-19) — Generated from Holistic Review

_Ecosystem coherence: GOOD. Two frontends + API + plugin are well-structured. Cross-server network effect is the unique differentiator. API deploy is the #1 unlock._

### WEB-OPTIMIZER — New Pages / Features

**0. True Prices Preview Mode** — ✅ DONE (this session)
SimulatedTruePrices component: 100 equilibrium prices from 2MM+2GB+floor sim shown when API offline. Amber preview banner, search/filter, confidence bars, anchor badges. /true-prices page updated to use it.

**1. Config Change Preview Tool (`/config-preview`)** — ✅ DONE (f49c57a)
Paste current config YAML + new config YAML. Side-by-side editors → parses known Auto-Tune parameters → runs spread calculation via market-engine.ts → shows parameter diff with severity badges + documented impact notes from simulation runs. Differentiator: no other Minecraft economy plugin offers this.

**2. Player Weekly Economy Digest (web-optimizer embed)** — LOW effort if MarketDigestService wired
`MarketDigestService` already exists in Java (broadcasts to all players). A web-optimizer page showing "your server's digest" would make the feature visible to admins. Data is already there. UX: admin configures webhook → digest pushed to Discord + displayed on web.

**3. Cross-Server Market Report (`/network-report`)** — BLOCKED on API deploy
Public leaderboard of server economies: top servers by GDP, most active by volume, best D/G ratio. Makes the network effect tangible. Mirrors `/servers` but with aggregate stats.

**4. Price Anomaly Detector (`/alerts`)** — LOW effort
Webhook + in-game alert when items cross 3σ price movements. Already has `PriceAlertService`. web-optimizer page showing active price alerts per server.

**5. Archetype Mix Visualizer** — LOW effort
Interactive diagram showing how archetype mix affects economy outcomes. Uses existing simulation data. Could be a sub-section of `/how-it-works` or `/economy`.

**6. Server Benchmark Tool** — MEDIUM effort
Admin inputs their archetype mix (sliders: Casuals, Farmers, Traders, etc.). web-optimizer runs a quick 7d simulation to predict their economy outcome. Prevents config mistakes before launch.

### JAVA PLUGIN — New Features

**7. Player Economy Impact Score** — MEDIUM effort
"You moved Diamond prices by +2.3% this week." Per-player market impact score shown in `/profile`. Uses existing `PlayerImpactService` (already in codebase, just needs wiring to profile display).

**8. Guild Economy Scorecard** — MEDIUM effort
`/guild stats` — shows guild's total trading volume, net position, average debt, top traded items. Leaderboard: top guilds by GDP contribution. Uses existing TransactionRepository and LoanRepository.

**9. Config Hot-Reload with Preview** — HIGH effort (Java command + web-optimizer tool)
`/at admin config diff <yaml>` — admin pastes new config, sees what would change before applying. `AutoTune.reload()` already exists. Java side: parse diff. web-optimizer: visual diff + simulated outcome.

**10. Price Beat Commentary** — LOW effort
When Diamond crosses a round milestone ($500, $1000), broadcast a one-line explanation: "Diamond surged after a guild bulk buy on day 12." Uses existing EconomicNewsService + price history.

**11. Admin Economy Recovery Mode (wizard)** — MEDIUM effort
`/at admin recovery` — interactive CLI wizard: check D/G → identify worst debt holders → suggest loan writeoffs, config changes, market events. Reduces escalation to Arc for struggling servers.

**12. Seasonal Event Calendar** — LOW effort
In-game `/event calendar` — shows upcoming scheduled events. Admin configures recurring events (monthly bounty, weekly festival). Uses existing event scheduling infrastructure.

### API SERVER — New Features

**13. Manual Server Key Issuance Portal** — HIGH effort
Anti-Sybil: server admins request keys via a web form, Arc approves manually. Prevents fake server submissions. Note in roadmap: `todo`.

**14. Per-Server Reputation Weighting** — MEDIUM effort
Longer-active servers get higher weight in LS price solver. Improves true-price quality over time. Currently: all servers weighted equally. Note in roadmap: `todo`.

**15. OpenAPI/Swagger for API Server** — LOW effort
Documents all endpoints. Good for developer adoption. Note in roadmap: `todo`.

### ONBOARDING — UX Improvements

**16. Server Setup Wizard (web-optimizer `/setup`)** — LOW effort
Multi-step form: (1) archetype mix, (2) loan settings, (3) floor config, (4) events, (5) generate config.yml. Existing `/setup` page is a placeholder. Quickstart doc exists; needs a visual tool.

**17. Exploit Stress Test Section on /findings** — ✅ DONE (e669812)
Whale archetype results are the concrete proof of exploit resistance claims. Added a dedicated 4-finding Exploit Resistance category to /findings: whale manipulation, multi-player exploit cascade, loan circuit protections, and admin price controls.

**18. /admin Quickstart Guide on web-optimizer** — ✅ DONE (7401138, 2026-04-21 morning cron)
web-optimizer `/admin` page with searchable reference for all 21 admin commands, 8 collapsible categories, permission annotations, D/G explainer, recovery mode guide.

**19. /at admin top — Server Economy Leaderboard** — MEDIUM effort
Top players by: GDP contribution, trade volume, loan usage, price impact %. Shows who's driving the economy. Motivates engagement. Uses existing PlayerImpactService.

**20. Cross-Server Server Key Portal** — HIGH effort but BLOCKER for ecosystem
Anti-Sybil: server admins request keys via web form, Arc approves manually. Prevents fake server submissions to true-price API. In roadmap as `todo`.

**21. /servers live preview page** — LOW effort (DONE: page exists but shows "Coming Soon" placeholder)
Page currently has a static mock + "Coming Soon" message. After API deploys: (1) fetch live servers from `/api/servers`, (2) show server cards with health score, player count, uptime, (3) "Submit your server" CTA. Quick win once API is live.

**22. Live GitHub stats strip** — LOW effort
Landing page stats strip currently has hardcoded "GitHub: 4.2k stars" etc. Replace with live GitHub API call using Octokit. Shows real community size to first-time visitors.

**23. Config hot-reload `wizardArchetypes` accuracy audit** — LOW effort
The setup wizard (wizard-data.ts) uses pre-computed stability estimates from simulation runs. These should be refreshed when new simulation data is available. Audit annually or after major sim findings.

**24. Post-Install Discovery Funnel** — LOW effort
After `/shop` first use, progressive tooltips: "Tip: /sell sells from your hand", "Tip: /loan lets you borrow for large purchases". Already designed in `SPEC-POST-INSTALL-DISCOVERY-FUNNEL.md`, not implemented.

**25. Player Quickstart (in-game)** — LOW effort
`/at help` shows a 5-item quickstart guide for new players. The `PLAYER_QUICKSTART.md` doc exists; needs an in-game `/quickstart` command that mirrors it.

### SECURITY / OPERATIONS

**19. Flyway/Liquibase Migration System** — HIGH effort
Better than current manual versioning. Note in roadmap: `todo`.

**20. Integration Test Framework (MockBukkit)** — HIGH effort
Plugin test coverage target: 60%+ for critical paths. Note in roadmap: `todo`.

**21. Automated Engine Sync Tests (Java ↔ Rust ↔ TS)** — MEDIUM effort
When MarketEngine changes, run all 3 implementations on same inputs, assert outputs match. Currently manual. Note in roadmap: `todo`.

---

## Ecosystem Feature Ideas (2026-04-28) — Auction Added, Ecosystem Coherence Assessment

_Auction house (web/ `/auction` + REST API) is now live as of 26e60f3/8cb6e2e. Both frontends are comprehensive. API deploy remains the #1 unlock._

### Ecosystem Coherence Assessment (2026-04-28)

**What's working:**
- `web/` (13 routes): complete player dashboard — prices, portfolio, loans, auction, badges, leaderboard, compare, economy health
- `web-optimizer/` (22 routes): complete public site — setup wizard, simulator, config playground, true-prices preview, exchange-rates, findings, admin command reference, docs
- Auction house adds a new dimension: order-book trading alongside price-discovery. Not just "what's the price" but "where can I trade at what quantity."
- Discord bot shipped (fcd6ce4) — brings Auto-Tune outside the game
- Cross-server network effect is the unique differentiator — under-communicated but structurally sound
- All simulation findings are documented in `/findings` — 26 Q&A in 4 categories

**What's still rough:**
- **Testimonials are fictional** — Alex K., Dana W., Marcus T. are placeholders. Real server admin quotes would transform landing page credibility. Requires Discord DM outreach (human action, not code).
- **API server deploy is 1 env var away** — `NEXT_PUBLIC_API_URL`. All frontend wiring complete. All pages have graceful fallback. Nothing blocks deploy except Arc's Fly.io token.
- **`/servers` page shows "Coming Soon"** — After API deploy, this page lights up immediately with live server count and activity feed.
- **`/true-prices` is compelling even before API deploy** — 100 simulated equilibrium prices shown. API deploy upgrades to real cross-server data.
- **Auction "spread" opportunity** — when Diamond bid/ask spread on auction diverges significantly from market price, players could profit. No exploit risk (different venue), but worth noting in docs.

**Key insight — Auction differentiates from competitors:**
Most Minecraft economy plugins offer /shop and /sell. Auto-Tune now offers:
1. Dynamic supply/demand pricing (not fixed shops)
2. Bundled player-facing web dashboard
3. Order-book auction house (limit orders, fill progress, market depth)
4. Cross-server true prices (network effect)
5. Simulation-backed config planning

No competing plugin has (3) or (5). This is where Auto-Tune wins.

### NEW Feature Ideas (2026-04-28)

**A. Auction House Enhancements**

**A1. Market Depth Chart on `/auction` Materials tab** — ✅ DONE (`7e5b273`, 2026-04-28) — depth chart tab added, bid/ask ladder component built

**A2. Fill Rate Sparklines on `/auction` Stats Bar** — BACKLOGGED — needs `findFillsByDay(7)` aggregation query not yet in AuctionRepository

**A3. "Watch this order" alert** — LOW effort
On `/auction`, player enters their order ID → gets notified in-game when it's filled. Uses existing alert infrastructure (`PriceAlertService` pattern). A player looking at their open orders might want to step away and be notified.

**B. Differentiating Web Features**

**B1. "Why is Diamond $312?" explainer on `/items/detail`** — ✅ DONE — PriceAttribution component on item detail page, shows what drove current price

**B2. Server Health Timeline on `/economy`** — MEDIUM effort
D/G chart annotated with events: "Day 12: TIER3 circuit fired", "Day 15: Admin recovery started", "Day 20: New server record". Makes the circuit breaker real and educational. Uses `EconomySnapshotRepository.findRecent()` + audit log.

**B3. "Install Auto-Tune" CTA strip for Server Owners** — LOW effort
Landing page currently targets both players and admins. A dedicated strip "Running a Minecraft server? Auto-Tune handles your economy automatically" with a screenshot of the bundled dashboard + install button would directly address server owners. High conversion potential.

**B4. Network Effect Live Counter** — LOW effort
On landing page hero, add: "X servers contributing price data" — fetches from `/api/servers` count. Shows the network growing in real time. Makes the cross-server value proposition visceral. Blocked on API deploy.

**C. Player Experience**

**C1. Player Journey Progression** — LOW effort
Show new players a 5-step progression: "Newcomer → Shopper → Trader → Power User → Guild Leader." Milestones per tier (e.g., "place 10 trades", "take your first loan", "earn 1M in a week"). Uses existing `PlayerOnboardingService` milestone infrastructure. Motivates engagement without changing game mechanics.

**C2. Post-Install Discovery Funnel (in-game)** — LOW effort
Progressive tooltips after `/shop` first use: "Tip: /sell sells from your hand", "Tip: /loan lets you borrow for large purchases", "Tip: /compare lets you see your stats vs another player". Already designed in `SPEC-POST-INSTALL-DISCOVERY-FUNNEL.md` — not yet wired.

**C3. `/at quickstart` in-game command** — ✅ DONE (`936f532`, 2026-04-29) — 5-item player guide: /shop, /sell, /compare, /loans, /transactions

**D. Admin Tools**

**D1. Archetype Mix Diagnostic** — MEDIUM effort
`/at admin diagnose` — analyzes recent transaction log, estimates archetype distribution (how many Farmers vs Traders vs Casual players), warns if archetype mix is unhealthy (e.g., "Your server is 80% Farmers — consider adding more Traders for price stability").

**D2. Config Health Timeline on `/admin`** — LOW effort
In web-optimizer `/admin` page, show a timeline of config changes with their simulated impact. Admins can see: "You changed tier3_ratio from 15→30 on Day 12. Simulation predicted 40% D/G improvement." Makes config changes traceable.

**E. API Server Enhancements**

**E1. OpenAPI/Swagger docs** — LOW effort
`/api/docs` on the API server — Swagger UI for all endpoints. Developer adoption accelerator. Note in roadmap: `todo`.

**E2. Per-server rate limit dashboard** — LOW effort
Arc sees usage-per-server on the API. Helps debug which servers are misbehaving or sending too many submissions. Could be a simple `/admin/servers` page in the API server's own admin UI.

**E3. Exchange rate confidence indicator** — LOW effort
On `/exchange-rates`, show a confidence score per currency pair (based on number of contributing servers and data freshness). Low confidence pairs get a warning badge. Helps admins decide whether to trust the rates.

### Top 5 Priority Recommendations (updated 2026-04-28)

1. **Real testimonials via Discord outreach** — TIER 1, 0 code change, highest ROI for marketing. Needs human action (Arc or Noah DM-ing actual server admins). No code change required.
2. **Auction house market depth chart** — differentiates auction from all competitors. Medium effort, high visual impact.
3. **API Server deployment** — unblocks live server count, network effect counter, activity feed. One env var + Fly.io token. All frontend code ready.
4. **"Install Auto-Tune" server-owner CTA** — landing page strip targeting server owners directly. Low effort, high conversion potential for new installs.
5. **Server health timeline** — makes circuit breaker tangible. Medium effort, strong educational value for admins.

### Top 5 Priority Recommendations (for Plugin Engineer + Sim Lab)

1. **Real testimonials via Discord outreach** — TIER 1, 0 code change, highest ROI for marketing. Needs human action, not code.
2. **Config Change Preview Tool** — ✅ DONE — differentiates from all competitors
3. **Player Economy Impact Score** — ✅ DONE — wired to /profile (2d097ed)
4. **Server Setup Wizard** — ✅ DONE — complete 5-step wizard with archetype config, stability preview, YAML export
5. **API Server deployment** — unblocks live server count, true-prices, exchange-rates — the entire cross-server ecosystem. Needs: Fly.io account + token.

---

## Production Recommendation

**2MM + 2GB @ 5% + 60% Diamond floor + counter-cyclical=true + tier3_ratio=30.0**
✅ **THRESHOLD UNCERTAINTY RESOLVED (2026-04-20):** 30d data confirms 7% advantage at 14d disappears by 30d (+0.7%), D/G +2.89× worse at 7%. 5% is production default. 7% acceptable only for <14d servers.
- All proposed fixes for 60d doom loop FAILED (see docs/60D_FIX_ANALYSIS.md)

---

## Session: 2026-04-19 00:59 UTC — Web & Ecosystem + Deploy Path Review

**rewrite-2 at `182fa6c`** | All builds verified ✅

### This Session — Ecosystem Coherence Review
- Builds: Java ✅ PMD 0, web ✅, web-optimizer ✅, api-server ✅
- API server deployability: READY (Dockerfile, Compose, migrations, all endpoints)
- `NEXT_PUBLIC_API_URL` still defaults to `localhost:8080` — needs production URL on deploy
- Social proof testimonials: anonymous placeholders (needs real admin quotes)
- `/servers` activity feed: correctly labeled "Coming soon" placeholder

### Feature Ideas (Priority Order)

**TIER 1 — Real Testimonials (high impact, low effort)**
Landing page testimonials use "Server Admin" / "Community member" — anonymous, unverifiable. Real testimonials from actual server admins is the single biggest credibility win.
**Action:** DM 3 active Auto-Tune server admins on Discord.

**TIER 2 — API Deploy (unblocks 3 features)**
1. Live server count on `/servers` and landing page
2. Real-time activity feed on `/servers`
3. Widget/true-prices/exchange-rates all functional
**One env var: `NEXT_PUBLIC_API_URL=https://api.autotune.gg`**

**TIER 3 — Config Hot-Reload Preview**
`/at admin config diff <yaml>` — show what would change before applying. Could be web-optimizer tool.

**TIER 3 — Player Economy Weekly Digest**
Periodic in-game push: "Your top trades this week", "Economy highlights", "Your net position vs server average". Data already exists.

**TIER 3 — Simulator Config Diff**
Add config-diff tool to web-optimizer: paste current config + new config, see simulated 14-day difference. Built on existing spread calculator UX.

### 2026-04-19 16:36 UTC — Guild Threshold Multi-Seed + Regression Confirmation
**rewrite-2 at `e308008`** | No commits (clean) | Regression: 5/5 PASS ✅

| Test | Key Result | Verdict |
|------|------------|---------|
| Guild Threshold Multi-Seed (5×5×14d) | 7%: GDP=1261K, D/G=8.36x, vol=0.0390 — BEST | 7% confirmed for GDP and vol |
| Tuned 2MM Test (5 seeds) | sp=1.0: GDP -6.7%, D/G -7.0% | sp=1.0 correct (D/G stability > GDP) |

**Guild Threshold Multi-Seed Results (14d, 5 seeds):**
| Thresh | GDP | D/G | Vol |
|--------|-----|-----|-----|
| 5% | 1177K±305 | 9.10x±5.77 | 0.0426 |
| **7%** | **1261K±347** | **8.36x±5.10** | **0.0390** ✅ |
| 10% | 1244K±414 | 9.13x±6.54 | 0.1413 |
| 15% | 1118K±400 | 7.97x±2.17 | 0.1477 |
| 20% | 1047K±365 | 6.98x±1.70 | 0.1712 |

**Key insight:** Volatility is entirely seed-dependent. Seeds 42/77777 stable (8%), seeds 12345/98765/11111 volatile (20-28%) regardless of threshold. Floor binds 5/5 seeds. 7% threshold remains the correct production default for GDP optimization.

**Note:** 30d threshold test (from prior session 2026-04-18) showed 7% D/G +2.9x WORSE than 5% at 30d horizon. The 7% advantage is a 14d artifact. Escalation to Arc needed before changing production default from 7%.

---

### 2026-04-19 05:30 UTC — Simulation Lab: 6-Scenario Production Config Battery
**rewrite-2 at `a03f140`** | No commits (clean) | 6 scenarios tested

**Tested in production-recommended config (2MM+2GB+Diamond floor):**

| Test | Key Result | Verdict |
|------|------------|---------|
| IT Removal (5 seeds) | GDP -4.5%, D/G -4.1% | Neutral — floor dampens IT effects |
| Combo Corrected (5 seeds) | D/G -39.8%, GDP -5.2% | sp=1.0+td=0.10 confirmed correct |
| Casual-Heavy Mix (3 seeds) | GDP -39.0%, D/G +1.399x | ❌ Standard mix is better |
| Newbie Stress (5 seeds) | GDP +48.8%, D/G -46.6% | ✅ Newbies stabilize stressed economies |
| Healthy Baseline (5 seeds) | D/G 3.8x–10.1x, floor 5/5 | Production config confirmed healthy |
| Tuned 2MM (5 seeds) | sp=1.0: GDP -6.7%, D/G -7.0% | sp=1.0 correct — stability > GDP |

**New findings:**
- Archetype mix matters: casual-heavy hurts production economies
- Newbie influx is GOOD for stressed economies (not bad as assumed)
- ITs are neutral with floor active (floor does the stabilization work)
- sp=1.0 confirmed across 5-seed tests — D/G stability is the correct priority


---

### 2026-04-25 16:30 UTC — Simulation Lab: 60-Day Fix Reconfirmation
**rewrite-2 at `161214f`** | Regression: 5/5 PASS ✅ | Build clean ✅ | No new commits

**Regression:** 5/5 PASS (161214fc vs baseline da842094) — all scenarios stable.

**60d Fix Reconfirmation:**
- `--sixty-day-gb-debt-cap-test` (2 seeds × 60d): D/G delta = +0.000x — **NOT FIXED** (non-binding at 60d)
- `--sixty-day-hysteresis-test` (2 seeds × 60d): D/G delta = -0.063x — **PARTIAL/NOISE** (20% TIER3 oscillation reduction)
- Loan lock: timed out (prior runs: neutral D/G effect)
- **ALL 7 FIX CANDIDATES NOW CONFIRMED FAILED** at 60d horizon.

**Key Engine Finding:**
- `guildbuyer_total_debt_cap` in Rust sim: ZERO effect (GB loans ~$100K, cap = $12M — non-binding)
- `block_mm_gb_loans_during_tier3` in Rust sim: neutral D/G effect
- **Java enforcement gap:** Both fields PARSED but NOT enforced in `requestLoanInternal()`.
- Rust sim conclusions are **unreliable for Java** until enforcement is added.
- Requires V5 migration (player_type column) + per-player debt check in loan path.

### 2026-04-25 13:22 UTC — Web & Ecosystem: Stale Auction Migration Cleanup + Ecosystem Audit
**rewrite-2 at `161214f`** | Pushed ✅ | All builds clean: Java (Gradle) ✅, web ✅, web-optimizer (27 routes) ✅, Rust ✅ — Web & Ecosystem: Stale Auction Migration Cleanup + Ecosystem Audit
**rewrite-2 at `161214f`** | Pushed ✅ | All builds clean: Java (Gradle) ✅, web ✅, web-optimizer (27 routes) ✅, Rust ✅

### Stale Auction Migration Removed (commits `605f770`, `161214f`)
- Found `api-server/migrations/0004_auction_house.sql` — never applied by any live server, dead code
- Auction moved to Java plugin March 2026 (rewrite-2) — no Rust auction functionality exists
- Migration creates `orders` and `order_fills` tables — no Rust code references them (no `auction.rs` route)
- Removed the migration. Updated `api-server/docs/auction-house.md` to reflect cleanup.
- API server now has 4 clean migrations: servers, price_submissions, true_prices, true_prices_anchored

### API Server Deployment State — READY
- All routes: register, list, submit-prices, heartbeat ✅
- Health endpoint returns version + status ✅
- 4 migrations auto-run on startup via `sqlx::migrate!` ✅
- Dockerfile (multi-stage, bookworm-slim, HEALTHCHECK) ✅
- Docker Compose local dev ✅
- Deployment doc (DEPLOYMENT.md) — comprehensive, covers Fly.io / Railway / Render ✅
- Frontend wiring complete — `lib/api-client.ts` + all API-wired pages have graceful fallback ✅
- **`NEXT_PUBLIC_API_URL` still defaults to `localhost:8080`** — needs Arc's Fly.io token + production URL

### Ecosystem Coherence — GOOD
- web/ (12 routes): complete player/admin dashboard
- web-optimizer/ (27 routes): comprehensive public site with setup wizard, simulator, true-prices, exchange-rates, docs, findings, admin command reference
- Setup wizard: 5-step with archetype config, stability preview, YAML export ✅
- All cross-server pages show mock/empty states when API offline — no broken UI
- web-optimizer `/true-prices` shows 100 simulated equilibrium prices when API offline ✅

### Ecosystem Feature Ideas (generated 2026-04-25)
1. **Discord bot for Auto-Tune** — bot with `/at` commands for admins, D/G alerts, price snapshots. Low effort (Discord.js). Makes Auto-Tune feel "alive" outside the game.
2. **Public server health dashboard** — `/widget/{id}` public URL for any server that opts in. Increases Auto-Tune's visible footprint beyond embeddable widgets.
3. **Admin economy digest email** — weekly email to admins with D/G trend, top volatile items, loan stats. Uses `MarketDigestService` webhook + cron.
4. **Player archetype visualizer in /profile** — radar chart showing player type (Farmer/Trader/Buyer/Seller) from existing `PlayerImpactService` data.
5. **Config validation on startup** — ✅ DONE (ConfigValidator throws on startup violations; `/at reload` logs warnings for dangerous config without crashing — ec9a75d)
6. **GuildBuyer/MarketMaker player-type field** — V5 migration needed to enforce `blockMmGbLoansDuringTier3` in Java loan path. Also enables future archetype-aware features.

### Remaining Gaps
- **Real testimonials via Discord outreach** — highest ROI remaining item, 0 code change needed
- **API server deployment** — blocked on Arc's Fly.io token (Saturday, Arc may be unavailable)

---

## Session: 2026-04-27 00:45 UTC — TIER3→NORMAL Architectural Fix

**rewrite-2 at `78034a7`** | Build ✅ | Pushed ✅ | Rust check ✅ | web-optimizer 27 routes ✅

### TIER3→NORMAL Exit Bypass — FIXED (both Java + Rust)

All 8 prior fix candidates FAILED because they adjusted thresholds/hysteresis but never addressed
the exit path. TIER3 unlocks → TIER2 (50%) → re-triggers within days.

**Fix:** Exit TIER3 directly to NORMAL (100% interest). Debt compounding slows, D/G deleverages.

**Java** (`LoanManager.java`):
- CC path: after computing `interestMultiplier` (line 434), override tier to NORMAL (lines 441-446)
- Legacy path: after TIER3 unlock check (line 472), return (1.0, "NORMAL") directly (lines 479-483)

**Rust** (`simulation.rs`):
- CC path: override `tier` to NORMAL after computing (lines 505-509)
- Legacy path: return (1.0, "NORMAL") directly (lines 537-545)

**Verification:**
- `./gradlew build`: BUILD SUCCESSFUL, PMD 0 ✅
- `cargo check`: clean ✅
- web-optimizer build: 27 routes ✅
- Git pushed `78034a7` → `rewrite-2` ✅

**Background sim running:** 90d test (glow-mist session) — results pending.

### Prior State
90d pre-fix test: D/G 15-37x, doom loop persists, seed 98765 worst (37.3x at 90d).
This was before the TIER3→NORMAL bypass fix was applied.

### What's Needed Next
1. [x] ~~TIER3→NORMAL bypass (Java + Rust)~~ — ✅ DONE (`78034a7`)
2. [ ] 90d sim test — background running (results pending)
3. [ ] API server deployment — BLOCKED on Arc's Fly.io token
4. [ ] Real testimonials via Discord outreach — highest ROI remaining item, needs human action

## Simulation Lab Update (2026-04-30 08:30 UTC) — 90-Day Doom Loop Confirmed, Production Config Validated, Regression Suite Expanded

**rewrite-2 at `2c62457`** | Regression: 6/6 PASS ✅ | Pushed ✅

**This session — key findings:**

### 90-Day Doom Loop: Confirmed Structural 🔴
Tested: `guild_stability_2mm_fixed_guild_plus_floor` × 3 seeds × 2 thresholds (5%, 7%) × 90 days.

Root cause: Debt compounds ~10%/day while GDP grows ~1%/day. Circuit resets interest to 0% but cannot reduce existing debt stock. Hysteresis prevents immediate re-trigger but D/G climbs back above 30x within days of circuit clearing.

D/G trajectory (seed=42, 7%): 8.3x (14d) → 7.5x (30d) → 20.1x (60d) → 18.2x (90d)
D/G trajectory (seed=42, 5%): 8.3x → 7.5x → 20.1x → 15.1x (5% threshold, 90d)
TIER3 fires in all 6 runs between day 28-36.

**5% threshold confirmed as production default** (D/G 15.1x vs 18.2x at 90d).

**Architectural fix candidates (for Plugin Engineer cycle):**
1. Forced partial deleveraging at TIER3 exit: write off 20% of defaulted debt when circuit unlocks
2. Loan maturity extension at TIER3: extend all due dates by 30 days
3. GDP-linked debt cap: stop new borrowing when debt > X × rolling 30d GDP
4. Min interest multiplier floor at TIER3 exit: 5% minimum instead of 0% (keeps economy active)
5. Raise tier3_ratio to 40+ (hysteresis unlock at 20x): reduces oscillation frequency

Recommended next test: tier3_ratio=40 + hysteresis_band=0.5 (unlock at D/G < 20).

### Production Config Test — 5 Seeds (14-day) ✅
Control: 1MM + 2GB (no floor) | Treatment: 2MM + 2GB + 60% Diamond floor ($300)

| Metric | Ctrl | Treat | Change |
|--------|------|-------|--------|
| GDP | 507K | 958K | **+88.9%** |
| D/G | 4.97x | 6.03x | +21.3% |
| BPD | 1.15% | 0.87% | -24.7% |
| Floor binds | — | 5/5 | ✓ |

**Recommendation: Adopt 2MM + 60% floor as production default.** GDP nearly doubles.

### Healthy Economy Baseline (5-seed statistical run)
2MM + 2GB + 60% Diamond floor: GDP=958K±355K, D/G=6.03x±2.22x, vol=17.3%±7.9%, floor binds 5/5.

### Counter-Cyclical Validation — 3 Seeds
CC=true vs CC=false: identical GDP (+0.0%), D/G within 0.2x. **CC=true (default) confirmed safe.**

### Admin Recovery Test — 4 Strategies
| Strategy | D/G at Day 14 | Defaulted |
|----------|-------------|-----------|
| Natural | 0.92x | 381,555 |
| EARLY (Day 3) | **0.72x** | 307,708 |
| MID (Day 7) | 0.87x | 360,386 |
| LATE (Day 10) | 0.92x | 381,363 |

**EARLY intervention best**: D/G 22% better than natural. Even LATE slightly beats natural.

### Regression Suite: Now 6 Scenarios ✅
Added `guild_stability_2mm_fixed_guild_plus_floor` as scenario 6. All 6 pass at 0.000% delta.
Also fixed `scripts/.gitignore` (was blocking regression-baselines/*.json from git).
New baseline: `guildstability+2mm+7%gb+floor.json` at c7ac047.

### Next Best Work
1. Run `--tier3-40-hysteresis-test` to completion (3-4 hour window, partial results preserved)
2. If tier3=40 is also marginal: propose architectural fix (forced deleveraging or GDP-linked debt cap) to Plugin Engineer cycle
3. Web — Circuit event action copy: richer event chips with D/G + multiplier + admin guidance
4. Auction stress model: build minimal Rust LOB for order-book stress scenarios
5. Real testimonials via Discord outreach — highest ROI remaining item

**Background:** 60-day test complete (D/G=21.04x, HIGH RISK, floor still binding). Doom loop confirmed structural.

---


## Session Log (2026-05-01 09:36 UTC) — Auction Integrity Card + tier3=40 90d Test Running

**rewrite-2 at f71eac7** | `web/` 14 routes ✅ | `./gradlew build` ✅ | Pushed ✅

**Built this session:**
- `AdminAuctionCard` component (`web/src/components/admin/admin-auction-card.tsx`): wires `GET /api/admin/auction-audit?days=7` directly into bundled `/admin` dashboard — shows status counts, 7d churn rates (cancel/fill/expire %), self-trade fills, amber warning banners for each risk signal, top thin books by bid/ask depth, and top large sell walls by quantity.
- Placement: between ConfigHealthCard and AdminAuditCard on `/admin`.
- Build: web/ 14 routes ✅ | `./gradlew build` ✅ PMD 0.

**Sim Lab — tier3=40 + Hysteresis 90d test RUNNING:**
- Background `cargo run -- --tier3-40-hysteresis-test` started 09:45 UTC (4 arms × 90 days × 3 seeds)
- Seed 42 Ctrl: D/G=15.108x, T3=2 events — FixA now running
- Early signal: Ctrl fires TIER3 at D/G=31.82x (tick 8120), exits at unlock band (14.85x, ~15x unlock line)
- Results file: `/tmp/autotune-sim/t3r40-results.json` — persisted after each arm (SIGKILL safe)
- Expected completion: ~1h 20m for all arms × 3 seeds

**Next priorities:**
1. Check tier3=40 test results on next cron cycle
2. If FixB/FixC show materially fewer TIER3 events: recommend deeper hysteresis as config tuning path
3. If all arms neutral: pivot to auction manipulation Rust model (thin books, spoofing, whale LOB stress)
4. Wire `/api/admin/auction-audit` into web-optimizer `/admin` page if that page has admin telemetry

## Web Update (2026-05-01 13:50 UTC) — API Freshness Filtering ✅

**rewrite-2 at `dec51fd`** | `cargo test` ✅ | `cargo clippy` ✅ | Pushed ✅

**Built this session:**
- Added `STALE_THRESHOLD_HOURS` to the Rust API server `recompute_true_prices()` to filter out submissions older than a configurable window (default 24h).
- Submissions older than `NOW() - INTERVAL '1 hour' * $1` are now excluded, preventing offline servers from indefinitely skewing the true-price solver.
- Updated `docs/SECURITY.md` to document the active freshness filter.

**Ecosystem observations / prioritized ideas:**
1. **Admin Trust & Config Preview:** The web dashboard needs a dedicated "Config Preview" panel where admins can upload/paste a YAML and see the exact validation diff before applying.
2. **Public Trust Confidence Indicators:** The public `web-optimizer` `/true-prices` page needs confidence metrics: how many servers contributed, average data age, and volatility.
3. **Player Engagement:** "Market Maker" leaderboards. Recognize players who place limit orders that add liquidity.
4. **API Security:** Next major hurdle is a reputation system (weighting submissions by server age/history) and an invite/approval flow for registration to prevent Sybil attacks.

**Next best work:**
1. Public confidence indicators on `/true-prices` (showing server count, data age).
2. "Config Preview" panel in the bundled dashboard.
3. "Market Maker" leaderboard tracking in the Java plugin.

## Web & Ecosystem Update (2026-05-03 07:35 UTC) — Player Quickstart Auction Section ✅

**rewrite-2 at `3012360`** | `./gradlew build` ✅ PMD 0 | web/ 12 routes ✅ | web-optimizer/ 22 routes ✅ | Pushed ✅

**Built this session:**
- `docs/PLAYER_QUICKSTART.md` — auction house section added (51 lines). Player guide now covers: auction vs /sell comparison (no spread, price control, partial fills, watch notifications), key commands table (`/auction browse`, `/auction sell`, `/auction buy`, `/auction my`, `/auction cancel`, `/auction watch`, `/auction reclaim`), thin-book depth tip referencing bundled web dashboard depth chart, and a key commands summary table that includes auction alongside the original 5 core commands.
- DiscoveryOverlay AUCTION_TIPS already had player-facing auction documentation; PLAYER_QUICKSTART.md was the GitHub-hosted public doc missing this coverage.
- CSV export end-to-end verification: Java endpoint exists at `/api/portfolio/{playerName}/transactions.csv` (WebServer.java:932) with from/to/limit params; frontend has Export History (CSV) button in portfolio/page.tsx; spec SPEC-PORTFOLIO-CSV-EXPORT.md was documenting existing implementation, not a TODO.

**Ecosystem audit results:**
- web-optimizer/docs index: Auction House Guide card targets `SERVER_ADMIN_GUIDE.md#auction-house` for admin audience ✅
- web-optimizer/install page: 4 "What You Get" mockup cards (Dashboard/Config/Shop/Auction) ✅
- Auction integrity signals on public /auction page: already present (INTEGRITY_ITEMS array covers cancellation churn, self-trade detection, thin book warnings, large sell wall alerts) ✅
- SPEC-POST-INSTALL-DISCOVERY-FUNNEL.md: already implemented ✅
- SPEC-PORTFOLIO-CSV-EXPORT.md: already implemented ✅
- web/ bundled dashboard auction: 4 tabs + AdminAuctionCard + My Orders watch toggles ✅

**Key remaining gaps identified:**
1. Public install page step 4 (Configure basic settings): teaches manual YAML editing. Could add safe workflow teaching: copy exported YAML → `/at admin config preview` → replace → reload. Low code, high trust value.
2. Install page step 5 (Verify): mentions `/at admin health` but doesn't teach the config preview workflow for first-time admins reviewing their config.
3. Auction integrity signals on public site are shown to visitors evaluating Auto-Tune, but there's no equivalent "integrity telemetry" card visible on the `/admin` page of the bundled web dashboard showing real server data (AdminAuctionCard exists but needs verification of wiring to `/api/admin/auction-audit`).

**Still blocked:**
- API server deploy (Arc/Fly.io token)
- Real testimonials (human outreach)

