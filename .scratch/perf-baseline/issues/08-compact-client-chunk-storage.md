# Compact client chunk storage

Status: ready-for-agent
Type: task
Blocked by: —

Decided 2026-09-26: option 1, sections in `core`, after issue 09.

## Goal

Stop holding 256 KB per loaded chunk on the client when most of it is air.

## Evidence (`reports/2026-09-26-8ca3746c.md`)

- The full heap snapshot holds 932 `$kotlin.wasm.internal.WasmByteArray` for 175 MB: 33 % of the client heap, the
  largest single item. Each is a chunk's `blocks` array: `Chunk.TOTAL` = 16 × 16 × (`WORLD_MAX_Y` + 1 = 1025) bytes.
- Terrain rarely rises above a few hundred blocks, so most of each array is zeros.

## Options

1. **Sections**: `Chunk` stores 16×16×16 sections, `null` for all-air ones (`core`, so server and client share it;
   ADR-0002). Largest win on both sides, touches the mesher, persistence and the wire format (ADR-0003: no
   compatibility needed).
2. **Client-only trim**: keep blocks up to the chunk's top non-air Y (`cachedTopY` exists) and treat above as air.
   Smaller change; a block placed above the top needs a regrow.
3. **Evict data of far meshed chunks**: keep only the mesh beyond the physics/edit radius and re-request the data
   when needed.

## Acceptance

- Live client heap after `make perf-heap` drops by ≥ 100 MB with no frame-time regression (`make perf`, back to back
  with the base commit).
