---
title: Architecture
---

# Architecture

- **Server authoritative** — the client sends `MoveIntent`; the server validates
  and replies `PlayerUpdate`.
- **Client-side prediction** — the client predicts its own movement at ~60 fps
  and reconciles toward the server: XZ by soft correction, Y by soft correction
  within a tolerance and a snap beyond one block (ADR-0001).
- **Shared simulation** — rules run by both sides live in `core`, so client
  prediction and server stay identical (ADR-0002).
- **Chunk rendering** — one `VertexData` buffer per chunk (~200 draw calls); a
  `WorldUpdate` triggers a re-mesh of the affected chunk.

| Module | Path | Role |
|--------|------|------|
| `core` | `core/src/commonMain` | Domain model, protocol, physics, chunk gen — shared |
| `server` | `server/src/main/kotlin` | Ktor WebSocket, game loop, persistence |
| `app/webApp` | `app/webApp/src/wasmJsMain` + `app/webApp/ts-src` | Web client (Kotlin/Wasm + BabylonJS, React UI) |
| `app/minigames/<name>` | own npm projects | Mini-game bundles |
| `codec-processor` | KSP | Generates the protocol codec registries |

Read on:

- [World generation pipeline](world-generation.md)
- [Game loop — tick sequence](game-loop.md)
- [Chunk transport modes](chunk-transport.md)
- [Automatic manager state machine](managers-state-machine.md)
- [Protocol messages](protocol.md)
- [Architecture decisions (ADR)](../adr/index.md)
