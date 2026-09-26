# Drop CPU copies of terrain vertex data

Status: needs-triage
Type: task
Blocked by: —

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
