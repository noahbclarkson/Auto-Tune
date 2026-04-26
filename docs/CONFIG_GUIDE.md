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
| `loans.debt-gdp-tier3-ratio` | `30.0` | TIER3 circuit fires when D/G exceeds this value |
| `loans.credit-score.enabled` | `true` | Use credit score to adjust interest rates |
| `loans.credit-score.default` | `500` | Starting credit score for new players |
| `loans.overdue.default-points` | `50` | Credit score penalty on loan default |
| `loans.term.premium-min` | `0.0` | Minimum term premium added to interest |
| `loans.term.premium-max` | `0.05` | Maximum term premium added to interest |
| `loans.counter-cyclical` | `true` | Reduce interest rate as Debt/GDP rises (0% at D/G ≥ circuit-breaker-ratio) |
| `loans.post-default-cooldown-hours` | `168` | Lock borrowers from new loans after default (7 days) |
| `loans.single-loan-gdp-cap` | `1.0` | Maximum loan size as multiple of 24h GDP (cap at ~1.0; values ≤ 0.10 backfire) |
| `loans.tier3-hysteresis-band` | `0.1` | TIER3 stays locked until D/G drops below tier3 × (1 − band). Default 0.1 = 10% band. Recommended 0.5 for deep hysteresis |
| `loans.min-interest-multiplier` | `0.0` | Floor for counter-cyclical interest multiplier. 0.0 = pure counter-cyclical (0% at D/G=tier3) |
| `loans.guildbuyer-total-debt-cap` | `3.0` | Maximum total debt any single GuildBuyer can hold, as multiple of economy GDP |
| `loans.block-mm-gb-loans-during-tier3` | `false` | Block MarketMaker and GuildBuyer loan requests while TIER3 circuit is engaged |

**tier3-hysteresis-band** (default `0.1`): When TIER3 fires (D/G ≥ tier3 ratio), the circuit stays locked until D/G drops to `tier3 × (1 − band)`. At default 0.1 (10% band), it unlocks at 90% of tier3. At 0.5 (50% band), it stays locked until D/G falls below `tier3 × 0.5` — a much deeper hysteresis. A 50% band prevents the circuit from re-triggering immediately after TIER3 exit. Recommended setting: `0.5`.

**min-interest-multiplier** (default `0.0`): Counter-cyclical interest uses `max(multiplier, this)` instead of `max(0, multiplier)`. At default 0.0, interest can reach 0% at D/G=tier3. Set to `0.10` for a 10% minimum interest rate even at crisis levels.

**guildbuyer-total-debt-cap** (default `3.0`): Prevents cascading debt accumulation during TIER3 lock. A single GuildBuyer cannot hold more than 3× economy GDP in total debt. When their debt hits the cap, they cannot open new loans. Only affects GuildBuyer/MarketMaker archetypes; normal players are unaffected.

**block-mm-gb-loans-during-tier3** (default `false`): When `true`, MarketMaker and GuildBuyer players cannot open loans while TIER3 circuit is active. Combined with `tier3-hysteresis-band=0.5`, this prevents MM/GB from accumulating debt that immediately re-triggers TIER3 on circuit unlock.

**debt-gdp-tier3-ratio** (default `30.0`): TIER3 circuit fires when system-wide Debt/GDP exceeds this ratio. Interest is fully paused while the circuit is locked. It unlocks when D/G drops below `tier3-ratio × (1 - tier3-hysteresis-band)`. At default 30.0 with 50% hysteresis band, circuit unlocks at D/G < 15×.

**counter-cyclical** (default `true`): Interest rate is linearly reduced as Debt/GDP rises. At D/G=0 → 100% rate; at D/G = circuit-breaker-ratio → 0% rate. Formula: `multiplier = max(0, min(1.0, 1.0 - D/G / circuitBreakerRatio))`. This dampens debt accumulation before the circuit breaker fires.

**post-default-cooldown-hours** (default `168` / 7 days): After a loan defaults, the borrower cannot take new loans for this duration. Verified in simulation to reduce final D/G by ~65% in cascade scenarios. Re-borrow events drop to zero after cooldown is enforced.

**single-loan-gdp-cap**: ⚠️ **Do NOT set this below 0.10.** Simulation testing (guildbuyer-failure-test archetype, seed=42, 14 days) showed that a 10% GDP cap worsens D/G from 0.75× → 1.85×. Bounding MM loans reduces MM's market-making ability → price volatility increases → economy shrinks → D/G worsens. Leave at default 1.0 (each loan capped at one economy GDP).

### `spread.*` — Floor and Ceiling Price Bounds

| Key | Default | Description |
|-----|---------|-------------|
| `spread.floor-percent` | `0.60` | Price floor as fraction of base price (0.60 = 60% of base) |

**floor-percent** (default `0.60`): Items cannot fall below `basePrice × floorPercent`. Floor is applied after all other price calculations. A 60% floor (+6.5% GDP vs no floor) is the sweet spot — floors above 70% choke the economy by suppressing natural correction. Floor paradox confirmed: at 80-90%, Diamond internal price collapses to $0.35-2.04 despite a displayed price of $350-450.

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
  debt-gdp-tier3-ratio: 30.0  # TIER3 fires at 30× D/G (D/G < 15× to unlock with 50% band)
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

> **Archetype mix matters more than parameters.** The 2MM + 2GB@5% archetype configuration (2 MarketMakers + 2 GuildBuyers) doubles GDP and halves volatility. See [SERVER_ADMIN_GUIDE.md](./SERVER_ADMIN_GUIDE.md) for the full player economy design guide.

