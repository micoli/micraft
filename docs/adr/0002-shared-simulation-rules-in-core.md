---
status: accepted
---

# Rules run by both client and server live in `core`

Any rule that both the client and the server evaluate (movement, AABB physics, chunk generation, block properties,
ability costs shown to the player) lives once in the Kotlin Multiplatform `core` module and is compiled into both
the JVM server and the Wasm client. Any value the server can tune at runtime reaches the client as data (for example
in `Welcome` or a config sync message), and the client never hardcodes it. Duplicated rules and constants have
repeatedly drifted apart and broken **Prediction**, so this is strict, even for a few lines.

## Consequences

- Movement composition and gravity/jump/fly constants still violate this today (`MovementProcessor` vs
  `LocalPlayerController`, `CLIENT_GRAVITY`); tracked in `.scratch/architecture-review/issues/01-player-kinematics-core.md`.
- A TypeScript UI never re-implements a game rule; it asks Kotlin/`core` or the server.
