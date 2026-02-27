# Auto-Tune API Server Reference

Base URL (local): `http://localhost:8080`

All responses are JSON.

## Authentication

Authenticated endpoints use an API key in the `Authorization` header:

```http
Authorization: Bearer <api_key>
```

How it works:
- API keys are generated at registration time (`POST /api/servers/register`).
- The raw key is returned exactly once.
- The server stores only a SHA-256 hash of the key.
- Requests are authorized by looking up that hash.

> Note: The current implementation uses a bearer key lookup (hashed in storage). If you run an HMAC signing layer in front, keep this bearer key as the shared secret material for your signer/verifier.

---

## Health

### `GET /health`
- **Auth:** No
- **Response:**

```json
{
  "status": "ok",
  "version": "0.1.0"
}
```

---

## Server Management

### `POST /api/servers/register`
Register a new Minecraft server and receive an API key.

- **Auth:** No
- **Request body:**

```json
{
  "name": "My SMP"
}
```

- **Success response:** `201 Created`

```json
{
  "server_id": "uuid",
  "api_key": "64_hex_chars"
}
```

- **Errors:**
  - `400` invalid name length
  - `409` duplicate server name
  - `500` internal error

### `GET /api/servers`
List registered servers.

- **Auth:** No
- **Response:** `200 OK`

```json
[
  {
    "id": "uuid",
    "name": "My SMP",
    "player_count": 12,
    "created_at": "2026-02-22T00:00:00Z",
    "last_seen": "2026-02-22T00:10:00Z"
  }
]
```

---

## Prices

### `POST /api/servers/{server_id}/prices`
Submit an item ratio matrix for a specific server.

- **Auth:** Yes (`Authorization: Bearer <api_key>`)
- **Path param:** `server_id` (UUID) must match the authenticated key's server.
- **Request body:**

```json
{
  "item_names": ["Dirt", "Cobblestone", "Diamond"],
  "ratio_matrix": [
    [1.0, 0.5, 0.01],
    [2.0, 1.0, 0.02],
    [100.0, 50.0, 1.0]
  ],
  "player_count": 24
}
```

- **Success response:** `200 OK`

```json
{
  "success": true,
  "items_processed": 3
}
```

- **Errors:**
  - `401` missing/invalid key
  - `403` key does not match path `server_id`
  - `400` invalid matrix shape / empty items
  - `422` ratio matrix validation failed
  - `500` storage failure

### `GET /api/prices/true`
Get current computed true prices.

- **Auth:** No
- **Response:** `200 OK`

```json
{
  "prices": [
    {
      "item": "Diamond",
      "price": 95.42,
      "confidence": 0.91,
      "servers": 6
    }
  ],
  "last_updated": "2026-02-22T00:15:00Z"
}
```

### `GET /api/prices/history/{item}`
Get historical snapshots for one item (latest first, up to 200 points).

- **Auth:** No
- **Path param:** `item` string (item name)
- **Response:** `200 OK`

```json
{
  "item": "Diamond",
  "history": [
    {
      "price": 95.42,
      "server_count": 6,
      "timestamp": "2026-02-22T00:15:00Z"
    }
  ]
}
```

---

## Exchange Rates

### `GET /api/servers/exchange-rates`
Estimate each server's economy multiplier vs true prices.

- **Auth:** No
- **Response:** `200 OK`

```json
{
  "base": "true_prices",
  "rates": [
    {
      "server_id": "uuid",
      "name": "My SMP",
      "rate": 1.18,
      "player_count": 24,
      "last_seen": "2026-02-22T00:10:00Z"
    }
  ]
}
```

Interpretation:
- `rate > 1.0`: that server is more expensive than baseline
- `rate < 1.0`: that server is cheaper than baseline

---

## cURL Examples

### Register a server

```bash
curl -X POST http://localhost:8080/api/servers/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"My SMP"}'
```

### Submit prices

```bash
curl -X POST http://localhost:8080/api/servers/<server_id>/prices \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <api_key>' \
  -d '{
    "item_names": ["Dirt","Cobblestone","Diamond"],
    "ratio_matrix": [[1,0.5,0.01],[2,1,0.02],[100,50,1]],
    "player_count": 20
  }'
```

### Fetch true prices

```bash
curl http://localhost:8080/api/prices/true
```

### Fetch exchange rates

```bash
curl http://localhost:8080/api/servers/exchange-rates
```

---

## Docker Quickstart

From repo root:

```bash
docker compose up --build
```

Then test:

```bash
curl http://localhost:8080/health
```

Environment defaults are in `.env.example`:
- `DATABASE_URL`
- `BIND_ADDR`
- `POSTGRES_PASSWORD`
- `API_PORT`
- `RUST_LOG`
