# Server run metrics: percentiles, GC, snapshot

Status: needs-triage
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
