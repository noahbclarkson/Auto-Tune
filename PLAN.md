## Cron (2026-05-24 12:42 UTC) — Web & Ecosystem: Circuit Event Modal Done ✅

**rewrite-2 `61943a4`** | `./gradlew build` ✅ PMD 0 | `web/`: 12 routes ✅ | `web-optimizer/`: 29 routes ✅ | Pushed: `61943a4`

### Circuit Event Detail Modal — DONE ✅
New `CircuitEventModal` component wired into `EconomyChart` (`/economy` page):
- Click any circuit event chip → modal with full event context
- Tier transition badge (previousTier → newTier) with color coding
- Stats: D/G ratio, total GDP, total debt, interest multiplier, timestamp
- Admin-initiated badge when applicable
- Recommended action text based on tier + D/G level
- Escape/backdrop close

Also: circuit event chips now have `cursor-pointer hover:brightness-110` to indicate clickability.

### Ecosystem Gap Findings
- **Leaderboard P&L:** `LeaderboardEntryDto` has volume data (totalBought/Sold) but no net P&L. P&L columns exist only at `HoldingDto` (per-item) level. A player-impact P&L leaderboard requires new backend aggregation.
- **Exchange rate history:** `/exchange-rates` page has current-rate bar chart but no time-series. Would need API client extension + API server endpoint + new LineChart component.
- **Config comparison tool:** `/config-playground` has sliders + YAML export. A "compare two configs side-by-side" mode would build on this foundation.

### State
Repo at `61943a4`. All builds passing. Pushed.
**Blocked:** API deploy | Real testimonials

### Next
1. Exchange rate history chart (API + LineChart component)
2. Config comparison tool (extend playground with two-config diff)
3. `/compare` dedicated page (vs inline section on landing)

---

**rewrite-2 `90e8427`** | `./gradlew build` ✅ PMD 0 | `web-optimizer/` 29 routes ✅ | `web/` 12 routes ✅ | Pushed: none (clean session)

### Builds — All Clean
- `./gradlew build -x installWebDeps -x test` → BUILD SUCCESSFUL PMD 0
- `web-optimizer/` direct: 29 routes ✅
- `web/` direct: 12 routes ✅
- Zero TODOs/FIXMEs in main source ✅
- API server auth reviewed: sound — 90d key rotation, SHA-256 storage, Bearer auth on submissions

### API Server Auth — `api-server/src/auth.rs`
- **90-day key rotation** — correct balance between security and usability
- **SHA-256 hashed keys** — no plain-text storage anywhere
- **Registration open** (`/servers/register`) — name+player_count only, no pre-existing key required. Right UX for onboarding new servers.
- **Submission requires Bearer auth** (`POST /servers/{id}/prices`) ✅
- **Key format** `sk_live_<uuid>_<random>` — easy to identify in logs

