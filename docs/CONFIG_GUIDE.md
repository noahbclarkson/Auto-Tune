# Configuration Guide

> Auto-Tune rewrite-2. Reference for server admins tuning their economy.

## How Config Works

`config.yml` is loaded by `ConfigManager.java` and parsed into the nested `AutoTuneConfig` record hierarchy. Every config key maps directly to a field in one of the config records.

## Top-Level Keys

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `update-interval` | integer | `6000` | Ticks between price recalculations (6000 = 5 minutes at 20 tps) |
| `max-price-change-percent` | decimal | `1.5` | Max base price change per tick as a percentage |
| `trade-window-days` | decimal | `7` | Days of transaction history to consider |
| `base-spread` | decimal | `0.20` | Total spread (BPD + SPD), e.g. 0.20 = 20% total spread |
| `market-frozen` | boolean | `false` | Pauses price calculation; trades still recorded |

---

## `storage.*` — External Database (MariaDB/MySQL)

Auto-Tune defaults to SQLite (`autotune.db` in the plugin data folder). For servers with many players or long runtimes, MariaDB or MySQL is recommended for better performance and backupability.

| Key | Default | Description |
|-----|---------|-------------|
| `storage.type` | `SQLITE` | `SQLITE` or `MYSQL` |
| `storage.host` | `localhost` | Database server hostname |
| `storage.port` | `3306` | Database server port |
| `storage.database` | `autotune` | Database name |
| `storage.username` | `root` | Database username |
| `storage.password` | `""` | Database password |
| `storage.pool.maximum-size` | `10` | Max connections in pool |
| `storage.pool.minimum-idle` | `2` | Always-open idle connections |
| `storage.pool.connection-timeout` | `30000` | ms before connect timeout |
| `storage.pool.idle-timeout` | `600000` | ms before idle connection removed |
| `storage.pool.max-lifetime` | `1800000` | ms before connection recycled |

**Example MySQL config:**
```yaml
storage:
  type: MYSQL
  host: 192.168.1.100
  port: 3306
  database: autotune
  username: autotune_user
  password: "s3cur3p@ssw0rd!"
  pool:
    maximum-size: 10
    minimum-idle: 2
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
```

> **MariaDB is recommended** over MySQL for compatibility with Auto-Tune's JDBI SQL dialect. MySQL 8+ works but some queries use MariaDB-specific `ON CONFLICT` syntax. If using MySQL, test thoroughly before production.

---

## `spread.*` — Spread Behavior

| Key | Default | Description |
|-----|---------|-------------|
| `spread.volume-impact` | `0.8` | How strongly volume imbalance shifts BPD vs SPD (0–1) |
| `spread.player-impact` | `0.6` | How strongly player count reduces spreads (0–1) |
| `spread.liquidity-coeff` | `0.01` | Coefficient for volume-based spread tightening |
| `spread.liquidity-full-effect-traders` | `10` | Unique traders needed for maximum liquidity effect |

**volume-impact** (0–1): Higher values mean a single directional trade imbalance shifts spreads more aggressively. At 0, imbalance has no effect on spreads.

**player-impact** (0–1): Higher values mean spreads tighten more as players join. At 0, player count has no effect on spreads.

**liquidity-coeff**: Higher values = more spread compression for high-volume items. Together with `liquidity-full-effect-traders`, controls how quickly spread compression saturates.

---

## `player-scaling.*` — Player Count Scaling

| Key | Default | Description |
|-----|---------|-------------|
| `player-scaling.full-effect-players` | `10` | Online players needed for ~99% price effect |

The scaling formula uses a `tanh` curve: `tanh(onlineCount * atanh(0.99) / fullEffectPlayers)`. At `fullEffectPlayers=10`, 10 online players gives ~99% scaling. At 20 players, scaling is ~99.97% — diminishing returns beyond.

---

## `loans.*` — Loan System

