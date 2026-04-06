# Auto-Tune FAQ

_Common questions from server admins, answered directly._

---

## General

### What is Auto-Tune?

Auto-Tune is a Minecraft server plugin that replaces static item shops with a dynamic supply-and-demand economy. Prices adjust automatically based on what players are actually buying and selling — no admin price editing required.

### What servers does it support?

Paper 1.21.4. Other versions may work but are not tested. Requires Java 21.

### Does it work with Vault?

Yes. Auto-Tune uses Vault for economy integration (player balances, transactions). Install Vault first.

### Does it replace EssentialsX shops?

Yes — and that's the point. Instead of a fixed-price shop grid, players can buy and sell any item any time at market prices. The `/shop` and `/sell` commands replace your existing shop plugin.

### Does it work alongside existing economy plugins?

Auto-Tune manages item prices and the shop. It uses Vault for money. If your current economy plugin also uses Vault, they may conflict. Best results with Auto-Tune as the sole economy manager.

---

## Pricing

### Prices aren't doing what I expected. Help.

**"I set Diamond to $500 but it settled at $200."** — This is normal. Base prices are *starting points*, not targets. Minecraft economies are naturally seller-heavy (more players gather than spend). Prices settle where supply meets demand, typically 40–70% below your base price.

**"Prices barely moved since I installed."** — Check your player volume. Auto-Tune needs active trading to update prices. A server with 3 players trading once a day won't have dynamic prices.

**"Prices changed too fast!"** — Lower `max-price-change-percent` (try 1.0 or 0.5). Also check `base-spread` — wide spreads dampen price movement.

**"All my prices dropped 60% overnight."** — You likely had a supply glut: a single player sold a huge volume of one item. The engine responded by lowering prices. This is correct behavior. Check `/at admin health` to see what happened.

See [Economy Concepts](ECONOMY_CONCEPTS.md) for a deeper explanation of why prices move.

---

## The Economy is Distressed

### Debt is piling up. Is this normal?

First, check your D/G ratio: `/at admin health`. A healthy economy with active loans typically runs 3–8× D/G.

If D/G is climbing rapidly:
1. **Check if the circuit breaker fired** — look for a yellow/red message about TIER 2 or TIER 3. The circuit breaker pauses interest when debt gets too high.
2. **Check your archetype mix** — if you have mostly Farmers and few active buyers, sellers accumulate debt because no one is buying their items.
3. **Check your base prices** — if items are priced too high, players can't afford to buy, creating a downward spiral.

**Recovery options:**
- `/at admin prices reset <item>` — reset a specific item to its base price
- `/at admin prices reset all` — reset all prices (use with caution — notifies players)
- **Add MarketMakers or GuildBuyers** if your player economy has too many farmers

### The circuit breaker keeps firing (TIER 3)

TIER 3 means debt-to-GDP exceeded 15×. Interest is paused. Here's why and what to do:

**Why it fired:**
- A player took on too much debt and defaulted
- Or a mass player exodus left debts unpaid

**What happens:** Interest is paused until D/G drops below 13.5× (10% hysteresis). New loans can still be taken but interest won't compound on existing debt.

**Recovery:**
- Let the economy grow naturally — GDP increases, D/G decreases
- Or temporarily increase `loans.min-interest` (not recommended long-term — see [Config Guide](CONFIG_GUIDE.md))
- The 60% price floor helps prevent cascading defaults in stressed economies

**Prevention for next time:**
- Keep `loans.counter-cyclical: true` (default)
- Consider a 60% price floor on key items (Diamond, Gold)
- Monitor D/G with `/at admin health` weekly

### I want to disable the loan system entirely

```yaml
loans:
  enabled: false
```

All existing debts remain. No new loans can be taken. Run `/at admin health` to confirm the loan section shows 0 active loans.

---

## Configuration

### What config should I start with?

Use the [Quickstart Guide](QUICKSTART.md) for the 5 key decisions. For most SMP servers, the defaults are solid — just enable the 60% price floor on Diamond-type items:

```
/at admin item floor DIAMOND 300
/at admin item floor GOLD_INGOT 150
```

### How do I set prices for items that don't have base prices?

Items must have a `base_price` in `shops.yml` to be tracked. Add items manually:

```yaml
shops:
  items:
    DRAGON_HEAD:
      base_price: 5000
```

Or use the admin command:
```
/at admin item baseprice DRAGON_HEAD 5000
```

Items without a base price can't be bought or sold through Auto-Tune's shop.

### How do I add custom items (from other plugins)?

Same as above — add them to `shops.yml` with a `base_price`. Auto-Tune treats all items the same regardless of which plugin adds them to Minecraft.

### Can I import prices from my existing shop?

Use the bulk export/import commands:

```
/at admin prices export my_prices.csv
# Edit the CSV
/at admin prices import my_prices.csv
```

