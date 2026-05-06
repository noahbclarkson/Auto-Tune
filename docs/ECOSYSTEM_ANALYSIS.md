# Ecosystem Analysis — 2026-04-26 (updated)

> **2026-04-26 update:** 8/8 TIER3 fix candidates FAILED — architectural fix needed. Circuit is a governor, not a cure. AdminRecovery must be used proactively (day 3–7, not day 10+). Whale archetype worsens D/G +37% but circuit contains it. Newbie confirmed irreplaceable by GuildBuyer.

> **2026-04-21 update:** 180-day test confirms economy does NOT stabilize past day 120. D/G escalates 16×→42× (seed 42). TIER3→NORMAL bypass is the only viable architectural fix.

> **2026-04-15 update:** sell_pressure=1.0 confirmed correct default; Newbie archetype fully characterized; Events confirmed harmful in healthy economies; Casual-heavy devastating for 2MM+2GB+floor.


> **2026-04-13 update:** New definitive archetype findings from 5-seed simulation lab.

## Definitive Archetype Recommendations (2026-04-13)

After multi-seed validation (5 seeds × 5 configs), the archetype effects are now settled:

| Config | Verdict | GDP Effect | D/G Effect |
|--------|---------|------------|------------|
| 2MM+2GB | ✅ Production default | baseline | baseline |
| +2IT (healthy) | ⚠️ Optional | **+30.1%** | +2.41× (watch D/G) |
| +2IT (stressed) | ⚠️ Mixed | **−12.2%** | **−1.32×** (counter-cyclical benefit) |
| +2VT | ❌ Never | **−9.2%** (healthy) / **−12.7%** (stressed) | neutral |
| +2IT+2VT | ❌ Catastrophic | **−25.6%** (IT gains reversed and tripled by VT) | −0.62× |
| +2AFKFarmer | ❌ Catastrophic | **−49.5%** GDP, **+65.9%** volatility | +21.4% (destabilizing) |
| +2Hoarder (replace 2Far) | ⚠️ Neutral | **+1.5%** (flat) | **−6.9%** (slight improvement) |

**Key mechanism:** VT fires on spread widening and price dislocations. In any economy, it amplifies the dominant directional pressure — sell cascades in stressed economies, debt amplification in IT-boosted economies. VT is always harmful in ANY config. IT+VT together is a 55-percentage-point GDP reversal from IT alone.

**IT behaves opposite in stressed vs healthy economies:** In healthy economies, ITs create buy pressure → GBs trigger more → multiplicative debt amplification → GDP rises but D/G worsens. In stressed economies, ITs buy during price dips (counter-cyclical) → they absorb sell pressure from Farmers/Hoarders → debt accumulation slows → D/G improves but GDP falls. This split verdict means ITs should only be added to HEALTHY economies with careful D/G monitoring.

**AFKFarmers are the most dangerous archetype tested:** AFKFarmers (5-15% online, dump at 0-3% margin when online, gather rapidly) cause a −49.5% GDP collapse and +65.9% volatility. Mechanism: offline accumulation → sudden dump → price spike crash → circuit breaker fires constantly. This is more destructive than IT+VT. If servers have AFK farmers, consider reducing `gather_rate` or adding a cooldown on large sells.

**Hoarders are essentially neutral:** Replacing 2 Farmers with 2 Hoarders yields GDP +1.5% (flat) and D/G −6.9% (slight improvement). Hoarders hold inventory → less supply → slightly higher prices → GuildBuyers trigger less → less debt. Effect is marginal but measurable.

**Updated SERVER_ADMIN_GUIDE.md:** VolumeTrader description updated to "Always harmful — −9% to −13% GDP; never recommended." Large server recommendation stripped of VT. GuildSeller confirmed dead-end retained.

---

## Key Findings (2026-04-15)

**sell_pressure_multiplier default corrected to 1.0:** Previous default 0.80 sacrificed D/G stability (+40%) for +5% GDP. Confirmed via 5-seed head-to-head: sp=0.80 → D/G 7.86x; sp=1.0 → D/G 4.73x (−40%). GDP trade-off: −5.2%. Correct default is symmetric 1.0. Admins wanting growth can set 0.80; admins wanting stability keep 1.0.

**Newbie archetype fully characterized:** Newbie is the "anti-Farmer" — 2.2× buy rate, minimal selling. Dramatically positive in BOTH healthy and stressed economies:
| Scenario | GDP | D/G | Volatility |
|----------|-----|-----|------------|
| Healthy (2MM+2GB) | **+41.5%** | +34.7% | **−68%** |
| Stressed (1MM+2GB) | **+33.3%** | **−42.1%** | **−35%** |
Mechanism: Newbies replace sell pressure with buy pressure. In healthy: demand soars → GDP up. In stressed: absorb sell glut → D/G improves. ALWAYS dramatically reduces volatility. Tradeoff: D/G worsens in healthy (Newbies borrow to fund buying). Best use: high-volatility servers, stressed economies.

