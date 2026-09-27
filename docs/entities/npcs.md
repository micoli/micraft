---
title: NPCs
---

# NPCs

## How to play

NPC types include `SELLER`, `BLACK_SMITH`, `GOAT`, `DUCK`, `WOLF`, `CAT`, `BEAR`,
`POLAR_BEAR` and more, with behaviours: **static**, **interactable**,
**random-wander**, and **hostile-aggro**. Models are Blockbench `bbmodel`
animations with configurable bone aliases in `walkBoneAliases`
(`rightArm`/`leftArm`/`rightLeg`/`leftLeg` for the walk cycle; add
`rightWing`/`leftWing` and the client plays a wing-flap while the NPC is flying).

- **`/spawn <npc_model> [x y z]`** *(admin)* — spawn on the block you look at.
- **`/npc <spawn|list|remove|tp|roster> [args]`** — manage NPCs; `roster [region]`
  shows a Region's Roster (current Region by default).
- **`/goto <playerName|npcName>`** — teleport to an NPC.
- **`/npcbuy` / `/npcsell`** — trade with `SELLER` NPCs. See
  [Economy](../social/economy.md).
- Some creatures can be tamed into [pets](pets.md) with **`/tame`**.

`NpcManager` handles wander, pathfinding and interaction each tick;
`NpcSpawner.trySpawn` runs every 100 ticks (5 s) and when a player enters a new
Region. It fills the active Regions — the Region of each player and the Regions
around it — from their Roster, every type up to its share of the Region budget;
births count toward the same budget, and a full Region refuses them. NPCs of a
Region nobody is near are parked and come back when a player returns; a wild NPC
no longer in its Region's Roster is dropped instead, Quest givers are recreated
by their spawner, and Pets are never parked.

**Roster** — each Region has a Roster ([ADR-0010](../adr/0010-wild-npc-population-per-region-roster.md)):
2–4 passive and 1–3 hostile NPC types drawn from those its Biome (`spawnBiomes`)
and Danger level (`minLevel..maxLevel`) allow, derived from the World seed and
never stored. Sea and lake Regions only draw aquatic types. A rare type
(`maxTotal` ≤ 5) joins about one eligible Region in five, capped at 2
individuals. The Biome's `regionBudget` is split among the Roster by each
type's `spawn.weight` (default 1).

**Names** — every auto-spawned NPC gets a randomly generated name, unique
across every live NPC *and* every player (connected or not). An animal type
(one with an `animal:` block, e.g. `fox`, `wolf`) gets a single plain name
("Rusty"); anything else gets a two-part fantasy name ("Aldric Ironforge"),
with race (orc/elf/dwarf/human) inferred from the type key. `/goto`
autocomplete lists every live NPC as `Name (Type)`.

**Movement modes** — a definition lists one or more `movementMode` values
(`WALKING` default, `SWIMMING`, `FLYING`):

| `movementMode` | behaviour |
|----------------|-----------|
| `[WALKING]` | land creature, gravity applies |
| `[SWIMMING]` | pure water dweller (`shark`, `dolphin`, `squid`): never drowns, holds depth while submerged, spawns in the water column of a `liquid` biome |
| `[SWIMMING, WALKING]` | amphibious: never drowns, spawns and walks on land |
| `[FLYING]` | always airborne (`parrot`): gravity off, cruises `flyCruiseHeight` blocks above the ground and wanders in the air |
| `[FLYING, WALKING]` | flies while free (`pterodactyl`, `eagle`), but **lands and fights on the ground** whenever it has an aggro target or a player has targeted it; climbs back to cruise altitude once the fight is over |

Flight altitude is driven by `NpcPhysics.cruise` (config: `flyCruiseHeight`,
`flyVerticalStep` in `npc.yaml`). There is no 3D pathfinder — flyers wander over
open terrain by straight lines, they do not weave through obstacles or dive-attack.

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
flyCruiseHeight: 8.0     # blocks a flying NPC holds above the ground
flyVerticalStep: 0.4     # max altitude change per tick
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
