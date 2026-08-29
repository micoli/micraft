---
title: Chunk transport modes
---

# Chunk transport modes

The server selects the transport via `Welcome.chunkTransport`; the client
switches mode on connect.

| Mode | Delivery | Download priority | Mesh priority |
|------|----------|-------------------|---------------|
| `websocket` | Server pushes `ChunkData` frames over `/chunks` WS | Server-controlled | sorted by proximity + FoV at drain time |
| `http` | `HttpChunkFetcher` pulls chunks on demand (max 4 concurrent) | priority queue, gated by FoV readiness | sorted by proximity + FoV at drain time |

## Priority tiers (score bands)

| Tier | Condition | Score | Gated until… |
|------|-----------|-------|---------------|
| 1 | Under player (dx=0, dz=0) | 0 | — |
| 2 | Radius ≤ 1 (incl. diagonals) | 1000+ | — |
| 3 | FoV ≤ 60° | 2000+ | — |
| 4 | dist > halfR (any direction) | 3000+ | near-FoV chunks meshed |
| 5 | dist ≤ halfR, outside 60° FoV | 4000+ | near-FoV chunks meshed |

In `websocket` mode the download ordering is server-side only; the **mesh queue
is re-sorted** on every drain tick so the client always meshes closest/FoV chunks
first regardless of arrival order.

View radii (`VIEW_RADIUS`, `FORWARD_VIEW_RADIUS`) are in
[world constants](../reference/constants.md), overridable via
[`server.yaml`](../systems/server-config.md).
