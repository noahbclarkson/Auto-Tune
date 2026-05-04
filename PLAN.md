# PLAN.md — Anvil's Work Plan

_Living document. Update after every session. Prioritize ruthlessly._

**Branch:** `rewrite-2` (do NOT merge to master)

---

## Simulation Lab (2026-05-04 01:48 UTC) — Regression PASS, Floor Paradox Confirmed, Admin Recovery Optimal

**rewrite-2 at `201aa21`** | Regression 6/6 PASS ✅ | Zero drift | All builds clean

**Regression Suite:** ALL PASS (6/6 scenarios, 0.000% displacement)
- Standard Economy ✅ | Spread Stability ✅ | Low Player Count ✅
- Standard+MM Economy ✅ | GuildStability+MM+7%GB ✅ | GuildStability+2MM+7%GB+Floor ✅

**Floor Paradox — Fully Characterized:**
| Floor | 14d GDP | 14d D/G | Verdict |
|-------|---------|---------|---------|
| 30% | flat | flat | Non-binding (internal $155 > $150) |
| **60%** | **+29.2%** | **0.71x** | **OPTIMAL — production default** |
| 70% | +9.1% | 0.84x | Degrades vs 60% |
| 90% | -14.7% | 1.09x | Catastrophic — internal Diamond $0.40 |

- Multi-seed (5 seeds, 1MM+2GB): 60% floor → **+4.2% GDP avg, floor binds 5/5**, D/G -0.10x
- Production config (2MM+2GB+60% floor): **+88.9% GDP vs 1MM, BPD -24.7%, 5/5 binds**
- 30d long-run: D/G climbs to 17.7x even with floor (floor masks internal collapse)
- **Floor guarantees displayed price, NOT internal economic health**

**Admin Recovery Timing — Day 3 is Optimal:**
- Natural: D/G 0.92x, defaulted $381,555
- EARLY (Day 3): D/G 0.72x, defaulted $307,708 — **saves 0.20x D/G, 19.4% less default**
- MID (Day 7): D/G 0.87x, defaulted $360,386 — saves 0.05x D/G
- LATE (Day 10): no better than natural
- **VERDICT: Recovery after Day 7 has negligible benefit. Target Day 3-7.**

**O(N²) performance note:** GuildStability scenarios slow at TIER3 fire (tick 11371+). Engine produces correct numbers but slowly. Use dedicated test modes (not `--headless guild-stability`) for 60d+ runs.

**Next simulation priorities:**
1. Guild debt cap sweep: cap × [2×/3×/5× GDP] × 60d
2. Long-run floor test: 60% floor vs no floor × 90d × 3 seeds
3. Circuit hysteresis × long-run: tier3=30/hyst=50% vs tier3=40/hyst=50% × 90d × 3 seeds
4. O(N²) profiling: loan processing bottleneck at TIER3 oscillation

**Still blocked:** API server deploy (Arc/Fly.io token); real testimonials (human outreach)

---

# PLAN.md — Anvil's Work Plan

_Living document. Update after every session. Prioritize ruthlessly._

**Branch:** `rewrite-2` (do NOT merge to master)

---
