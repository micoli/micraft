---
name: perf
description: Run the MiCraft performance baseline (headed browser + fixed-seed perf server) and analyse it exhaustively — client frame time in ms and FPS, GPU time, render load, client/server memory, server tick per phase, budgets, stability, before/after comparison. Use when measuring CPU/GPU/memory, checking an optimisation or a regression, or when the user asks for perf numbers.
---

# Performance baseline: run and analyse

Plan, decisions and budgets: `.scratch/perf-baseline/spec.md`. Results land in `.scratch/perf-baseline/results/`
(one JSON per scenario and invocation), reports in `.scratch/perf-baseline/reports/`.

## 1. Run

1. **Clean tree**: commit or stash first — a dirty tree is flagged in the report and is not a baseline.
2. **Fresh client**: after any client change, `make build` (the perf server serves `app/webApp/build/web/`).
3. **Idle machine**: the run opens a headed Chrome on the real GPU; tell the user to leave the machine alone.
4. `make perf` (~4 min): starts a fresh perf server once (seed 42, port 8092), runs scenario A (idle at spawn,
   5 s warm-up + 3×15 s) then B (traverse: 8 s walk jumping, 1.5 s climb, 20 s flight per window, a new heading
   each window, detours when stuck, teleports 24 blocks ahead when every detour fails). NPC spawning is
   seeded (`MICRAFT_NPC_SEED=42`); spawns still depend on the path, so NPC counts vary slightly.
   Run it detached and wait for it rather than blocking a foreground call:
   `nohup bash -c 'make perf 2>&1 | grep -E "\[perf\]| passed| failed|Error:"; echo done' > perf/.run.log 2>&1 &`
   then wait on `perf/.run.log` containing `done`.
5. **Stability check** (before trusting a first baseline or a small delta): run `make perf` twice on the same commit.
6. **Before/after an optimisation**: run on the base commit, then on the change, back to back in the same session;
   compare with `--base <sha>`. Server tick times are only comparable within a session: on Apple silicon the
   scheduler may move the perf server between performance and efficiency cores, which alone moved tick p50
   ×2.5 between two sessions on identical server code.

Longer windows only when asked: `PERF_WINDOW_MS`, `PERF_WARMUP_MS`, `PERF_WALK_MS`, `PERF_FLY_MS` (and
`PERF_START_X/Z`) are read by the scenarios; `make perf-idle` / `make perf-traverse` run one scenario.

Done when: every scenario reported `passed` and wrote its JSON. A failure or timeout is reported with its output,
never silently rerun.

## 2. Analyse

`make perf-report` (latest commit measured; `ARGS="--sha <sha>"`, `ARGS="--base <sha>"` to compare). It prints the
report the analysis starts from; save it with the interpretation to
`.scratch/perf-baseline/reports/<date>-<sha>.md`.

Interpret **every section, per scenario**, and write the conclusions under each table:

- **Validity** first: the report excludes a traverse window that travelled < 50 % of the median, or an idle
  window that moved, from every aggregate. Name each excluded window and why. If most windows are excluded, the run
  is not a baseline: rerun it.
- **Frame time**: always state each figure in **ms and FPS** (FPS = 1000 / ms), e.g. "p95 18.3 ms (55 FPS)".
  - The render loop is capped at ~14 ms and paced by the display: p50 ≈ 16.7 ms (60 FPS) is the ceiling, not a problem.
  - p95/p99/max and the **long-frame share** (frames > 1.5 × p50: a missed refresh) are the stutter. Budgets:
    long frames ≤ 1 %, p95 ≤ 20 ms (50 FPS), p99 < 33.3 ms (30 FPS).
  - CPU vs GPU bound: frame time − GPU time ≈ CPU time (tick, meshing, GC). GPU p95 near frame p95 → GPU-bound.
- **Render load**: draw calls, meshes, triangles, textures — relate to frame/GPU time and to traverse vs idle.
- **Memory**: client JS heap trend across windows (growth ⇒ possible leak; WasmGC objects live in the JS heap),
  GPU buffers vs view radius, server heap min/avg/max.
- **Server tick**: total p50/p95/p99/max in ms and as % of the 50 ms budget, TPS vs 20; name the costliest
  phases (p95/p99) and the sparse ones (fewer samples = runs every N ticks).
- **Server process**: CPU %, GC count/time and share of wall time, network KB/s (traverse streams chunks).
- **Budgets**: every ✗ explained with its most likely cause from the other sections.
- **Stability**: spread per metric across invocations; a metric over 10 % is noise-dominated — say its deltas
  are not conclusive.
- **Comparison** (with `--base`): only deltas beyond the noise count; name what got better/worse and why.

Close with: the 3 most important findings, suspected causes, and the next measurement or code change to try —
each as a candidate issue under `.scratch/perf-baseline/issues/` if the user agrees.

Done when: every section of every scenario has a written interpretation, every budget ✗ has a cause, all frame
figures appear in both ms and FPS, and the report file is saved.

## 3. Client heap (when memory is the question)

`make perf-heap` (~2 min, headed): two traversals from the perf start, with the JS heap read before and after a
forced GC at each step (the gap is garbage; what survives is live) and a sampling heap profile of what was
allocated during the run and is still alive, bottom-up by allocating function and by origin (Kotlin/Wasm,
BabylonJS, JS app, native). Report: `.scratch/perf-baseline/heap/<stamp>-<sha>.md`; the raw `.heapprofile` in
`perf/heap/` opens in Chrome DevTools → Memory. `PERF_HEAP_SNAPSHOT=1` also writes a full heap snapshot (large;
DevTools only). WasmGC objects allocated inside Wasm are attributed to the nearest JS frame of the Kotlin glue
(no URL, a line number): break those down with a full snapshot.

## Moving parts

| Piece | Where |
|-------|-------|
| Perf server (fresh data root `perf/.data`, admin `perf-admin@test.local`) | `server/build.gradle.kts` `runPerfServer`, `make perf-server[-stop]` |
| Server run metrics (`POST/GET /api/admin/perf/reset\|snapshot`) | `server/.../game/perf/`, `TickProfiler` |
| Client collector (`window.mcPerf`, on when `window.__mcPerf`) | `app/webApp/ts-src/game/lib/perf/` |
| Scenarios, steering, result writer | `app/webApp/ts-src/perf/` |
| Report + budgets | `app/webApp/ts-src/perf/analyze.mjs`, `perf/budgets.json` |
| Client heap profile | `app/webApp/ts-src/perf/client.heap.ts`, `perf/helpers/heapProfile.ts` |
