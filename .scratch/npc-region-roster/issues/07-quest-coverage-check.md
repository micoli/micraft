# Quest coverage check per Biome × Danger tier

Status: resolved
Type: task
Blocked by: 01, 05

## Context

Spec: `../spec.md` (Guards and tooling). With the Roster filter, some (Biome, Danger tier) pairs may have Regions
whose Quest giver offers nothing, so no giver appears there.

## What to build

- A check, run at Quest load and in a server test, that reports each (Biome, Danger tier) pair where no Quest can
  pass the filter for any possible Roster. Warning only, never a load failure.
- Surface the result where Quest writers see it (load log and the test's output), naming the uncovered pairs and
  the NPC types available there.

## Acceptance criteria

- [x] Server test prints the coverage table and fails only on unexpected exceptions.
- [x] The current gaps are listed in this issue's comments as a backlog of Quests to write.

## Comments

- 2026-09-27: `QuestCoverage.gaps` (`game/npc/roster`): for each Biome × Danger tier, the NPC types eligible at any
  level of the tier, and whether some Quest of that tier only targets them. Liquid Biomes are always a gap (no dry
  ground for a Quest giver). `QuestRegistryLoader` logs one warning with the gaps when it knows the Biomes (wired from
  the Koin `BiomeRegistry`); `ShippedQuestConfigTest.printsTheQuestCoverageOfEveryBiomeAndDangerTier` prints the table.
- Current gaps (10 of 40), all from missing Quest givers on water — every land pair has a possible Quest:
  - sea T1–T5 (dolphin, shark, squid; T4 adds eel, jellyfish, kraken_spawn, octopus; T5 kraken_spawn)
  - lake T1–T5 (dolphin, squid; T4 adds eel, jellyfish, kraken_spawn, octopus; T5 kraken_spawn)
  Backlog: a giver able to stand on water (boat, pier, shore) or shore-side placement for aquatic Regions, before
  writing sea/lake Quests. Coverage is "some Roster could serve it": a given Region's random Roster may still miss.
- Review follow-up: a pair is covered only if one Roster drawn at a single Danger level of the tier could hold every
  target of some Quest (≤ 4 passive, ≤ 3 hostile, ≤ 1 rare). The load warning names the NPC types. Gaps unchanged:
  sea and lake, T1–T5.
