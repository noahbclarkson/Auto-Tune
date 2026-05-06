# PLAN.md — Anvil's Work Plan

_Living document. Update after every session. Prioritize ruthlessly._

**Branch:** `rewrite-2` (do NOT merge to master)

---

## Cron (2026-05-04 19:03 UTC) — API Docs Stale Auth Header + Rate Limits Fixed ✅

**rewrite-2 at `77c3e9d`** | `./gradlew build` ✅ PMD 0 | `web/` 14 routes ✅ | `web-optimizer/` 22 routes ✅ | Pushed ✅

**Bug fixed: `web-optimizer/src/app/api-docs/page.tsx` had 3 stale doc issues:**
1. **Wrong auth header** in 4 places: said `X-API-Key` but API server uses `Authorization: Bearer <api-key>` (confirmed in `api-server/src/auth.rs`)
2. **Wrong rate limits**: said "1 req/tick per server" and "1/IP/hour" — actual limits: submit=6 req/min/IP, registry=10 req/min/IP (from `rate_limit.rs`)
3. **Minecraft jargon**: heartbeat said "once per tick" — removed

**Build verification (all clean):** Gradle PMD 0, both web TypeScript checks, api-server clippy, price-solver clippy, market-sim check

**Code audit (no new bugs found):** WebServer error handling solid, phantom transaction fix confirmed, offline payment fix confirmed, AuctionRepository SQLite date fix confirmed, MarketEngine division guards confirmed, no TODO/FIXMEs

**Docs drift risk identified:** `docs/API.md` and `docs/SECURITY.md` were correctly updated in a prior session, but `api-docs/page.tsx` was missed. Pattern: when Rust API server changes, the web-optimizer's human-readable page may not sync.

**PMD suppression audit:** 69/124 Java files still have blanket `@SuppressWarnings("PMD")` — deferred (high-value but tedious). Recommended: one large file per session.

**Next priorities:**
1. PMD targeted suppression cleanup — start with `MarketEngine.java` (802 lines, critical engine file)
2. Add docs sync reminder to API server README
3. Check remaining web-optimizer docs pages for API consistency

---

## Cron (2026-05-04 12:27 UTC) — Bug Hunting & Repo Health ✅

**rewrite-2 at `df0b815`** | `./gradlew build` ✅ PMD 0 | `./gradlew test` ✅ | Pushed ✅

**Strategic redirect:** Noah directed bugs + repo health over features. Updated MEMORY.md.

**Bugs fixed:**
- Partial fill notification showed pre-fill remaining qty instead of post-fill (`3c9d016`)
- Offline sellers never received payment in both matching engine and GUI paths (`3c9d016`)
- Buy orders from offline buyers matched → items lost; added online-player filter before matching (`3c9d016`)
- Expired buy order refunds silently dropped when owner offline (`adcacde`)

**Repo health:**
- Removed duplicate imports (AuctionManager, ConfigManager, EnchantmentPricing)
- Upgraded sqlx 0.7 → 0.8 in API server, resolved future-incompat warning (`bfd38ee`)
- Both TS projects pass `tsc --noEmit` with zero errors, no `any` types
- All Rust crates clippy clean

**Noted for future sessions:**
- 50/117 Java files have blanket `@SuppressWarnings("PMD")` — needs targeted cleanup
- Buy/sell async methods record market engine side effects before economy withdrawal
- Transaction ordering in EconomyManager could cause phantom price movements in rare races

---

## Cron (2026-05-04 07:21 UTC) — Web & Ecosystem Audit ✅

**rewrite-2 at `db2b67c`** | `web-optimizer/` build ✅ | `web/` build ✅ | Pushed ✅

**Work:** Auction House Guide standalone doc + docs index audit + ecosystem feature ideation.

**Built:**
- `docs/AUCTION_HOUSE_GUIDE.md` — 8-command player reference, web dashboard tabs, depth chart reading, admin monitoring, integrity patterns, config section, matching engine explanation, watch/cross-server scope
- Updated `web-optimizer/src/app/docs/page.tsx` — auction guide card points to standalone doc, `/auction` added to quick links, description updated with depth chart mention

**Commit:** `db2b67c feat(docs): add standalone Auction House Guide with depth chart and integrity sections`

**Ecosystem audit finding:** auction ecosystem is now complete across both surfaces (bundled `web/` and public `web-optimizer/`). All coherent. No orphan gaps.

