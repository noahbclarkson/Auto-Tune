# Auto-Tune Dashboard API Reference

> Bundled Javalin web server — served at `http://your-server:8989`. Powers the plugin's built-in web dashboard at `/:8989`. Also accessible to third-party integrations, scripts, and monitoring tools.

**Auth:** Optional basic auth via `web.auth` config (username + password). Without auth, all endpoints are public.  
**Format:** JSON by default unless noted.  
**Errors:** `{"error": "message"}` on failure.

---

## Items

### `GET /api/items`

All shop items with current buy/sell prices and spread.

**Response `200`:**
```json
[
  {
    "id": 1,
    "material": "DIAMOND",
    "displayName": "Diamond",
    "buyPrice": 312.50,
    "sellPrice": 287.40,
    "basePrice": 500.00,
    "bpd": 4.24,
    "spd": 4.23,
    "section": "ORES",
    "trend": "UP",
    "trendPercent": 2.3
  }
]
```

`trend`: `"UP"`, `"DOWN"`, or `"STABLE"`. `bpd` = buy price deviation %, `spd` = sell price deviation %.

---

### `GET /api/items/{id}`

Single item detail.

**Response `200`:** Full item object with `id`, `material`, `displayName`, `buyPrice`, `sellPrice`, `basePrice`, `bpd`, `spd`, `section`, `trend`, `trendPercent`, `priceFloorOverride`, `priceCeilingOverride`, `frozen`.

---

### `GET /api/items/{id}/history`

Price history for an item.

**Query params:** `limit` (default 100, max 200)

**Response `200`:**
```json
{
  "item": "DIAMOND",
  "history": [
    { "price": 312.50, "buyPrice": 325.00, "sellPrice": 300.00, "timestamp": 1711440000000 },
    { "price": 308.20, "buyPrice": 320.00, "sellPrice": 296.40, "timestamp": 1711438500000 }
  ]
}
```

`price` is the mid-market price. Timestamps are milliseconds since epoch.

---

### `GET /api/items/{id}/attribution`

"What moved this price?" — per-segment price change attribution.

**Query params:** `limit` (default 50, max 200)

**Response `200`:**
```json
{
  "item": "DIAMOND",
  "currentPrice": 312.50,
  "segments": [
    {
      "price": 325.00,
      "changePercent": -3.85,
      "changeType": "SUPPLY_GLUT",
      "eventName": "Oversupply Event",
      "volumeRatio": 2.3,
      "timestamp": 1711439400000
    }
  ]
}
```

`changeType`: `"SUPPLY_GLUT"`, `"DEMAND_SURGE"`, `"VOLUME_SHIFT"`, `"TREND"`, `"MARKET_EVENT"`, or `"UNKNOWN"`.

---

### `GET /api/items/{id}/transactions`

Recent transactions for a specific item.

**Query params:** `limit` (default 50)

**Response `200`:**
```json
[
  {
    "type": "BUY",
    "material": "DIAMOND",
    "quantity": 3,
    "pricePerUnit": 312.50,
    "totalPrice": 937.50,
    "playerName": "Notch",
    "timestamp": 1711440000000
  }
]
```

---

### `GET /api/items/{id}/trend`

Current price trend for an item.

**Response `200`:**
```json
{
  "material": "DIAMOND",
  "trend": "UP",
  "trendPercent": 2.3,
  "streak": 3,
  "direction": "up"
}
```

---

## Economy

### `GET /api/economy/gdp`

Economy GDP (Gross Domestic Player) — total estimated economic value.

**Response `200`:**
```json
{
  "gdp": 1523400.00,
  "timestamp": 1711440000000
}
```

---

### `GET /api/economy/inflation`

Economy inflation estimate.

**Response `200`:**
```json
{
  "averagePriceChange": 1.24,
  "label": "LOW",
  "timestamp": 1711440000000
}
```

`label`: `"DEFLATION"`, `"LOW"`, `"MODERATE"`, `"HIGH"`, or `"EXTREME"`.

---

### `GET /api/economy/debt`

Total debt and loan statistics.

**Response `200`:**
```json
{
  "totalDebt": 483200.00,
  "activeLoans": 14,
  "debtPerCapita": 4832.00,
  "timestamp": 1711440000000
}
```

---

### `GET /api/economy/history`

Economy history snapshots for charting.

**Query params:** `limit` (default 100)

**Response `200`:**
```json
{
  "history": [
    {
      "gdp": 1523400.00,
      "totalDebt": 483200.00,
      "activeLoans": 14,
      "timestamp": 1711440000000
    }
  ]
}
```

---

### `GET /api/economy/trends`

Top trending items by price change.

**Response `200`:**
```json
{
  "trends": [
    {
      "material": "DIAMOND",
      "displayName": "Diamond",
      "trend": "UP",
      "trendPercent": 2.3
    }
  ]
}
```

---

### `GET /api/economy/volume-multiplier`

Global trade volume multiplier (z-score of recent activity).

