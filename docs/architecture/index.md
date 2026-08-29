---
title: Architecture
---

# Architecture

- **Server authoritative** — the client sends `MoveIntent`; the server validates
  and replies `PlayerUpdate`.
- **Client-side prediction** — `GameClient` predicts XZ locally at ~60 fps and
  soft-corrects toward the server. Y (gravity) is always server-authoritative.
- **Shared simulation** — all AABB physics and chunk generation live in `core`,
  so client prediction and server stay identical.
- **Chunk rendering** — one `VertexData` buffer per chunk (~200 draw calls); a
  `WorldUpdate` triggers a re-mesh of the affected chunk.

| Module | Path | Role |
|--------|------|------|
| `core` | `core/src/commonMain` | Domain model, protocol, physics, chunk gen — shared |
| `server` | `server/src/main/kotlin` | Ktor WebSocket, game loop, persistence |
| `app/webApp` | `app/webApp/src/wasmJsMain` | Web client (Kotlin/Wasm + BabylonJS) |
| `app/desktopApp` | `app/desktopApp/src/main` | Desktop client (JVM) |
| `app/shared` | `app/shared/src/commonMain` | Shared Compose code |

Read on:

- [World generation pipeline](world-generation.md)
- [Game loop — tick sequence](game-loop.md)
- [Chunk transport modes](chunk-transport.md)
- [Automatic manager state machine](managers-state-machine.md)
- [Protocol messages](protocol.md)
