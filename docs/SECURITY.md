# Auto-Tune Cross-Server Security & Trust Model

> How Auto-Tune protects the opt-in true-price network from manipulation, abuse, and bad data.

**Audience:** server admins evaluating cross-server price aggregation.  
**Scope:** the Rust API server, Java `PriceReporter`, and price-solver path on `rewrite-2`.

---

## Why this matters

Cross-server true prices are useful only if admins can trust the input data. Auto-Tune servers submit anonymised item **ratio matrices**; the API aggregates those submissions and computes true prices that can seed new servers and power public market views.

That creates obvious attack surfaces:

- Fake servers trying to dominate the network.
- Compromised server keys submitting bad data.
- Outlier ratio matrices trying to move a valuable item.
- Flooding expensive solver endpoints.
- Stale or incompatible submissions polluting current prices.

Auto-Tune's model is layered: authenticate every writer, rate-limit expensive operations, reject invalid matrices, filter statistical outliers, and keep exchange-rate decisions local to each plugin.

---

## Current write authentication

Authenticated endpoints use:

```http
Authorization: Bearer <api_key>
```

Every registered server receives a random 32-byte key shown exactly once during `POST /api/servers/register`. The API server stores only the SHA-256 hash.

Current protections:

- **One key maps to one server ID.** Submissions to `/api/servers/{server_id}/prices` and heartbeats to `/heartbeat` are rejected with `403` if the key does not match the path server ID.
- **Plaintext keys are not stored.** Lost keys cannot be recovered; they must be replaced.
- **Java plugin uses Bearer auth.** `PriceReporter` and exchange-rate fetches already send `Authorization: Bearer ...`.

Planned hardening:

- Maintainer/admin approval for public network registration.
- Self-service key rotation.
- Explicit key revocation endpoint and audit trail.

---

## Rate limits

Expensive write paths are IP rate-limited with token buckets:

| Endpoint | Limit |
|----------|-------|
| `POST /api/servers/register` | 10 req/min per IP, burst 10 |
| `POST /api/servers/{id}/prices` | 6 req/min per IP, burst 6 |

Rate-limited responses return `429 Too Many Requests` and a `Retry-After` header. Public reads remain open so dashboards and public pages can load without keys.

---

## Matrix validation

A server submits a square ratio matrix:

```text
ratio_matrix[i][j] = price(item_i) / price(item_j)
```

The API rejects malformed submissions before storing them:

- `item_names` must be non-empty.
- Matrix dimensions must match `item_names.length`.
- Matrix must be square.
- Ratios must pass `price_solver::validate_ratio_matrix` transitivity checks.
- Payloads are capped at 1 MiB.

This blocks arbitrary contradictory data before it reaches the solver.

---

## Outlier filtering in the true-price solver

The API does not average submitted prices directly. During recomputation it:

1. Loads the latest submission from each server.
2. Builds a unified item graph.
3. Collects ratio observations per item pair in log-space.
4. Filters observations beyond `OUTLIER_SIGMA` standard deviations from the pair consensus (`3.0` by default).
5. Replaces outlier pair entries with unknown values, then bridge-infers from neighbouring valid ratios.
6. Solves prices with constrained least-squares.

Example:

```text
Server A: DIAMOND / IRON_INGOT = 9.5
Server B: DIAMOND / IRON_INGOT = 9.8
Server C: DIAMOND / IRON_INGOT = 28.0  ← filtered as an outlier when enough peers exist

Solved ratio stays near the honest cluster instead of following Server C.
```

Important limitation: outlier detection needs multiple independent observations. With only one or two servers, the network should be treated as low-confidence.

---

## Solver weighting

Current solver weights each server by reported `player_count`, with a minimum weight of 1. This gives active servers more influence than empty/private test servers.

Risks and follow-up ideas:

- Player count is self-reported, so it should not become the only trust signal.
- Add capped player-count weighting so a single large server cannot dominate.
- Add age/reputation weighting: new servers start lower, stable 30+ day servers earn more trust.
- Track per-server residual history and automatically quarantine chronically outlying servers.

---

## Data freshness

Current recomputation uses the latest submission from each registered server. The `servers` table tracks `last_seen`; `price_submissions` tracks `submitted_at`.

Recommended next hardening before public launch:

- Exclude submissions older than a freshness window, e.g. 24 hours.
- Surface freshness and server count in public true-price confidence copy.
- Require the Java plugin to include plugin version metadata in submissions.
- Exclude or downweight versions with incompatible ratio-generation logic.

---

## Anti-Sybil posture

A Sybil attack is one operator registering many fake servers to outvote legitimate ones.

Current mitigations:

- Per-IP registration limits.
- One API key per server ID.
- Matrix validation.
- Outlier filtering when enough honest peers exist.

Required before heavy public marketing:

1. Maintainer-approved server registration or invite codes.
2. Key rotation/revocation workflow.
3. Freshness filtering.
4. Capped player-count weighting plus age/reputation weighting.
5. Public confidence labels that distinguish "few servers" from "strong consensus".

---

## Exchange-rate abuse prevention

Cross-server exchange rates are intentionally a plugin-level decision. The API exposes aggregate market data; the Java plugin computes how its own prices compare to true prices.

This avoids the worst abuse pattern: the API server cannot arbitrarily force a server economy to change. Admins opt in, and the local plugin remains the authority for applying exchange-rate effects.

---

## Data boundary

The API server is price-discovery infrastructure, not player telemetry.

| Data | Stored by API? |
|------|----------------|
| Player UUIDs or names | No |
| Balances | No |
| Individual transactions | No |
| Chat/social data | No |
| Server name and ID | Yes |
| Server player count | Yes |
| Aggregated ratio matrix | Yes |
| True prices and history | Yes |
| Last seen/submission timestamps | Yes |

---

## Launch checklist

Before presenting the network as production-grade public infrastructure:

- [x] Bearer-token auth with hashed keys.
- [x] Server-ID/path auth checks.
- [x] Per-IP write rate limits.
- [x] Ratio matrix validation.
- [x] Log-space 3σ outlier filtering.
- [ ] Freshness filter for stale submissions.
- [ ] Key rotation/revocation endpoint.
- [ ] Registration approval/invite flow.
- [ ] Plugin version metadata in submissions.
- [ ] Capped player-count weighting and age/reputation weighting.
- [ ] Public confidence labels that explain low server count, stale data, and outlier suppression.

Auto-Tune is close to a trustworthy cross-server network, but the public launch story should be honest: the solver has strong mathematical safeguards today; registration governance, freshness filtering, and reputation controls are the next required hardening layer.
