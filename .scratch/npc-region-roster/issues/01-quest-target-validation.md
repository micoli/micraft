# Quest target validation at load

Status: ready-for-agent
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

- [ ] Server test: a Quest with an unknown `npcType` fails loading; one with an unreachable target logs a warning.
- [ ] All bundled Quests load without error.
- [ ] `docs/gameplay/quests.md` states the rule.
