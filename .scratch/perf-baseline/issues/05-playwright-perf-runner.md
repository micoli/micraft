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

## Answer (implementation — acceptance still pending)

Implemented 2026-09-26 in `app/webApp/ts-src/perf/` (reuses the installed Playwright; no new npm project):

- `playwright.config.ts`: headed, 1 worker, 1600×900; `webServer` boots or reuses `:server:runPerfServer` (8092).
- `helpers/perfSession.ts`:
  - reserves `PerfRunner` (WARRIOR, in-game `admin` group) through the admin API, logs in, and sets `__mcPerf` +
    `__mcE2E` **without a session**. That keeps the per-tick E2E JSON snapshot off: only `actions.runCommand` is used.
  - `/god:on`; `measure()` brackets server `perf/reset|snapshot` and `window.mcPerf`, and records `travelledBlocks`
    from the camera.
- `helpers/results.ts`: writes `.scratch/perf-baseline/results/<date>-<sha>-<scenario>.json` with env (sha, dirty, CPU,
  GPU, OS, encoder), a warm-up window, raw runs, and a `summary` holding the median of the headline metrics.
- `idle.perf.ts` (A): the chunks settle, then 1 warm-up + 3 windows of 60 s.
- `traverse.perf.ts` (B): start at (200, -150), which is open plains. The default spawn (8, 8) is boxed in and the
  Character cannot walk out. Each window uses a different heading: walk 30 s holding W + Space (there is no auto-step,
  so a one-block rise blocks a plain walk), then double-tap Space to fly, ascend 3 s, and fly 60 s.
- `make perf-idle` / `make perf-traverse`; env overrides `PERF_WINDOW_MS`, `PERF_WALK_MS`, `PERF_FLY_MS`,
  `PERF_START_X/Z`, `PERF_NO_SERVER`.
- Smoke runs (short windows) pass. B travels ~45 blocks per 18 s window, and loaded chunks grow 228 → 360.
- Still to do: two full-length runs on an unchanged tree to check the < 10 % variance acceptance.
