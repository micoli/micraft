# Drop CPU copies of terrain vertex data

Status: resolved
Type: task
Blocked by: —

Decided 2026-09-26: voxel raycast for creative targeting (exposed from Kotlin) + remesh on context restore; before issue 08. No E2E covers creative targeting: the user validates it by hand.

## Goal

Free the vertex/index arrays Babylon keeps on the CPU once terrain meshes are on the GPU.

## Evidence (`reports/2026-09-26-8ca3746c.md`)

- The full heap snapshot holds 155 MB of `JSArrayBufferData` (11 199 buffers): 29 % of the client heap, matching the
  GPU buffer total (147–175 MB). Babylon keeps each `VertexBuffer`'s data after upload.

## What depends on the CPU copy

- Creative mode targets terrain with `scene.pick()` + `getNormal()` (`game/lib/creativeMode.ts`), which reads
  positions, normals and indices. Replace it with a voxel raycast along `scene.createPickingRay(...)`, as the FPS
  mode already does, so terrain meshes can become non-pickable.
- WebGL context loss: Babylon rebuilds buffers from the CPU data on restore. Without it, remesh every loaded chunk on
  `engine.onContextRestoredObservable`.

## Approach

After `applyToMesh` in `chunkEndFromWorker` / `chunkEnd` / impostors, release the data
(`mesh.geometry.clearCachedData()` or per-buffer), once the two dependencies above are handled.

## Acceptance

- Live client heap after `make perf-heap` drops by ≥ 100 MB; creative mode block targeting still works (E2E);
  a forced context loss (`WEBGL_lose_context`) restores the terrain.

## Answer

Done in `df032ffa`. Terrain and impostor meshes call `geometry.clearCachedData()` in game (admin editors keep the
default); creative mode targets with `VoxelRaycast` (`core`) exported as `mcRaycastVoxel`, covered by
`e2e/creative-targeting.spec.ts`; `ChunkManager.remeshAll()` runs after a context restore (checked by hand with
`WEBGL_lose_context`: identical frame before/after). Full heap snapshot: 533 → 348 MB self size, `JSArrayBufferData`
155 MB → gone from the top; live ArrayBuffers 35 MB (`heap/2026-09-26T171833-df032ffa.md`).
