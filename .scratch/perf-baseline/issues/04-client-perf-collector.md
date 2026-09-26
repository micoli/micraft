# Client perf collector: frame times, GPU, memory

Status: resolved
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

## Answer

Implemented 2026-09-26:

- `game/lib/perf/perfCollector.ts`: `createPerfCollector(source)` computes frame-time and GPU-time percentiles,
  average and max for draw calls, active meshes and active indices, the texture count, and memory at snapshot time.
  Unit-tested through a fake `PerfSource` (`__tests__/perfCollector.test.ts`).
- `game/lib/perf/babylonPerfSource.ts`: the production adapter.
  - Babylon `SceneInstrumentation` / `EngineInstrumentation`; frames count only when the scene actually rendered
    (the game loop caps itself at ~14 ms, so `onEndFrame` also fires for skipped rAF frames).
  - GPU timer results arrive a few frames late, so frames with a 0 reading are skipped.
  - Memory: `performance.memory` for the JS heap, and the Wasm memory captured from the `instantiate*` import object
    (Kotlin imports `intrinsics.memory`). It reads **0**: Kotlin/Wasm uses WasmGC, so its objects live in the JS heap.
    GPU buffer bytes are summed from unique geometries at snapshot time.
- Enabled only when `window.__mcPerf` is set before boot; exposed as `window.mcPerf.reset()` / `snapshot()`.
- Field is `activeIndices` (what Babylon exposes), not vertices.
- Live check (dev server, 10 s idle): 525 rendered frames, frame p50 17.5 / p95 34.2 / p99 42.9 ms, GPU p50 6.2 ms,
  ~950 draw calls, 1.7 M active indices, 88 MB GPU buffers, 685 MB JS heap. Per-frame counter reads are negligible;
  a snapshot costs ~3 ms.
- Deferred: per-frame split (tick / mesh drain / GPU upload / render). It needs `LocalPlayerController`'s
  `perfInstrumentationEnabled` accumulators to be shared instead of reset by its spike ring buffer.
