# Auction House Guide

_A practical guide to Auto-Tune's built-in P2P auction house. Written for server admins and players._

---

## What Is the Auction House?

The auction house is a **limit-order marketplace** built directly into Auto-Tune. Unlike `/shop` (where the server sets the price), players post their own buy or sell orders at their own prices. Trades execute automatically when a buy order and a sell order cross.

**Key difference from `/shop`:**
- `/shop`: instant at server-set price, always fills, loses the spread
- `/auction`: player-set price, waits for a match, avoids the spread

Every Auto-Tune install includes the auction house — no separate plugin, no extra setup.

---

## Player Commands

| Command | What it does |
|---------|-------------|
| `/auction browse` | View the live order book (all active buy/sell orders) |
| `/auction sell <price> <qty>` | Post a sell limit order from the item in your hand |
| `/auction buy <material> <price> <qty>` | Place a buy limit order; fills automatically if it crosses a sell order |
| `/auction my` | View your active orders and fill history |
| `/auction cancel <id>` | Cancel one of your active orders |
| `/auction info <id>` | Inspect any order in full detail |
| `/auction watch <id>` | Get in-game alert when an order fills |
| `/auction reclaim` | Get back expired sell-order items and pending auction deliveries |
| `/auction history` | Your personal fill history |

### Placing a Sell Order

Hold the item you want to sell, then run `/auction sell <price> <qty>`. Your listed items go into escrow immediately. A sell order stays active for 72 hours (default) or until it is fully filled.

```
/auction sell 315 8
# Posts 8 of your held item as a sell order at $315 per unit
```

### Placing a Buy Order

Browse with `/auction browse` to see active asks. To buy, run `/auction buy <material> <price> <qty>`. Your coins are escrowed, and the matching engine fills immediately against compatible sell orders when possible. Any unfilled quantity remains as an active buy order.

```
/auction buy DIAMOND 315 8
# Bids for 8 diamonds at up to $315 each
```

### Cancelling an Order

Run `/auction cancel <order-id>`. For sell orders, items are returned to your inventory immediately. For buy orders, escrowed coins are refunded immediately. No penalty for cancelling.

### Reclaiming Expired Orders

When an order expires (72 hours by default), sell orders' items remain in escrow if they could not be returned automatically. Auction items that could not fit in your inventory, or buy-order fills that arrived while you were offline, are also saved as pending returns. Run `/auction reclaim` to get them back.

---

## Web Dashboard — /auction

Players can also browse and interact with the auction from the bundled web dashboard at `http://your-server:8989/auction`. The web UI has 4 tabs:

1. **Active Orders** — full order book with side/material/price/qty/filled status and a material filter
2. **Recent Fills** — live fill feed with price, quantity, and time since fill
3. **Materials Book** — per-material bid/ask best prices and 24h volume
4. **My Orders** — your active orders, fill history, and watch status

The web dashboard also includes a **Depth Chart** tab (in the Active Orders section) showing cumulative bid/ask ladder depth — visualizes where the market is thin or thick at each price level.

---

## Depth Chart — Reading Market Thinness

The depth chart shows the cumulative quantity of bids (green) and asks (rose) at each price level.

- **Green area (buy walls):** represents buy orders at each price. Larger green = stronger buy support.
- **Rose area (sell walls):** represents sell orders at each price. A tall rose spike means a large seller could move the price down significantly.
- **Where they meet:** the natural equilibrium price.

**Thin books** (small total area) mean low liquidity — a single large order can move the price significantly against you. Admins see thin-book warnings in `/at admin auction` and in the bundled `/admin` dashboard.

---

## Admin Monitoring — /at admin auction

Run `/at admin auction` to see the auction health report:

- **Active orders** by side and material
- **7-day fill/cancel/expire rates**
- **Cancellation churn** — high cancel rates may indicate spoofing
- **Self-trade fills** — same player on both sides is not real liquidity
- **Thin book warnings** — materials with very few or very small orders
- **Large sell wall alerts** — unusually large sell orders that could move the market

The same data is exposed via `GET /api/admin/auction-audit?days=N` for the bundled web dashboard.

---

## Auction Integrity — What to Watch For

### Cancellation Spoofing

A player posts many small sell orders at slightly different prices, then cancels them when buyers start crossing. This creates artificial volume signals. Watch the cancel rate in `/at admin auction`. If cancellation churn exceeds ~30% of active orders over 7 days, investigate.

### Self-Trades

A player posts a buy order and a sell order at the same price with two accounts, filling themselves to generate false volume. Self-trade fills are flagged in the integrity audit. If self-trade rate exceeds ~5%, consider investigating.

### Thin-Book Manipulation

A whale posts a very large sell order against a thin book — one order moves the displayed price dramatically. This is not necessarily malicious (it may reflect genuine inventory), but it can confuse players. Admins see large sell wall alerts.

### Order Expiry and Escrow

Sell orders that expire return to online players when possible. If the player is offline or inventory delivery fails, the items are saved for `/auction reclaim`. Pending buy-order deliveries are saved the same way if the buyer is offline or full, so fills never silently destroy items.

---

## Configuration

Configure auction behavior in `config.yml` under the `auction` section:

```yaml
auction:
  enabled: true
  default-duration-hours: 72        # How long orders stay active
  max-orders-per-player: 50        # Limit active orders per player
  min-order-value: 1.0              # Minimum coin value for a buy order
  thin-book-threshold: 5             # Active orders below this = thin book warning
  self-trade-flag-threshold: 0.05    # Self-fill rate above this = warning (fraction)
```

---

## How Orders Match

The matching engine uses **price-time priority**:
1. The best (highest) buy price fills first
2. Among orders at the same price, the oldest (earliest posted) fills first
3. Partial fills are supported — a large order can be filled by multiple smaller crossing orders

When a sell order fills partially, the seller receives coins for the filled portion immediately. The remaining quantity stays active until fully filled or cancelled.

---

## Watching Orders

Players can run `/auction watch <order-id>` to receive an **in-game notification** when their watched order fills. Watch state persists across server restarts and works for offline players — when they log in, they see the fill notification as a pending message.

From the web dashboard, players can also toggle watch state on any active order.

---

## Cross-Server Auction?

The auction house operates **on a single server only** — it matches orders within that server's economy. There is no cross-server auction (orders do not bridge between servers).

Cross-server features in Auto-Tune are limited to **price discovery** (true prices via the API server) and **exchange rates** — the auction is local to each server by design.