| Key | Default | Description |
|-----|---------|-------------|
| `loans.base-interest-rate` | `0.05` | Annual-ish interest rate (5% per compound) |
| `loans.compound-interval-hours` | `24` | Hours between interest compounds |
| `loans.max-loan-multiplier` | `2.0` | Max loan size as multiple of player's total traded value |
| `loans.debt-gdp-circuit-breaker-ratio` | `10.0` | Pauses interest when total debt exceeds GDP × this |
| `loans.credit-score.enabled` | `true` | Use credit score to adjust interest rates |
| `loans.credit-score.default` | `500` | Starting credit score for new players |
| `loans.overdue.default-points` | `50` | Credit score penalty on loan default |
| `loans.term.premium-min` | `0.0` | Minimum term premium added to interest |
| `loans.term.premium-max` | `0.05` | Maximum term premium added to interest |
| `loans.counter-cyclical` | `true` | Reduce interest rate as Debt/GDP rises (0% at D/G ≥ circuit-breaker-ratio) |
| `loans.post-default-cooldown-hours` | `168` | Lock borrowers from new loans after default (7 days) |
| `loans.single-loan-gdp-cap` | `1.0` | Maximum loan size as multiple of 24h GDP (cap at ~1.0; values ≤ 0.10 backfire) |

**debt-gdp-circuit-breaker-ratio**: When system-wide total debt exceeds `GDP × ratio`, loan interest accrual is paused for that cycle. It auto-resumes when debt drops back below the threshold. Default 10.0 means circuit opens when debt is 10× the 24h GDP.

**counter-cyclical** (default `true`): Interest rate is linearly reduced as Debt/GDP rises. At D/G=0 → 100% rate; at D/G = circuit-breaker-ratio → 0% rate. Formula: `multiplier = max(0, min(1.0, 1.0 - D/G / circuitBreakerRatio))`. This dampens debt accumulation before the circuit breaker fires.

**post-default-cooldown-hours** (default `168` / 7 days): After a loan defaults, the borrower cannot take new loans for this duration. Verified in simulation to reduce final D/G by ~65% in cascade scenarios. Re-borrow events drop to zero after cooldown is enforced.

**single-loan-gdp-cap**: ⚠️ **Do NOT set this below 0.10.** Simulation testing (guildbuyer-failure-test archetype, seed=42, 14 days) showed that a 10% GDP cap worsens D/G from 0.75× → 1.85×. Bounding MM loans reduces MM's market-making ability → price volatility increases → economy shrinks → D/G worsens. Leave at default 1.0 (each loan capped at one economy GDP).

### `spread.*` — Floor and Ceiling Price Bounds

| Key | Default | Description |
|-----|---------|-------------|
| `spread.floor-percent` | `0.60` | Price floor as fraction of base price (0.60 = 60% of base) |

**floor-percent** (default `0.60`): Items cannot fall below `basePrice × floorPercent`. Floor is applied after all other price calculations. A 60% floor (+6.5% GDP vs no floor) is the sweet spot — floors above 70% choke the economy by suppressing natural correction. Floor paradox confirmed: at 80-90%, Diamond internal price collapses to $0.35-2.04 despite a displayed price of $350-450.

---

## `guildbuyer.*` — GuildBuyer Archetype

| Key | Default | Description |
|-----|---------|-------------|
| `guildbuyer.enabled` | `true` | Enable GuildBuyer player archetype |
| `guildbuyer.guild-price-dip-threshold` | `0.07` | Buy when price falls within this fraction of perceived value (7% = recommended) |

**guild-price-dip-threshold**: The single most important GuildBuyer parameter. Simulation found:
- **7%** (default): Uniquely safe — D/G < 0.1× consistently across all seeds
- **5%**: Net positive but D/G varies wildly (0.03× to 27× across seeds — catastrophic on some)
- **≥10%**: Selectivity causes massive single purchases on credit → debt spiral (D/G 5-20×)

Recommendation: leave at 7%. Lower values require careful monitoring.

---

## `enchantment.*` — Enchantment Pricing

