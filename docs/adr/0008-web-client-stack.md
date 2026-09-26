---
status: accepted
---

# Web client: Kotlin/Wasm for the game, BabylonJS for 3D, React for the UI

The web client splits into three parts. The game logic is Kotlin compiled to Wasm, so it shares `core` (physics,
protocol, chunk generation) with the server. Rendering uses BabylonJS instead of a home-made 3D engine. The UI
(HUD, windows, admin panel) is React/TypeScript, so it can iterate quickly with a mainstream toolchain (Storybook,
Vitest, Playwright). We accepted a Kotlin ↔ JS bridge between the three parts in exchange for code sharing with
the server and not owning a renderer.

## Consequences

- The bridge (BabylonJS bindings, UI events) is a seam to keep narrow; generated glue such as `mc_bindings.js` is never edited by hand.
- UI logic that must match server rules goes through Kotlin/`core`, never re-implemented in TypeScript (ADR-0002).
