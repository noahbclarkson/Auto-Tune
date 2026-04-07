# Auto-Tune API Reference

> Auto-Tune Price Solver API — `rewrite-2`. Serves cross-server true-price discovery and exchange-rate calculation.

**Base URL:** `http://localhost:8080` (development)  
**Auth:** API key via `X-API-Key` header for write endpoints; public read for most endpoints.  
**Errors:** All error responses follow `{"error": "message"}`.

---

## Authentication

Write endpoints require a server API key, passed as a header:

```
X-API-Key: <your-api-key>
```

API keys are returned once at registration (`POST /api/servers/register`) and must be stored securely. Only the hash is stored server-side.

---

## Public Endpoints

### `GET /health`

Health check.

**Response `200`:**
```json
{ "status": "ok", "version": "0.1.0" }
```

---

### `POST /api/servers/register`

Register a new server and receive an API key.

**Request:**
```json
{ "name": "My Minecraft Server" }
```

| Field | Constraints |
|-------|-------------|
| `name` | 1–128 characters, non-empty |

**Response `201`:**
```json
{
  "server_id": "550e8400-e29b-41d4-a716-446655440000",
  "api_key": "at_sk_01HXM5KQ9B..."
}
```

> **Store `api_key` immediately.** It is shown exactly once and cannot be recovered.

**Errors:**
- `400` — name is empty or too long
- `409` — a server with that name already exists
- `429` — rate limited (10 req/min per IP for this endpoint)

---

### `GET /api/servers`

List all registered servers with their last-seen timestamps and submission stats.

**Response `200`:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "My Minecraft Server",
    "player_count": 24,
    "created_at": "2026-03-01T10:00:00Z",
    "last_seen": "2026-03-26T08:00:00Z",
    "last_submission_at": "2026-03-26T08:00:00Z",
    "last_submission_item_count": 47
  }
]
```

---

### `GET /api/prices/true`

Get current cross-server true prices. These are solved absolute prices derived from the ratio matrix submitted by all registered servers.

**Response `200`:**
```json
{
  "prices": [
    { "item": "DIAMOND", "price": 8.50, "confidence": 0.82, "servers": 3 },
    { "item": "IRON_INGOT", "price": 0.31, "confidence": 0.75, "servers": 3 }
  ],
  "last_updated": "2026-03-26T08:00:00Z"
}
```

`confidence` is computed from:
- Normalized RMS residual of the LS fit (0–1)
- Number of servers contributing data
- Item coverage across servers

---

### `GET /api/prices/history/{item}`

Price history for a specific item across all servers.

**Path params:** `item` — exact item name, e.g. `DIAMOND`

**Query params:** `limit=200` (default, max 200)

**Response `200`:**
```json
{
  "item": "DIAMOND",
  "history": [
    { "price": 8.20, "server_count": 3, "timestamp": "2026-03-25T00:00:00Z" },
    { "price": 8.35, "server_count": 3, "timestamp": "2026-03-25T04:00:00Z" }
  ]
}
```

---

### `GET /api/servers/exchange-rates`

Per-server economy multiplier relative to true prices.

If a server's rate is `1.5`, its prices are 1.5× the true price — its currency is worth less. If `0.8`, its economy runs cheaper than the global average.

**Response `200`:**
```json
{
  "base": "true_prices",
  "rates": [
    {
      "server_id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "My Minecraft Server",
      "rate": 1.42,
      "player_count": 24,
      "last_seen": "2026-03-26T08:00:00Z"
    }
  ]
}
```

---

## Authenticated Endpoints

These are scoped under `/api/servers/{server_id}` and require `X-API-Key`.

### `POST /api/servers/{server_id}/prices`

Submit a price ratio matrix for this server. This triggers an asynchronous recomputation of true prices.

**Headers:** `X-API-Key: <server-api-key>`

**Request:**
```json
{
  "item_names": ["DIAMOND", "GOLD_INGOT", "IRON_INGOT"],
  "ratio_matrix": [
    [1.0,   0.035, 0.006],
    [28.6,  1.0,   0.17],
    [170.0, 5.88,  1.0  ]
  ],
  "player_count": 24
}
```

`ratio_matrix[i][j]` = price of `item_names[i]` / price of `item_names[j]`. Must be square (n×n) and diagonally symmetric (`[i][j] = 1/[j][i]`).

**Validation:**
- `item_names` must be non-empty
- Matrix must be square and match `item_names` length
- Matrix must satisfy transitivity (validated server-side)
- Max payload: **1 MiB**

**Response `200`:**
```json
{ "success": true, "items_processed": 3 }
```

**Errors:**
- `400` — malformed request (empty items, non-square matrix)
- `401` — missing API key
- `403` — API key does not match server ID
- `422` — ratio matrix fails transitivity validation
- `429` — rate limited (6 req/min per IP for this endpoint)

---

## Rate Limits

| Endpoint | Limit |
|----------|-------|
| `POST /api/servers/register` | 10 req/min per IP |
| `POST /api/servers/{id}/prices` | 6 req/min per IP |
| All other endpoints | Free (general tier) |

When limited, the server returns `429 Too Many Requests` with a `retry-after` header.

---

## Security Model

The API server is designed to prevent a single bad actor from poisoning the true-price solver. Three layers of defense:

### 1. Manual Server Key Issuance

Servers cannot self-register without a maintainer-issued key. Before a server can submit prices, it must:
1. Call `POST /api/servers/register` with a server name and email
2. Wait for maintainer approval (keys are emailed — not instant)

This prevents Sybil attacks where one entity registers hundreds of fake servers to dominate the ratio matrix.

> **Note:** Current implementation is lightly Rate-Limited but not yet fully gated. Full manual-approval flow is on the roadmap.

### 2. Outlier Detection in the Price Solver

The least-squares solver computes a best-fit set of absolute prices from all server ratio matrices. Outlier servers — those submitting ratios wildly different from the consensus — contribute less to the final solution:

- **Confidence scoring:** Each server's submission is scored by how well its ratios fit the global solution. Servers with high residual error (their ratios don't agree with others) receive lower weight.
- **Minimum servers for consensus:** True prices require at least 2–3 independent servers. A single server's ratios produce undefined absolute prices (you can't solve absolute values from one server's relative measurements).

```
Server A: DIAMOND/IRON_INGOT = 9.5
Server B: DIAMOND/IRON_INGOT = 9.8
Server C: DIAMOND/IRON_INGOT = 28.0  ← Outlier: excluded or downweighted

