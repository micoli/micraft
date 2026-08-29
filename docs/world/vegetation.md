---
title: Vegetation growth
---

# Vegetation growth

## How to play

Plant a `SEED` item on a `vegetationHost` block and it grows through a random
chain: `SEED → SPROUT → SAPLING → tree`. `VegetationManager` checks growth every
`growthCheckIntervalTicks`; breaking the block cancels growth. Each stage waits a
random duration between its `minTicks` and `maxTicks`.

## Configuration

`data/config/vegetation.yaml` (bundled default `resources/config/vegetation.yaml`,
schema `vegetation.schema.json`):

```yaml
enabled: true
growthCheckIntervalTicks: 40
chains:
  - name: oak_growth
    stages:
      - { block: SEED,    minTicks: 400, maxTicks: 1200 }
      - { block: SPROUT,  minTicks: 600, maxTicks: 2000 }
      - { block: SAPLING, minTicks: 800, maxTicks: 2400 }
```

Which columns get initial vegetation at generation time is set per biome
(`vegetation:` entries in [`biomes.yaml`](biomes.md)) and gated by
[`roads.yaml`](structures.md) (`vegetationAllowedOnRoad`).

Reload with `/reload`.
