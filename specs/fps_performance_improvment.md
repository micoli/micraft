# Client FPS drops — investigation & fixes

## Context

User reported FPS drops correlated with chunk meshing/downloading. Rather than guess at a fix,
the approach was: instrument first, let the numbers pick the target, fix, re-measure. This
document is the record of that loop — what was measured, what was changed, what moved the
needle, what didn't, and what's still open.

All work landed on `main` directly (client-only changes, `app/webApp/**`), one commit per
iteration:

```
4e9d86d2 perf(client): add per-subsystem frame-time instrumentation for FPS-drop diagnosis
cff0ec65 perf(client): move chunk-mesh geometry building off the main thread
3b9d9dda perf(client): add mesh/draw-call count to the FPS spike ring buffer
e987e191 perf(client): double SLAB_HEIGHT to cut chunk draw-call count
87fb7e78 perf(client): push SLAB_HEIGHT to 64, retest iteration
b744dffc fix(client): reset chunk-mesh timing stats on drainPendingChunks' idle path
28dacc41 fix(client): use performance.now() for chunk-decode timing, not Date.now()
4b5c967f perf(client): time npc/vehicle/remote-player ticks, close the blockMs gap
9f8f3f40 perf(client): render ChunkDebug grid on canvas instead of 225 DOM nodes
a37b0476 perf(client): gate FPS instrumentation behind a master switch, off by default
```

## Instrumentation built (still live in the client)

Everything below is permanent client instrumentation — not a one-off profiling session. It exists
so a future FPS complaint can be diagnosed the same way, without re-deriving any of this.

### Master on/off switch — disabled by default

All of it (rolling HUD timing stats, the spike ring buffer, and the render-loop's own
`performance.now()` timing — the highest-frequency piece, running every frame rather than every
10-tick block) is gated behind one flag, off unless explicitly turned on:

```js
window.mcState.perfInstrumentationEnabled = true; // enable
delete window.mcState.perfInstrumentationEnabled; // disable again (or = false)
```

Read on the Kotlin side via `jsIsPerfInstrumentationEnabled()` (`BabylonBindingsUtil.kt`), checked:

- every tick, before sampling mesh/GPU/other-subsystem timings into the rolling windows,
- once/second, before sampling the WS-decode average,
- once per 10-tick HUD block, before computing avg/min/max, pushing to the spike ring buffer, and
  querying `scene.meshes.length`/`scene.getActiveMeshes().length`,
- every frame inside `jsEngineRunRenderLoop`'s JS (`BabylonBindingsScene.kt`) — checked directly
  in JS rather than round-tripping into Kotlin, since this is the highest-frequency site.

When disabled: `chunkDecodeMsAccum`/`chunkDecodeCount` (`NetworkStats.kt`) still reset every
second regardless (cheap, avoids unbounded growth), and the physics/interaction/aux `jsNow()`
wraps in `tick()` still run (a handful of timestamp reads — negligible on their own) — but nothing
downstream of them (rolling-window sampling, the spike ring buffer, HUD's extra timing rows, the
render-loop timing) executes. `Statistics.tsx`'s `Mesh`/`Net decode` rows and any `[fps-spike]`
log will just read/show `0` while disabled.