**Events HURT healthy economies:** DEMAND_SURGE + SUPPLY_GLUT + INFLATION_BOOST + GOLD_RUSH applied to 2MM+2GB+floor → GDP −2.0%, D/G +0.634x WORSE. Seed 98765 went catastrophic (D/G 7.46x vs 4.87x control). **Do NOT enable frequent/strong events in production 2MM+2GB+floor. Events are only appropriate for stagnant economies.**

**Counter-cyclical=true confirmed correct default:** CC=true vs CC=false × 3 seeds → GDP identical (0.0% diff), D/G marginally better with CC=false (4.817x vs 5.026x). TIER3=0 in both arms. No measurable GDP cost, D/G equivalent. Keep as default.

**Casual-heavy (6Cas+1Far+1Tra) devastates 2MM+2GB+floor:** GDP −39.0%, D/G +1.399x worse, vol −37.5%. The 2MM+2GB+floor config is balanced for 3Cas+3Far+2Tra standard mix. **Casual-heavy servers need a different config (lower guild_buyer_multiplier, higher diamond floor). Archetype mix is a first-order concern.**

**Production recommendation (2026-04-20):** `2MM + 2GB @ 5% + 60% Diamond floor + counter-cyclical=true + tier3_ratio=30 + sell_pressure_multiplier=1.0`. (⚠️ **Updated 2026-05-06:** 90-day sim shows 60% floor → GDP -19.1%, D/G +1.6x worse vs no floor. Floor is a short-term seller-protection tool, not a long-run health mechanism. See CONFIG_GUIDE.md for current guidance.)

---

## State of the Project (2026-04-07)

Both frontends are feature-complete:
- `web/` (14 routes): all core features built, CSV export implemented, discovery overlay live
- `web-optimizer/` (15 routes): Setup Wizard implemented, all landing sections done, OG image generated
- `rewrite-2` at `39cafe0`, clean state

**Three SPECs exist, two are fully implemented:**
- ✅ SPEC-PORTFOLIO-CSV-EXPORT — Java endpoint built, frontend button live
- ✅ SPEC-POST-INSTALL-DISCOVERY-FUNNEL — DiscoveryOverlay on /items and /portfolio
- ⚠️ SPEC-SERVER-SETUP-WIZARD — Fully implemented in web-optimizer (8 components, wizard-data, route)

**rewrite-2 has unbuilt changes:** The Admin Advice feature (`EconomyAdvisor.java`) and `/at admin advice` command were added but the web-optimizer landing page hasn't incorporated them yet (no update to landing page sections).

## Ecosystem Observations

### What's Working
1. **Docs are comprehensive and role-based.** Admin path (README → QUICKSTART → SERVER_ADMIN_GUIDE → ECONOMY_CONCEPTS) and player path (README → PLAYER_QUICKSTART) are solid. Players now have dedicated onboarding docs.
2. **Setup Wizard is production-ready.** All 8 components built, wizard-data.json pre-computed from sim data, no exec needed.
3. **CSV export is live.** Players can export trading history — makes Auto-Tune feel like a real platform.
4. **Discovery overlay teaches new players.** Tips on /items and /portfolio help players discover features they wouldn't find organically.

### What's Missing (Ecosystem Gaps)

**1. The landing page doesn't sell the new features.**
The landing page hero mentions "live demo" and "simulator" but doesn't highlight: Setup Wizard, CSV export, discovery funnel, or the recent `/shop info` transparency feature. These are concrete differentiators vs competitors.

**2. No "Social Proof" for actual usage.**
web-optimizer shows GitHub stars but no real server count, trade volume, or active economy statistics. The API server's aggregated stats are never shown publicly. A live ticker ("X servers | Y trades today | Z items priced") would be powerful social proof.

**3. Players can star items but there's no UI for it.**
The Java plugin has `/api/shop/favorites/{playerName}` endpoints fully built. But there's no star button in the web/ shop UI and no favorites section on portfolio. This is a 30-minute frontend addition that would drive engagement.

**4. `/shop info` command is plugin-only, not in web dashboard.**
The new `/at admin advice` and `/shop info <item>` commands give price attribution in-game. But the web/ dashboard (which players use more than in-game commands) has no equivalent "Why did this price move?" feature on item detail pages.

**5. No player onboarding sequence.**
New players arrive at a server, open `/shop`, see prices, and leave. They don't know about `/compare`, `/loans`, `/portfolio`, `/auction`, or market events. The discovery overlay helps on first visit but there's no follow-up. A 7-day progressive onboarding series (in-game mail or Discord DM) would dramatically increase feature engagement.

### Security Concerns (API Server)

**Sybil attack on server registry:**
Any actor can register fake servers and submit manipulated ratio matrices to poison the true-price solver. The 3σ outlier filter helps but doesn't prevent coordinated Sybil attacks with many fake servers. Manual server key issuance is the correct mitigation — needs a web portal.

