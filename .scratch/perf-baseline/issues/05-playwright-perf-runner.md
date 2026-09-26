# Headed Playwright runner: scenarios A and B

Status: needs-triage
Type: task
Blocked by: 02, 03, 04

## Goal

`make perf-idle` and `make perf-traverse` each produce one JSON result file.

## Scope

- `perf/` Playwright project (separate config from `e2e/`), **headed** Chrome on the reference Mac, fixed viewport,
  shadows and FOV set explicitly.
- It drives the game like a player (the E2E rule): the Character is created through the admin API, then slash commands and key presses.
  - **A idle**: spawn, wait for the chunks around spawn to settle, then measure 60 s.
  - **B traverse**: `/tp` to the fixed start, walk 30 s in a straight line, then fly 60 s (same heading, same speed).
- Around the measured window: `POST /api/admin/perf/reset` + `window.mcPerf.reset()` before, and both snapshots after.
- The result JSON records the scenario, git sha, date, machine (CPU/GPU/OS), config (view radius, chunk transport,
  encoding), and the server + client snapshots, written to `.scratch/perf-baseline/results/`.
- Warm-up run discarded; 3 measured repetitions, median reported.

## Acceptance

- Two runs on an unchanged tree differ by < 10 % on p95 frame time and avg tick.
