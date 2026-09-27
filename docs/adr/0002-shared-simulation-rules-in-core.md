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

- Player movement runs through `core` `PlayerKinematics.step()`: `MovementProcessor` and the client Prediction are
  adapters over a `BlockQuery`, and gravity / jump / fly speeds reach the client as `KinematicTuning` in `Welcome`
  and `GameConfigSync`. Only what counts as solid differs: the client does not see occupied entities.
- A TypeScript UI never re-implements a game rule; it asks Kotlin/`core` or the server.
