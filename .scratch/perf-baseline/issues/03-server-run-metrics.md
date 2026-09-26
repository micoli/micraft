# Server run metrics: percentiles, GC, snapshot

Status: resolved
Type: task

## Goal

The server can report what happened during one run, not just a moving average.

## Current state

- `game/tick/TickProfiler.kt` keeps an **EMA only** per phase (α = 0.1). It shows no percentiles and hides spikes.
- `/metrics` exposes the JVM heap, CPU load and threads, but no GC counts or pauses.

## Scope

- Per-phase and total tick **histogram** (HdrHistogram or fixed buckets) → p50/p95/p99/max. Keep the EMA for the admin UI.
- GC: collection count and time per collector (`GarbageCollectorMXBean`); allocation rate if cheap.
- Admin, process-level: `POST /api/admin/perf/reset` (start of a run) and `GET /api/admin/perf/snapshot` (JSON:
  tick percentiles per phase, heap min/max/avg sampled each second, GC, network bytes, connected Characters, loaded
  chunks, NPC count).
- Test covering the histogram and the reset/snapshot routes; regenerate OpenAPI.

## Acceptance

- A snapshot after a 60 s run returns the percentiles and the GC totals for that window only.

## Answer

Implemented 2026-09-26:

- `TickProfiler.resetRun()` / `runSnapshot()`: exact nearest-rank p50/p95/p99/max per phase over a bounded window
  (72 000 samples, 1 h at 20 tps). The EMA is unchanged.
- `game/perf/RunMetrics` behind a `RunProbe` seam (`JvmRunProbe` in production): heap min/max/avg, CPU load average,
  and GC/network deltas since the reset, sampled every second.
- `game/perf/PerfRun` + `POST /api/admin/perf/reset` and `GET /api/admin/perf/snapshot`: admin-only and world-scoped
  (`X-Micraft-Game-Session`).
- Smoke test on the dev server: 100 ticks in 5 s, total tick p50 0.16 ms, p95 0.46 ms, p99 1.36 ms.
