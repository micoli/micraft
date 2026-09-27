# Spawn from the Roster within the Region budget

Status: resolved
Type: task
Blocked by: 02

## Context

Spec: `../spec.md` (Budget). Today `NpcSpawner.trySpawn` iterates every definition, enforces `minTotal`/`maxTotal`
world-wide and `maxNpcs` per 256² grid square (all types, value from the Biome); births bypass the floors.

## What to build

- `NpcSpawner` only spawns the NPC types of the Region's Roster, up to each type's share of `regionBudget`.
- Population is counted per Region (wild NPCs only, births included); pets and Quest givers do not count.
- Reproduction is refused when the Region budget is full.
- Remove `minTotal` from `NpcSpawnConfig`, entity yaml and schema; keep `maxTotal` only as the rares' world-wide
  ceiling.
- Adapt `FlatArenaChunkGenerator`, `EndToEndBoundedChunkGenerator`, `WorldSimulator` / `SimulationConfig` to
  `regionBudget`.
- `/npc roster` shows live population / budget per type.
- Update `docs/entities/npcs.md` (and its stale "every 200 ticks") and `docs/world/biomes.md`.

## Acceptance criteria

- [x] Server tests: no non-Roster type spawns; a Region never exceeds its budget, births included; shares follow
      weight.
- [x] Existing E2E NPC specs still pass (see `project_e2e_preexisting_failures` for known failures).
- [x] `make dc CMD="./gradlew :server:test"`, `make check-schemas`, `make docs` clean.

## Comments

- 2026-09-27: `RegionPopulation` (`game/npc/roster`) caches each Region's Roster and counts wild NPCs (no owner;
  auto-spawned or animal) per Region and type. `NpcSpawner` picks a random column, its Region, then a Roster type
  still under its share; `maxPerChunk` and the rares' `maxTotal` still apply. Mating and births are refused in a full
  Region (`AnimalInteractionProcessor.isCrowded` and `spawnOffspring`).
- `maxNpcs` and `minTotal` removed; `maxTotal` removed from the 65 common types (only ≤ 5 stays).
- A zero budget turns auto-spawn off without capping births: E2E worlds keep no auto-spawn, and the simulator's
  `maxNpcs` (kept as the protocol field name) is now its arena Region's budget.
- Flat arena and E2E bounded generators expose one Region covering the whole world.
