# Quest target validation at load

Status: resolved
Type: task

## Context

Spec: `../spec.md` (Guards and tooling). `QuestRegistryLoader` never checks KILL `npcType` against the NPC registry:
7 Quests target `goat`, which does not exist (`mountain_goat` does), and `wolf_hunt` (L3) targets `bear`, which only
spawns at L11–15.

## What to build

- Quest load fails with a clear message when a KILL objective's `npcType` is not a known NPC type.
- Quest load warns when no (Biome, Danger level) pair within the Quest's Danger tier can host the target
  (`autoSpawn`, `spawnBiomes`, `minLevel..maxLevel`).
- Rename `goat` → `mountain_goat` in `first_steps`, `goat_patrol`, `goat_stampede`, `goat_horde`, `great_cull`,
  `mass_cull`, `village_hunt`, and in `preyTypes` of `wolf.yaml` / `wolf_baby.yaml`.
- Fix or retune the Quests the new warning reports (e.g. `wolf_hunt`), or list them in the issue comments.

## Acceptance criteria

- [x] Server test: a Quest with an unknown `npcType` fails loading; one with an unreachable target logs a warning.
- [x] All bundled Quests load without error.
- [x] `docs/gameplay/quests.md` states the rule.

## Comments

- 2026-09-27: `QuestTargetValidator` (errors: unknown type; warnings: never spawns within the Quest's Danger tier).
  `QuestRegistryLoader` takes the NPC types and fails on errors. Biomes are not checked: an NPC type's biome list
  only narrows where it spawns, never makes a tier unreachable. Only warning on shipped data was `wolf_hunt`
  (`bear`, L11–15): retargeted to `wolf_man` (L2–4). Covered by `ShippedQuestConfigTest`.