| Key | Default | Description |
|-----|---------|-------------|
| `enchantment.enabled` | `true` | Apply enchantment multipliers to enchanted item prices |

Enchanted items sell at `baseMaterialPrice × cumulativeMultiplier`. Multipliers are per-enchantment, per-level, defined as YAML lists:

```yaml
enchantment:
  multipliers:
    SHARPNESS: [1.25, 1.50, 1.75, 2.00, 2.50, 3.00]
    EFFICIENCY: [1.30, 1.60, 1.90, 2.20, 2.60, 3.00]
    FORTUNE: [1.50, 2.00, 2.50]
    UNBREAKING: [1.20, 1.40, 1.60]
```

An item with Sharpness II + Efficiency IV sells at `basePrice × 1.75 × 2.20` = 3.85× base price.

---

## `web.*` — Bundled Dashboard

| Key | Default | Description |
|-----|---------|-------------|
| `web.enabled` | `true` | Enable the built-in web dashboard |
| `web.port` | `8989` | HTTP port for the dashboard |
| `web.bind` | `0.0.0.0` | Interface to bind to |
| `web.password` | (none) | Optional password protection |

---

## `autosell.*` — Auto-Sell

| Key | Default | Description |
|-----|---------|-------------|
| `autosell.enabled` | `true` | Enable per-player autosell |
| `autosell.broadcast` | `false` | Announce autosell transactions in chat |
| `autosell.minimum-price` | `0.01` | Items below this price are NOT autosold (0 = sell everything) |
| `autosell.sound-on-pickup` | `ENTITY_ITEM_PICKUP` | Sound on autosell of picked-up items |
| `autosell.sound-on-inventory-sell` | `UI_LOOT_YOUR_FILLED_CONTAINER` | Sound after inventory autosell |

---

## `cleanup.*` — Database Cleanup

Prevents unbounded growth. All sections are independently configurable.

| Key | Default | Description |
|-----|---------|-------------|
| `cleanup.cleanup-interval-hours` | `24` | How often to run cleanup (0 = disabled) |
| `cleanup.transactions.retention-days` | `14` | Trade transaction history |
| `cleanup.market-history.retention-days` | `7` | Price/volume sparkline history |
| `cleanup.economy-snapshots.retention-days` | `30` | Economy-wide GDP/debt snapshots |
| `cleanup.auction-orders.retention-days` | `30` | Terminal auction orders (FILLED/EXPIRED/CANCELLED only) |
| `cleanup.auction-fills.retention-days` | `60` | Individual auction fill records |
| `cleanup.market-events.retention-days` | `7` | Ended market event history |

Active OPEN / PARTIALLY_FILLED auction orders are **never deleted** regardless of retention settings.

---

## `auction.*` — Auction House

In-game order-book auction system. Players place buy/sell orders, matched by price-time priority.

| Key | Default | Description |
|-----|---------|-------------|
| `auction.default-duration-hours` | `72` | How long orders remain active before expiring |
| `auction.expiration-check-interval-minutes` | `15` | How often to process expired orders (0 = disabled) |

**Expiration behavior:**
- Buy orders: escrowed funds are **automatically refunded** when expired
- Sell orders: items are **NOT returned automatically** — players must reclaim via `/auction reclaim`

Set `expiration-check-interval-minutes: 0` only if you want to manage order expiry manually via `/auction cancel`.

---

## `market-events.*` — Dynamic Market Events

Server-wide events that amplify or dampen price movements for matching items. Events affect **price velocity** (how fast prices change), not absolute prices — natural market forces still apply, so exploits are not possible.

| Key | Default | Description |
|-----|---------|-------------|
| `market-events.enabled` | `true` | Enable market events |
| `market-events.check-interval-minutes` | `5` | How often to check event lifecycle |

**Event types:**

