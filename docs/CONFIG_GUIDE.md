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

**debt-gdp-circuit-breaker-ratio**: When system-wide total debt exceeds `GDP × ratio`, loan interest accrual is paused for that cycle. It auto-resumes when debt drops back below the threshold. Default 10.0 means circuit opens when debt is 10× the 24h GDP.

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
  debt-gdp-circuit-breaker-ratio: 5.0  # tighter circuit
  compound-interval-hours: 48           # slower compounding
```

---

## Recommended Starting Points

| Server Size | `fullEffectPlayers` | `base-spread` | `max-change` |
|-------------|---------------------|---------------|--------------|
| 1–5 players | 3 | 0.30 | 2.0 |
| 5–20 players | 10 | 0.20 | 1.5 |
| 20–50 players | 15 | 0.15 | 1.0 |
| 50+ players | 20 | 0.10 | 0.5 |

Auto-Tune's market engine is stable across the full parameter range (840-config sweep confirmed). Start conservative and tune based on observed behavior, not preemptively.
