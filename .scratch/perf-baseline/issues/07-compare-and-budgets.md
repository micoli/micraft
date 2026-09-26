# Compare script and budget check

Status: resolved
Type: task
Blocked by: 05

## Goal

`make perf-compare BASE=<file> HEAD=<file>` prints a before/after table and flags budget breaches.

## Scope

- A script in `perf/` reading two result JSONs: one row per metric with base, head, delta % and a ✓/✗ against the
  budgets in `.scratch/perf-baseline/spec.md` (moved to a `perf/budgets.yaml` once stable).
- Markdown output, so it can be pasted into a commit body or issue.
- Record the first baseline (A, B, D×3) as `results/baseline-<date>-<sha>/` and link it from the spec.

## Acceptance

- Comparing a run with itself shows 0 % deltas; an injected 20 ms sleep in the tick shows up as a tick budget breach.

## Answer

Implemented 2026-09-26 as `app/webApp/ts-src/perf/analyze.mjs` + `perf/budgets.json` (`make perf-report`,
`ARGS="--base <sha>"`), driven by the project skill `/perf`. It produces validity (and excludes invalid windows),
frame and GPU time in ms and FPS, render load, memory, the server tick per phase, the server process, budgets,
stability across invocations, and a comparison against a base commit. The first baseline is in
`reports/2026-09-26-33659457.md`.
