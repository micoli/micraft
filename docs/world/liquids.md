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

## Configuration

Per-block liquid flags live in the block yaml
([`resources/blocks/`](blocks-catalog.md)):

```yaml
liquid: true
viscosity: 4        # higher = slower flow
```

Lake generation is part of the biome definition
([`biomes.yaml`](biomes.md)).
