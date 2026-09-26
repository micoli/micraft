# Performance baseline (CPU / GPU / memory)

Decided 2026-09-26. Goal: reproducible measurements before any optimisation, replayed before and after each change.

## Decisions

- **Scenarios**:
  - **A** idle at spawn, 60 s.
  - **B** traversal: straight line, walk then fly, through ungenerated terrain.
  - **D** load: N moving Characters (10 / 25 / 50).
  - **C** (dense NPC / combat area) comes later.
- **World**: a real procedural world with a fixed `worldSeed`, in a throwaway data root (`MICRAFT_DATA_DIR`,
  `MICRAFT_WORLD_NAME`). E2E Test worlds are not representative (flat bounded generator, no NPC spawn, reduced tick).
- **Client driver**: headed Playwright on the reference Mac (real GPU). Headless is only for future CPU-only regression checks in CI.
- **Load driver**: JVM bots reusing `core` (codec, `MoveIntent`), authenticated through the admin API, with no rendering.
- **Results**: one JSON file per run in `.scratch/perf-baseline/results/<date>-<sha>-<scenario>.json`, plus a compare
  script. No Prometheus/Grafana for now.
- **Tooling lives in the repo**: `perf/` plus `make perf-<scenario>`.

## Budgets (recalibrated after the first baseline, `app/webApp/ts-src/perf/budgets.json`)

The display paces the render loop, so a frame time p95 ≤ 16.7 ms cannot pass: any frame that slips past
one refresh lands at 18–25 ms. The primary frame budget is therefore the share of *long frames*
(> 1.5 × the median frame, i.e. a missed refresh).

| Area | Budget |
|------|--------|
| Client frames | long frames ≤ 1 %; p95 ≤ 20 ms (50 FPS); p99 < 33.3 ms (30 FPS) during traversal |
| Server tick | avg < 25 ms and p99 < 50 ms with 25 Characters |
| Server memory | heap stable over 10 min (no growth trend) |
| Client GPU memory | bounded by the view radius (no growth while moving) |

## Issues

| # | Issue | Blocked by |
|---|-------|------------|
| 01 | [Chunk transport: WebSocket vs HTTP](issues/01-chunk-transport-websocket-vs-http.md) | 02, 04, 05 |
| 02 | [Fixed-seed perf world](issues/02-fixed-seed-perf-world.md) | — |
| 03 | [Server run metrics: percentiles, GC, snapshot](issues/03-server-run-metrics.md) | — |
| 04 | [Client perf collector: frame times, GPU, memory](issues/04-client-perf-collector.md) | — |
| 05 | [Headed Playwright runner: scenarios A and B](issues/05-playwright-perf-runner.md) | 02, 03, 04 |
| 06 | [JVM load bots: scenario D](issues/06-jvm-load-bots.md) | 02, 03 |
| 07 | [Compare script and budget check](issues/07-compare-and-budgets.md) | 05 |