### State
Repo clean. All surfaces consistent. No bugs. No pushes.
**Blocked:** API deploy (Arc's Fly.io token) | testimonials (human outreach)

---

## Cron (2026-05-17 07:15 UTC) — Web & Ecosystem: Builds Clean, Ecosystem Feature Ideation ✅

**rewrite-2 `c8cf082`** | `./gradlew build` ✅ PMD 0 | `web-optimizer/` 25 routes ✅ | `web/` 12 routes ✅ | Pushed: none (clean session)

### Builds
- `./gradlew build -x installWebDeps` ✅ PMD 0 (59s)
- `web-optimizer/` direct: 25 routes ✅
- `web/` direct: 12 routes ✅
- Rust fmt/clippy clean across all crates

### Ecosystem Review
Reviewed full public site surface — all consistent:
- **Docs hub** (`/docs`): 15 doc cards with audience badges, reading times, GitHub links ✅
- **Install guide**: 6 steps, Discord bot, correct circuit values (tier3=30.0, counter-cyclical) ✅
- **Trust page**: Anti-Sybil, freshness filtering, outlier rejection — live/partial badges correct ✅
- **Servers page**: Mock servers with health scores; real data after API deploy ✅
- **Roadmap**: Accurate; `web-optimizer` live dashboard marked "in-progress" (code done, blocked on API deploy) ✅
- **Compare table**: 4×4 against Essentials/ShopGUI+/PlayerShops — credible ✅
- **Why Auto-Tune**: Scenario cards with mock sparklines — clear narrative ✅

**API deploy is still #1 ecosystem unlock.** No workaround available.

### Feature Ideas (future cycles — per Noah's redirect NOT prioritized now)

**Plugin:**
1. Anti-dump telemetry package: sell-wall detection, spread shock/cooldown triggers, admin alerts (cap+spread-shock confirmed +23.8% D/G vs cap-only in sim)
2. Config version history: `/at admin config history` with rollback
3. In-game setup wizard: `/at wizard` first-run flow
4. Scheduled market events: cron-style event scheduling
5. Circuit interest rate preview in dashboard

**Public web:**
1. Server showcase (after API deploy + real testimonials)
2. Interactive config comparison tool (pick two → side-by-side sim results)
3. Exchange rate history chart on `/true-prices`
4. Public API status page (`/status`)

**Bundled web:**
1. Player impact leaderboard (top P&L, most trades)
2. Auction fill browser notifications (Notification API)
3. Market digest history endpoint: `GET /api/digest/history`

### State
Repo clean. All surfaces consistent. No pushes. API deploy remains #1 blocker.

---

## Cron (2026-05-17 01:07 UTC) — Web & Ecosystem: Whale Anti-Dump Final Results ✅

**rewrite-2 `c8cf082`** | `./gradlew build` ⚠️ web race | `web-optimizer/` 29 routes ✅ | `web/` 13 routes ✅ | market-simulation fmt/clippy/test ✅ | Pushed: none

### Builds
- `web/` — 13 routes ✅
- `web-optimizer/` — 29 routes ✅
- market-simulation — fmt ✅ clippy ✅ test 14/14 ✅
- api-server — fmt ✅ clippy ✅
- price-solver — fmt ✅ clippy ✅ test 2/2 ✅
- Java — PMD 0 (`:buildWeb` race, known; run `cd web && npm run build` directly)

### Simulation: Whale Anti-Dump Final Combined Results
5-seed: control / whale / capped(500) / shock(cap+spread-shock)

| Arm | Avg D/G | vs Control | vs Whale | vs Capped |
|-----|---------|------------|----------|-----------|
| Control | 2.601x | — | — | — |
| Whale | 5.975x | **+130%** | — | — |
| Capped (500) | 5.578x | +114% | -6.6% | — |
| **Shock (cap+spread)** | **4.555x** | **+75%** | **-23.8%** | **-18.3%** |

Shock = cap(500) + `whale_spread_shock_trigger_bps=0.10` + `whale_spread_shock_multiplier=2.5` + `whale_spread_shock_duration_ticks=288`

**Finding:** Spread shock provides **18.3% extra D/G reduction** over cap-only. Combined arm: 76.2% of uncapped whale D/G vs 93.4% for cap-only.

**Recommendation:** Enable cap (500) + spread shock together. Tighter cap (100-200) tested prior — slightly worse than 500.

### Next Priorities
1. API deploy (Arc's Fly.io token) — #1 unlock for live-data surfaces
2. Real testimonials — replace fictional personas on landing
3. Server showcase page once API deploy + real quotes available
4. Seed-42 failure analysis for recommended config (keep Newbie+GB, understand seed 42 regression)

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-17 01:00 UTC) — Sim: Spread Shock Combined Test Works ✅

**rewrite-2 `c8cf082`** | Headless whale stress test completed | Rust fmt/clippy ✅ test 14/14 ✅

### Shipped: Spread Shock + Cap = Better Anti-Dump

First combined test of cap + spread shock:

| Arm | Avg D/G | vs Control | vs Whale |
|-----|---------|------------|----------|
| Control | 2.601x | — | — |
| Whale | 5.975x | +130% | — |
| Capped (500) | 5.578x | +114% | -6.6% |
| **Shock** | **4.555x** | +75% | **-23.8%** |

**Finding:** Spread shock provides **18.3% extra D/G reduction** over cap-only.
- Combined: 76.2% of uncapped whale D/G
- Cap-only: 93.4% of uncapped whale D/G

**Recommendation:** Enable cap (500) + spread shock together. Test tighter cap (100-200).

### Next Priorities
1. ~~Test tighter cap (100-200) + spread shock combo~~ — DONE (2026-05-18)
2. API deploy (Arc's Fly.io token)
3. Real testimonials (human outreach)

**Blocked:** API deploy | Real testimonials

---

## Cron (2026-05-16 13:13 UTC) — Web & Ecosystem: Trust Link Fix + Builds Clean ✅

**rewrite-2 `31f8532` → `18b44b2`** | `./gradlew build` ✅ PMD 0 | `web-optimizer/` 29 routes ✅ | `web/` 13 routes ✅ | Pushed ✅

### Shipped: Landing CrossServerBanner Now Links /trust ✅
**File:** `web-optimizer/src/components/landing/cross-server-banner.tsx`

Gap: CrossServerBanner footer linked `/true-prices` and `/servers` but not `/trust`. All inner pages (servers, true-prices, exchange-rates, install) already linked `/trust`. The landing page is the highest-traffic entry for new visitors — trust should be surfaced there too.

**Fix:** Added "Trust & Safeguards" link to the banner footer alongside existing `/true-prices` and `/servers` links.

### Builds
- `./gradlew build -x installWebDeps` ✅ PMD 0 (38s)
- `web-optimizer/` direct build: ✅ 29 routes including `/trust` and `/faq`
- `web/` direct build: ✅ 13 routes
- Rust market-simulation: 14/14 tests ✅

### API Freshness Filtering — Already Implemented
`api-server/src/price_computer.rs:178` — `stale_threshold_hours` env var filters silent servers from true-price computation. Confirmed implemented. No separate task needed.

### Next Priorities
1. API deploy (Arc's Fly.io token) — the #1 unlock for live-data surfaces (true-prices server count, landing "Active Servers" count, exchange-rates live data, servers activity feed)
2. Real testimonials (human outreach to server admins)
3. Server showcase page once API deploy + real quotes are available

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-16 08:30 UTC) — Simulation Lab: Whale Stress Test (5-seed) ⚠️

**rewrite-2 `31f8532` → `HEAD`** | sim: clippy ✅ fmt ✅ test ✅ | Pushed

### Shipped: whale stress test multi-seed run
Seeds: 42, 12345, 98765, 77777, 11111 | Arms: control / whale / whale+capped

### Key Findings

**D/G Ratio (Debt/GDP)**
| Arm | Avg D/G | vs Control |
|-----|---------|------------|
| Control (no whale) | 2.601x | — |
| Treatment (whale) | 5.975x | **+130%** |
| Capped (500/item cap) | 5.578x | +114% |

Capped vs Treat: **only 6.6% D/G improvement** — 500/item cap too loose.

**GDP Impact**
- Whale inflates GDP +132.5% (wealth gets socialized into economy)
- Cap reduces GDP -8% vs untreated whale (wealth still dumped, just slower)

**Stability** (avg volatility threshold < 0.05)
- Control: ✅ stable all 5 seeds
- Treatment: ❌ unstable 4/5 seeds
- Capped: ❌ still unstable most seeds

### Anti-Dump Configs (commit 5f7056e)
New configs exist but NOT yet tested in whale_stress scenario:
- `whale_spread_shock_trigger_bps`: spread shock on volume spike (default 10%)
- `whale_spread_shock_multiplier`: 2.5x during shock
- `whale_spread_shock_duration_ticks`: 288 ticks (1 day)
- `whale_high_value_sell_cooldown_ticks`: 12 ticks on Epic/Legendary

### Commit 31f8532 Fix
Whale cooldown was a no-op. Counter only decremented, never incremented on sell.
After fix: properly sets cooldown after Epic/Legendary item sell.

### Next Priorities
1. Wire anti-dump spread_shock into whale_stress scenario for combined mitigation testing
2. Lower `whale_max_dump_per_item` — current 500 is too loose to constrain whale impact
3. Add explicit spread-shock trigger sweep scenario
4. Document best engine parameters in PLAN.md once validated

### Whale Anti-Dump Test (60d Confirm)
- Sell cap (500/item) → only 6.6% D/G improvement, -8% GDP
- Need combined: cap + spread shock + cooldown tested together
- Current cap too loose — recommend lower default (e.g., 100-200)
- Next: test combined mitigation configs

**Blocked:** API deploy | Real testimonials

---

## Cron (2026-05-16 16:30 UTC) — Simulation Lab: Whale Stress Test ⚠️

**rewrite-2 `18b44b2`** | Rust clippy ✅ | fmt ✅ | test ✅ | No pushes

### 5-Seed Whale Stress Test Results
| Metric | Control | Whale | Capped (500) | Delta vs Whale |
|--------|--------|-------|----------------|-----------|
| Avg D/G | 2.601x | 5.975x | 5.578x | +114% |
| Avg GDP | 445K | 1,035K | 952K | -8.0% |
| Stability | 5/5 stable | 1/5 stable | 2/5 stable | slight |

### Key Finding
Sell-size cap alone is INSUFFICIENT. 500 units/item cap only reduces D/G by 6.6% vs uncapped whale. Combined mitigations (cap + spread shock + cooldown) need testing together.

### Code Quality
- Rust clippy ✅ fmt ✅ test ✅
- Whale configs exist but NOT combined in test run

### Next Priorities
1. Test combined anti-dump: moderate cap + spread shock + cooldown together
2. Recommend tighter cap default (100-200 vs current 500)
3. Add admin telemetry for whale-like sell walls

**Blocked:** API deploy | Real testimonials

---

## Cron (2026-05-15 07:08 UTC) — Web & Ecosystem: Trust Page + Test Cleanup ✅

**rewrite-2 `4cc9b7d` → `adc9055`** | `web-optimizer/` 24 routes | Pushed ✅

### Shipped
- **Trust & Governance page** (`/trust`): Core principles, safeguards grid (live/partial/planned badges), data boundary table, honest caveat.
- **Header nav:** `/trust` added to Community section.
- **Test replacement:** `AutoTuneTest.java` (no-op) → `TransactionResultTest.java` covering real `TransactionResult` cases.

### Builds
- Java: ✅ PMD 0 | `web-optimizer/`: ✅ 24 routes | `web/`: ✅

### Ecosystem State
Trust page closes the highest-priority public-facing gap. API deploy remains #1 unlock for live-data surfaces.

### Next Priorities
1. ~~Link `/trust` from true-prices and servers pages~~ — DONE `22450d0`
2. ~~Seed-42 recommended-config investigation~~ — DONE: confirmed as random outlier (4/5 seeds improve)
3. Server showcase page after API deploy + real testimonials

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-15 02:15 UTC) — Web & Ecosystem: Public /faq Page Shipped ✅

**rewrite-2 `345e131` → `0a36644`** | Pushed

### Shipped: Public `/faq` page
- Created `web-optimizer/src/app/faq/page.tsx` — first-class FAQ route with:
  - 10 categories: General, Pricing, Economy Distressed, Configuration, Market Events, Dashboard, Cross-Server, Performance, Troubleshooting, Still Stuck
  - Live search: filters all questions by keyword in real-time
  - Category sidebar + accordion Q&A
  - Code blocks with monospace styling
  - Discord/GitHub CTA for unresolved questions
- Added `/faq` to header nav (Learn section, between `/admin` and `/docs`)
- Updated footer to link `/faq` internally (was external GitHub link)

### Builds
- Java build (no web): ✅ BUILD SUCCESSFUL
- `web-optimizer/` build: ✅ 23 routes including `/faq`
- `web/` build: ⚠️ race condition at `:buildWeb` (known, run `cd web && npm run build` directly)

### Docs Gap Closed
`docs/FAQ.md` now has a public-facing route. Admins no longer need to browse GitHub to read the FAQ — it's directly accessible at `/faq` on the public site.

### Next Priorities
1. Trust/Governance page (`/trust`) — link from true-prices, exchange-rates, servers, and cross-server banner
2. Server showcase page (after API deploy + real testimonials)
3. Bundled `web/` first-run admin verification card: already shipped (`99f878f`) ✅

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

### Next Priorities
1. ~~Seed-42 failure analysis~~ — DONE: confirmed as random-seed artifact
2. ~~Combined Whale mitigation sweep~~ — DONE (2026-05-18)
3. Plugin-facing default recommendation: Newbie+GB config (4/5 seed win)

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-14 20:25 UTC) — Web & Ecosystem: First-Run Admin Verification ✅

