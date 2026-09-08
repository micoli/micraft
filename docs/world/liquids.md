---
title: Liquids
---

# Liquids

## How to play

Water source blocks flow: down first (gravity), then horizontally up to 7 blocks,
with per-block viscosity. Biome lakes are generated at world creation.

- **`/water [x y z]`** *(admin)* — place a water source on the block you look at.
- **`/pump`** — remove all connected liquid blocks in sight.

`LiquidManager` runs every tick over an in-memory active set — see the
[state machine](../architecture/managers-state-machine.md). It activates when
`WATER` is placed or a block adjacent to liquid is removed, and deactivates when a
cell has no `AIR` neighbour.

## Swimming & breath

Standing in liquid forces the **crawling** stance (swim pose) for hitbox and
rendering; horizontal speed keeps the stance the player requested on land, scaled
by the block's `viscosity`. Vertical motion becomes buoyancy: `ascend` swims up
at `SWIM_UP_SPEED`, `descend` dives at `SWIM_DOWN_SPEED`, otherwise the player
sinks slowly under reduced gravity. All of this is decided server-side in
`MovementProcessor`; the web client mirrors it for prediction.

While the player's head is underwater (`PlayerState.headInLiquid`) an **air**
bar shows under the health/rage/mana bars and drains by `DRAIN_PER_TICK` each
tick. Back in air it refills by `REFILL_PER_TICK` (much faster) and the bar
hides once full. At zero breath a `Drowning` damage-over-time effect is applied
until the player surfaces or is downed. God mode freezes the bar full and blocks
the drowning damage.

NPCs that cannot swim breathe by the same rules (`NpcManager` breath tick →
`NpcDeathCause.DROWNING`). NPCs whose `movementMode` includes `SWIMMING` never
drown; a `SWIMMING`-only NPC also spawns inside the water column of a `liquid`
biome — see [NPCs](../entities/npcs.md).

Breath timing constants live in `BreathConstants` (`core`); swim speeds in
`PlayerConstants` — see the [reference table](../reference/_generated/constants.md).

## Configuration

Per-block liquid flags live in the block yaml
([`resources/blocks/`](blocks-catalog.md)):

```yaml
liquid: true
viscosity: 4        # higher = slower flow
```

Lake generation is part of the biome definition
([`biomes.yaml`](biomes.md)).
