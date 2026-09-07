---
title: Biomes
---

# Biomes

## Overview

Biome types: `snow_peaks`, `desert`, `dry_plains`, `plains`, `forest`,
`pine_forest`. They are distributed across the world by **Voronoi zones** keyed on
a moisture value (0→1) and an altitude band, with blend zones between neighbours.
Each biome sets surface/subsurface/filler blocks, elevation range, grass colour,
NPC cap, vegetation entries and cavern parameters.

Moisture bands are tuned so every biome is actually reachable (the generator's
noise clusters around the middle): `desert` and `dry_plains` — the only
tree-free biomes — take the drier half, `plains`/`forest`/`pine_forest` the
wetter half. `snow_peaks` overrides any biome above altitude 150.

## Configuration

`data/config/biomes.yaml` (optional — falls back to `BiomeRegistry.default()` if
missing; bundled default in `resources/config/biomes.yaml`):

```yaml
voronoiCellSize: 256
voronoiBlendRadius: 20
zoneLevelSafeDist: 768     # cells within this many blocks of origin are zone level 1
zoneLevelMaxDist: 4096     # cells at/beyond this distance are RPG_LEVEL_MAX
biomes:
  - id: snow_peaks
    zones:
      - { moistureMin: 0.0, moistureMax: 1.0, altitudeMin: 150, altitudeMax: 1024 }
    surface: SNOW
    subsurface: STONE
    subsurfaceDepth: 2
    maxNpcs: 40
    elevationMin: 150
    elevationMax: 200
    fillers:
      - { type: STONE, density: 0.4 }
      - { type: AIR, density: 0.05 }
    vegetation:
      - { type: pine_tree_snow, density: 0.04 }
    caverns:
      cavernMinHeight: 8
      cavernMaxHeight: 25
      staircaseEnabled: true
```

Full property list: `core/.../world/BiomeDefinition.kt`. Schema:
`biomes.schema.json`. Reload with `/reload`.

--8<-- "reference/_generated/biomes.md"

## Zone levels

Every Voronoi cell also carries an **RPG zone level** (`VoronoiBiomeZones.zoneLevelAt`),
used to gate NPC difficulty and player spawn placement. Level scales with the
cell seed's distance from the world origin:

- distance ≤ `zoneLevelSafeDist` (768 blocks) → level 1 — a safe starting basin
  around `(0, 0)`;
- between `zoneLevelSafeDist` and `zoneLevelMaxDist` (4096) → linear ramp;
- distance ≥ `zoneLevelMaxDist` → `RPG_LEVEL_MAX`.

So difficulty grows in rough concentric rings outward. New players and
[faction spawns](../systems/server-config.md) are placed in a level-below-5,
tree-free cell near the origin. This is the spatial counterpart of the
[skill level → zone tier](../rpg/index.md#skill-level-zone-tier) table.
