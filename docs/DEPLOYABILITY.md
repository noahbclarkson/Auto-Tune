# API Server Deployability Checklist

_Review before any deployment attempt. All items must be ✅ before going live._

---

## ✅ Build & Binary

- [ ] `cargo build --release --package api-server` succeeds (clean, no warnings)
- [ ] `Dockerfile` present at `api-server/Dockerfile` and builds successfully: `docker build -f api-server/Dockerfile .`
- [ ] Multi-stage build: builder stage + slim runtime stage (debian:bookworm-slim)
- [ ] `HEALTHCHECK` defined in Dockerfile (curl against `/health`)
- [ ] Binary stripped and static (no `libssl` linking issues in container)

---

## ✅ Database & Migrations

- [ ] `sqlx` postgres pool initializes on `DATABASE_URL`
- [ ] 5 migration files present in `api-server/migrations/`:
      `0001_servers.sql`, `0002_price_submissions.sql`, `0003_true_prices.sql`,
      `0004_auction_house.sql`, `0005_true_prices_anchored.sql`
- [ ] Migrations run automatically on startup via `db::run_migrations(&pool)`
- [ ] `POSTGRES_PASSWORD` in `.env` matches the one baked into the Docker image

---

## ✅ API Endpoints (Frontend-Wired)

| Route | Method | Auth | Used By | Status |
|-------|--------|------|---------|--------|
| `/health` | GET | None | Widget, health probes | ✅ |
| `/api/servers/register` | POST | None | Registration flow | ✅ |
| `/api/servers/:id/prices` | POST | API key | Plugin price submission | ✅ |
| `/api/prices/true` | GET | None | `/true-prices` page | ✅ |
| `/api/prices/history/:item` | GET | None | Price charts | ✅ |
| `/api/servers` | GET | None | `/servers` page | ✅ |
| `/api/servers/:id/heartbeat` | POST | API key | Server liveness | ✅ |

---

## ✅ Security

- [ ] API key auth: `Authorization: Bearer <key>` header checked via `ApiKeyAuth` middleware
- [ ] API key stored as bcrypt hash (not plaintext) — `auth.rs` uses `sha2`/`hex`
- [ ] Rate limiting: `RateLimiter` on submit + register endpoints (30/min submit, 5/min register per IP)
- [ ] CORS configured: `CORS_ALLOWED_ORIGINS` env var (comma-separated), defaults to localhost + autotune.dev
- [ ] `DATABASE_URL` pulled from env (never hardcoded)
- [ ] `rustls` TLS (no OpenSSL runtime required in container)

---

## ✅ Environment Variables

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `DATABASE_URL` | **Yes** | — | Postgres connection string |
| `BIND_ADDR` | No | `0.0.0.0:8080` | Listen address |
| `RUST_LOG` | No | `info` | Tracing level |
| `POSTGRES_PASSWORD` | Via `DATABASE_URL` | — | DB auth (used in compose) |
| `API_PORT` | No | `8080` | Exposed port (used in compose) |
| `CORS_ALLOWED_ORIGINS` | No | `localhost,autotune.dev` | CORS origins |

---

## ✅ Frontend Wiring

- [ ] `NEXT_PUBLIC_API_URL` env var wired in `web-optimizer/src/lib/api-client.ts`
- [ ] `EmbeddableWidget` component calls `${apiUrl}/api/admin/health` — **endpoint not yet in API server** ⚠️
- [ ] `/true-prices` page fetches from `${API_BASE_URL}/api/prices/true`
- [ ] `/servers` page fetches from `${API_BASE_URL}/api/servers`
- [ ] All frontend routes build with `NEXT_PUBLIC_API_URL` unset (graceful degradation)

---

## ⚠️ Gap: `/api/admin/health` Not Implemented

The `EmbeddableWidget` calls `${apiUrl}/api/admin/health` but this endpoint **does not exist** in the API server.

Options:
1. **Quick fix**: Reuse `/health` — returns `{"status":"ok"}`. Widget can use this directly.
2. **Proper fix**: Add `GET /api/admin/health` returning `{status, version, server_count, volatile_items}` from DB.

**Recommended**: Option 2 as a follow-up. For immediate deploy, set widget to call `/health` instead.

---

## ✅ Docker Compose (Local Dev)

- [ ] `docker-compose.yml` present at repo root
- [ ] `postgres:16-alpine` with healthcheck (`pg_isready`)
- [ ] `api-server` service builds from `api-server/Dockerfile`
- [ ] `postgres` port exposed as `5432` (can connect from host for debugging)
- [ ] `api-server` port exposed as `${API_PORT:-8080}:8080`
- [ ] Volume `postgres_data` persists DB across restarts
- [ ] `POSTGRES_PASSWORD` variable interpolation works

---

## ✅ Production Hosting Options

### Option A: Fly.io (Recommended for Simplicity)
```bash
fly launch --image <registry>/api-server:latest  # no Dockerfile inference needed
fly secrets set DATABASE_URL=postgres://...
fly secrets set CORS_ALLOWED_ORIGINS=autotune.dev,yoursite.com
fly deploy
```

### Option B: Self-Hosted (VPS/Dedicated)
```bash
# Build Docker image
docker build -t autotune-api -f api-server/Dockerfile .
docker run -d --name autotune-api \
  -e DATABASE_URL=postgres://... \
  -e CORS_ALLOWED_ORIGINS=... \
  -p 8080:8080 \
  autotune-api
```

### Option C: Railway / Render
- Both support Docker images directly
- Set `DATABASE_URL`, `CORS_ALLOWED_ORIGINS`, `RUST_LOG`
- PostgreSQL add-on available in both platforms

---

## ✅ Smoke Test After Deploy

```bash
# Health check
curl https://api.yourdomain.com/health
# → {"status":"ok","version":"0.1.0"}

# Register (should return server_id + api_key)
curl -X POST https://api.yourdomain.com/api/servers/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Server"}'
# → {"server_id":"...","api_key":"..."}

# Submit prices (requires Bearer token)
curl -X POST https://api.yourdomain.com/api/servers/<id>/prices \
  -H "Authorization: Bearer <key>" \
  -H "Content-Type: application/json" \
  -d '{"item_names":["DIAMOND"],"buy_prices":[500.0],"sell_prices":[490.0]}'
```

---

## ✅ Monitoring

- [ ] Healthcheck endpoint configured in hosting platform (Fly.io/ Railway healthcheck → `/health`)
- [ ] Structured JSON logs via `tracing` (RUST_LOG=debug for requests)
- [ ] PostgreSQL connection pool: `sqlx` with `max_connections=10` default
- [ ] Rate limit headers returned: `retry-after` on 429 responses
