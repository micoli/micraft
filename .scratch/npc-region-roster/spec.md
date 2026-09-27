# Region Roster: rationalised wild NPC population and consistent Quests (2026-09-27)

Source: grilling session on branch `framework-improve`. Decision record: `docs/adr/0010-wild-npc-population-per-region-roster.md`.
Vocabulary: `CONTEXT.md` (Region, Danger level, Danger tier, NPC type, Roster, Quest giver).

## Problem

Every `autoSpawn` NPC type whose `spawnBiomes` and `minLevel..maxLevel` match a location may spawn there, with no
weights. A forest cell has 21–27 eligible types, a plain 18–23. Per-type world-wide floors (`minTotal`: 150 ducks,
110 mountain goats, 90 cats…) force restocking everywhere, and births come on top. Regions feel crowded and
interchangeable.

Quests ignore where NPCs live. The single Quest giver (hermit_man) spawns in every 256² grid square, whatever the
Biome or Danger tier, and offers its 25 `zone_tier*` Quests filtered only by Character level. 42 other Quests are
offered by nobody. 7 Quests target `goat`, which is not an NPC type (`mountain_goat` is). `wolf_hunt` (L3) needs
`bear` (L11–15 only). Eel, yeti and sandworm Quests are offered in Regions where these never spawn.

## Decisions

### Unit: the Region

- Population is managed per **Region** (Voronoi cell, one Biome, one Danger level). The square spawn grid
  (`npcZoneSize`) stops driving spawn, caps, activation and Quest giver placement.
- Danger level is constant per Region (already true in code; glossary aligned).

### Roster

- Derived, never persisted: `f(World seed, Region, eligible NPC types)`. Config changes plus `/reload` may
  reshuffle Rosters; accepted.
- Eligible = `autoSpawn`, Biome in `spawnBiomes` (empty = any), Region Danger level in `minLevel..maxLevel`.
- Composition: 2–4 passive animals + 1–3 hostiles. Sea/lake Regions draw only aquatic types; land Regions draw no
  aquatic-only type.
- Rares (`maxTotal` ≤ 5): enter ~20 % of the Regions where they are eligible, at most one per Roster.

### Budget

- `biomes.yaml` `maxNpcs` → `regionBudget`, fixed per Biome, independent of Region area. Defaults: forest 25,
  plains 20, pine_forest 20, dry_plains 15, snow_peaks 15, desert 10, sea 12, lake 6.
- New NPC type field `weight` (default 1): the budget is split among the Roster in proportion to weight. A rare
  is capped at 1–2 individuals whatever its share.
- Births count toward the budget: a full Region stops reproduction.
- `minTotal` removed. `maxTotal` kept only as a world-wide ceiling for rares.

### Activation and parking

- Active = the Region of each Character plus its Voronoi neighbours (`VoronoiBiomeZones.cellsInRadius`).
- NPCs of inactive Regions are parked as today (`pendingRespawns`), keyed by Region.
- `onZoneCrossed` becomes a Region change.
- Parked NPCs whose type is no longer in their Region's Roster are dropped instead of respawned. Pets excluded.
  No purge at boot.

### Quests

- Exactly one Quest giver per Region, only when at least one Quest passes the filter; placed on natural ground
  near the Region's Voronoi seed.
- Giver NPC type: among `behavior: quest_giver` types whose `spawnBiomes` and level range fit the Region
  (spawnBiomes finally honoured); hermit_man as fallback.
- `offersQuests` removed. Pool = every Quest, filtered by: all KILL targets in the Region's Roster, and Quest level
  within the Region's Danger tier; then the existing Character-level, `dependsOn`, status and cooldown rules.
- A kill counts wherever it happens.
- `/quest accept <id>` only accepts a Quest offered by the Quest giver of the Character's current Region.
  Admins bypass.

### Guards and tooling

- Quest load fails on a KILL `npcType` that is not an NPC type. Fix `goat` → `mountain_goat` (7 Quests, and
  `preyTypes` in `wolf.yaml` / `wolf_baby.yaml`).
- Quest load warns on a target no (Biome, Danger level) pair can host at the Quest's tier.
- Coverage test: warn for each (Biome, Danger tier) pair where no Quest can pass the filter.
- `/npc roster [region]`: Region name, Danger level and tier, Roster with weights, live population / budget, Quest
  giver. Same permission as `/npc`, autocompletion on Region names.

## Out of scope

- No hostile NPC type beyond Danger level 25: `.scratch/zone-tier-rank/issues/02-no-hostiles-above-danger-level-25.md`.
- NPC Ability Rank from Danger tier: `.scratch/zone-tier-rank/issues/01-npc-ability-rank-from-zone-tier.md`.
- Merchants and blacksmith placement.

## Risks

- Test worlds use `FlatArenaChunkGenerator` / `EndToEndBoundedChunkGenerator`, which read `maxNpcs`: they need a
  Region and a budget that keep existing E2E specs deterministic.
- `quests-kill.spec.ts` and `quests-fetch.spec.ts` run `/quest accept` without meeting a giver: they must move
  the Character into a Region whose giver offers the Quest (issue 06).
- `WorldSimulator` / `SimulationConfig` read `maxNpcs` and drive the spawner through `canSpawn`.
- Docs to update: `docs/entities/npcs.md` (also stale "200 ticks"), `docs/world/biomes.md`,
  `docs/gameplay/quests.md`, generated reference (`make docs`).

## Issues

| # | Issue | Blocked by |
|---|-------|------------|
| 01 | [Quest target validation at load](issues/01-quest-target-validation.md) | – |
| 02 | [Region Roster derivation and `/npc roster`](issues/02-region-roster-derivation.md) | – |
| 03 | [Spawn from the Roster within the Region budget](issues/03-spawn-from-roster-budget.md) | 02 |
| 04 | [Activation and parking per Region](issues/04-region-activation-parking.md) | 03 |
| 05 | [One Quest giver per Region, offers filtered by Roster](issues/05-quest-giver-per-region.md) | 02 |
| 06 | [`/quest accept` limited to the current Region's giver](issues/06-quest-accept-region-giver.md) | 05 |
| 07 | [Quest coverage check per Biome × Danger tier](issues/07-quest-coverage-check.md) | 01, 05 |