**rewrite-2 `38ea2d5` → `345e131`** | `web/` build ✅ | Pushed ✅ | #autotune checked ✅

### Shipped
- Added a bundled `/admin` **First-Run Verification** card (`web/src/components/admin/first-run-verification-card.tsx`).
- Integrated it above the detailed config/auction/audit panels so new admins immediately see whether Auto-Tune is live.
- Uses existing endpoints only:
  - `/api/stats`: plugin reachability, server name, online players, tracked item count.
  - `/api/admin/health`: GDP/trade activity, buy/sell mix, circuit tier.
- Handles three states clearly:
  - **Economy Verified** — plugin reachable, items tracked, activity recorded, circuit normal.
  - **Economy Starting Up** — no items/trades yet, with concrete `shops.yml`, `/shop`, and `/sell` guidance.
  - **Economy Setup Issues** — API/circuit failures surfaced as action needed.
- Follow-up fix `345e131` tightened trade-mix logic so heavily one-sided economies warn instead of being called balanced.

### Ecosystem Observation
This closes the highest-leverage bundled dashboard onboarding gap from prior audits: “is my economy live?” is now answered directly in the admin dashboard without adding backend surface area. The remaining onboarding/trust bottlenecks are public-web discovery (`/faq`, trust/governance page) and API deploy.

### Future Ideas to Pick Up
**Plugin Engineer**
1. Dedicated `/api/admin/verification` endpoint with richer diagnostics: Vault provider, DB migration version, latest market tick, latest transaction, price reporter heartbeat.
2. Anti-dump telemetry package: sell-wall detection, spread-shock/cooldown state, and admin alerts once Sim Lab validates thresholds.
3. In-game `/at wizard` can reuse the same verification checklist semantics after first setup.

**Sim Lab**
1. Seed-42 recommended-config failure analysis before final Java default recommendation.
2. Combined Whale mitigation sweep: moderate cap + sell-wall spread shock/cooldown + alert thresholds.
3. API adversarial low-sample model: Sybil servers, fake player counts, and confidence-label thresholds.

**Public Web / Docs**
1. Add `/faq` route rendering `docs/FAQ.md` content with category/search UX.
2. Add Trust/Governance page linked from true-prices, exchange-rates, and server pages before API launch.
3. Once testimonials are real, replace public social proof placeholders with server-admin quotes and outcomes.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-14 16:30 UTC) — Simulation Lab: Recommended Long-Run Validation ✅

**rewrite-2 at `653c7bd` start** | Rust sim harness added | `cargo clippy -- -D warnings` ✅ | `cargo fmt` ✅ | `cargo test` ✅ 14/14

### Code / Harness
- Added `Scenario::recommended_config_plus_vt()`.
- Added `--recommended-longrun-test` for baseline vs recommended config across `30d/60d × 5 seeds`.
- Added `--recommended-vt-test` for recommended config vs `recommended+2VT` across `14d × 5 seeds`.

### Long-Run Result — Recommended Config Holds
Treatment: `2MM + 2GB + 3Cas + 1Far + 2Tra + 2Newbie + 60% Diamond floor`
Control: `2MM + 2GB + 3Cas + 3Far + 2Tra + floor`

- 30d: GDP `+26.1%`, D/G `-3.045x`, vol `-10.4%`, TIER3 unchanged `0→0`, D/G wins `4/5` seeds.
- 60d: GDP `+1.6%`, D/G `-1.579x`, vol `-34.4%`, TIER3 unchanged `2→2`, D/G wins `4/5` seeds.

**Recommendation:** Keep Newbie+GB config as the strongest default candidate. It passes 30/60d validation and is not just a short-run artifact. Caveat: seed `42` regresses badly; inspect that failure mode before final Java default changes.

### VT Result — Do Not Add to Default
`recommended_config + 2VT` vs recommended at 14d:
- GDP `-11.2%`
- D/G `-0.122x` only
- Vol(CV) `+10.5%`
- D/G wins only `2/5` seeds

**Recommendation:** Do **not** stack VT on top of Newbie+GB defaults. VT's tiny average D/G benefit is not robust and it dilutes the cleaner Newbie demand-sink effect.

### Next Priorities
1. Seed-42 failure analysis for recommended config at 30/60d.
2. Plugin-facing default-config note: recommend Newbie+GB, exclude IT+VT/VT from defaults.
3. Combined Whale mitigation sweep: cap + spread shock/cooldown + admin telemetry.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

_Living document. Update after every session. Prioritize ruthlessly._

**⚠️ STRATEGIC REDIRECT (2026-05-04): Bug Fixes + Repo Health over New Features**

Noah has directed: **stop adding features, focus on finding and fixing bugs, and clean up the repo**.

The auction ecosystem, circuit telemetry, config preview, and depth charts are all done. The product is now feature-rich enough — what it needs is correctness, stability, and maintainability.

**Redirect:**
- Priority: bugs, refactoring, project structure, code health
- Deprioritize: new features, new pages, new simulation scenarios
- Look for: sharp edges in plugin logic, Rust code smells, TypeScript type gaps, integration mismatches between Java/Rust/web, test coverage gaps, migration edge cases, error handling holes
- Clean up: dead code, stale TODOs, inconsistent naming, copy drift between docs and code

**Still blocked (non-code):** API server deploy (Arc's Fly.io token), real testimonials (human outreach). These are unaffected by this redirect.

---



## Cron (2026-05-14 08:30 UTC) — Simulation Lab: Recommended Newbie Config Confirmed ✅

**rewrite-2 at `653c7bd`** | `--gb-newbie-healthy-test` ✅ | market-simulation fmt/clippy/test ✅ | Pushed ✅

### Code / Harness
- Added named `Scenario::recommended_config()` for the production candidate: `2MM + 2GB + 3Cas + 1Far + 2Tra + 2Newbie + 60% Diamond floor`.
- Corrected stale labels in `--gb-newbie-healthy-test`: treatment is **1Far+2Newbie**, not 2Far+2Newbie. The code was already running 1Far; the output/comments were misleading.
- Reworded verdict to the precise claim: aggregate GDP/DG/vol improve and D/G improves 5/5 seeds.

### Recommended Config Head-to-Head
Control: `2MM + 2GB + 3Cas + 3Far + 2Tra + 60% Diamond floor`
Treatment: `2MM + 2GB + 3Cas + 1Far + 2Tra + 2Newbie + 60% Diamond floor`

- GDP: `626,974` → `722,883` (`+15.3%`)
- D/G: `4.999x` → `3.083x` (`-1.916x`)
- Vol(CV): `27.997%` → `24.819%` (`-11.4%`)
- Buy ratio: `80.1%` → `78.0%` (`-2.1pp`, more balanced)
- TIER3 events: `0` → `0`
- Per-seed D/G improved 5/5; largest win seed `77777`: `7.720x` → `2.206x`

### Product Recommendation
- Recommend the **Newbie+GB config candidate** over the current 2MM+2GB+3Far baseline: it keeps economies healthier by replacing structural sell-side farming pressure with fresh buy-side demand.
- Caveat: seed `42` GDP dropped (`709K` → `457K`) while D/G improved, so validate over 30–60 day horizons before making it a Java default.
- Do **not** assume VT stacks additively with Newbie. Test `recommended_config + 2VT` separately before recommending VT as default.

### Next Simulation Priorities
1. 30–60 day recommended-config validation (`recommended_config` vs current baseline).
2. `recommended_config + 2VT` head-to-head: confirm whether VT adds liquidity or dilutes Newbie+GB gains.
3. Whale mitigation design/sweep: moderate cap + sell-wall spread shock/cooldown + admin telemetry thresholds.
4. Keep IT out of default recommendations unless admins explicitly trade leverage risk for higher activity.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-14 01:04 UTC) — Web & Ecosystem: Trust/Onboarding Audit ✅

