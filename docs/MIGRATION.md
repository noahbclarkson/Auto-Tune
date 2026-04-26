# Migration Guide: Auto-Tune rewrite-2

> **rewrite-2** is a near-complete rebuild of Auto-Tune. This guide covers every breaking change, removed features, new requirements, and configuration differences between the old plugin (main branch) and this version.

**Read this guide before upgrading a live server.**

---

## Overview

rewrite-2 is not a drop-in replacement. The market engine, configuration schema, database layout, auction system, web server, and API architecture have all changed significantly.

Estimated upgrade time on a test server: **1–2 hours** for a typical setup.

---

## Breaking Changes

### Auction House — Moved to In-Game

The auction house is **no longer** a cross-server web feature. It is now fully contained in the Java plugin as an in-game GUI (`/auction` command).

**What changed:**
- Old: `POST /api/auction/...` endpoints on the Rust API server
- New: `/auction` command — browse, place sell orders, fill buy orders, cancel — all in-game
- No cross-server auction presence in rewrite-2

**Action:** If you relied on the old web auction, that workflow is gone. The new auction is in-game only.

---

### Config Format — New Hierarchy

The `config.yml` structure has been replaced with a nested `AutoTuneConfig` record hierarchy. Old config keys may not map directly.

**Key changes:**
- Spread parameters moved into a `spread {}` block
- Player scaling has its own `player-scaling {}` block
- Loan circuit breaker added: `loans.debt-gdp-tier3-ratio` (default: 30.0 — later raised from initial 10.0)
- New `autosell {}` block with `sound-effects`, `minimum-price-threshold`
- New `treasury {}` block for dynamic tax collection
- `web {}` block added for the built-in Javalin dashboard (port, auth)

**Action:** Start from the new default config (`config.yml` in the plugin jar) rather than migrating your old config. Copy over only the values you intentionally changed.

---

### Database — Fresh Install Required

The database schema is entirely new. There is no migration path from the old `autotune.db` or equivalent.

**What changed:**
- Schema completely redesigned (see `src/main/resources/db/V1__Initial_Schema.sql`)
- `max_price` and `min_price` columns removed — volatility is managed by engine parameters instead
- `autosell_items` table added (per-item autosell settings)
- `item_ratios` table added (cross-server price relationships)
- `price_alerts` table added
- Economy snapshots and transaction history are stored differently

**Action:** Plan a fresh economy start. There is no way to import old price or transaction history.

---

### Commands — Cloud 2.x Syntax

