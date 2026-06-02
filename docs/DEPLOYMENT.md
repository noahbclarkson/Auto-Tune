# Minimal API Server Deployment Plan

_One-page ops guide to get the Auto-Tune API server from source to live._

---

## Step 0 — Prerequisites

- [ ] `docker` installed and running
- [ ] `docker compose` (v2+) available
- [ ] A Postgres 16 database (local via compose, or hosted via Railway/Render/Supabase)
- [ ] A domain/subdomain pointed at your server (optional for dev, required for production)

---

## Step 1 — Build the Docker Image

```bash
cd /path/to/autotune/autotune

docker build \
  -t autotune/api-server:latest \
  -f api-server/Dockerfile .
```

Verify:
```bash
docker run --rm autotune/api-server:latest /app/api-server --help 2>&1 | head -3
# → starts and logs "starting Auto-Tune price API" briefly before dying (no DATABASE_URL)
```

---

## Step 2 — Local Dev with Docker Compose

```bash
# Create .env from example
cp env.example .env
# Edit .env — set a strong POSTGRES_PASSWORD

# Start postgres + api-server
docker compose up -d postgres
# Wait for postgres to be healthy (10s)
docker compose up -d api-server

# Verify
curl http://localhost:8080/health
# → {"status":"ok","version":"0.1.0"}
```

First run: the API server will automatically run all 5 migrations via `sqlx`.

---

## Step 3 — Connect the Frontend

```bash
# In public-site/.env.local
NEXT_PUBLIC_API_URL=http://localhost:8080
```

Or for production:
```bash
NEXT_PUBLIC_API_URL=https://api.yourdomain.com
```

The `public-site` Next.js app will now call the real API on `/api/prices/true`, `/api/servers`, etc.

---

## Step 4 — Production Hosting

### Fly.io (recommended — $0–$5/mo tier sufficient)

```bash
# Install flyctl
curl -L https://fly.io/install.sh | sh

# Launch (creates fly.toml from the Dockerfile)
fly launch --image autotune/api-server:latest --no-deploy
# → Answer: "Would you like to copy the .dockerignore?" → No (or Yes, it doesn't matter)

# Set secrets
fly secrets set DATABASE_URL=postgres://user:pass@host:5432/dbname
fly secrets set CORS_ALLOWED_ORIGINS=autotune.dev,yoursite.com

# Also add a Postgres DB via Fly.io
fly postgres create --name autotune-db
fly postgres attach --app autotune-api-server

# Deploy
fly deploy

# Set the DATABASE_URL from the attached postgres
fly secrets set DATABASE_URL=postgres://autotune:password@top2.nearest.forbidden.database.dev:5432/autotune

# Verify
curl https://your-app.fly.dev/health
```

### Railway (easiest — auto-detects Docker)

1. Push repo to GitHub
2. Connect Railway → New Project → Auto-detect Docker
3. Add environment variables: `DATABASE_URL`, `CORS_ALLOWED_ORIGINS`
4. Railway auto-provisions PostgreSQL add-on
5. Deploy → done

### Render

1. New → Blueprint → connect GitHub repo
2. `docker-compose.yml` at root is auto-detected
3. Set env vars: `DATABASE_URL`, `CORS_ALLOWED_ORIGINS`
4. Render provisions managed PostgreSQL

---

## Step 5 — Register First Server

```bash
curl -X POST https://api.yourdomain.com/api/servers/register \
  -H "Content-Type: application/json" \
  -d '{"name":"My Minecraft Server"}'

# Response:
# {"server_id":"<uuid>","api_key":"atk_live_<64-char-hex>"}

# Save api_key — it is NOT stored in plaintext and cannot be recovered.
```

Add this to your Minecraft server's Auto-Tune `config.yml`:

```yaml
auto-tune:
  api:
    server-id: "<uuid from above>"
    api-key: "atk_live_<hex>"
    endpoint: "https://api.yourdomain.com"
```

---

## Step 6 — Frontend Verification

| Page | Expected Behaviour |
|------|--------------------|
| `/true-prices` | Shows items with prices fetched from API |
| `/servers` | Lists registered servers |
| `/widget/myserver` | Shows live prices for specific server |

All three pages degrade gracefully when `NEXT_PUBLIC_API_URL` is unset (show placeholder/mock data).

---

## Quick-Reference: Environment Variables

| Variable | Required | Default | Notes |
|----------|----------|---------|-------|
| `DATABASE_URL` | **Yes** | — | Full connection string: `postgres://user:pass@host:5432/db` |
| `BIND_ADDR` | No | `0.0.0.0:8080` | Inside-container listen |
| `RUST_LOG` | No | `info` | `debug` for request logs |
| `CORS_ALLOWED_ORIGINS` | No | `localhost,autotune.dev` | Comma-separated list |
| `POSTGRES_PASSWORD` | Via compose only | — | Used in `DATABASE_URL` interpolation |
| `API_PORT` | Via compose only | `8080` | Host-side port mapping |

---

## Rollback

```bash
# Fly.io
fly releases
fly deploy --image <previous-image>

# Docker direct
docker run -d --name autotune-api-old --rm $(docker images --format "{{.ID}}" autotune/api-server:latest | sed -n '2p'))
# Tag and replace current container
```

---

## Next Steps After First Deploy

1. **Fix `/api/admin/health` gap** — add the endpoint the widget expects, or update widget to use `/health`
2. **Wire plugin → API server** — plugin already has the config fields; just needs real endpoint + key
3. **Add server heartbeat** — plugin calls `POST /api/servers/:id/heartbeat` on interval
4. **Set up monitoring** — Fly.io has built-in metrics; Railway/Render need external (Grafana Cloud, Datadog)
