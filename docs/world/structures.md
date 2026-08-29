---
title: Houses & roads
---

# Houses & roads

## Overview

The procedural generator places **houses** at Voronoi cell centres (rectangular
buildings and a circular temple) and carves **roads** between cells. Both are
part of the [world generation pipeline](../architecture/world-generation.md).

## Houses — `data/config/houses.yaml`

Bundled default `resources/config/houses.yaml`, schema `houses.schema.json`.

```yaml
enabled: true
gridCellSize: 48
clusterCheckRadius: 2
floorHeight: 4
maxHouseSize: 20
houseTypes:
  - id: cabin
    widthMin: 5
    widthMax: 8
    floorsMin: 1
    floorsMax: 1
    roofTypes: [gabled]
    roomsMin: 2
    roomsMax: 4
    doorsMin: 1
    doorsMax: 1
defaultBiome:
  wallBlock: STONE
  roofBlock: STONE
  floorBlock: STONE
```

## Roads — `data/config/roads.yaml`

Bundled default `resources/config/roads.yaml`, schema `roads.schema.json`.

```yaml
enabled: true
vegetationAllowedOnRoad: false
minVegetationDistanceFromRoad: 1
voronoiCellSize: 128
displacementScale: 20.0
displacementFrequency: 0.02
defaultRoad:
  width: 3
  surface: GRAVEL
  roadProbability: 0.7
biomes:
  desert: { width: 5, surface: SANDSTONE, roadProbability: 0.5 }
```

Both reload with `/reload`. Changing them only affects **newly generated**
chunks.