**Why off by default**: this was built for one specific investigation, not as an always-on
production feature — even though individual pieces are cheap, the combination (mesh-count scene
queries, ring-buffer allocation, per-frame `performance.now()` calls) is unnecessary overhead for
normal play. Turn it on deliberately when diagnosing a future FPS report, following the workflow
in [Frame-spike ring buffer](#frame-spike-ring-buffer) below, then turn it back off.

### Rolling HUD stats (`LocalPlayerController.kt`)

Extends the existing 20s rolling-window HUD stats system (`STATS_WINDOW_MS`, already tracking
FPS/tick-jitter/reconcile stats) with per-subsystem timing, all sourced from `jsNow()` (or
`jsPerfNow()` — see [bugs fixed](#bugs-fixed-along-the-way)) deltas around each subsystem:

- **Mesh drain** (`ChunkManager.kt`): `lastFaceScanMs` / `lastFaceProcessMs` / `lastGpuUploadMs` /
  `lastFacesProcessedThisDrain`, set once per `drainPendingChunks()` call, read by
  `LocalPlayerController` every tick into `meshDrainMsSamples` / `gpuUploadMsSamples`.
- **Physics/interaction/aux** (`LocalPlayerController.tick()`): coarse-grained wall-clock wraps
  around the movement+collision block, the events/raycast/break/place/NPC-targeting block, and
  the sky/weather/minimap-draw block.
- **Network decode** (`GameClient.kt` / `NetworkStats.kt`): time spent in
  `ServerMessageCodec.decode()` on the `/chunks` WS loop, accumulated and reset once/second
  alongside the existing `bytesIn`/`bytesOut` counters.
- **Render** (`BabylonBindingsScene.kt`): `scene.render()` itself timed with `performance.now()`
  inside `jsEngineRunRenderLoop`, exposed via `jsGetRenderMsAccum` / `jsGetRenderFrameCount` /
  `jsGetRenderMsMax`, reset each HUD block.
- **Mesh/draw-call count**: `jsGetTotalMeshCount` / `jsGetActiveMeshCount`
  (`scene.meshes.length` / `scene.getActiveMeshes().length`) — cheap proxy for draw calls.
- **Other subsystems** (`GameClient.kt`): `npcManager.tick()` + `vehicleManager.tick()` +
  `remotePlayerManager.tick()` timed as one block (`otherTickMs`), since they run right after
  `localController.tick()` inside the same 16ms loop iteration but were otherwise invisible to
  any per-subsystem timer.

New `HudData`/`jsUpdateHUD` fields surface `meshDrainMsAvg/Min/Max`, `gpuUploadMsAvg/Min/Max`,
`wsDecodeMsAvg` on the HUD (`Statistics.tsx`, rows `Mesh` and `Net decode`) when
`statistics_toggle` is on.

### Frame-spike ring buffer

A capped `ArrayDeque<FrameSnapshot>` (`SPIKE_RING_SIZE = 300`) in `LocalPlayerController.kt`,
pushed once per 10-tick HUD block with every timer above plus `blockMs` (wall-clock since the
last block) and `bytesIn`. When the block's worst single tick interval exceeds a threshold, the
last `SPIKE_LOG_ENTRIES` (20) entries are flushed to the browser console as
`console.warn('[fps-spike]', [...])` via `jsLogSpike`.

**The threshold is live-tunable from the browser console**, no rebuild/reload needed:

```js
window.mcState.spikeThresholdMs = 200; // default 50ms (DEFAULT_SPIKE_THRESHOLD_MS)
```

This is the primary tool for diagnosing a future "FPS dropped" report: enable the
[master switch](#master-onoff-switch--disabled-by-default) (`window.mcState.perfInstrumentationEnabled = true`
— required, everything below is a no-op without it), reproduce the drop, grab the `[fps-spike]`
console output, and read off which subsystem's ms figure is elevated for that window. Turn the
master switch back off when done.

## Changes that measurably helped

### 1. Chunk-mesh geometry moved to a Web Worker (`cff0ec65`)

**Before**: `chunkProcessFaces` (face-buffer → per-material `VertexData` typed arrays) ran
synchronously on the main thread, budget-sliced across `drainPendingChunks()` calls.

**What changed**: new `chunkMeshWorker.ts` (self-contained copy of the geometry-building logic,
built from a one-time `blockDefs` postMessage snapshot instead of `window.mc`) + `chunkWorkerPool.ts`
(round-robin pool, `min(hardwareConcurrency-1, 4)` workers, poll-based result handoff since
Kotlin/Wasm↔JS interop doesn't bridge Promises well). `ChunkManager.drainPendingChunks()` submits
the face buffer once (`jsRequestChunkMesh`) after its row scan and polls `jsIsChunkMeshReady`
instead of slicing `chunkProcessFaces` inline. GPU upload (`vd.applyToMesh`) stays main-thread —
a Worker has no canvas/WebGL context — via `jsChunkEndFromWorker`.

Build system note: `ts-src` uses esbuild (not webpack), so the worker ships as its own esbuild
entry (`build:chunkWorker` in `ts-src/package.json` → `chunk-mesh-worker.js`), cache-busted with
`?v=<mc_bindings build timestamp>` rather than a webpack content hash.

**Scope decision**: the synchronous `renderChunk()` path (immediate re-mesh on `WorldUpdate`
block edits) was deliberately left untouched — still inline `chunkProcessFaces`/`chunkEnd`. Small
immediate edits, not the bulk-load scenario this was targeting.

**Measured impact**: `meshDrainMs` (main-thread mesh work) dropped from ~15-26ms/block to
~5-15ms/block (the remainder being the still-inline row scan, not moved to the worker). Confirmed
the CPU-heavy part is genuinely off the main thread now. **Did not by itself reduce overall
`blockMs`** — see the next finding for why.

### 2. `SLAB_HEIGHT` 16 → 32 → 64 (`e987e191`, `87fb7e78`)

**Finding that motivated this**: after (1), `renderMs` (`scene.render()` itself) remained the
dominant per-block cost (~150-200ms), while mesh/GPU-upload work was comparatively small
(~10-110ms). `activeMeshes` was ~900-1300/frame — draw-call count, not chunk-mesh CPU cost or
shader cost (see [bloom/FXAA test](#3-bloomfxaa-ruled-out) below), was the actual bottleneck.

Each chunk is split into `SLAB_HEIGHT`-tall × per-material sub-meshes for tight per-slab frustum
culling (comment in `chunkBuilder.ts`/`chunkMeshWorker.ts` — must be kept in sync between the
two files). Taller slabs mean fewer, larger meshes: fewer draw calls, coarser culling.

**Iteration 1 (16→32)**: `totalMeshes` dropped ~25% (4300-4800 → 3279-3331), `renderMs` dropped
~40-50% (120-200ms → 82-96ms steady-state) — but `activeMeshes` (post-cull, actually-drawn count)
barely moved (1150-1200, about the same as before). Conclusion at the time: most of the win came
from less **per-mesh bookkeeping over the total mesh set** (Babylon still evaluates every mesh for
active/inactive each frame), not fewer post-cull draw calls.

**Iteration 2 (32→64)**: this time `activeMeshes` genuinely dropped too (~650-830, down from
~1150-1200), `totalMeshes` ~1900-2270, `renderMs` ~50-110ms steady-state — a real further cut in
actual draw calls, not just bookkeeping.

**Stopped at 64** rather than pushing further: slabs this tall coarsen frustum culling
meaningfully (a tall vertical slab intersects the camera frustum more often even when only a
small portion of it is visible — relevant underground/in caves), and returns were visibly
diminishing (16→32 was a ~40-50% win, 32→64 was smaller). Visually verified in-browser after the
64 change that nothing looked broken (no obviously wrong culling) before committing, but this
was a quick visual check, not systematic — see [open items](#slab_height-culling-correctness) below.

### 3. Bloom/FXAA ruled out

Tested live via the console (`window.mcState.renderPipeline.bloomEnabled = false` /
`.fxaaEnabled = false`, pipeline already exposed on `window.mcState` — no rebuild needed): 20
samples each, same scene state (`activeMeshes`/`totalMeshes` matched between runs so the
comparison was controlled). `renderMs` averaged 133ms OFF vs 128ms ON — within noise. **Not a
meaningful cost**; left enabled.

### 4. `ChunkDebug` overlay: DOM grid → canvas (`9f8f3f40`)

Found via an actual Chrome DevTools performance trace (see
[Methodology](#methodology-devtools-trace) below), not manual instrumentation — the `DOMSize`
insight flagged **413 DOM elements, 225 children under one parent, a 78ms forced-layout
recalculation**. Root cause: `ChunkDebug.tsx` rendered one `<div>` per chunk cell (up to 15×15 for
`FORWARD_VIEW_RADIUS`), and `LocalPlayerController.kt`'s `jsUpdateChunkDebug` call is
**unconditional** — it runs every 10-tick block regardless of whether the overlay is visible.
Worse, `GameScreen.tsx` shows this overlay **by default during initial world load**
(`chunkLoading || chunkDebugVisible`), i.e. exactly when chunk-streaming load is already at its
heaviest.

**Fix**: rewrote `ChunkDebug.tsx` to draw the whole grid + player yaw arrow in a single
`<canvas>` pass instead of one `<div>` per cell. Per-cell hover tooltips are lost (debug overlay,
not interactive UI — accepted tradeoff).

**Verified via a second DevTools trace**: `DOMSize` and `ForcedReflow` insights **no longer
appear at all** post-fix (only the unrelated `CLSCulprits` remained). Fix confirmed to eliminate
the specific issue found, not just reduce it.

**Caveat on overall impact**: the 78-98ms forced-reflow figure from the trace was cumulative over
a ~7s trace window (~14 HUD blocks), i.e. ~7ms/block on average — real, but a small fraction of
the ~100-170ms/block gap that remained unexplained even after this fix (see
[remaining gap](#the-unexplained-blockms-gap) below). Don't expect this alone to have "fixed"
the FPS complaint; it fixed one confirmed, measured problem.

## Bugs fixed along the way

Two bugs were found and fixed in the instrumentation itself, both because a "spike" reading kept
not matching intuition:

1. **`b744dffc`** — `ChunkManager.drainPendingChunks()` returns early when there's nothing to
   mesh (the common idle steady-state). The early return skipped resetting
   `lastFaceScanMs`/`lastGpuUploadMs`/`lastFacesProcessedThisDrain`, so those fields kept
   returning whatever they were on the **last call that actually did work** — indefinitely, while
   idle. Symptom: `gpuUploadMs` and `facesProcessed` pinned at an identical nonzero value for
   dozens of consecutive blocks, reading as a phantom repeating chunk re-upload. Fixed by zeroing
   those fields on the idle-return path too.
2. **`28dacc41`** — `jsNow()` (`Date.now()`, integer-millisecond resolution) was used to time
   chunk-message decode, which routinely finishes in well under 1ms — so nearly every
   before/after delta rounded to exactly `0`, reading as "decode is free" rather than "decode is
   too fast to measure this way." Added `jsPerfNow()` (`performance.now()`, sub-ms) and switched
   this one call site to it. `wsDecodeMs` now reads small nonzero fractional values
   (~0.01-0.03ms), confirming decode genuinely is negligible — but now for a real reason, not a
   measurement artifact.

Both are worth remembering as a general lesson for this codebase: **`jsNow()`/`Date.now()` is too
coarse for anything expected to take under ~2-3ms** — use `jsPerfNow()`/`performance.now()`
instead for new short-duration timing.

## Methodology: DevTools trace

Everything above item 4 came from custom JS/Kotlin timers reasoned about by hand. When the
`blockMs` gap (wall-clock time per 10-tick HUD block minus the sum of every per-subsystem timer)
stayed large (~100ms+) even after covering mesh/GPU/physics/interaction/aux/render/network/
npc-vehicle-remote, further blind instrumentation stopped being productive — the missing time is,
by construction, time where **no instrumented code is running at all** (GC, browser/event-loop
scheduling, React reconciliation), which a manual `performance.now()` wrap around a specific code
path can't see by definition.

At that point the investigation switched to Chrome DevTools' `performance_start_trace` /
`performance_stop_trace` (via the `chrome-devtools` MCP tools), which captures the full main-thread
timeline (GC, layout, script, React's own scheduler instrumentation) rather than only the paths a
human thought to wrap. This is what surfaced the `ChunkDebug` DOM-size/forced-reflow issue — a
category of problem the custom instrumentation was structurally unable to find.

**Caveat**: a trace/console session driven through DevTools *automation* (as opposed to a human
using DevTools on their own foreground tab) can itself introduce timing artifacts — one
automated session captured a single `blockMs: 1045` outlier with all subsystem timers near-zero,
almost certainly due to the automated/backgrounded tab being throttled by the browser rather than
a real in-game stall. Automated traces are good for finding *categories* of problem (forced
reflow, DOM size, long tasks) via the structured Insights API; they are not reliable for
fine-grained "is this specific number better or worse than that one" comparisons — that still
needs the user testing live, foreground, with the console-based spike log.

## The unexplained `blockMs` gap

Even after every fix above, `blockMs` (wall-clock per 10-tick block) exceeds the sum of every
per-subsystem timer by roughly **100-170ms** in steady state, and the `ChunkDebug` canvas fix
(which *did* remove a confirmed, trace-verified cost) barely moved this figure — because that
cost was only ~7ms/block on average, a small fraction of the total gap.

This gap is the main open question. Candidates, in rough order of suspected weight, none
confirmed:

1. **GC pauses.** Nothing in this codebase's JS/Kotlin timers can see a GC pause that happens
   between two timed sections — it just inflates the wall-clock gap between them. The chunk-mesh
   worker's result handoff allocates a fresh copy (`TypedArray.prototype.slice()`) per
   material/slab group per chunk mesh job (`chunkMeshWorker.ts`, `ctx.onmessage` handler) — this
   is a plausible allocation-pressure source worth checking first, since it's new (from the
   worker-migration change) and directly proportional to chunk-load activity, which is when the
   gap seems largest.
   - **How to check**: Chrome DevTools Performance panel (manual, foreground tab), enable
     "Screenshots" + look at the "GC" track / minor-GC event density during a period of heavy
     chunk loading. `performance.memory` (Chrome-only, imprecise) polled alongside the spike ring
     buffer could give a cheap first signal without a full trace.
2. **`delay(16)` / coroutine scheduling drift.** The game-tick loop (`GameClient.kt`) uses
   `kotlinx.coroutines.delay(16)` (→ `setTimeout` under Kotlin/Wasm-JS), which has no hard
   guarantee of firing at exactly 16ms — under main-thread contention (from anything else running,
   including the render loop's own `requestAnimationFrame` callback) it can fire meaningfully
   late. Ten ticks' worth of drift adds up fast.
   - **How to check**: instrument the *actual* delay between successive `tick()` invocations vs.
     the nominal 16ms (already partly captured by `tickIntervals`/`tickJitterMs` in the existing
     HUD stats — worth cross-referencing against `blockMs` directly rather than inferring), or use
     DevTools' "Timers" track in a manual trace to see `setTimeout` fire-time vs. scheduled-time.
3. **React reconciliation cost from the HUD update itself.** `uiState.hud = HudData(...)` fires
   once per 10-tick block — the same cadence as the gap measurement — and triggers a React
   re-render of whatever subscribes to that state (`Statistics.tsx` at minimum). Never measured in
   isolation.
   - **How to check**: React DevTools Profiler (separate from Chrome's Performance panel) around
     a period of active HUD updates, or temporarily stub `uiState.hud = ...` to a no-op and compare
     `blockMs` before/after (quick, if crude, isolation test).
4. **General main-thread contention from the DevTools/CDP connection itself**, if continuing to
   investigate via automated tooling — see the caveat above. Any further gap-hunting should
   default to the user's own foreground-tab console testing (the `[fps-spike]` log), reserving
   DevTools automation for category-level checks (Insights) rather than precise timing
   comparisons.

**Recommendation if this is picked back up**: don't add more custom `performance.now()` wraps —
the remaining gap is, almost by definition, in places those can't see. Go straight to a **manual**
DevTools Performance trace (user's own foreground tab, not automation) over a single ~1-2s window
during active chunk loading, and read the flame graph directly for GC/idle/scheduling gaps between
the already-known-cheap script execution blocks.

## Other unexplored leads

Lower confidence / larger scope than the `blockMs` gap above, not investigated further this
session:

- **Mesh merging across chunks/materials.** `activeMeshes` is down to ~450-800/frame (from
  ~900-1300) after the `SLAB_HEIGHT` change, but that's still several hundred draw calls, and each
  slab-height increase trades away culling precision to get there. The next lever for reducing
  draw calls without that tradeoff would be merging adjacent same-material meshes (within a chunk,
  or across chunk borders) — geometry-batching, not just coarser slabbing. Nontrivial: needs
  dynamic re-merging on block edits (`WorldUpdate`), not just at initial mesh time, and interacts
  with the per-slab culling bounding boxes this session was tuning. No measurement taken on
  expected payoff before starting — would want a quick manual estimate of achievable mesh count at
  various merge granularities before committing to the engineering cost.
- **`gpuUploadMs` spikes.** Individual chunk uploads (`vd.applyToMesh`, still main-thread by
  construction — a Worker can't touch the GPU) occasionally spike to 30-80ms in one call, visible
  in the spike ring buffer's `gpuUploadMs` field and correlated with `renderMsMax` spikes in the
  same block. Never addressed. Possible directions: spread a large chunk's upload across multiple
  `vd.applyToMesh` calls / multiple frames instead of one shot, or cap how much geometry a single
  drain call is allowed to upload per frame (mirroring the existing face-processing budget
  concept, `FACE_SLICE_SIZE`/`budgetMs` in `ChunkManager.kt`, but for the upload phase instead of
  the now-moved-to-worker geometry phase).
- **Material/shader-switch count.** Draw-call count (`activeMeshes`) was used as the perf proxy
  throughout this session, but changing *material* between consecutive draw calls has its own
  GPU-state-change cost independent of raw draw-call count. Never measured separately — would need
  either `SceneInstrumentation` (Babylon's built-in per-frame timing breakdown — active-mesh-eval
  time, render-target time, etc., not currently used anywhere in this codebase) or manual
  reasoning about how many distinct materials are active in a typical view.
- **`CLSCulprits` insight**, present in both DevTools traces taken this session (unlike `DOMSize`/
  `ForcedReflow`, which the `ChunkDebug` fix eliminated). Not investigated — plausibly just the
  "Loading world…" overlay disappearing (a real, expected one-time layout shift, not a recurring
  gameplay cost), but not confirmed either way.
- **`SLAB_HEIGHT` culling correctness at 64.** Only checked with one quick visual pass in the
  browser after the change (no obviously-wrong geometry culling). Not systematically verified —
  worth a deliberate check standing in a few caves/enclosed underground spaces and looking for
  chunks failing to cull (visible through walls / floor) or, conversely, visibly popping in/out
  at the edge of the frustum in a way that wasn't happening at `SLAB_HEIGHT=16`.