**Feature ideas generated** (prioritized for Plugin Engineer and Sim Lab cycles):

HIGH PRIORITY — Adoption blockers:
1. API server deploy — live true-prices, server count, activity feed
2. Real testimonials via Discord outreach to actual server admins

MEDIUM PRIORITY — Web & docs:
1. StabilityPreview component for `/setup`: show 5-metric preview from actual sim output. Needs live sim runner or pre-computed scenarios. Turns `/setup` into "test your economy before installing."
2. Setup Wizard → config preview inline hint: setup already teaches `config preview` in ConfigExport step. Could surface "preview before replace" nudge more prominently.
3. True-prices confidence copy: distinguish low server count / stale data / outlier-suppressed consensus rather than one generic number.
4. Player weekly market recap page: best trade, biggest mover, watched orders filled, materials you traded, server-wide hot market stories.

MEDIUM PRIORITY — Plugin features:
1. Auction opportunity hints panel: compare auction best bid/ask to live shop buy/sell. "Likely arbitrage" phrasing with risk labels — careful wording to avoid exploit loops.
2. Config diff surface (web admin): economy update interval, spread/slippage, auction fees/limits, price reporter settings. Warn when storage/API credentials change.
3. Auction order short aliases: `/auction i <id>`, `/auction w <id>`, `/auction c <id>` — reduce UUID friction for players who type commands.
4. Loan maturity extension: architectural debt-stock fix — loans don't last forever, partial deleveraging trigger when overdue.

LOWER PRIORITY — Sim Lab:
1. Auction LOB stress model: thin-book spoofing, cancellation storms, whale sell walls. Rust has no LOB model yet — manual test plan or minimal Rust LOB implementation.
2. GuildBuyer debt cap sweep: cap × [2×/3×/5× GDP] × 60d — does tighter cap reduce D/G at long run?
3. Floor × D/G long-run: 60% floor vs no floor × 90d × 3 seeds — does floor reduce final D/G or just mask it?

---

## Cron (2026-05-04 07:07 UTC) — Auction Owner Fill Notifications ✅

**rewrite-2 at `92d056c`** | `./gradlew build` ✅ PMD 0 | `./gradlew test` ✅ | Pushed ✅

**Bug fixed: Order owners received no in-game feedback on partial fills or buy-side fills.**
`AuctionManager.processFill()` only sent a gold seller message on full fill; buy order owners received nothing. No partial fill notifications existed.

**Fix:** Added `notifyOwnerOfFullFill()`, `notifyOwnerOfPartialFill()`, and `sendOwnerMessage()` helpers wired into both `processFill()` (matching engine path) and `recordFillAsync()` (GUI path):
- Full fill → ⚡ message to order owner (both buy + sell sides)
- Partial fill → 📦 message with qty, price, remaining amount
- `sendOwnerMessage()` deduplicates online/offline → `PendingNotificationRepository` for offline players