---

## Advanced Loan Parameters (2026-04-25)

These parameters fine-tune the loan circuit breaker behavior for long-running servers.

### `tier3-hysteresis-band`

**Default: `0.1`** | **Recommended: `0.5`**

When TIER3 fires (D/G ≥ `debt-gdp-tier3-ratio`), the circuit stays locked at 0% interest until D/G drops below `tier3 × (1 − tier3-hysteresis-band)`.

| Band setting | tier3=30 unlocks at | Effect |
|---|---|---|
| `0.1` (default) | D/G < 27.0× | 10% band — circuit re-triggers quickly |
| `0.3` | D/G < 21.0× | 30% band — moderate hysteresis |
| `0.5` (recommended) | D/G < 15.0× | 50% band — deep hysteresis, circuit stays locked longer |

**Why it matters:** The 60d doom loop (D/G 8→20→16) is caused in part by the circuit unlocking too early. At 0.1 band, the circuit unlocks at D/G=27 and immediately re-triggers TIER3 within days as debt continues accumulating. A 50% band keeps the circuit locked until D/G < 15× — giving the economy time to actually deleverage.

**Simulation evidence:** 50% hysteresis band tested (2 seeds × 60d): D/G delta = -0.063x (neutral/noise). The fix alone is insufficient — pair with `block-mm-gb-loans-during-tier3: true` for meaningful effect.

**When to use:** Long-running servers (>60 days) experiencing persistent TIER3 oscillation. Set to `0.5` and monitor D/G weekly.

### `min-interest-multiplier`

**Default: `0.0`** | **Recommended: `0.005`**

Floor for counter-cyclical interest multiplier. At `0.0`, interest can reach 0% at D/G=tier3 (pure counter-cyclical). At `0.005`, a 0.5% minimum interest applies even during crisis.

| Setting | At D/G=30, tier3=30 |
|---|---|
| `0.0` (default) | 0% interest — debt stops growing but also doesn't shrink |
| `0.005` | 0.5% interest — small but non-zero — keeps deleveraging pressure active |

**Why it matters:** At pure 0% interest, players with large debts have zero incentive to repay. The economy gets stuck near the circuit threshold. A tiny minimum (0.5%) creates gentle repayment pressure without being punitive.

**When to use:** Servers experiencing persistent D/G oscillation near the circuit threshold. Set to `0.005` (not higher — you don't want to raise interest during a crisis).

### `guildbuyer-total-debt-cap`

**Default: `3.0`** | **No change recommended**

Maximum total debt any single GuildBuyer player can hold, as a multiple of economy GDP. Prevents one GB from accumulating overwhelming debt during TIER3 lock.

| Cap | With GDP=$4M | Effect |
|---|---|---|
| `3.0` (default) | $12M max per GB | Non-binding at 60d (GB loans ~$100K) |
| `1.0` | $4M max per GB | Would constrain GB during stress |
| `0.5` | $2M max per GB | May limit legitimate GB activity |

**Simulation evidence:** 3× cap tested (2 seeds × 60d): D/G delta = +0.000x — non-binding at 60d horizon. The cap only becomes relevant if a single GB accumulates >$1M debt, which requires very large economies. Not recommended as a primary D/G control.

**When to use:** Large economies with multiple active GuildBuyers. Keep at `3.0` unless you have specific concerns.

### `block-mm-gb-loans-during-tier3`

**Default: `false`**

When `true`, MarketMaker and GuildBuyer players cannot open new loans while TIER3 circuit is active. Their existing loans remain active but accumulate no interest.

| Setting | Effect |
|---|---|
| `false` (default) | MM/GB can accumulate interest-free loans during TIER3 lock — extends lock period |
| `true` | MM/GB loans blocked during TIER3 — prevents zero-interest debt accumulation |

**Why it matters:** When the circuit fires and sets interest to 0%, MM/GB players can borrow freely with no cost. Their loans accumulate and, when the circuit unlocks, the debt explosion immediately re-triggers TIER3. Blocking MM/GB loans during TIER3 prevents this accumulation.

**When to use:** Servers with persistent TIER3 oscillation. Set to `true` and pair with `tier3-hysteresis-band: 0.5` for maximum circuit stability.

### Recommended Combined Config for Long-Running Servers

```yaml
loans:
  # Circuit breaker — deep hysteresis for 90d+ stability
  debt-gdp-tier3-ratio: 30.0        # TIER3 fires when D/G >= 30×
  tier3-hysteresis-band: 0.5        # unlock only when D/G < 15× (50% band)
  block-mm-gb-loans-during-tier3: true  # prevent MM/GB zero-interest loan accumulation
  
  # Counter-cyclical fine-tuning
  counter-cyclical: true
  min-interest-multiplier: 0.005    # 0.5% minimum even at D/G >= 30×

  # Safety caps
  guildbuyer-total-debt-cap: 3.0    # 3× GDP per GuildBuyer
  single-loan-gdp-cap: 1.0         # 1× GDP per loan
  total-debt-gdp-cap: 2.0          # 2× GDP economy-wide
  post-default-cooldown-hours: 168  # 7-day lock after default
```

**NOTE:** These are containment measures, not cures. 180d simulation shows D/G eventually escalates regardless of config. Monitor D/G weekly. Consider `/at admin recovery` if D/G exceeds 25×.