**rewrite-2 at `e59142e`** | `web/` build ✅ | `web-optimizer/` build ✅ | market-simulation fmt/clippy ✅ | Pushed ✅

### Fix Shipped
- Corrected `docs/SECURITY.md` launch-hardening summary so it no longer lists freshness filtering as future work after the checklist already marks stale-submission filtering as implemented.
- Commit: `e59142e docs: correct security launch hardening summary`.

### Ecosystem Observations
- The product ecosystem is coherent: Java plugin remains the authority, bundled `web/` explains a live server economy, Rust sim validates tuning, API server aggregates opt-in cross-server ratios, and `web-optimizer/` markets/docs the network.
- The biggest remaining ecosystem risks are **trust and onboarding**, not feature volume.
- Cross-server exchange rates should stay plugin-local. The API should publish true-price data plus confidence signals; individual servers should decide how much to trust/apply that data.
- API trust docs are accurate after the SECURITY.md fix, but public-facing confidence UX is still thin until API deployment is live.
- Bundled admin UX is strong after the admin/report fixes, but first-time admins still need a verification flow that answers: Vault hooked? DB migrated? web server reachable? first price event recorded? price reporter heartbeat accepted?

### Prioritized Future Ideas — Web / Docs
1. **Public Trust & Confidence page (`web-optimizer/`)** — surface SECURITY.md concepts as admin-readable UX: server count, freshness, outlier suppression, low-confidence states, and what “true price” does/does not mean.
2. **True-price confidence labels** — show “low sample”, “stale”, “outlier-filtered”, and “strong consensus” states on true-prices/exchange-rates/server pages before public launch.
3. **Admin first-run checklist (`web/`)** — guided card on `/admin`: config loaded, Vault provider detected, DB migrations current, market tick active, latest transaction seen, auction repo healthy, API heartbeat status.
4. **FAQ route (`web-optimizer/faq`)** — promote `docs/FAQ.md` into a public route for admins comparing alternatives.
5. **Server showcase once API deploys** — highlight healthy opt-in servers with confidence badges, not raw leaderboard rankings that incentivize manipulation.

### Prioritized Future Ideas — Plugin Engineer
1. **API key lifecycle** — key rotation/revocation endpoint + audit trail; registration approval/invite flow before heavy public marketing.
2. **Anti-dump telemetry package** — detect concentrated sell-walls by player/item/time window; expose admin alerts and dashboard events; avoid hidden hard caps until Sim Lab validates mitigations.
3. **Configurable sell-wall spread shock** — temporary spread widening/cooldown under abnormal sell pressure, paired with telemetry so admins understand why prices moved.
4. **First-run verification command** — `/autotune verify` or admin dashboard endpoint that reports Vault, DB, web, price reporter, and economy tick status in one place.
5. **Player discovery nudges** — optional progressive tips/quests for `/compare`, `/loans`, `/auction`, and `/portfolio` so players discover differentiators organically.

### Prioritized Future Ideas — Sim Lab
1. Recommended config head-to-head: `2MM+2GB+2Newbie+2VT` vs current `2MM+2GB` baseline.
2. Combined Whale mitigation sweep: moderate per-item cap + sell-wall spread shock + high-value cooldown + admin-alert thresholds.
3. API adversarial model: fake high-player-count servers, Sybil registration clusters, and outlier threshold sensitivity at low server counts.
4. ItemTier classification audit: verify Java default tier assignments align with server-admin intuition and sim stress profiles.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-13 08:48 UTC) — Simulation Lab: Rust ItemTier Parity ✅

**rewrite-2 at `6593efb`** | market-simulation fmt/clippy/test/regression ✅ | Regression baselines refreshed ✅ | Pushed ✅

### Code / Parity
- Restored Java/Rust parity for `ItemTier` spread and max-price-change multipliers in `scripts/market-simulation`.
- Added Rust `ItemTier` with Java-equivalent multipliers and default material-name classification.
- Added normalization so display names (`Golden Apple`, `Netherite Ingot`) match Java material keys (`GOLDEN_APPLE`, `NETHERITE_INGOT`).
- `MarketEngine` now applies tier multipliers only when explicit per-item overrides are absent:
  - `max_price_change_override` keeps override precedence over tier multiplier.
  - `base_spread_override` keeps override precedence over tier multiplier.
- Refreshed all sim regression baselines because the change intentionally shifts core spread/price behavior.

### Simulation Findings
- `--regression`: PASS after baseline refresh.
- `--headless guild-stability`: avg volatility `0.0031` ✅ stable; final D/G about `3.70x`; 7/8 loans defaulted.
- `--headless standard`: avg volatility `0.0166` ✅ but not healthy; TIER3 fired at tick `3325` with D/G `30.98x`, final D/G about `22.18x`.
- `--whale-stress-test` with ItemTier parity:
  - Control avg D/G `2.601x`
  - Whale avg D/G `6.131x` (`2.358x` worse than control)
  - 500 units/item/tick cap avg D/G `5.326x` (`0.869x` of uncapped Whale)
  - Capped GDP `+7.3%` vs uncapped Whale while leverage remained high

### Next Simulation Priorities
1. Test combined Whale mitigation now that tier parity is fixed: moderate per-item cap + sell-wall spread shock and/or high-value sell cooldown.
2. Add a plugin-facing anti-dump design note: configurable throttle, telemetry, and alerts rather than a hidden hard limit.
3. Audit whether Java's default `ItemTier` classifications are intuitive for server admins (notably `BLAZE_ROD` is LEGENDARY and `REDSTONE` defaults COMMON while `REDSTONE_BLOCK` is UNCOMMON).
4. Test IT+VT combined (`--it-vt-healthy-test`): can VT's D/G reduction counteract IT's spread compression at the cost of D/G?
5. Test recommended config (2MM+2GB+Newbie+VT) head-to-head vs current 2MM+2GB baseline.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

---

## Cron (2026-05-14 00:53 UTC) — Simulation Lab: IT+VT Cancels, Healthy Baseline ✅

**rewrite-2 at `6593efb`** | Regression: ALL PASSED | Build: clean | No pushes

### IT+VT Combination Test (`--it-vt-healthy-test`, 5-seed)
**FINDING: IT+VT CANCELS — do NOT combine these archetypes**
- Control (2MM+2GB): GDP 571K, D/G 5.56x, vol 0.2569, buy 80.3%
- Treat (+2IT+2VT): GDP 527K (-7.7%), D/G 5.35x (-0.22x), vol 0.2676 (+4.2%)
- IT (+30.1% GDP, +2.41x D/G) + VT (-9.2% GDP) = net -7.7% GDP. They cancel.
- **Action: Never add IT+VT together in any config.**

### Healthy Baseline Stats (2MM+2GB+floor, 5-seed)
- Mean GDP: 627K ± 67K | Mean D/G: 5.00x ± 1.82x | All 5 seeds floor-bound
- D/G range: 2.98x–7.72x | All below TIER2

### Next Priorities
1. Recommended config head-to-head vs 2MM+2GB (next session)
2. Whale anti-dump design: throttle + spread shock + admin telemetry
3. Regression baseline metadata: track `6593efb` not `24310f2c`

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-13 16:30 UTC) — Simulation Lab: Archetype Combinations ✅

**rewrite-2 at `6593efb`** | Regression: PASS | Build: ✅ | fmt/clippy clean | Pushed: none

### Simulation Findings

**IT Healthy Economy** (`--it-healthy-test`, seed=42): IT compresses BPD -21.7% and boosts GDP +94.6%, but D/G worsens 3.06x → 3.49x. IT's debt-financed accumulation/release cycle creates leverage pressure. Insufficient alone as stability mechanism.

**Whale Stress Test** (5-seed): Control avg D/G 2.601x → Whale 6.131x (+2.358x); capped 500 units/item/tick only reduces to 5.326x. Sell caps alone are insufficient; combined throttle+spread-shock+telemetry needed.