**Response `200`:**
```json
{
  "multiplier": 1.05,
  "label": "NORMAL"
}
```

`label`: `"VERY_LOW"`, `"LOW"`, `"NORMAL"`, `"HIGH"`, `"VERY_HIGH"`.

---

## Transactions

### `GET /api/transactions`

Recent economy-wide transactions.

**Query params:** `limit` (default 50)

**Response `200`:**
```json
[
  {
    "type": "BUY",
    "material": "DIAMOND",
    "quantity": 3,
    "pricePerUnit": 312.50,
    "totalPrice": 937.50,
    "playerName": "Notch",
    "timestamp": 1711440000000
  }
]
```

---

## Loans

### `GET /api/loans`

All active loans.

**Response `200`:**
```json
{
  "loans": [
    {
      "id": 1,
      "playerName": "Notch",
      "principal": 5000.00,
      "remaining": 3500.00,
      "interestRate": 0.05,
      "dueDate": 1714128000000,
      "status": "ACTIVE",
      "creditScore": 650
    }
  ]
}
```

`status`: `"ACTIVE"`, `"OVERDUE"`, or `"DEFAULTED"`.

---

### `GET /api/loans/stats`

Loan system aggregate statistics.

**Response `200`:**
```json
{
  "totalActiveLoans": 14,
  "totalActiveDebt": 483200.00,
  "averageInterestRate": 0.052,
  "defaultRate": 0.03
}
```

---

## Portfolio

### `GET /api/portfolio/{playerName}`

Player's holdings, P&L, and trade summary.

**Response `200`:**
```json
{
  "playerName": "Notch",
  "netWorth": 52340.00,
  "cashBalance": 12340.00,
  "inventoryValue": 40000.00,
  "totalBought": 156000.00,
  "totalSold": 103660.00,
  "realizedPnL": -2340.00,
  "holdings": [
    {
      "material": "DIAMOND",
      "quantity": 64,
      "avgBuyPrice": 310.00,
      "currentPrice": 312.50,
      "unrealizedPnL": 160.00
    }
  ]
}
```

---

### `GET /api/portfolio/{playerName}/transactions`

Player's transaction history.

**Query params:** `limit` (default 50), `from` (ISO-8601), `to` (ISO-8601)

**Response `200`:** Array of transaction objects (same shape as `/api/transactions`).

---

### `GET /api/portfolio/{playerName}/transactions.csv`

Download player's transaction history as CSV.

**Query params:** `limit` (max 10000), `from`, `to`

**Response `200`:** `text/csv` file download.

```csv
date,type,material,quantity,pricePerUnit,totalValue
2026-04-06T08:00:00Z,BUY,DIAMOND,3,312.50,937.50
```

---

### `GET /api/portfolio/{playerName}/pnl-history`

Player's realized + unrealized P&L over time.

**Response `200`:**
```json
{
  "playerName": "Notch",
  "history": [
    {
      "realizedPnL": -150.00,
      "unrealizedPnL": 210.00,
      "totalPnL": 60.00,
      "timestamp": 1711440000000
    }
  ]
}
```

---

## Leaderboard

### `GET /api/leaderboard`

Top buyers, sellers, and net traders.

**Response `200`:**
```json
{
  "topBuyers": [
    { "playerName": "Notch", "totalBought": 156000.00, "transactionCount": 42 }
  ],
  "topSellers": [
    { "playerName": "Herobrine", "totalSold": 234000.00, "transactionCount": 87 }
  ],
  "netTraders": [
    { "playerName": "Notch", "netTrade": -52000.00 }
  ]
}
```

`netTrade` = totalSold − totalBought (negative = net buyer).

---

## Badges

### `GET /api/badges/player/{playerName}`

Player's earned badges and when they earned them.

**Response `200`:**
```json
{
  "playerName": "Notch",
  "badges": [
    {
      "badge": "FIRST_SALE",
      "earnedAt": "2026-03-15T10:00:00Z"
    },
    {
      "badge": "LOAN_SHARK",
      "earnedAt": "2026-03-20T14:30:00Z"
    }
  ]
}
```

---

## Market Events

### `GET /api/events`

Active and upcoming market events.

**Response `200`:**
```json
{
  "events": [
    {
      "id": "uuid",
      "name": "Diamond Rush",
      "type": "DEMAND_SURGE",
      "materials": ["DIAMOND"],
      "multiplier": 1.50,
      "startsAt": 1711440000000,
      "endsAt": 1711526400000,
      "status": "ACTIVE"
    }
  ]
}
```

`status`: `"SCHEDULED"`, `"ACTIVE"`, `"ENDED"`, or `"CANCELLED"`.

---

## Admin

### `GET /api/admin/health`

Economy health diagnostics. Same data as `/at admin health` command.