The CSV format: `material,base_price,buy_enabled,sell_enabled`

---

## Market Events

### What are market events?

Temporary price amplifiers/suppressors. A `DEMAND_SURGE` on Diamond makes Diamond prices rise faster while the event is active. Events are great for server events (Christmas, seasonal content, in-game holidays).

See [Server Admin Guide → Market Events](SERVER_ADMIN_GUIDE.md) for full details.

### My scheduled event didn't fire

Check: `/at event templates`. Is your template listed?

Check: `/at event list`. Is the event status SCHEDULED or ACTIVE?

Common reasons events don't fire:
- Event start time is in the past (scheduled for yesterday)
- Event duration is too short (it ended before the market tick)
- The material pattern doesn't match any items (`GOLD_*` won't match `GOLD_INGOT` if formatted wrong)

### Can players see active events?

Yes — if `boss-bar.enabled: true` (default), a boss bar appears when an event activates. Players can also check `/at events` to see active events.

---

## The Dashboard

### The bundled dashboard (`:8989`) shows no data

1. Is the web server enabled? Check config: `web-server.enabled: true`
2. Is port 8989 accessible? (check your firewall)
3. Has anyone traded yet? The dashboard shows data after the first trades are recorded.
4. Check `/at admin health` — does it show data? If not, the database isn't recording trades.

### How do I embed the dashboard in a website?

The dashboard is a Next.js app bundled in the plugin JAR. It's designed to be served by the plugin's built-in Javalin web server, not embedded externally.

For external access, proxy port 8989 through nginx/Caddy:

```nginx
location /economy/ {
  proxy_pass http://127.0.0.1:8989/;
}
```

### Can I use the dashboard without in-game access?

Yes — the dashboard is a standalone web app. Any admin with the server URL can monitor the economy from a browser. Password-protect with a reverse proxy if needed.

---

## Cross-Server (API Server)

### How does cross-server pricing work?

Servers that opt in submit *ratio matrices* to the API server (not prices, not player data). The solver computes "true prices" — the set of prices consistent with all servers' ratios. New servers can seed their prices from this consensus.

See [Economy Concepts → Cross-Server Ecosystem](ECONOMY_CONCEPTS.md#the-cross-server-ecosystem) for details.

### I'm getting "API unreachable" on the web dashboard

The bundled dashboard connects to `http://localhost:8080` by default for API data. Update `api-server.url` in config.yml to point to your deployed API server.

### How do I get an API key?

Contact the Auto-Tune maintainers. Server keys are issued manually to prevent Sybil attacks. Once you have a key, register at `/servers` on the web-optimizer site.

---

## Performance

### Does Auto-Tune lag my server?

Auto-Tune's market engine runs every 5 minutes (configurable: `market.tick-interval-minutes`). The tick processes all trades in the window, updates prices, and runs loan interest. On a server with <500 players and <10K items tracked, the tick takes <50ms.

The web server (Javalin on port 8989) runs on a separate thread and doesn't affect game tick performance.

### My database is huge

Enable the cleanup manager in config:

```yaml
cleanup:
  enabled: true
  interval-hours: 24
```

This prunes old transactions (14d retention), market history (7d), economy snapshots (30d), and auction data (30d). Run `/at admin cleanup` to trigger a cleanup immediately.

### Does it work with SQLite or MySQL?

Both. SQLite is the default (zero config). For production servers with >50 players, MariaDB/MySQL is recommended for performance.

---

## Troubleshooting

### `/shop` says "No items available"

1. Check `shops.yml` has items with `base_price` set
2. Check items have `buy: true` or `sell: true` enabled
3. Check `ShopManager` loaded correctly: `/at admin debug` shows loaded item count

### Players can't sell items

- Does the item have `sell: true` in shops.yml?
- Does the player actually have the item in their inventory?
- Is the sell price above the minimum transaction value (`economy.min-sell-value`, default $0.01)?

### `/loans` says I don't have permission

Players need `autotune.loans` permission. Give it with:
```
/perm user <player> add autotune.loans
```

Or add to your permissions plugin's groups.

### The economy feels "stuck" — prices don't move

Likely causes:
- No recent trades (increase `market.tick-interval-minutes` or wait)
- `max-price-change-percent` too low
- Price is at floor or ceiling (check `/at admin item info <item>`)
- Everyone is hoarding (check buy ratio — if >85%, sellers are rare)

---

## Still Stuck?

- **Discord:** Ask in #autotune
- **GitHub Issues:** Bug reports welcome, feature requests as well
- **docs/**: See SERVER_ADMIN_GUIDE.md, CONFIG_GUIDE.md, ECONOMY_CONCEPTS.md

Include `/at admin health` output and your `config.yml` (remove sensitive keys) when asking for help.