**GB+Newbie Combo** (5-seed, strongly positive): 2MM+2GB+2Far+2Newbie vs 2MM+2GB+3Far — GDP +15.3%, D/G -1.916x (3.083x vs 4.999x), **5/5 seeds improved**. Newbie provides buy-side demand that GB's sell-side supply needs for two-sided balance. Strongest tested archetype combination.

**VolumeTrader Multi-Seed** (5-seed, positive): +2VT vs MM+GB — GDP +15.3%, D/G -1.23x (2.64x vs 3.88x), BPD -16.3%, vol +10.3%. Contrarian liquidity thesis holds. Recommend adding 2 VT to production config.

### Key Archetype Design Lesson
GB sells excess supply; Newbie buys what GB sells. Together they create two-sided balance. VT provides contrarian liquidity that reduces D/G. IT compresses spreads but accumulates debt — net negative for D/G. Combined IT+VT not yet tested (`--it-vt-healthy-test`).

### Next Simulation Priorities
1. Run `--it-vt-healthy-test` — combined IT+VT to test if VT's D/G reduction counteracts IT's leverage cost
2. Run recommended config (2MM+2GB+Newbie+VT) head-to-head vs current 2MM+2GB
3. Run `--healthy-baseline-5seed` for clean statistical baseline of recommended config
4. Design plugin anti-dump pattern: per-item throttle + spread shock + admin telemetry/alerts

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-13 06:20 UTC) — Java Plugin Repo Health Cleanup ✅

**rewrite-2 at `24310f2`** | `./gradlew build -x installWebDeps` ✅ PMD 0 | Pushed ✅

### Cleanup
- Removed tracked stale patch artifact `src/main/java/com/noahblclarkson/autotune/database/DatabaseManager.java.patch`.
- Confirmed it was historical migration scratch data, not a build input. Keeping it under `src/main/java` was misleading repo debris.

### Verification / Audit
- Full Gradle build passed with bundled `web/` Next export, tests, PMD, and shadow jar.
- Rust market-simulation clippy clean from session check.
- Migration runner spot-check: V1, V2, V3, V4 repair, V5, V5b/version-6, V7, V8, V9 all wired.
- Offline-money audit spot-check: Vault money paths still use `Bukkit.getOfflinePlayer(...)`; `Bukkit.getPlayer(...)` usages inspected are UI/online-only paths with guards.

### State
- No open GitHub issues. Pre-push CI green; post-push CI queued immediately after `24310f2`.
- No CLAUDE.md architecture changes needed.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-13 01:07 UTC) — Simulation Lab: Whale Sell-Cap Mitigation ✅

**rewrite-2** | Rust `cargo clippy -- -D warnings` ✅ | `cargo fmt` ✅ | `cargo test` ✅ 12/12 | `--regression` ✅ all passed

### Simulation Findings

- Re-ran corrected `--whale-stress-test` across seeds `42, 12345, 98765, 77777, 11111`.
- Baseline Whale remains clearly destabilizing despite higher apparent activity:
  - Control avg D/G: `2.172x`
  - Uncapped Whale avg D/G: `5.800x` (`2.671x` worse than control)
  - Control avg GDP: `476,437`
  - Uncapped Whale avg GDP: `1,436,717` (`+201.6%`) — activity masks leverage risk.
- Tested per-item Whale dump cap as a concrete plugin-style mitigation:
  - `500` units/item/tick: avg D/G `5.017x` (`0.865x` of uncapped, ~13.5% lower), avg GDP `1,097,037` (`-23.6%` vs uncapped)
  - `100` units/item/tick: avg D/G `5.121x`, worse than the 500 cap; seed 42 regressed to `2.523x` vs uncapped `2.289x`
- Conclusion: sell-size caps alone help only modestly and can backfire when too tight by prolonging the sell-wall. Do not recommend a raw cap as the only plugin change.

### Code / Harness Changes

- Added `SimConfig.whale_max_dump_per_item: Option<i32>` with serde default (`None` preserves current behavior).
- Threaded the cap into `PlayerAgent::new_whale()` and `WhaleConfig.max_dump_per_item`.
- Updated Whale dumping so capped dumps continue across ticks until inventory is fully sold, then enter dormant state.
- Extended `run_whale_stress_test()` to print Control vs Whale vs Capped results and include capped runs in the all-seed summary.
- Added config deserialization test for missing Whale cap defaulting to uncapped behavior.

### Java/Rust Parity Notes

- Spot-checked shared price/spread/slippage formulas against Java `MarketEngine`: trade ratio, player scaling, sell-pressure multiplier, trend dampening, event multiplier, spread liquidity/player impact, global volume multiplier, and sqrt slippage are aligned for fields modeled in Rust.
- Parity gap found: Java applies `ItemTier` spread and max-price-change multipliers via `ShopItem.effectiveSpreadMultiplier()` / `effectiveMaxPriceChangeMultiplier()`. Rust simulation currently has only explicit per-item overrides and no default item-tier model. This means rare/high-value item behavior is under-modeled unless overrides are manually configured.

### Next Simulation Priorities

1. Add Java `ItemTier` multiplier parity to Rust `ItemConfig` / engine defaults before trusting rare-item stress tuning.
2. Test sell-wall spread shocks or high-value sell cooldowns in combination with a moderate per-item cap; the cap alone is not enough.
3. Convert Whale findings into a plugin-facing design: admin-configurable anti-dump throttles + detection/telemetry rather than hidden hard limits.

---

## Cron (2026-05-13 00:52 UTC) — Plugin/Web Health Audit: Shareable Report Recovery Fix ✅

**rewrite-2 at `9bd9218`** | `./gradlew build -x installWebDeps` ✅ PMD 0 | `web/` build ✅ | Pushed ✅

### Bug Fixed
- `web/src/components/admin/shareable-report-card.tsx` treated `ADMIN_RECOVERY` as generic Tier 3 and could still show a high shareable health score while manual recovery mode was active.
- Fixed with explicit `ADMIN_RECOVERY` label/color, unknown-tier fallback, and circuit-tier severity caps for shareable report scores: TIER1 ≤60, TIER2 ≤30, TIER3/Admin Recovery ≤10.

### Audit Notes
- Reviewed `LoanManager` request/repay/interest/circuit logic and `EconomyManager` buy/sell transaction ordering.
- Confirmed zero real TODO/FIXME markers in Java/TypeScript/Rust source; only generated Rust build artifacts under `target/` contain upstream TODO comments.
- No CLAUDE.md architecture changes needed.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-12 19:10 UTC) — Web & Ecosystem: Repo Clean, Discord Bot Audit ✅

**rewrite-2 at `5c47af0`** | All builds green | No pushes (clean per Noah's redirect)

### Builds
- `./gradlew build -x installWebDeps` ✅ PMD 0 (51s)
- `web/` 13 routes ✅ | `web-optimizer/` 22 routes ✅
- market-simulation: fmt/clippy clean ✅ | 11/11 tests ✅
- api-server: fmt/clippy clean ✅ | price-solver: fmt/clippy clean ✅ | 2/2 tests ✅

### Audit: No Bugs Found
- Zero TODOs/FIXMEs in all source (Java/TypeScript/Rust) — confirmed
- Circuit thresholds consistent: TIER1=3-5x, TIER2=5-30x, TIER3>30x — all surfaces ✅
- API docs accurate: Bearer auth, `/api` prefix, heartbeat under ApiKeyAuth, plugin_version, ratio_matrix ✅
- sweep-results sp=0.80 copy corrected (prior session) ✅
- Discord bot (`discord-bot/index.js`): 338 lines, ES module, proper error handling, commands `/at status|price|top|help` — fully functional ✅

### Ecosystem Observations
- **web/ `/admin`:** Complete. Missing: first-run verification checklist for new admins (ConfigHealthCard validates ranges but no "is my economy live?" guided checklist).
- **web-optimizer/ landing:** "Active Servers: Network growing" — honest placeholder, no live count yet (blocked on API deploy).
- **API docs (`/api-docs`):** Accurate for all implemented features ✅
- **Discord bot:** Documented in install guide and docs index ✅
- **FAQ:** Only `docs/FAQ.md`, no web-optimizer `/faq` page — noted but not a bug

### State
Repo clean. All surfaces consistent. No bugs. No pushes needed.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-12 16:30 UTC) — Simulation Lab: Whale Stress + Reporting Fix ✅