**Cross-server exchange rate exploitation:**
If Server A's exchange rate is computed against a manipulated true-price baseline, it could under/over-value its currency relative to other servers. The circuit breaker on debt prevents economic cascade, but exchange rates could still be weaponized for arbitrage. An exchange rate circuit breaker (cap the per-server multiplier) may be needed.

**Revenue model tension:**
Auto-Tune is free and open source. There's no revenue model. This means:
- No funding for dedicated API server hosting
- No incentive for server admins to join the network (free-rider problem)
- No resources for a server key issuance portal
Consider: GitHub Sponsors, optional "Auto-Tune Pro" features (extended analytics, Discord alerts), or a voluntary "support the network" donation link on web-optimizer.

## Feature Ideas (2026-04-07 Evening)

### TIER 1 — Quick Wins (Low Effort, High Impact)

**1. Shop Favorites UI (web/) — 30min**
Already has Java backend. Add ★ button to ItemCard, persist to `/api/shop/favorites`, show favorites on portfolio page. High engagement, trivial backend work.

**2. "What moved this price?" on web/ item detail — 1h**
In-game `/shop info <item>` was just added. Port the attribution logic to web/ `/items/detail` page. Show: last price change direction + magnitude, 7-day trend, recent large trades, active events. Reuses existing API endpoints.

**3. Landing page refresh — 1h**
Add Setup Wizard callout, CSV export mention, and `/shop info` to the hero section's feature list. Make the value proposition concrete above the fold.

**4. "Economy Health" embeddable badge (web-optimizer) — 2h**
Small HTML snippet: `<div id="autotune-health" data-server="name" data-key="apikey"></div>` + `<script src="https://autotune.live/badge.js">`. Shows GDP trend, D/G, circuit breaker status. Server admins embed on their forum/website. Generates referral traffic.

### TIER 2 — Medium Investment (Medium Impact)

**5. Progressive Player Onboarding (plugin) — 3h**
Track days since first join. Day 1: mail with economy tips. Day 3: tip about `/compare`. Day 7: tip about loans. A series of in-game mail messages that don't spam but educate. Low DB cost (player metadata).

**6. Live stats ticker on landing page (web-optimizer) — 2h**
When API server is deployed: show "X servers | Y trades today | Z items priced" as a live-updating ticker. Real-time social proof. Uses API server's aggregated `/api/stats` endpoint (needs to be added).

**7. Admin Audit Log command (plugin) — 3h**
`/at admin audit [days]` generates a markdown/text report: top movers, loan records, circuit breaker transitions, GDP/D/G trends. Can output to Discord webhook. Useful for admins who want to share economy performance.

### TIER 3 — Big Bets (High Ceiling)

**8. Player-to-player trade with price discovery (plugin) — 1day**
`/trade <player>` opens a GUI where both players drag items. The system shows estimated Auto-Tune value of each side (based on recent transaction prices). Players negotiate with market transparency. Reduces item dumping on static shops.

**9. Multi-language support (i18n) — 2days**
Spanish, Portuguese, Chinese language switcher for both web/ and web-optimizer/. All strings externalized. Auto-Tune has international reach — Spanish-speaking Minecraft servers would benefit greatly.

**10. Auto-Tune "Pro" optional tier (ecosystem) — ongoing**
Free tier: core plugin + bundled web dashboard. Pro tier (no paid hosting needed, just GitHub Sponsors): extended analytics, historical data export, Discord alerts with custom triggers, priority server key issuance. Provides a revenue path without hosting costs.

## Cross-Server Feature Analysis

### What Makes Sense
- **True prices** (ratio-based, anchor-scaled): Strong. Ratios are server-size agnostic, geometric mean is robust to outliers, anchor items provide scale.
- **Exchange rates** (per-server multiplier vs baseline): Risky if true-price baseline is poisoned, but valuable if the network is honest. Needs circuit breaker.
- **Per-server reputation weighting** (longer history = higher LS weight): Good incentive for sustained participation. Natural onboarding reward.

### What Creates Exploit Vectors
- **Server count displayed publicly**: Easy to fake. Use trade volume + items priced instead of server count.
- **Cross-server "item of the month"**: Gamification encourages coordinated manipulation. Add a "consensus required" threshold (e.g., must appear on 5+ servers).
- **Server health leaderboard**: Admins will fake health by modifying plugin output. Only show metrics that are hard to fake (trade volume, not D/G — D/G can be gamed by not taking loans).

## API Server Deployment — The #1 Blocker

Everything is wired and ready. The Rust API server code is complete. What remains:
1. Deploy to a public HTTPS URL (Railway, Fly.io, or similar)
2. Set `NEXT_PUBLIC_API_URL` in web-optimizer/.env
3. Build + push web-optimizer
4. Build server key issuance portal (simple web form + email)

Until #1 is done, web-optimizer's true-prices, exchange-rates, and servers pages show empty/mock data.
