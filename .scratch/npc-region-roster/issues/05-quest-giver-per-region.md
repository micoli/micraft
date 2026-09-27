# One Quest giver per Region, offers filtered by Roster

Status: resolved
Type: task
Blocked by: 02

## Context

Spec: `../spec.md` (Quests). `QuestGiverSpawner` keeps one giver per 256² grid square and ignores `spawnBiomes`;
hermit_man's `offersQuests` lists 25 Quests; `computeOfferableQuests` (`QuestOfferFilter.kt`) filters on Character
level, `dependsOn`, status and cooldown only.

## What to build

- `QuestGiverSpawner`: exactly one Quest giver per Region, only when at least one Quest passes the filter below;
  placed on natural ground near the Region's Voronoi seed.
- Giver NPC type: a `behavior: quest_giver` type whose `spawnBiomes` and level range fit the Region; hermit_man as
  fallback.
- Remove `offersQuests` (NPC type field, yaml, schema). The giver's pool is every Quest where all KILL targets are
  in the Region's Roster and the Quest level lies in the Region's Danger tier; existing rules apply on top.
- `/npc roster` shows the Region's Quest giver and its offered Quests.
- Update `docs/gameplay/quests.md`.

## Acceptance criteria

- [x] Server tests: no giver in a Region without a suitable Quest; one giver otherwise; an eel Quest is never
      offered in a land Region; a Quest outside the tier is not offered.
- [x] `make check-schemas`, `make docs`, `make dc CMD="./gradlew :server:test"` clean.

## Comments

- 2026-09-27: `RegionQuests.suitedTo` (level in the Region's Danger tier, every kill target in its Roster; FETCH
  Quests only need the level). `QuestGiverSpawner` groups loaded chunks by Region, keeps one giver per Region that has
  a suited Quest (despawns the rest), places new ones on dry natural ground at the loaded chunk closest to the seed,
  and refreshes `NpcInstance.offeredQuests` every lifecycle pass. `offersQuests` is gone; both the dialog and the LLM
  chat offer `offeredQuests`.
- Giver type: a `quest_giver` whose `spawnBiomes` include the Region's Biome, else any `quest_giver` of the right level
  (hermit_man's `spawnBiomes` are [forest, pine_forest, snow_peaks], so it is the fallback elsewhere).
- `NpcSpawner` and `QuestGiverSpawner` share the World's `RegionPopulation` (built in `NpcSubsystemFactory`).
- Known gaps: sea/lake Regions get no giver (no dry ground), so their Quests are never offered — surface it in issue
  07's coverage table. A giver restored from parking has empty offers until the next pass (≤ 5 s) — issue 04.
