# NPC Ability Rank follows NPC Level

Status: ready-for-agent
Type: task
Blocked by: —

## Context

Domain rule (CONTEXT.md, **Rank**): an NPC uses the Rank of its Level's band; the bands match the Danger tiers.
Today an NPC's Rank is static per yaml (`NpcAttackSlot(attackId, rank = 1)`), independent of its Level: `wolf_man`
(L2–4) bites with `wolf_bite` Rank 3.

Decided in the grilling of 2026-09-27 (see `## Comments`).

## Rule

- **Level → Rank**: L1–5 → R1, L6–10 → R2, L11–15 → R3, L16–20 → R4, L21+ → R5 — the same bands as
  `ZoneTier.npcLevelRange` (`core/.../game/world/ZoneTier.kt`), strictly 1:1. No config table.
- **Derived on the fly** from the NPC's current Level, never stored or persisted. A wild NPC's Level is fixed at
  birth, so its Rank is stable; a Pet gains Ranks as it levels up.
- **One rule for every NPC**: wild, predators (`CombatProcessor.handleNpcAttackNpc`), Residents, Quest givers,
  merchants, Pets (`PetCoordinator` → `handleNpcAttackNpc`).
- **Fallback** per Ability: highest Rank defined ≤ the computed Rank. If the Ability only defines Ranks above it,
  the NPC does not use that Ability.

## Scope

- Pure function Level → Rank (core, next to `ZoneTier`), plus the per-Ability fallback resolution. The pending
  `ZoneTier` → `DangerTier` rename (follow-up in `.scratch/npc-region-roster/spec.md`) is not part of this ticket.
- `CombatProcessor` (NPC→Character ~l.284, NPC→NPC ~l.378): resolve the effective Rank from the attacker's Level
  instead of `slot.rank`; the cooldown key uses the effective Rank.
- Remove `rank` from `NpcAttackSlot` and `NpcYamlOverride.attacks`; strip `rank:` from every
  `resources/entities/*/*.yaml` (58 files); `make gen-schemas` in the same commit so `make check-schemas` rejects it.
  Mention in the commit body that data-layer overrides (ADR-0009) still carrying `rank:` will fail to load.
- Rename `ResidentKey.rank` → `ordinal` (`game/npc/resident/Resident.kt`, 4 files) — "Rank" is reserved for the
  Ability Rank.
- Surface the effective Rank per Ability:
  - world simulator (`WorldSimulator.kt:666`, currently prints the yaml rank);
  - admin NPC view (`app/webApp/ts-src/admin/pages/npcs/`) — it shows no attacks today: add them to the NPC admin DTO,
    then `make dc CMD="./gradlew :server:exportOpenApi"` + `make gen-api`.
- Validation test (`ShippedEntityConfigTest`): every NPC type has at least one usable Ability at its `minLevel`
  (NPC types with no `attacks` at all are exempt).

## Out of scope

- Aligning Character Class unlock Levels with the tier bands → `03`.
- Rebalancing NPCs that lose Ranks (e.g. `wolf_man` R3 → R1) → `04`.
- Showing the Rank to players (nameplate, tooltip, Pet sheet) → `05`.

## Acceptance

- Unit test on Level → Rank at every band boundary (5/6, 10/11, 15/16, 20/21, and `RPG_LEVEL_MAX`).
- Unit test on the fallback: lower Rank picked when the computed one is missing; Ability skipped when only higher
  Ranks exist.
- `NpcSpawnerTest`: the same NPC type spawned at two Levels in different bands attacks with two different Ranks.
- A Pet whose Level crosses a band boundary attacks with the higher Rank.
- `ShippedEntityConfigTest` validation passes on the shipped entities.
- `make check-schemas`, `make check-openapi`, `make dc CMD="./gradlew :server:test"` pass.

## Comments

### Grilling 2026-09-27 — decisions

- The yaml `rank:` is removed: the Rank is the Ability's, available according to the NPC's Level (not the Region's
  Danger level, not an offset per NPC type).
- Level → Rank = tier bands, 1:1, in code.
- Computed from the current Level, not frozen at spawn (Pets level up).
- Fallback: highest defined Rank ≤ computed; otherwise skip the Ability; validation test catches NPC types left
  without any usable Ability.
- Same rule for all NPCs, Pets included.
- `ResidentKey.rank` → `ordinal`.
- Rebalancing measured separately (world simulator), player-side display deferred.
- CONTEXT.md updated (Danger tier, Level, Rank). No ADR: 1:1 bands are cheap to reverse and unsurprising.