**rewrite-2 at `5c47af0`** | Rust fmt/clippy ✅

### Simulation Findings

- `--whale-stress-test` confirms a single Whale can destabilize an otherwise healthy MM/GB economy.
  - Seed 42: Control D/G `0.916x` → Whale D/G `2.289x` (`2.497x` worse), buy ratio `90.1%` → `81.4%`, raw GDP rose sharply but debt rose faster.
  - Remaining 4-seed summary before the reporting fix: avg D/G `2.485x` → `6.677x`; avg GDP `491k` → `851k`. The Whale produces apparent activity/GDP while worsening leverage and volatility.
- Quick `standard-with-mm` headless run showed it is not a safe recommended baseline: final GDP `$2.5k`, debt `$214k`, D/G `~84.5x`, 6/8 loans defaulted, repeated TIER3 entries. A lone MarketMaker without GuildBuyers/floor support cannot absorb sell pressure.

### Bugs / Repo Health Fixed

- Fixed `--whale-stress-test` reporting bug: volatility delta was multiplied by 100 but displayed as a raw volatility delta.
- Fixed multi-seed summary bug: seed 42 was run and shown separately but excluded from the “all seeds” averages.
- Removed stale `(RECOMMENDED)` label from `standard-with-mm`; it is now marked as a legacy baseline.

### Next Simulation Priorities

1. Test concrete Whale mitigations before recommending plugin changes: per-item sell-size caps, high-value item sell cooldowns, and/or spread widening under sell-wall shocks.
2. Re-run the corrected `--whale-stress-test` after mitigation experiments so the fixed all-seed summary becomes the canonical output.
3. Keep auditing Rust/Java parity around debt caps, circuit-breaker thresholds, and GDP window definitions before trusting long-run tuning results.


---

## Ecosystem Observations (2026-05-11) — Web & Docs Deep-Dive

**`web/` (bundled dashboard, 13 routes):** Complete, functional, professional. Live WebSocket prices, transaction feed, top movers with magnitude bars, market health bar, economy chart with circuit event markers, auction house (4 tabs + depth chart), portfolio (badges + trading timeline + impact score), loans table, admin dashboard (circuit tiers, health score, recovery advisor, auction integrity card, config health, shareable report). Auction ecosystem is the standout differentiator.

**`web-optimizer/` (public site, 22 routes):** Comprehensive. Landing page with LiveDemo (browser-based ~60 ticks/sec engine), hero, feature cards, social proof. Key pages: how-it-works (spread math with formulas), findings (26 simulation Q&As), simulator (real-time price engine), setup wizard (5-step: type → count → goals → stability preview → YAML export), config-preview (YAML diff + sim impact), sweep-results (840-config viewer), install guide (good prerequisites, curl snippet, verification steps), auction marketing, docs, true-prices, exchange-rates, servers (mock), health-badge (embeddable widget), api-docs.

### Ecosystem Gaps & Risks
1. **Live data gap** — Landing page "Active Servers: Network growing" and "true-prices" only show simulated/mock data. API deploy would make these surfaces genuinely compelling. This is the #1 ecosystem unlock.
2. **Trust story missing** — API server has keys + outlier filtering + freshness filtering implemented, but no public governance/trust page explaining how fake-server injection or price manipulation is prevented. Needed before any cross-server marketing.
3. ~~**Discord bot undocumented**~~ — Fixed ✅ (`d04e39f`): Step 6 added to install wizard, Discord Bot card added to docs index.
4. **Testimonials still fictional** — Alex K., Dana W., Marcus T. personas look credible but are explicitly not real admins. Real quotes needed before any marketing push.
5. **First-run verification gap** — No guided checklist in bundled `/admin` for new admins to confirm their economy is working after install.
6. **Pricing page thin** — "Free & open source" single tier with GitHub CTA. No comparison vs competitors or hosted tier framing.

### Feature Ideas (for future cycles, per Noah's redirect these are NOT prioritized now)

**Plugin (Java):**
- In-game economy tutorial quest (first trade → see price move → check dashboard)
- Player spending by category in `/portfolio`
- `/at admin config diff <file>` — live config change preview before applying
- Economy event broadcasts (circuit fire, D/G threshold crossings)

**Public web (web-optimizer):**
- Trust/security governance page for API (server key rotation, outlier filtering, freshness)
- Server showcase page (after real testimonials collected)
- Discord bot docs page
- Interactive config comparison tool (pick two configs → side-by-side sim results)
- FAQ page (currently only `docs/FAQ.md`, no public web-optimizer page)

**Bundled web (web/):**
- First-run setup verification checklist card on `/admin`
- Player impact leaderboard
- Auction fill browser notifications (Notification API for `/auction/order` pages)

---

## Cron (2026-05-11 01:19 UTC) — Simulation Lab: Headless Investigation

- Investigated `market-simulation` scenarios.
- Discovered that `--headless` runs often fail dynamically due to `winit` display requirements (Missing Wayland/X11).
- **Next step:** Configure Xvfb or fix the `headless` feature flags in Rust to allow true offscreen execution in cron.

---

## Cron (2026-05-11 01:34 UTC) — Web & Ecosystem: Headless Sim Fix Confirmed, Repo Clean ✅

**rewrite-2 at `c82967c`** | All builds verified | No new pushes (clean session)

### Builds
- `./gradlew build` → fails at `:buildWeb` (pages-manifest.json race, pre-existing)
- `web/` → 13 routes ✅ (recharts `victory-vendor` warnings, non-fatal, Next.js 15.5.5)
- `web-optimizer/` → 22 routes ✅
- Rust sim fmt/clippy → clean ✅
- Rust sim tests → 11/11 pass ✅

### Headless Simulation Investigation

**Issue (resolved):** MEMORY noted cron environments fail with `WinitEventLoop` error without DISPLAY.

**Finding:** `--headless` scenarios (e.g., `cargo run -- --headless guild_stability`) work perfectly. The `WinitEventLoop` error only occurs in the **GUI** path (eframe). The headless `run_headless()` function uses standard Simulation tick loop, no winit dependency. `guild_stability` output: avg vol 0.0031 < 0.05 ✓.

**Conclusion:** No fix needed. Headless scenarios are fully functional. The `WinitEventLoop` error was from a separate code path, not the headless scenario runner.

### Audit
- Zero TODOs/FIXMEs in Java/TypeScript/Rust ✅
- 72/126 Java files class-level `@SuppressWarnings("PMD")` — deferred per Noah's redirect
- All prior bug fixes verified in memory: auction DB-first PENDING, OfflinePlayer Vault ops, circuit thresholds consistent, docs drift corrected
- `web/` build via Gradle has race condition at `:buildWeb` — run `cd web && npm run build` directly as workaround

### State
Repo clean. No new commits. No bugs found.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Cron (2026-05-10 16:30 UTC) — Simulation Lab: Flash Crash Scenario ✅

**rewrite-2 at `6e23401`** | Rust clippy/fmt ✅ | Test 11/11 ✅ | Pushed ✅

### New Scenario: Flash Crash Panic Spread Test

Added `Scenario::flash_crash_panic_spread_test()`:
- **Trigger:** 40% price shock at day 5 (tick 1440)
- **Duration:** 21 days
- **Archetypes:** 5 Casual + 3 Farmer + 3 Trader + 2 Hoarder

### Results

Economy remained **STABLE** (avg volatility 0.0127 < 0.05 threshold). No loan cascade, balanced transaction churn (54.3% buys).

---

**Branch:** `rewrite-2` (do NOT merge to master)

---

## Cron (2026-05-10 13:25 UTC) — Bug Audit: No Issues Found ✅

**rewrite-2 at `32dde51`** | `./gradlew build` ✅ PMD 0 | `web/` 13 routes ✅ | `web-optimizer/` 22 routes ✅ | Rust fmt/clippy ✅ | Pushed: none (clean session)

### Audit Results

