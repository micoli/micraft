# No hostile NPC type beyond Danger level 25

Status: needs-triage
Type: task

## Context

Danger levels run 1–60 (`RPG_LEVEL_MAX`), and Danger tier 5 covers 21–60. Every level-gated NPC type has
`maxLevel` ≤ 25, so Regions beyond ~2150 blocks from spawn (level 26+, the bulk of tier 5) can only roster
ungated fauna and a few ungated hostiles (wolf, spider, jackal, hyena, polar_bear, wolf_veteran…). Tier 5 is
the easiest-looking part of the World.

Split out of the Region Roster grilling (2026-09-27), which assumes the eligible pool per (Biome, Danger level)
is meaningful at every level.

## Current state

- Level ramp: `VoronoiBiomeZones.cellLevel`, `zoneLevelSafeDist: 768`, `zoneLevelMaxDist: 4096`
  (`resources/config/biomes.yaml`).
- Tier bands: `core/.../game/world/ZoneTier.kt` (T5 = 21–60).
- NPC level gates: `minLevel`/`maxLevel` in `resources/entities/*/*.yaml`, highest `maxLevel` = 25.

## Options to weigh

- Open the top of each tier-5 type to 60 (and scale NPC level with the Region's Danger level).
- Compress the level ramp so tier 5 ends at 25.
- Add higher-level NPC types.