Commands use [Cloud 2.x](https://github.com/CloudWright/cloud) (Incendo fork). Command structure changed:

| Old | New |
|-----|-----|
| `/atshop` | `/shop` |
| `/atsell` | `/sell` |
| `/atautosell` | `/autosell` |
| `/atloan` | `/loan` |
| `/atadmin` | `/autotune admin` |
| `/atprice` | `/autotune price` |

Sub-command syntax is new. Run `/autotune help` in-game for the current command tree.

---

### Dependencies

**Required:**
- Paper 1.21.4 or a recent fork (Glowstone, Paper, Purpur, etc.)
- **Vault** — must be installed and an economy provider active (EssentialsX, etc.)
- An economy plugin that Vault can talk to

**Optional:**
- **PlaceholderAPI** — for `%autotune_price_<material>%` and economy placeholders

**Removed:**
- No MySQL requirement by default (SQLite works fine; MariaDB optional)
- No external web server required — Javalin is bundled

---

### Web Server

A Javalin web server is now **bundled in the plugin**. It serves the Next.js dashboard automatically.

- **Port:** 8989 (default, configurable via `web.port`)
- **Dashboard:** `http://your-server:8989` — prices, economy, loans, leaderboard
- **Auth:** Optional basic auth (configure in `web.auth`)
- **WebSocket:** Real-time price updates pushed to connected browsers

**Action:** Open port 8989 in your firewall if you want the dashboard accessible. It is local-only by default.

---

### Price Reporting — New Architecture

Price reporting to the cross-server API has changed:

**Old:** Price data sent directly to the auction API  
**New:** `PriceReporter` class submits a **ratio matrix** (item-to-item relative prices) to the Rust API server. True prices are computed server-side via constrained least-squares.

```yaml
# New config section
price-reporter:
  api-url: "https://api.yourdomain.com"   # Your deployed Rust API server
  api-key: "at_sk_..."                    # From POST /api/servers/register
  server-id: "uuid-from-registration"
  interval-seconds: 300                   # Submit every 5 minutes
```

If you don't deploy the Rust API server, you can disable price reporting entirely — the plugin works fully without it.

---

### API Server — Separate Deployment

The Rust API server (`api-server/`) is a **separate deployment** from the plugin. It is not bundled.

To enable cross-server true prices:
1. Deploy `api-server/` (see `api-server/README.md`)
2. Point `price-reporter.api-url` at it in your plugin config
3. Register your server via `POST /api/servers/register`

Without the API server, the in-game economy and bundled dashboard work fully.

---

### Market Engine — Different Default Behaviour

rewrite-2 uses a **new pricing model** compared to the old flat-price system:

| Aspect | Old | New |
|--------|-----|-----|
| Price changes | Admin-set or fixed | Supply/demand driven, per-tick |
| Spreads | Fixed BPD/SPD | Dynamic, 5-factor pipeline |
| Player count effect | None | Tanh-scaled spread compression |
| Volume liquidity | None | Per-item spread reduction |
| Trade history | Simple average | Recency-weighted window |
| Sector correlation | None | Related items nudge together |
| Loan circuit breaker | None | Pauses interest if debt/GDP > 10× |

**Implications:**
- Prices will settle away from base prices naturally (typically 40–70% below base in balanced economies)
- The `sell_pressure_multiplier` (default: 1.0) is symmetric. Setting 0.80 improves GDP ~5% but worsens D/G ~40% — not recommended as a default. Only use 0.80 for growth-oriented servers willing to accept higher loan exposure.
- `baseSpread` (default: 0.20) controls the buy/sell spread width — raise it for wider spreads
- See `docs/CONFIG_GUIDE.md` for tuning recommendations by server size

---

### Shop Items — Enchanted Items Now Priced

Enchanted items now have sell values calculated from base material + cumulative enchantment multipliers:

```
enchanted_sell_price = base_material_price × Π(enchantment_multipliers)
```

| Enchantment | Per-Level Multiplier |
|-------------|---------------------|
| Sharpness | ×1.25 |
| Efficiency | ×1.30 |
| Protection | ×1.20 |
| Power | ×1.25 |
| ... and more (see `EnchantmentPricing.java`) |

Enchanted items that don't have an exact shop entry fall back to the base material price.

---

## What Was Removed

- **Static price overrides** — replaced by per-item config overrides (`/autotune price setmax|setmin|reset`)
- **Direct MySQL as default** — SQLite is now the default; MariaDB is optional
- **Separate auction web app** — auction is now entirely in-game
- **Shop GUI from old system** — rebuilt with InventoryFramework

---

## What Is New (Key Features)

- **Dynamic tax system** — `TreasuryService` collects buy/sell/auction/loan-interest tax into server treasury (`/treasury` commands)
- **Per-item autosell minimum price** — players can set floor prices (`/autosell minprice <material> [price]`)
- **In-game price alerts** — `/alert add|list|remove|rearm|toggle` with ABOVE/BELOW triggers
- **MarketHistoryGui** — `/autotune trends` shows in-game price history charts
- **Per-item config overrides** — admin commands to set per-item spread and max price change
- **PlaceholderAPI integration** — 12 placeholders for use in scoreboards, TAB, etc.
- **PriceReporter retry queue** — bounded 5-entry retry queue, 3 attempts, won't drop submissions when API is down

---

## Upgrade Checklist

```
Before upgrade:
[ ] Read this entire guide
[ ] Back up your current server (full backup, not just plugin files)
[ ] Test rewrite-2 on a staging server first
[ ] Note all your non-default config values (you'll need to re-apply them)
[ ] Decide on your database: SQLite (default) or MariaDB
[ ] Plan for a fresh economy — old transaction/price history cannot be imported
[ ] If using auction: brief your players on the new /auction command

During upgrade:
[ ] Stop your server
[ ] Remove the old Auto-Tune jar from plugins/
[ ] Place the new jar in plugins/
[ ] Start the server — default config.yml will be generated
[ ] Update config.yml with your intended values
[ ] If using MariaDB: update database connection settings
[ ] If using the API server: deploy it, note the URL and API key
[ ] If using price reporting: configure price-reporter section
[ ] Restart the server
[ ] Verify: /autotune help works, /shop shows items, /auction opens GUI
[ ] Check the dashboard at http://your-server:8989
[ ] Monitor for the first few hours — watch spread widths and price drift

After upgrade:
[ ] Update any scoreboard/tab configs using old placeholder names
[ ] Update any API integrations that used the old auction endpoints
[ ] Brief players on /auction if you use the auction feature
[ ] Review docs/CONFIG_GUIDE.md for tuning recommendations
```

---

## Getting Help

- **Discord:** [Auto-Tune Discord](https://discord.gg/bNVVPe5)
- **Issues:** [GitHub Issues](https://github.com/noahbclarkson/Auto-Tune/issues)
- **Docs:** `docs/` folder in this repo — CONFIG_GUIDE, SERVER_ADMIN_GUIDE, ARCHITECTURE, CONTRIBUTING

---

## Configuration Quick Reference

| Old Config Key | New Config Key | Notes |
|---|---|---|
| `economy.price-change-max` | `market.max-price-change-percent` | Default: 1.5% |
| `spread.base` | `spread.base-spread` | Default: 0.20 (20%) |
| `spread.volume-impact` | `spread.volume-impact` | Default: 0.8 |
| `loan.interest-rate` | `loans.base-interest-rate` | Default: 0.05 (5%) |
| N/A | `loans.debt-gdp-tier3-ratio` | Default: 30.0 (new!) |
| N/A | `autosell.sound-effects` | New block |
| N/A | `treasury.*` | New dynamic tax system |
| N/A | `price-reporter.*` | New price reporting config |
| `web.port` | `web.port` | Default: 8989 |