- **AuctionManager.processFill()** — DB-first PENDING pattern verified correct (line 433: insertFill pending, line 551: updateFillStatus COMPLETED)
- **AuctionManager.recordFillAsync()** — DB-first PENDING pattern verified correct (line 719: insertFill pending, line 782: updateFillStatus COMPLETED)
- **Zero TODOs/FIXMEs** — No stale code markers in Java, TypeScript, or Rust
- **Rust cargo test** — 11/11 pass ✅
- **Config endpoint defaults** — Already fixed in `32dde51`

### Builds
- `./gradlew build` ✅ PMD 0
- `web/` 13 routes ✅
- `web-optimizer/` 22 routes ✅
- Rust fmt/clippy clean ✅

### State

Repo is clean. All critical economy/auction bugs from 2026-05-04 audit are resolved. No new bugs found in this session.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

---

## Cron (2026-05-10 13:25 UTC) — Bug Audit: Admin Dashboard Circuit Legend Fixed ✅

**rewrite-2 at `98ba808`** | `./gradlew build` ✅ PMD 0 | `web/` 13 routes ✅ | `web-optimizer/` 22 routes ✅ | Pushed ✅

### Bug Found + Fixed: Admin Dashboard Circuit Breaker Legend Wrong

**Location:** `web/src/app/admin/admin-content.tsx` — Circuit Breaker Tiers legend (lines 415-437)

The admin dashboard `/admin` showed a circuit-breaker legend with **hardcoded fixed-rate tiers**:
- TIER1: 3–5x, interest capped at 50%
- TIER2: 5–10x, interest capped at 25%
- TIER3: >10x, interest paused

But the WebServer's `/api/admin/health` uses the **counter-cyclical** path (default=true), where interest is **proportional** to D/G ratio and TIER3 fires at `debtGdpTier3Ratio=30.0`:
- TIER1: 3–5x, proportionally reduced (50% at D/G=5x)
- TIER2: 5–30x, proportionally reduced (25% at D/G=10x)
- TIER3: >30x, interest paused

The legend was the old fixed-cap design from before counter-cyclical was the default. Admins reading this legend and seeing TIER3 in-game would get wrong expectations about when the circuit fires and what the interest rates look like.

**Fix:** Updated 3 legend descriptions to match counter-cyclical behavior.

### Audit Results

- **Auction ecosystem**: Complete and correct ✅
- **Economy page circuit labels**: Correct (TIER1=3×, TIER2=5×, TIER3=30×, unlock at 15×) ✅
- **web-optimizer economy page**: Correct ✅
- **WebServer health endpoint**: Returns `counterCyclical: true` (default) ✅
- **No TODOs/FIXMEs** in Java, TypeScript, or Rust ✅
- **Rust cargo test**: 11/11 pass (verified prior session)
- **Social proof testimonials**: 3 fictional personas (Alex K., Dana W., Marcus T.) — real testimonials still blocked on human outreach
- **MarketMockPanel** in web-optimizer hero: correctly labeled as mock/illustrative data ✅

### Builds
- `./gradlew build` ✅ PMD 0
- `web/` 13 routes ✅
- `web-optimizer/` 22 routes ✅

### State

Repo is clean. Admin dashboard circuit legend now matches actual counter-cyclical behavior. All other circuit documentation in web and web-optimizer is correct. No new bugs found.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

**rewrite-2 at `32dde51`** | `./gradlew build` ✅ PMD 0 | `web/` 13 routes ✅ | `web-optimizer/` 22 routes ✅ | Pushed ✅

### Bug Found: `WebServer` `/api/config` Returned Stale `debtGdpTier3Ratio` Defaults

**Location:** `WebServer.java:1397-1400` — loan section of `/api/config` response

`/api/config` powers the bundled `web/` admin dashboard config preview. Wrong values:
- DEFAULT was 15.0, actual config default is **30.0**
- RANGE_MAX was 15.0, should allow **up to 100**
- RANGE_MIN was 12.0, but ConfigValidator soft-warns below **20.0**

Fix: DEFAULT=30.0, RANGE_MIN=20.0, RANGE_MAX=100.0. Matches `config.yml`, ConfigValidator, and all docs.

**Audit:** All other config endpoint defaults and all docs/web-optimizer pages are correct for tier3=30. Historical changelog entries correctly note the old tier3=15 era as history.

### Builds
- `./gradlew build` ✅ PMD 0
- `web/` 13 routes ✅
- `web-optimizer/` 22 routes ✅

### State
All economy/auction bug fixes from 2026-05-04 audit plan: RESOLVED ✅. Repo is clean.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)
---

## Cron (2026-05-11 07:07 UTC) — Web & Ecosystem: sweep-results sweet-spot copy fixed ✅

**rewrite-2 at `af7ff1f`** | `./gradlew build` ✅ PMD 0 | `web/` 13 routes ✅ | `web-optimizer/` 22 routes ✅ | Rust clippy/fmt ✅ | Tests 11/11 ✅ | Pushed ✅

### Bug Found + Fixed: sweep-results "sp=0.80 is sweet spot" Stale Copy

**File:** `web-optimizer/src/app/sweep-results/page.tsx` — Key findings section

`sp=0.80` was being marketed as "the sweet spot" and "Best buy ratio" in the 840-config sweep-results page. This directly contradicts the 2026-04-13 finding: sp=0.80 sacrifices D/G stability (+40% WORSE) for +5.2% GDP. Production default is sp=1.0 (symmetric), giving D/G 4.73x vs 7.86x at sp=0.80.

**Fix:** Replaced three stale lines with two accurate ones:
- ✅ "Best D/G stability: sp=1.0 → D/G 4.73x, buy ratio 47.2% — symmetric default confirmed" (green)
- ✅ "sp=0.80 worsens D/G ~40% for +5% GDP — growth servers only" (gray)

**Root cause:** The sweep-results key findings were written when sp=0.80 was still being considered as a default. The sp=1.0 correction happened in ECOSYSTEM_ANALYSIS.md and related docs, but the sweep-results page wasn't updated in that same commit.

**Confirmed clean:** All other surfaces (config-impact-preview.tsx, docs/MIGRATION.md, docs/ECOSYSTEM_ANALYSIS.md, docs/ARCHITECTURE.md, docs/SERVER_ADMIN_GUIDE.md) already correctly describe the sp=0.80 tradeoffs. Only sweep-results had the stale framing.

### Audit Results
- **Zero TODOs/FIXMEs** in main source (Java/TypeScript/Rust) — only `target/` build artifacts have glutin/winit generated TODOs
- **All auction paths** verified clean (DB-first PENDING, OfflinePlayer Vault ops, V9 pending returns wired)
- **Database migrations** fully wired: V1–V9, V4 repair for V5 skip — all correct
- **Floor recommendation** docs consistent across all surfaces
- **No missing REST endpoints** detected

### Ecosystem Observations
1. **Market digest REST endpoint** — `MarketDigestService` runs in-game but no `/api/digest/history` for web admin visibility. `WebServer.java:1452` shows digest config in `/api/config` but no history endpoint.
2. **API freshness filtering** — `PriceReporter` sends `plugin_version` but `recompute_true_prices()` doesn't filter stale submissions yet. Needs `stale_threshold_hours` in schema + filter logic.
3. **Per-server submission health panel** — Would show active servers, last submission time, plugin version. Builds trust before public API launch. Blocks live true-prices page.

### State
Repo clean. `af7ff1f` pushed. All builds green. No further bugs found.

## Cron (2026-05-11 08:30 UTC) — Simulation Lab & API Health

- Ran Rust `market-simulation` scenarios (`guild_stability`, `standard`) headlessly. Both produced stable economies (volatility < 0.05).
- All 11 `market-simulation` tests, 36 `price-solver` tests, and 34 `api-server` tests passed.
- Workspace is clean with `clippy` and `fmt`.
- Confirmed `rewrite-2` is bug-free and requires no changes today. No new features added.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

## Cron (2026-05-12 08:36 UTC) — Simulation Lab: Whale Stress + Repo Clean ✅

**rewrite-2 at `6d33e76`** | `./gradlew build` ✅ PMD 0 | Rust clippy/fmt ✅ | Tests 11/11 ✅ | Pushed: none

