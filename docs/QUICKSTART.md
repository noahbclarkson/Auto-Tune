# Admin Quickstart — 5 Decisions Before You Launch

_This is the short version. For full details, see the Server Admin Guide._

Auto-Tune works out of the box with sensible defaults. These five decisions are where most admins spend time — and where the right choice makes a big difference.

---

## Decision 1: Who are your players?

Your **player archetype mix** is the single biggest driver of economy health. More MarketMakers = tighter spreads and bigger GDP. More GuildBuyers = more stable prices and lower debt.

| Archetype | What it does |
|-----------|-------------|
| **MarketMaker** | Posts buy and sell orders, tightens spreads |
| **GuildBuyer** | Buys items when prices dip, creates demand floor |
| **Farmer** | Gathers and sells, drives oversupply |
| **Casual** | Light trading activity |
| **Trader** | Buys and sells roughly equally |

**Recommended for most servers:** 2 MarketMakers + 2 GuildBuyers + the rest Casual/Farmer/Trader

This archetype mix is tested across 5 simulation seeds and produces: GDP +99.7% vs 1MM, volatility -48.9%, spreads -21.4%.

```yaml
# In config.yml — archetype tuning is in the simulation config
# For the Java plugin, archetype mix is determined by your player base
```

---

## Decision 2: Do you want the loan system?

Loans let players borrow money and create demand. The circuit breaker prevents catastrophic debt spirals.

**Turn loans ON if:** You want a deeper economy with credit, interest, and debt management.

**Turn loans OFF if:** You want a pure buy/sell economy with no debt mechanics.

```yaml
# Enable loans (default)
loans:
  enabled: true
  base-interest-rate: 0.05        # 5% annual
  post-default-cooldown-hours: 168  # 7 days before can borrow again

# Disable loans
loans:
  enabled: false
```

---

## Decision 3: Do you want a price floor?

A price floor (e.g., Diamond at $300) prevents items from collapsing to near-zero during oversupply events. The floor protects sellers but slightly worsens debt-to-GDP in healthy economies. In stressed economies, it acts as an economic circuit breaker.

| Floor strength | Effect |
|---------------|--------|
| **30–40%** | Never binds — natural equilibrium above floor |
| **50%** | Barely binds — marginal seller protection |
| **60%** | ✅ Best balance — meaningful protection, minimal GDP cost |
| **70%+** | Chokes the economy — don't use |

**Recommended:** 60% floor for Diamond-type items (i.e., `price_floor: 300` for an item with `base_price: 500`)

```yaml
# Per-item floor via command:
# /at admin item floor DIAMOND 300

# Or in config for items that have a base_price:
items:
  DIAMOND:
    base_price: 500
    # market engine applies floor at 60% of base = $300
```

---

## Decision 4: Do you want market events?

Market events let you temporarily amplify or suppress price changes for specific items or categories. Great for seasonal content (Christmas rush, summer drought) or server events.

```yaml
market-events:
  boss-bar:
    enabled: true   # Shows event announcement as a boss bar
  events:
    christmas_rush:
      type: DEMAND_SURGE
      materials: [DIAMOND, GOLD_INGOT, IRON_INGOT]
      multiplier: 1.5
      duration-minutes: 60
```

```bash
# Trigger immediately:
/at event invoke christmas_rush

# Schedule for later:
/at event schedule DEMAND_SURGE DIAMOND 1.5 60 30
#                                                        ^ starts in 30 minutes

# List templates:
/at event templates
```

---

## Decision 5: Do you want the economy digest?

The digest sends a weekly economy health report to a Discord webhook — no login required.

```yaml
market-digest:
  enabled: true
  webhook: "https://discord.com/api/webhooks/..."  # Your Discord webhook URL
  sections:
    health: true      # Composite health score + debt/gdp
    movers: true      # Biggest price movers
    loans: true       # New records, defaults, circuit breaker
    volume: true      # Transaction volume trends
    events: true      # Recent market events
```

```bash
# Send digest immediately:
/at admin digest send

# See digest config:
/at admin digest config
```

---

## The Defaults Are Good

If you don't touch anything, Auto-Tune will:
- Update prices every 5 minutes
- Keep spreads around 20% total (buy/sell gap)
- Run the loan system at 5% interest with a circuit breaker at 15× debt-to-GDP
- Let players trade freely 24/7

The defaults are calibrated from simulation evidence. Tweak after you see how your players interact with it.

---

## Next Steps

1. Install and start your server — watch the dashboard at `:8989`
2. After 24h, check `/at admin health` — is your buy ratio above 60%?
3. If spreads feel too wide, lower `base-spread` slightly (try 0.15)
4. If prices are too volatile, lower `max-price-change-percent` (try 1.0)
5. If debt is piling up, the circuit breaker handles it automatically

**Full config reference:** [Config Guide](CONFIG_GUIDE.md)
**Troubleshooting:** [Server Admin Guide → Common Issues](SERVER_ADMIN_GUIDE.md#common-issues--fixes)
