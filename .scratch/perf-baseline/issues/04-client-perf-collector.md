# Client perf collector: frame times, GPU, memory

Status: needs-triage
Type: task

## Goal

The web client records one run's CPU, GPU and memory figures and exposes them to the runner.

## Current state

- HUD `Statistics.tsx`: FPS min/max/current, Tick, Jitr, chunks DL/mesh, Net, Rec XZ/Y.
- `LocalPlayerController.updateFpsAndPerfSamples` feeds mesh drain and GPU upload timings behind
  `window.mcState.perfInstrumentationEnabled` (off by default).
- No GPU counters and no memory figures.

## Scope

- Frame-time histogram (p50/p95/p99/max) from the render loop, plus a per-frame split: game tick, mesh drain,
  GPU upload, render.
- BabylonJS `SceneInstrumentation` / `EngineInstrumentation`: draw calls, active meshes, active vertices, GPU frame time
  (when `EXT_disjoint_timer_query` is available), texture count.
- Memory: `performance.memory` (Chrome) for the JS heap, the Wasm linear memory size, and an estimate of GPU buffer
  bytes (sum of the chunk `VertexData` buffer sizes).
- `window.mcPerf.reset()` / `window.mcPerf.snapshot()` for the runner, following the `window.mcE2E` observation-bridge
  style; enabled by a flag so it costs nothing in normal play.
- Changes go through the Kotlin sources and the generator, never by editing `mc_bindings.js`.

## Acceptance

- A snapshot after 60 s idle returns plausible, non-zero values for every field; overhead with the collector
  enabled is < 2 % frame time.
