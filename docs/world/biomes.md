---
title: Biomes
---

# Biomes

## Overview

Biome types: `snow_peaks`, `desert`, `dry_plains`, `plains`, `forest`,
`pine_forest`, plus the aquatic biomes `sea` and `lake`. They are distributed
across the world by **Voronoi zones** keyed on
a moisture value (0→1) and an altitude band, with blend zones between neighbours.
Each biome sets surface/subsurface/filler blocks, elevation range, grass colour,
NPC cap, vegetation entries and cavern parameters.

Moisture bands are tuned so every biome is actually reachable (the generator's
noise clusters around the middle): `desert` and `dry_plains` — the only
tree-free biomes — take the drier half, `plains`/`forest`/`pine_forest` the
wetter half. `snow_peaks` overrides any biome above altitude 150.

Each biome's `elevationMin`/`elevationMax` differ. The surface-height band used
for a column is a **distance-weighted average of every nearby cell's** band
(weight falls off over `elevationBlendRadius`), a continuous field with no
sudden primary/secondary switch — so biome borders slope instead of forming
cliffs.

## Aquatic biomes

`sea` and `lake` are flat-band biomes: `elevationMin == elevationMax == waterLevel`
(`sea` at 60, `lake` at the `pine_forest` terrain floor, 72). Extra fields:

- `liquid: true` — marks the biome as water-filled.
- `waterLevel` — the flat water surface Y.
- `waterMaxDepth` (default 8) — deepest the carved basin floor drops below `waterLevel`.
- `waterFloorRelief` (default 0) — amplitude in blocks of small-scale noise bumps on the
  seabed. Damped toward the basin border so the closing ramp to the shore stays clean.
- `islandFraction` (default 0) — fraction 0..1 of the biome surface that emerges as land.
  A low-frequency noise field is thresholded (probit-calibrated) so roughly that fraction
  of the surface rises above `waterLevel`; a rim band ramps the floor up to each island
  shore instead of a wall.
- `islandHeight` (default 8) — peak height in blocks an island crest reaches above `waterLevel`.
- `tintColor` — `[r, g, b]` 0..1, applied as a full-screen colour + tighter fog only while
  the player's **head is underwater** (`PlayerState.headInLiquid`), fading in/out. Out of
  the water the view is unchanged.

**No overflow.** Every land biome shares an `elevationMin` floor `L = 72` (≥ the highest
`waterLevel`). The basin floor is carved down by `waterMaxDepth × basinEdge`, where
`basinEdge` tapers to 0 at any biome border, so the floor rises back to `waterLevel`
exactly at the shore. The water surface stays flat at `waterLevel` regardless of seabed
relief or islands, so a water block is never left exposed next to a lower neighbour — the
shore is always solid terrain at or above `waterLevel`. `sea` beside `lake` closes both
basins symmetrically at the shared border.

Aquatic biomes carry no vegetation and no caverns, and are excluded from faction
spawn placement. Minimap colour comes from their blue `grassColor`. Their
`maxNpcs` cap only admits NPCs that can swim (`movementMode` with `SWIMMING`);
a `SWIMMING`-only NPC spawns inside the water column rather than on the surface
— see [NPCs](../entities/npcs.md).
Players and land NPCs breathe while submerged: see
[Liquids → Swimming & breath](liquids.md).

## Configuration

`data/config/biomes.yaml` (optional — falls back to `BiomeRegistry.default()` if
missing; bundled default in `resources/config/biomes.yaml`):

```yaml
voronoiCellSize: 256
voronoiBlendRadius: 20     # surface-block dithering width at a border
elevationBlendRadius: 96   # distance falloff for the blended elevation field (anti-cliff)
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
