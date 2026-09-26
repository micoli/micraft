# Debugging recipes (agent reference)

## Runtime log levels

Logging is SLF4J + Logback with no `logback.xml` (console, INFO). Every logger registers itself in Logback's
`LoggerContext`, so the list below is always exhaustive.

| Route (process-level, admin-only) | Purpose |
|-------|---------|
| `GET /api/admin/loggers` | `{name, level, effectiveLevel}` for every logger; `level` is `null` when inherited |
| `PUT /api/admin/loggers/{name}` | `{level: "TRACE"\|"DEBUG"\|"INFO"\|"WARN"\|"ERROR"\|"OFF"\|null}`; `null` resets to inherited; works for a logger that has not logged yet |

Admin UI: `/admin/loggers` (`LoggersPage.tsx`). Raise a subsystem (e.g. `org.micoli.micraft.game.quest.QuestManager`)
to `DEBUG` while reproducing, then reset it. Changes are in-memory only and are lost on `make dev-restart-server`.

## Rec XZ / Rec Y (Prediction gap)

Decisions: ADR-0001 (per-axis Reconciliation), ADR-0002 (shared rules in `core`).

HUD `Rec XZ  n/total (pct%) avg=… ±…` (`Statistics.tsx`, computed by `reconcileStats()` in `LocalPlayerController.kt`)
= share of **render frames** in the last 20 s where the predicted XZ position was further than the tolerance from
the server's. Healthy is a few % at most; tens of % means the client predicts a different speed than
`MovementProcessor` applies. Key `dump_stats` (default F9, `V` on some setups) prints Rec XZ / Rec Y to the console
and a toast.

- **`avg` is the tell**: a near-constant value with a tiny `±` (e.g. `4.0 ±0.2`) is a *systematic speed mismatch*
  (the correction fights a steady drift). A large `±` points at jitter or late updates.
- **Check server tick health first**: `curl localhost:8080/status` (or `/metrics`, `micraft_tick_phase_avg_ms`). A
  total tick well under 50 ms rules out server tick spikes.
- **Per-sample log**: set `DEBUG_RECONCILE_LOG = true` in `LocalPlayerController.kt`, `make build-wasm`, and filter
  the browser console on `recXZ`. Each line (≤ 1 per 250 ms) carries `dist`, `err`, `moving/fly/swim/feet`,
  `srvStance`, `mult`, `sinceUpdate`, tolerances, `pred` vs `server`. Set it back to `false` when done.
- **Reconcile model**: `reconcileErrX/Z` is the *remaining error vector* (server position minus the client's
  prediction at the instant the confirmed intent was sent), consumed by soft corrections. Keep it a vector: an
  absolute target goes stale as the prediction advances.

**Invariant**: `LocalPlayerController.updateMovementAndPhysics` mirrors `server/.../tick/MovementProcessor.kt` term
for term (speed = `stance.speed × speedMult × dt × liquidSlowdown`, hitbox stance, submerged rule). A new movement
modifier goes in `core` (see `BlockType.liquidSlowdown`) and is applied on both sides. Past causes were all "server
applies X, client doesn't": the swimming slowdown, and a stale CRAWLING stance kept while flying.