**Response `200`:**
```json
{
  "healthScore": 78,
  "gdp": 1523400.00,
  "totalDebt": 483200.00,
  "debtGdpRatio": 0.32,
  "circuitBreakerTier": "TIER0",
  "buySellRatio": 0.64,
  "avgBpd": 4.2,
  "avgSpd": 4.1,
  "volumeMultiplier": 1.05,
  "inflationLabel": "LOW",
  "avgVolatility": 0.034,
  "topVolatileItems": [
    { "material": "NETHERITE_INGOT", "volatility": 0.12 }
  ],
  "topUndersoldItems": [
    { "material": "IRON_INGOT", "displacement": -0.45 }
  ]
}
```

`circuitBreakerTier`: `"TIER0"` (healthy), `"TIER1"` (>3× debt/GDP), `"TIER2"` (>5×), `"TIER3"` (>30×).

---

### `GET /api/admin/config`

Current config values, defaults, and recommended ranges.

**Response `200`:**
```json
{
  "spread": {
    "baseSpread": { "current": 0.20, "default": 0.20, "range": [0.05, 0.50], "unit": "decimal", "description": "Total bid-ask spread as fraction" },
    "volumeImpact": { "current": 0.80, "default": 0.80, "range": [0.0, 1.0], "unit": "decimal" }
  },
  "loans": {
    "baseInterestRate": { "current": 0.05, "default": 0.05, "range": [0.0, 1.0], "unit": "decimal" },
    "debtGdpTier3Ratio": { "current": 15.0, "default": 15.0, "range": [1.0, 100.0], "unit": "ratio" },
    "postDefaultCooldownHours": { "current": 168, "default": 168, "range": [0, 8760], "unit": "hours" },
    "counterCyclical": { "current": true, "default": true, "unit": "boolean", "description": "Reduce interest as Debt/GDP rises" }
  },
  "economy": {
    "tradeWindowDays": { "current": 7, "default": 7, "range": [1, 30], "unit": "days" },
    "maxPriceChangePercent": { "current": 1.5, "default": 1.5, "range": [0.1, 10.0], "unit": "percent" }
  }
}
```

---

## Price Alerts

### `GET /api/alerts/{playerName}`

Player's active price alerts.

**Response `200`:**
```json
{
  "alerts": [
    {
      "id": 1,
      "playerName": "Notch",
      "material": "DIAMOND",
      "alertType": "ABOVE",
      "targetPrice": 400.00,
      "active": true,
      "createdAt": "2026-04-01T10:00:00Z"
    }
  ]
}
```

---

### `POST /api/alerts`

Create a price alert.

**Request:**
```json
{
  "playerName": "Notch",
  "material": "DIAMOND",
  "alertType": "ABOVE",
  "targetPrice": 400.00
}
```

`alertType`: `"ABOVE"` or `"BELOW"`.

**Response `201`:** `{"id": 1, "success": true}`

---

## Shop Favorites

### `GET /api/shop/favorites/{playerName}`

Player's starred shop items.

**Response `200`:**
```json
{
  "playerName": "Notch",
  "favorites": [
    { "itemId": 1, "material": "DIAMOND", "addedAt": "2026-04-01T10:00:00Z" }
  ]
}
```

---

### `POST /api/shop/favorites/{playerName}/{itemId}`

Star or unstar a shop item.

**Request body:** (optional) `{ "starred": true }` — omit to toggle.

**Response `200`:**
```json
{ "success": true, "favorited": true }
```

---

## Stats

### `GET /api/stats`

General server and economy stats.

**Response `200`:**
```json
{
  "serverName": "My Server",
  "onlinePlayers": 12,
  "totalItems": 87,
  "lastUpdated": 1711440000000
}
```

---

## Prices & Spreads

### `GET /api/prices`

All item prices (id → price map).

**Response `200`:**
```json
{
  "prices": {
    "1": 312.50,
    "2": 24.30
  }
}
```

---

### `GET /api/spreads`

All item spreads.

**Response `200`:**
```json
{
  "spreads": {
    "1": { "bpd": 4.24, "spd": 4.23 },
    "2": { "bpd": 3.10, "spd": 3.05 }
  }
}
```

---

## WebSocket — Live Prices

### `WS /ws/market`

Real-time price updates. Connect from the browser dashboard for live price flashing.

**Server → Client (JSON):**
```json
{
  "type": "PRICE_UPDATE",
  "data": {
    "1": { "buyPrice": 312.50, "sellPrice": 287.40 },
    "2": { "buyPrice": 24.30, "sellPrice": 23.60 }
  }
}
```

```json
{
  "type": "ECONOMY_UPDATE",
  "data": {
    "gdp": 1523400.00,
    "totalDebt": 483200.00,
    "volumeMultiplier": 1.05
  }
}
```

**Connection:** `ws://your-server:8989/ws/market`

The browser dashboard uses this to flash updated prices green without reloading the page.

---

## CORS

The dashboard API allows cross-origin requests from the dashboard's own origin in development. In production, if the dashboard is served on the same port, CORS is not an issue. If you need third-party integrations, configure `web.allowedOrigins` in `config.yml`.