| Type | Effect |
|------|--------|
| `DEMAND_SURGE` | Amplifies upward price moves |
| `SUPPLY_GLUT` | Amplifies downward price moves |
| `INFLATION_BOOST` | Always adds upward drift |
| `DEFLATION_DROP` | Always adds downward drift |
| `GOLD_RUSH` | Symmetric multiplier on price changes |
| `CUSTOM` | Custom multiplier (specify `value`) |

**Material patterns:** Supports wildcards — `"DIAMOND"` (exact), `"GOLD_*"` (prefix), `"*_INGOT"` (suffix).

Events are triggered at runtime via `/at event` commands — not persisted in config. Use `/at event schedule` to pre-plan events.

---

## `scoreboard.*` — Economy Scoreboard

Per-player sidebar scoreboard showing live economy stats.

| Key | Default | Description |
|-----|---------|-------------|
| `scoreboard.enabled` | `false` | Show scoreboard to all players on join |
| `scoreboard.title` | `Auto-Tune Economy` | Sidebar title (max 32 chars) |
| `scoreboard.update-interval-seconds` | `30` | How often to refresh (min 10) |

Players see: GDP, Debt, Loans, Activity, Inflation. Each stat updates in place — no flickering.

---

## Admin Override Commands

| Command | Description |
|---------|-------------|
| `/at admin market freeze` | Toggle market freeze (pauses price calc, not trades) |
| `/at admin price set <item> <price> [hours]` | Override an item's price with optional TTL |
| `/at admin price remove <item>` | Remove a price override |
| `/at admin price list` | List all active overrides |
| `/at admin info` | Show market status, GDP, debt, loans, online |
| `/at admin stats` | Show item count, total market cap, avg spread |
| `/at admin reload` | Reload config and caches |

---

## Tuning Cookbook

### Tighter Spreads (high-liquidity server)

```yaml
base-spread: 0.10
spread:
  volume-impact: 0.9
  player-impact: 0.7
  liquidity-coeff: 0.02
```

### Wide Spreads (low-liquidity server)

```yaml
base-spread: 0.30
spread:
  volume-impact: 0.6
  player-impact: 0.4
  liquidity-coeff: 0.005
```

### Slow Price Changes (stable economy)

```yaml
max-price-change-percent: 0.5
player-scaling:
  full-effect-players: 5
```

### Fast Price Changes (volatile economy)

```yaml
max-price-change-percent: 3.0
player-scaling:
  full-effect-players: 20
```

### Prevent Debt Spiral (high-loan-risk server)

```yaml
loans:
  debt-gdp-circuit-breaker-ratio: 5.0  # tighter circuit (fires at 5× GDP vs default 10×)
  compound-interval-hours: 48           # slower compounding (every 2 days vs 1)
  counter-cyclical: true                # reduces interest as D/G rises (default: true)
  post-default-cooldown-hours: 168      # 7-day lock after default (default: 168)
```

The simulation-recommended approach: **counter-cyclical + post-default cooldown + default circuit-breaker ratio**. This reduces D/G by ~65% in cascade scenarios. Do NOT set `single-loan-gdp-cap` below 0.10 — it constrains MarketMaker inventory and worsens price volatility, which paradoxically increases D/G.

---

## Recommended Starting Points

| Server Size | `fullEffectPlayers` | `base-spread` | `max-change` |
|-------------|---------------------|---------------|--------------|
| 1–5 players | 3 | 0.30 | 2.0 |
| 5–20 players | 10 | 0.20 | 1.5 |
| 20–50 players | 15 | 0.15 | 1.0 |
| 50+ players | 20 | 0.10 | 0.5 |

Auto-Tune's market engine is stable across the full parameter range (840-config sweep confirmed). Start conservative and tune based on observed behavior, not preemptively.

> **Archetype mix matters more than parameters.** The 2MM + 2GB@7% archetype configuration (2 MarketMakers + 2 GuildBuyers) doubles GDP and halves volatility. See [SERVER_ADMIN_GUIDE.md](./SERVER_ADMIN_GUIDE.md) for the full player economy design guide.