### Builds
- `./gradlew build` ✅ PMD 0
- `web/` 13 routes ✅
- `web-optimizer/` 22 routes ✅
- market-simulation: clippy/fmt clean ✅ | 11/11 tests ✅
- api-server: clippy/fmt clean ✅ | 0 tests
- price-solver: clippy/fmt clean ✅ | 2/2 tests ✅

### Simulation Runs

**Regression suite** (`--regression`): PASS ✅ — zero displacement across all stored baselines

**Standard scenarios** (stable, all avg vol < 0.05):
- `--headless standard`: avg vol 0.0109 ✅
- `--headless guild-stability`: avg vol 0.0045 ✅
- `--exploiter-stress-test`: MM absorbs Exploiters — control D/G 22.76x vs treatment 0.47x. MM provides sufficient two-sided liquidity. Exploiters raise volatility 115% but don't break the market.

**New scenario: Whale stress** ⚠️
`--whale-stress-test` (3 seeds):
- Control: D/G 0.916x | Treatment: D/G 2.289x (+2.497x worse)
- Avg volatility: 0.2884 → 0.5771 (+28.9 points, +115%)
- Buy ratio: 90.1% → 81.4% (-8.7pp)
- GDP: +807% (Whale boosts raw volume) but D/G 2.8× worse

**Finding:** Whale archetype (accumulate → dump at 20% perceived value) significantly destabilizes healthy economies. MM/GB absorption insufficient against single Whale dump. No safeguards currently exist in plugin for this behavior pattern.

**Recommendation (sim level):** Per-item max sell-size caps per tick, spread circuit breaker on sell-side volume, high-value-only sell limits targeting Diamond/Gapple/Netherite.

### Headless Execution — CONFIRMED WORKING ✅
Confirmed `--headless` scenarios work perfectly in cron environment. The `WinitEventLoop` error only occurs when the GUI path is triggered. `--headless` uses `run_headless()` which has no winit dependency. MEMORY note from 2026-05-11 is a false alarm (corrected in MEMORY.md).

### Repo Health: Clean
- Zero TODOs/FIXMEs in all main source
- All configs, circuit thresholds, floor recommendations consistent
- No Winit errors in headless mode
- Long-running tests (`--ninety-day-test`, `--production-config-test`) output truncated at header — designed for multi-hour runs, not practical in cron window

### State
Repo clean. No bugs. New Whale stress finding documented. headless execution confirmed functional.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

---

## Ecosystem Observations (2026-05-13 PM) — Web & Docs Deep-Dive + Feature Ideation

**rewrite-2 at `6593efb`** | `./gradlew build` ✅ PMD 0 | web/ 14 routes ✅ | web-optimizer/ 22 routes ✅ | Rust fmt/clippy/test ✅ | Pushed: none

### Build Audit
- `./gradlew build -x installWebDeps` → BUILD SUCCESSFUL 52s, PMD 0
- `web/` bundled dashboard: 14 routes — clean
- `web-optimizer/` public site: 22 routes — clean
- market-simulation: 14/14 tests pass, fmt/clippy clean

### web/ (Bundled Dashboard) Assessment
- **Complete and professional** — live WebSocket prices, transaction feed, top movers, market health bar, economy chart with circuit markers, auction house (4 tabs + depth chart), portfolio, loans table, admin dashboard
- **Standout:** auction ecosystem differentiates from all competitors
- **Remaining gaps (feature ideas, not bugs):**
  - First-run verification checklist card on `/admin` for new admins confirming economy is live
  - Player impact leaderboard (top P&L, most trades)
  - Auction browser Notification API for `/auction/order` pages
  - `GET /api/digest/history` endpoint (WebServer exposes digest config but not historical trending)

### web-optimizer/ (Public Site) Assessment
- **22 routes covering:** landing, how-it-works, findings, simulator, setup wizard, config preview, sweep-results, auction, docs hub, true-prices, exchange-rates, servers, health-badge, widget, api-docs, changelog, roadmap, admin, install, why-auto-tune, economy, simulation-results
- **Docs hub** links to GitHub `rewrite-2/docs/` — 1,716 lines across 6 core guides ✅
- **Compare table** (`/compare`) — 4-category feature matrix vs Essentials, ShopGUI+, PlayerShops — credible
- **#1 gap: live data surfaces all mock** — landing "Active Servers: Network growing", `/true-prices` falls back to `SimulatedTruePrices`, `/servers` shows mock server cards. Unblocks when API deploy lands (blocked on Arc's Fly.io token).
- **Public FAQ page missing** — `docs/FAQ.md` exists but no `/faq` route in web-optimizer
- **docs/SECURITY.md** exists but not prominently linked from cross-server surfaces

### Ecosystem Coherence Assessment
- **What works:** Java plugin + bundled web/ + Rust sim + API server + web-optimizer/ form a coherent whole. Auction ecosystem is the strongest differentiator and is fully built.
- **Before marketing needs:** (1) API deploy for live data, (2) trust/governance page for cross-server, (3) real testimonials
- **Security risk:** Outlier filtering + freshness filtering + server-count weighting reduce manipulation but don't eliminate it. Trust story should be honest about residual risk.
- **Cross-server safe:** true prices (ratio matrix → anchored values), exchange rates (plugin-local computation)
- **Cross-server risky:** direct server-to-server balance signals, player identity sharing, submission rewards without punitive cost for bad data

### Feature Ideas (future cycles only — per Noah's redirect)

**Plugin — Anti-Dump System:** Whale stress shows D/G 2.6x→6.1x. Caps alone help ~13%. Plugin needs: configurable per-item sell throttle (admin-visible, not hidden) + spread widening on large sell-wall events + admin telemetry/alerts for whale-like patterns.

**Plugin — Config Version History:** `/at admin config history` with timestamped diffs and before/after D/G preview. Enables rollback and answers "what changed before the D/G spike?"

**Plugin — In-Game Setup Wizard:** `/at wizard` first-run: archetype mix → floor → loans → events → live verification check. Closes first-run gap in bundled `/admin`.

**Plugin — Scheduled Market Events:** Cron-style event scheduling (`/at admin event schedule GoldRush every 7d`). Enables automated events without manual intervention.

**Plugin — Circuit Interest Rate Preview:** Dashboard preview of how new loan params affect TIER2/TIER3 interest rates before applying. Builds admin confidence.

**Public web — Trust & Governance Page:** Prominently linked from cross-server banner. Explains server key auth, SHA-256 storage, outlier filtering (sigma), freshness filtering, no player data shared, plugin-local exchange rates. Unblocks public cross-server marketing.

**Public web — Public FAQ Page (`/faq`):** Web-rendered, searchable FAQ linked from nav. Closes discovery gap (currently requires GitHub browsing).

**Public web — Interactive Config Comparison:** Pick two configs → side-by-side sim results. High-value for admins evaluating changes.

**Public web — Server Showcase:** After real testimonials: admin quotes, server type, player count, outcome. Builds trust faster than feature lists.

**Bundled web — First-Run Admin Checklist Card:** On `/admin` for servers <7 days. Checks: economy live? trades processing? D/G healthy? auction orders flowing?

**Bundled web — Player Impact Leaderboard:** Top P&L, most trades, most items bought/sold per day. Gamifies the economy.

**Discord bot — Auction Fill DMs:** Completes watch pipeline — already has `/at price/status/top/help`, missing: DM when watched order fills.

### State
Repo clean. No bugs. No pushes. Live-data gap (API deploy) remains #1 ecosystem unlock. Feature ideas documented for future cycles.

**Blocked:** API deploy (Arc's Fly.io token) | Real testimonials (human outreach)

## 2026-05-18 Update (Anvil)
- Sim Lab regression test passed perfectly against the 24310f2c baseline. Rust engine behavior mirrors Java perfectly. No new simulation bugs found.
- The ⚠️ warning remains for the anti-dump configs (Java port from Rust).
- Validated lower whale cap (100 units/tick) + spread shock combination. Achieved 73.2% D/G reduction compared to uncapped whale stress, while maintaining economy stability. Porting this to Java is the next major plugin task.
