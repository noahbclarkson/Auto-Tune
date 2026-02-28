#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   scripts/e2e_local_setup.sh              # starts mock API + Next.js web
#   E2E_API_MODE=real scripts/e2e_local_setup.sh  # starts Rust API on :8989

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${E2E_API_MODE:-mock}"
API_PORT="${API_PORT:-8989}"
WEB_PORT="${WEB_PORT:-3001}"

log() { printf "[e2e-setup] %s\n" "$*"; }

start_mock_api() {
  log "Starting mock API on :${API_PORT}"
  nohup node "$ROOT/scripts/mock-web-api.mjs" > "$ROOT/.mock-api.log" 2>&1 &
  echo $! > "$ROOT/.mock-api.pid"
}

start_real_api() {
  log "Starting Rust API on :${API_PORT}"
  if [ -f "$HOME/.cargo/env" ]; then
    # shellcheck disable=SC1090
    . "$HOME/.cargo/env"
  fi
  if ! command -v cargo >/dev/null 2>&1; then
    log "cargo not found. Install rustup/cargo first."
    exit 1
  fi

  DATABASE_URL="${DATABASE_URL:-postgres://autotune:autotune_dev@localhost:5432/autotune}" \
  BIND_ADDR="0.0.0.0:${API_PORT}" \
  nohup cargo run --manifest-path "$ROOT/api-server/Cargo.toml" > "$ROOT/.api-server.log" 2>&1 &
  echo $! > "$ROOT/.api-server.pid"
}

start_web() {
  log "Installing frontend deps"
  npm --prefix "$ROOT/web" install >/dev/null

  log "Starting Next.js frontend"
  nohup npm --prefix "$ROOT/web" run dev > "$ROOT/.web.log" 2>&1 &
  echo $! > "$ROOT/.web.pid"
}

healthcheck() {
  log "Waiting for API"
  for _ in {1..30}; do
    if curl -fsS "http://localhost:${API_PORT}/health" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  log "Waiting for web"
  for _ in {1..45}; do
    if curl -fsS "http://localhost:${WEB_PORT}" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  log "Done"
  echo "API: http://localhost:${API_PORT}"
  echo "WEB: http://localhost:${WEB_PORT}"
  echo "Logs: $ROOT/.mock-api.log, $ROOT/.api-server.log, $ROOT/.web.log"
}

case "$MODE" in
  mock) start_mock_api ;;
  real) start_real_api ;;
  *) log "Unknown E2E_API_MODE=$MODE (expected mock|real)"; exit 1 ;;
esac

start_web
healthcheck