**State:** CI 3/3 green (PR #252), no open issues, API deploy blocked on Arc's Fly.io token, regression suite 6/6 scenarios.

---

## Simulation Lab (2026-05-04 01:48 UTC) — Regression PASS, Floor Paradox Confirmed, Admin Recovery Optimal

**rewrite-2 at `201aa21`** | Regression 6/6 PASS ✅ | Zero drift | All builds clean

**Regression Suite:** ALL PASS (6/6 scenarios, 0.000% displacement)
- Standard Economy ✅ | Spread Stability ✅ | Low Player Count ✅
- Standard+MM Economy ✅ | GuildStability+MM+7%GB ✅ | GuildStability+2MM+7%GB+Floor ✅

**Floor Paradox — Fully Characterized:**
| Floor | 14d GDP | 14d D/G | Verdict |
|-------|---------|---------|---------|
| 30% | flat | flat | Non-binding (internal $155 > $150) |
| **60%** | **+29.2%** | **0.71x** | **OPTIMAL — production default** |
| 70% | +9.1% | 0.84x | Degrades vs 60% |
| 90% | -14.7% | 1.09x | Catastrophic — internal Diamond $0.40 |

- Multi-seed (5 seeds, 1MM+2GB): 60% floor → **+4.2% GDP avg, floor binds 5/5**, D/G -0.10x

**Admin Recovery Timing:** Day 3 optimal (D/G 0.72x, saves 19.4% defaults vs natural). Day 7+ no benefit.

**Production Config (2MM+2GB+60% floor):** +89% GDP, -25% BPD, 74% buy ratio (balanced). Confirmed.

**tier3_ratio=40 CONFIRMED WORSE:** Prevents circuit firing → debt accumulates unchecked. D/G 17.1x vs 15.1x control (seed 42). Not the lever.

**Next sim priorities:** GuildBuyer debt cap sweep, floor × D/G long-run (90d), auction LOB stress model.

---

## Cron (2026-05-04 02:18 UTC) — Auction Depth Chart Added, Builds Clean ✅

**rewrite-2 at `9739783`** | `./gradlew build` ✅ PMD 0 | `web/` 14 routes ✅ | `web-optimizer/` 22 routes ✅ | API-server Rust 17/17 ✅ | Pushed ✅

**Work: SVG depth chart added to public `/auction` page**
- Standalone SVG `DepthChart` component (`web-optimizer/src/components/auction/depth-chart.tsx`) — no external deps, works with Next.js `output: 'export'`
- Cumulative bid/ask depth as step-chart area (bid=green, ask=rose)
- Wired into `/auction` page between order book demo and "how it works" section
- 3 callout cards: bid walls / ask walls / thin books

**Ecosystem observations:**
- Install page comprehensive: comparison table, 5 install steps, player flow cards, hosting guide, embeddable widget section
- Testimonials (Alex K., Dana W., Marcus T.) remain fictional — highest ROI non-code gap
- API server deploy blocked on Arc's Fly.io token
- Auction ecosystem now complete on both bundled (`web/`) and public (`web-optimizer/`) surfaces

**Still blocked:** API server deploy (Arc/Fly.io token), real testimonials

---

## Cron (2026-05-04 01:48 UTC) — Simulation Lab Regression + Floor Paradox + Admin Recovery Timing ✅

**rewrite-2 at `201aa21`** | `./gradlew build` ✅ PMD 0 | Regression 6/6 PASS | Pushed

**Floor × Long-Run sweep findings (14d, partial):**
- 60% floor = optimal sweet spot at 14d: +29.2% GDP, floor binds, D/G 0.71x
- ⚠️ **Overturned by 90d sweep (2026-05-06):** 60% floor → GDP -19.1%, D/G +1.6x worse vs no floor at 90d. Floor is short-term only. See 2026-05-06 PLAN entry for full correction.

**Admin Recovery Timing:** Day 3 is optimal (D/G 0.72x, saves 19.4% defaults vs natural).

**GuildBuyer archetype analysis:** 2MM+2GB+7%GB+60%floor is confirmed production default (+89% GDP).

**Next simulation priorities:** GuildBuyer debt cap sweep, floor × D/G long-run (90d), auction LOB stress model.

---

## Web Update (2026-05-01 19:35 UTC) — Install Page Auction Mockup ✅

**rewrite-2 at `2d43726`** | `web-optimizer/` build ✅ (22 routes) | Pushed ✅

**Install page — What You Get screenshots expanded from 3 → 4:**
- Added `AuctionMockup` component: live order book with bid/ask rows, spread indicator, fill status
- New card: `Icon: Gavel`, title "P2P Auction House", tag "Beyond /shop"
- Headline: "Three screens" → "Four screens. Zero configuration required."
- Committed `2d43726` → pushed.

**Ecosystem coherence observations:**
- `web/` bundled dashboard fully covers auction (4 tabs: Orders / Fills / Materials / Depth Chart) + `AdminAuctionCard` on `/admin`
- `SERVER_ADMIN_GUIDE.md` auction section (lines 377-386): commands documented but brief — no screenshots, no strategy tips
- Auction is the key differentiator from basic /shop plugins — should be prominent everywhere

---

## Plugin Update (2026-05-01 07:40 UTC) — Auction Admin Integrity Audit ✅

**rewrite-2 at `f9cb469`** | `AuctionRepositoryTest` ✅ | `./gradlew build` ✅ | Pushed ✅

**Built:** `/at admin auction` in-game integrity audit, `/at admin audit` Auction Integrity section, `GET /api/admin/auction-audit?days=N`, `AuctionRepository` audit queries.

**Product value:** admins can detect auction manipulation risk directly: cancellation spoofing, thin books, whale sell walls, and impossible self-trades.

**Next best plugin/admin work:**
1. Add an Auction Integrity card to bundled `web/` `/admin` using `/api/admin/auction-audit`.
2. Add configurable integrity thresholds once real server data indicates good defaults.
3. Add Rust sim auction manipulation scenarios: cancellation storms, thin-book probes, large sell walls.

---

## Web & Ecosystem Update (2026-05-01 02:15 UTC) — Config Dry-Run Preview + Ecosystem Audit ✅

**rewrite-2** | `./gradlew build` ✅ | bundled `web/` export ✅ via Gradle

**Built:** `/at admin config preview <filename>` — admins dry-run a candidate config file before replacing live `config.yml`.

**Product value:** Auto-Tune config now has a safer admin workflow for testing solvency-sensitive YAML changes.

**Ecosystem observations / prioritized ideas:**
1. **Plugin Engineer:** expand config preview into a full config diff surface: economy update interval, spread/slippage, auction fees/limits, price reporter settings, and warnings for changed storage/API credentials.
2. **Web/Public setup:** when the setup wizard exports config.yml, teach the safe workflow: copy to plugin folder → `/at admin config preview exported.yml` → replace `config.yml` → `/at admin reload`.
3. **Bundled dashboard:** add auction integrity/audit cards for cancellation churn, thin books, large sell walls, suspicious self-trade/fill patterns, and material-level liquidity risk.
4. **Player web delight:** add weekly market recap/player digest pages: best trade, biggest mover, watched orders filled, materials the player influenced, and server-wide "hot market" stories.
5. **API/server trust:** implement freshness filtering, plugin/protocol version metadata, key rotation/revocation, capped player-count weighting, and explanatory confidence labels before public true-price launch.
6. **Sim Lab:** model auction manipulation and cross-server manipulation: thin-book spoofing, cancellation storms, whale sell walls, one fake high-player server, many Sybils, and clustered outlier submissions.

---

## Plugin Update (2026-05-01 01:45 UTC) — Auction Fill-Rate SQLite Regression Fix ✅

**rewrite-2** | `./gradlew test --tests com.noahblclarkson.autotune.database.AuctionRepositoryTest` ✅ | `./gradlew build` ✅

**Fixed:** `DatabaseManager.isSqlite()` and made `AuctionRepository.findFillsByDay()` choose the right date expression per storage backend: SQLite uses `DATE(filled_at / 1000, 'unixepoch')`; MySQL/MariaDB keep `DATE(filled_at)`. Zero-fill day padding so sparklines show inactivity gaps honestly.

**Product value:** bundled `/auction` Total Fills sparkline now works correctly on the default SQLite path.

---

## Web & Ecosystem Update (2026-04-30 19:55 UTC) — Cross-Server Security Trust Docs + Rate-Limit Hardening ✅

**rewrite-2 at `c70293d`** | `cd web-optimizer && npm run build` ✅ | `cargo fmt --check` ✅ | Pushed ✅

**Built:** `docs/SECURITY.md` — dedicated cross-server API security/trust model covering Bearer auth, hashed server keys, server-ID path binding, write rate limits, matrix validation, log-space outlier filtering, current player-count weighting, data boundaries, exchange-rate abuse prevention.

**Hardened:** API-server token bucket defaults: registration 10 req/min per IP, price submission 6 req/min per IP.

---

## Simulation Lab Update (2026-04-30 16:30 UTC) — Deep Hysteresis Partial Run

**rewrite-2 at `c7fd8d1`** | Regression 6/6 PASS ✅ | `cargo clippy -- -D warnings` ✅

**Findings:** 80% hysteresis + 1%/30-day exit cap was identical to 80% hysteresis alone. Deeper hysteresis is an oscillation dampener, not a debt-stock fix.

**Next best Simulation Lab work:**
1. Add/resume-safe `--tier3-40-hysteresis-test`: tier3=30/hyst=0.5 vs tier3=40/hyst=0.5 over 90 days, 3 seeds.
2. If tier3=40 is also marginal, stop tuning circuit thresholds and prioritize architectural debt-stock fixes: forced partial deleveraging, loan maturity extension, or GDP-linked new-debt cap.

---

## Web Update (2026-04-30 14:10 UTC) — Auction My Orders Watch Toggles ✅

**rewrite-2 at `c7fd8d1`** | `cd web && npm run build` ✅ | Pushed ✅

**Built:** Native Watch/Unwatch controls in bundled `web/` `/auction` → My Orders rows. Fixed `api.auction.watch()` bug: now POSTs correctly instead of GETting status endpoint.

**Product value:** players manage auction fill alerts from the order list; native watch persistence works end-to-end from web to plugin DB.

---

## Web Update (2026-04-30 13:24 UTC) — Circuit Event Admin Guidance ✅

**rewrite-2 at `7c49e93` (+ `08c3ed7`)** | `cd web && npm run build` ✅ | `./gradlew build` ✅ | CI green ✅ | Pushed ✅

**Built:** Upgraded `/economy` circuit event chips into actionable admin guidance cards. State transition, date, D/G ratio, interest multiplier, severity color, hover detail with GDP/debt, and guidance copy.

**Product value:** admins no longer have to infer what a circuit transition means. Dashboard turns raw tier transitions into "what should I do next?" guidance.

---

## Plugin Update (2026-04-30 06:55 UTC) — In-Game Auction Order Info ✅

**rewrite-2 at `b946154`** | `./gradlew build` ✅ | CI 3/3 green ✅ | Pushed ✅

**Built:** `/auction info <order-id>` for in-game order inspection. Shows side/status/material/price/fill progress, timestamps, recent fill history. Full order IDs are clickable/copyable.

**Next best plugin/web work:**
1. ~~Web — Native-aware auction watch UX~~ — ✅ DONE (c7ac047)
2. Web — Circuit event action copy: richer event chips with D/G, multiplier, and admin guidance.
3. Sim Lab — Add `guild_stability_2mm_fixed_guild_plus_floor` to regression suite.
4. API/Security — publish server-key trust/rate-limit/outlier model before live true-price launch.

---

## Web & Ecosystem Update (2026-04-30 07:29 UTC) — Native Auction Watch ✅

**rewrite-2 at `c7ac047`** | `cd web && npm run build` ✅ | `./gradlew build` ✅ | Pushed ✅

**Built:** PlayerIdentityStrip component, AppContext extended with `playerName`, API client extended with `auction.watch/unwatch/status`. `/auction/order` now calls native POST/DELETE watch endpoints when playerName is known.

**Product value:** auction watch notifications now reach players in-game even when offline or browser is closed.

---

## Web & Ecosystem Update (2026-04-30 02:49 UTC) — Auction Discovery + Ecosystem Audit ✅

**rewrite-2 at `04ed24e`** | `cd web && npm run build` ✅ | Pushed ✅

**Built:** First-visit Auction House discovery hints via existing `DiscoveryOverlay` system. Auction tips explain player limit orders, order detail/fill history, depth chart risk for thin books, and native `/auction watch` notifications.

**Ecosystem observations:**
- Auto-Tune's player-facing differentiator is now the Auction House plus dashboard, not only dynamic shop prices.
- Server-health transparency is now much stronger after `at_circuit_events`; next web polish should turn raw tier transitions into admin-action language.
- Strongest remaining adoption blockers are non-code: live API deploy URL and real testimonials.

---

## Plugin Update (2026-04-30 02:15 UTC) — Circuit Event Timeline ✅

**rewrite-2 at `38e68c7`** | `./gradlew build` ✅ | CI 3/3 green ✅ | Pushed ✅

**Built:** Durable circuit-breaker/admin-recovery transition history via `at_circuit_events` (`V7__Circuit_Events.sql`). Added `CircuitEvent` model + `CircuitEventRepository`. Added `GET /api/economy/circuit-events?limit=N`. Updated bundled `web/` `/economy` chart with colored circuit-event annotations.

**Product value:** admins can see when safeguards engaged/cleared directly on economy history instead of inferring from raw Debt/GDP.

---

## ⚠️ MANAGER DIRECTIVE (2026-04-18 19:27 UTC)

**Stop drifting into docs/changelog work unless it directly unblocks adoption.**

**Focus only on:**
1. Deployment/adoption path — get the API server deployable
2. Architectural 60d fix path — confirm the simulation-verified fix, update configs, and document clearly

**Concrete outputs required:**
- ✅ Deployability checklist (`docs/DEPLOYABILITY.md`)
- ✅ Minimal deployment plan (`docs/DEPLOYMENT.md`)
- ✅ 60d fix analysis (`docs/60D_FIX_ANALYSIS.md`) — ALL 8 fix candidates FAILED
- ✅ 90-day findings: circuit CONTAINED not catastrophic — no escalation needed
- ⏳ API server first deploy (Fly.io token needed)

---

## Simulation Lab Update (2026-04-29 16:30 UTC) — Regression PASS, Correlation Sweep NEUTRAL, Auction Module Audit

**rewrite-2 at `be7024f`**

**Regression suite (5 scenarios):** ALL PASSED — 0.000% price displacement, 0.00000 BPD/SPD delta
**Sector correlation sweep:** signal is noise at current param values; do NOT increase default from 0.05 without full guild_stability sweep
**Auction module Java audit:** `AuctionMatchingEngine` (144 LOC) is a real price-time priority limit order book. No Rust equivalent — auction stress scenarios cannot be run in simulation without building a Rust LOB model.

---

## Simulation Lab (2026-04-28 08:49 UTC) — Engine Parity Audit: Graduated TIER3 Exit Cap COMPLETE ✅

**rewrite-2 at `ed5e47b`** | `./gradlew build` ✅ | PMD 0 ✅ | Pushed ✅

**Java/Rust engine parity — GRADUATED TIER3 EXIT CAP:**
Both CC and legacy exit paths now preserve computed counter-cyclical taper, apply `min(computed, tier3ExitMultiplierCap)` during delay window, and start delay window on hysteresis unlock tick.

**New config fields:**
- `tier3-exit-multiplier-cap`: 0.10 (10% cap during delay) — Rust parity
- `tier3-exit-delay-ticks`: 1152 (4 days at 288 ticks/day) — Rust parity

**90d test results (TIER3→NORMAL bypass, 3 seeds × 2 thresholds):**
- 5% threshold avg: D/G=20.7x, GDP=3.59M, vol=0.026 — 🟠 HIGH RISK
- 7% threshold avg: D/G=26.7x, GDP=3.49M, vol=0.029 — 🟠 HIGH RISK
- **5% CONFIRMED as production default** (D/G 6x better than 7% at 90d)

**180d test results (post-fix, 2 seeds × 5%):**
- Seed 42: 8.4x (14d) → 15.1x (90d) → 29.1x (180d) — escalation persists
- **Bypass reduced 180d D/G from 42x→29x (seed 42)** — meaningful but insufficient

**VERDICT:** The sweep is no longer the main blocker. The real blocker is **engine parity**. Low Player Count regression currently passing (5/5 suite clean). Graduated exit cap sweep (9 arms) confirmed all combos neutral (-3.3% to -4.0%). Not the missing lever.

**Next logical plugin step:** add a bundled `web/` auction route using the existing endpoints.

---

## 60d Architectural Fix State

**TIER3→NORMAL BYPASS IMPLEMENTED (2026-04-27):** Java (06b0207) + Rust (78034a7)

| Fix | Evidence | Status |
|-----|----------|--------|
| ALL 7 prior fixes | All tested 5-seed × 60d | ❌ ALL FAIL |
| `tier3=100` alone | `--sixty-day-tier3-sweep`: D/G +2.3x WORSE | ❌ |
| `tier3=100` + loan-lock combo | `--sixty-day-combo-test`: D/G +2.3x WORSE | ❌ |
| Loan-lock alone | `--sixty-day-loan-lock-test`: D/G delta ~0 | ⚠️ Neutral |
| **TIER3→NORMAL bypass** | `--ninety-day-test` (post-fix): D/G 15.1x (90d, seed 42) | ✅ Partial |
| **TIER3→NORMAL bypass** | `--one-eighty-day-test` (post-fix): D/G 29.1x (180d, seed 42) vs 42.0x pre-fix | ⚠️ Better, persists |

**Root cause:** Debt compounds ~10%/day, GDP grows ~1%/day. Circuit is symptom observer, not cure.

**Still blocked:** API server deploy (Arc's Fly.io token), real testimonials (Discord DM).

---

## What's Needed Next (2026-04-18 directive follow-up)

- [x] ~~Fix config.yml tier3=30.0~~ — ✅ DONE (ffd36c5)
- [x] ~~Add advanced loan params to config.yml~~ — ✅ DONE (ffd36c5)
- [x] ~~Add ConfigValidator checks for new params~~ — ✅ DONE (840312d)
- [x] ~~Config validator coverage audit~~ — ✅ DONE
- [x] ~~Docs field name sync~~ — ✅ DONE (4222f3f)
- [ ] ~~API server deployment~~ — BLOCKED on Arc's Fly.io token
- [ ] ~~Real testimonials via Discord outreach~~ — needs human action

## Cron (2026-05-05 01:41 UTC) — GB Debt Cap NOT FIXED + Floor Paradox Amplified ⚠️

**rewrite-2 at `7874611`** (no new commits — findings only) | `./gradlew build` ✅ PMD 0 | Regression 6/6 PASS ✅ | Pushed ✅

### Simulation Results

**GB Debt Cap (3× GDP) — NOT FIXED:**
- 60-day × 2 seeds: D/G 17.8x → 17.8x (delta +0.000x)
- TIER3 events identical (12 vs 12)
- Cap=3× GDP is non-binding at 60d. GB loans don't accumulate to that threshold in simulation window.
- **Tighter cap needed** (1-2× GDP) to actually constrain borrowing.

**Stressed Economy 30-Day Floor Paradox — AMPLIFIED:**
- `--stressed-30d-floor-test` (seed=42, chronic oversupply)
- Control GDP=7,562 vs Treatment GDP=**0** (-100.0%)
- Floor kills trade under sustained stress — prevents natural price correction
- D/G: 930x (ctrl) vs **4,986,466x** (floor) — paradox amplified dramatically
- **Floor is a structural liability under chronic stress**, not just a price mask
- TIER3 oscillates more violently with floor active (8 vs 6 events)

**Production Config Multi-Seed (5 seeds, 14d) — CONFIRMED:**
- 2MM+2GB+floor vs 1MM+2GB
- GDP **+88.9%**, floor binds **5/5 seeds**, BPD -24.7%
- 2MM+60% floor remains RECOMMENDED production default

### Key Insight: Floor Paradox Is Structural at 30d+

At 14d: floor paradox exists but economy survives.
At 30d (chronic stress): floor kills GDP entirely, D/G becomes catastrophic.
At 90d: partial run shows floor/no-floor identical D/G at 15.1x — floor neither helps nor hurts D/G long-run.
**Conclusion: Floor stabilizes prices but does NOT contain debt accumulation. It's a cosmetic stabilizer, not an economic cure.**

### Repo Health Notes
- 67/124 Java files have class-level `@SuppressWarnings("PMD")` — makes PMD useless
- PMD targeted suppression cleanup still pending (high-value but tedious)
- No runtime bugs found requiring immediate fix
- API deploy BLOCKED (Arc's Fly.io token)

### Next Simulation Priorities
1. **Tighter GB debt cap sweep**: cap × [0.5×/1×/2× GDP] × 2 seeds × 60d
2. **Loan maturity test**: add expiration to new loans, force rollover — does this reduce debt accumulation?
3. **Archetype gap**: Newbie archetype replacing GBs — unanswered question from prior session
4. **Resume-safe 90d floor test**: use `--output` persistence so SIGKILL doesn't lose data


## Cron (2026-05-05 19:15 UTC) — Docs Drift: Stale Circuit Breaker Thresholds Fixed ✅

**rewrite-2 at `9efe654`** | `./gradlew build` ✅ PMD 0 | `web-optimizer/` build ✅ (22 routes) | Pushed ✅

**Focus:** Noah's redirect — bug/repo-health pass. Docs drift audit for stale circuit-breaker threshold references.

### Bugs Found and Fixed

5 docs files had stale references to the circuit breaker firing at "10×" D/G when the actual default is `debt-gdp-tier3-ratio: 30.0`.

**Files fixed (5):**
- `docs/SERVER_ADMIN_GUIDE.md` — 3 stale 10× references corrected; thin-book "configurable threshold" removed (hardcoded: <2 bid or <2 ask orders); `/auction buy <order-id>` → `/auction buy <material> <price> [qty]`
- `docs/DASHBOARD_API.md` — `circuitBreakerTier` TIER3 label (>10×) → (>30×)
- `docs/MIGRATION.md` — "Pauses interest if debt/GDP > 10×" → "> 30× (default `debt-gdp-tier3-ratio: 30.0`)"
- `docs/ARCHITECTURE.md` — "debt / GDP > 10.0" → "> 30.0"
- `docs/QUICKSTART.md` — stale "TIER3 ratio: Set to 15 (not 10)" recommendation → current default 30 with correct hysteresis unlock math

### Root Cause
tier3_ratio default was 10 → 15 → 30 over multiple commits; these doc files weren't all updated in the same commits. This is the same docs-drift pattern seen in prior sessions (API.md X-API-Key → Bearer).

### Pattern Risk
When changing config defaults, ALL docs referencing that config key must be updated in the same commit. Consider a CI check that flags doc files containing config key names when those keys are changed.

### Verification
- `./gradlew build` ✅ PMD 0
- `cd web-optimizer && npm run build` ✅ (22 routes)
- Grep for remaining stale 10× circuit references: all remaining hits are legitimate (10%/day debt growth, 10% hysteresis band, historical changelog entries, FAQ day-10 recovery timing)
- Builds all clean

### Next Priorities
1. Continue docs drift audit — check CONFIG_GUIDE.md, FAQ.md, and any remaining .md files for other stale references
2. API deploy remains #1 external blocker (Arc/Fly.io token)
3. Real testimonials remain #2 external blocker (human outreach)

## Cron (2026-05-06 01:51 UTC) — Simulation Lab: Regression Baselines Refreshed + 90d Floor Warning ⚠️

**Commit:** `test(simulation): refresh baselines after loan cap parity` → pushed to `rewrite-2` | Rust sim regression 6/6 ✅ | `cargo test` 11/11 ✅ | clippy ✅ | fmt ✅

**Focus:** Rust market-simulation repo health and long-horizon engine insight after Java-parity loan cap changes.

### Regression Baselines Refreshed
`cargo run --quiet -- --regression` initially flagged drift in 3/6 scenarios:
- Standard Economy
- Spread Stability Test
- Standard+MM Economy

This was expected drift from `d82cd39 fix(simulation): align loan caps with Java GDP rules`. The stored baselines were still from `c7ac0470`, before rolling-24h GDP loan caps and economy-wide debt cap enforcement. Refreshed all six baselines against the corrected engine.

Verification:
- `cargo run --quiet -- --regression --update && cargo run --quiet -- --regression` ✅ — 6/6 pass
- `cargo test -q` ✅ — 11/11 pass
- `cargo clippy -- -D warnings` ✅
- `cargo fmt -- --check` ✅

### 90-Day Floor Comparison — 60% Floor Looks Structurally Bad
Ran `cargo run --release -- --floor-90d-compare`:
- Scenario: 2MM + 2GB + 3Cas + 3Far + 2Tra, 5% GB threshold
- Horizon: 90 days
- Seeds: 42, 12345, 98765

| Arm | Avg GDP | Avg D/G | Vol(CV) | Diamond internal |
| --- | ---: | ---: | ---: | ---: |
| 60% Diamond floor | 1.532M | 14.639x | 0.0724 | $317.57 |
| No floor | 1.893M | 13.019x | 0.0442 | $437.46 |

**Delta:** floor worsened D/G by `+1.620x` and reduced GDP by `-19.1%`.

Seed detail:
- 42: floor `17.804x` vs no-floor `11.985x` — floor much worse
- 12345: floor `11.384x` vs no-floor `12.737x` — floor modestly better
- 98765: floor `14.728x` vs no-floor `14.335x` — floor slightly worse

### Engine Insight
The 60% Diamond floor is **not** a structural solvency fix. At 90d it mostly masks price weakness and can worsen GDP + D/G. It may still be acceptable as a player-facing UX guardrail, but it should not be marketed or configured as an economy-health lever.

This partially overturns the earlier 14d conclusion that 60% floor was the production sweet spot. Short-horizon stability was misleading; long-horizon D/G says the floor can become harmful.

### Loan / GuildBuyer Sanity Checks
- `--loan-cap-test` ✅ — 4 capped loans, raw `$110,341` → capped `$15,976` (85.5% reduction), issued total down 76.4%.
- `--guildbuyer-failure-test` ✅ — 7-day cooldown still reduces D/G `1.986x` → `0.916x` (53.8%).

### Next Priorities
1. Run 5-seed long-horizon floor-strength sweep: no floor vs 30% vs 45% vs 60% over 60–90d.
2. Reconsider production default: no floor or lower floor may be healthier than 60% at 90d.
3. Document floors as UX guardrails, not debt/GDP stabilizers.
4. Audit Java/Rust GuildBuyer cap exact parity: Rust partial-fills remaining allowance; Java rejects the whole request.