True price (solved): DIAMOND ≈ 9.6× IRON_INGOT
```

### 3. Per-Server Reputation Weighting

Servers that have been submitting consistently over time earn higher weight in the solver:

- New servers (first 7 days): 0.5× weight
- Established servers (7–30 days): 1.0× weight
- Mature servers (30+ days): 1.5× weight

This means established servers vote with 3× the influence of brand-new ones, discouraging server churn-based manipulation.

### 4. Rate Limiting

Each IP is rate-limited on registration (10 req/min) and price submission (6 req/min). A single IP cannot flood the registry or overwhelm the solver.

### Cross-Server Exchange Rates: Abuse Prevention

Exchange rates (how much a server's economy differs from true prices) are computed at the plugin level — not by the API server. This is intentional:
- A server's exchange rate is derived from its OWN price submission, not from external data
- Players cannot influence exchange rates — only admins can
- The API server never stores player-level transaction data

### What the API Server Does NOT Have

- **No player-level data:** No player IDs, no transaction records, no balances. Only server-level aggregated price ratios.
- **No economic controls:** Cannot pause trading, freeze prices, or modify loan state — those are plugin-side decisions.
- **No chat or social features:** Purely a price discovery mechanism.

---

## WebSocket (future)

Real-time price updates via WebSocket are planned but not yet implemented. The current dashboard polls `GET /api/items` every 30 seconds.

---

## Submitting from the Java Plugin

The `PriceReporter` class in the Java plugin handles registration and price submission automatically:

```java
// In your config:
price-reporter:
  api-url: "https://api.yourdomain.com"
  api-key: "at_sk_..."        # From POST /api/servers/register
  server-id: "uuid-from-registration"
  interval-seconds: 300        # Submit every 5 minutes
```

> **Note:** PriceReporter submits **ratios**, not absolute prices. Each item's price is expressed relative to every other item in the matrix. The Rust API server solves for absolute prices from all server ratios using constrained least-squares.

