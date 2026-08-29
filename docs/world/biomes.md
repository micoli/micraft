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

## Configuration

`data/config/biomes.yaml` (optional — falls back to `BiomeRegistry.default()` if
missing; bundled default in `resources/config/biomes.yaml`):

```yaml
voronoiCellSize: 256
voronoiBlendRadius: 20
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
