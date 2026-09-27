# One Quest giver per Region, offers filtered by Roster

Status: ready-for-agent
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

- [ ] Server tests: no giver in a Region without a suitable Quest; one giver otherwise; an eel Quest is never
      offered in a land Region; a Quest outside the tier is not offered.
- [ ] `make check-schemas`, `make docs`, `make dc CMD="./gradlew :server:test"` clean.
