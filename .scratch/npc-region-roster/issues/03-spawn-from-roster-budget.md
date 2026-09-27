# Spawn from the Roster within the Region budget

Status: ready-for-agent
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

- [ ] Server tests: no non-Roster type spawns; a Region never exceeds its budget, births included; shares follow
      weight.
- [ ] Existing E2E NPC specs still pass (see `project_e2e_preexisting_failures` for known failures).
- [ ] `make dc CMD="./gradlew :server:test"`, `make check-schemas`, `make docs` clean.
