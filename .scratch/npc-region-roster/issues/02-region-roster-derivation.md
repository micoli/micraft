# Region Roster derivation and `/npc roster`

Status: resolved
Type: task

## Context

Spec: `../spec.md` (Roster, Budget, Guards and tooling). ADR-0010. The Roster is derived, never persisted.
Regions come from `VoronoiBiomeZones` (cell, name, Biome, `cellLevel`).

## What to build

- A Roster module: given the World seed, a Region (Voronoi cell, Biome, Danger level) and the NPC type registry,
  return the Region's NPC types with their budget share.
  - Eligible: `autoSpawn`, Biome in `spawnBiomes` (empty = any), Danger level in `minLevel..maxLevel`.
  - 2–4 passive + 1–3 hostile types; sea/lake Regions only aquatic types, land Regions no aquatic-only type.
  - Rares (`maxTotal` ≤ 5): included in ~20 % of eligible Regions, at most one per Roster, share capped at 1–2.
  - Deterministic for a given seed, Region and registry.
- New NPC type field `weight` (default 1); `biomes.yaml` `regionBudget` (replaces `maxNpcs`, new defaults in the
  spec). Regenerate schemas (`make gen-schemas`) and reference docs (`make docs`).
- `/npc roster [region]`: Region name, Danger level and tier, Roster with weights and share of `regionBudget`, and
  (once issue 03/05 land) live population and Quest giver. Autocompletion on Region names; same permission as `/npc`;
  i18n keys via `/add-i18n`.

Spawn behaviour is unchanged in this issue.

## Acceptance criteria

- [x] Unit tests: determinism, composition bounds, aquatic rule, rare frequency and cap, weight split.
- [x] `/npc roster` works in game and is covered by a server test.
- [x] `make check-schemas` and `make docs` clean.

## Comments

- 2026-09-27: `RosterBuilder` (`game/npc/roster`), `Region` (`game/world`, replaces `VoronoiBiomeZones.VoronoiCell`),
  exposed through `ChunkGenerator`/`WorldState` (`worldSeed`, `regionAt`, `regionsNear`). `/npc roster [region]`.
- `regionBudget` is added next to `maxNpcs`, not replacing it: the spawner still reads `maxNpcs` until issue 03, which
  removes it.
- Each rare rolls its own 20 % chance; one survivor at most. A rare's share is its weight share clamped to 1–2.
- Small pools stay short: a lake below level 16 has no aquatic hostile, so its Roster has none (counts are upper
  bounds). Worth a look in issue 07's coverage table.
- Autocomplete offers Region names at argument 1 for every `/npc` subcommand: `completeArg` does not receive
  argument 0.
- Follow-ups from review: rename `ZoneTier` → `DangerTier` (CONTEXT.md avoids "zone").
