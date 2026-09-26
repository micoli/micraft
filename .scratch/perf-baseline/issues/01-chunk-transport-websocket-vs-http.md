# Measure chunk transport: WebSocket push vs HTTP pull

Status: needs-triage
Type: research
Blocked by: 02, 04, 05

## Context

There are two chunk transports (`chunks.transport` in `server.yaml`): `websocket` (default, server-side
`ChunkStreamer` pool) and `http` (client pulls `GET /api/chunks/{cx}/{cz}` via `HttpChunkFetcher`, 4 workers).
HTTP was added in 59fc6ff4 to remove per-session pool state and latency from the server tick, but it never became
the default and nothing compares the two. Decision (2026-09-26): measure both, keep one, then write an ADR or remove
the loser. `/api/chunks` stays either way, because the admin Instance editor uses it.

## Measure (same scenario, both modes)

- Server: tick phase times (`micraft_tick_phase_avg_ms`, especially chunk streaming), CPU, heap, with N Characters
  moving through ungenerated terrain.
- Client: time-to-first-render around spawn, chunks/s received, mesh queue length, frame time during streaming.
- Network: bytes per chunk, request overhead (HTTP headers/auth) vs WS frames.

## Outcome

- The winner becomes the only game transport, with an ADR recording why.
- The loser's code is removed (`ChunkStreamer` pools or `HttpChunkFetcher` + the Welcome switch).
