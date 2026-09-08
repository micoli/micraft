---
title: NPCs
---

# NPCs

## How to play

NPC types include `SELLER`, `BLACK_SMITH`, `GOAT`, `DUCK`, `WOLF`, `CAT`, `BEAR`,
`POLAR_BEAR` and more, with behaviours: **static**, **interactable**,
**random-wander**, and **hostile-aggro**. Models are Blockbench `bbmodel`
animations with configurable walk-bone aliases.

- **`/spawn <npc_model> [x y z]`** *(admin)* — spawn on the block you look at.
- **`/npc <spawn|list|remove|tp> [args]`** — manage NPCs.
- **`/goto <playerName|npcName>`** — teleport to an NPC.
- **`/npcbuy` / `/npcsell`** — trade with `SELLER` NPCs. See
  [Economy](../social/economy.md).
- Some creatures can be tamed into [pets](pets.md) with **`/tame`**.

`NpcManager` handles wander, pathfinding and interaction each tick;
`NpcSpawner.trySpawn` runs every 200 ticks, capped per biome by the biome's
`maxNpcs`.

**Movement modes** — a definition lists one or more `movementMode` values
(`WALKING` default, `SWIMMING`, `FLYING`):

| `movementMode` | behaviour |
|----------------|-----------|
| `[WALKING]` | land creature, gravity applies |
| `[SWIMMING]` | pure water dweller (`shark`, `dolphin`, `squid`): never drowns, holds depth while submerged, spawns in the water column of a `liquid` biome |
| `[SWIMMING, WALKING]` | amphibious: never drowns, spawns and walks on land |
| `[FLYING]` / `[FLYING, WALKING]` | airborne — flight AI not implemented yet; a `FLYING`-only NPC just ignores gravity and holds its spawn height |

Any NPC that cannot swim breathes like a player and drowns
(`NpcDeathCause.DROWNING`) if its head stays underwater — see
[Liquids → Swimming & breath](../world/liquids.md).

{{ story "story/game-windows-npcshopdialog--basic" caption="SELLER NPC shop — buy and sell prices" }}

## Configuration

**Global NPC behaviour** — `data/config/npc.yaml` (schema `npc.schema.json`):

```yaml
wanderPauseTicksMin: 40
wanderPauseTicksMax: 120
wanderStepTicksMax: 60
interactionRange: 4.0
updateRange: 96.0
maxSpawnAttemptsPerTick: 3
jumpVelocity: 8.0
gameDayDurationSeconds: 1200.0
```

**Per-type definitions** — loaded by `NpcRegistryLoader`; codex info served at
`GET /api/admin/npc-types`. Live instances with full state:
`GET /api/admin/npcs`, `GET /api/admin/ws/npcs`. A definition may also set
`tameable: true` / `tameBaseChance` to allow taming — see [Pets](pets.md), or
`movementMode` (see above) for a swimming or flying creature.

**Models** — `resources/models/<name>/<name>.bbmodel` with an optional
`<name>.yaml` skin config.

Spawn caps per biome: [`biomes.yaml`](../world/biomes.md). Reload with
`/reload` or `/config:reload npc`.
