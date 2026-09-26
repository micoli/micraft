# Admin routes: auth + world + player edit behind one module

Status: needs-triage
Strength: Worth exploring

## Files
- `http/AdminController.kt` (3778 LOC, 92 routes; `requireAdmin` ×89, `adminWorld` ×73)
- `adminWorld()` L496 ≡ `wsWorld()` L506
- `requireAdmin` duplicated: `SimulationController.kt:64`, `MapController.kt:132`, `ChunkController.kt:53`
- JSON escapers: `adminWsJson` L3726, `GameWorld.kt:72` `toPlayerAdminJson`

## Problem
Each route repeats the guard, world resolution and the live/offline player split (live: mutate + broadcast + save + `CharacterSync`; offline: `loadPlayerFile` ×6 + `savePlayerState`). A forgotten guard leaves the route open.

## Solution
- An `adminRoute { world -> }` DSL or Ktor plugin: auth plus `X-Micraft-Game-Session` resolution.
- `PlayerEditor.edit(name) { state -> }`: hides the live/offline split, persistence and resync.

## Tests
8 admin test files for 92 routes.
