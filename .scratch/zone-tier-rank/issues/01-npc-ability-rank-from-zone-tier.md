# NPC Ability Rank follows Danger tier

Status: needs-triage
Type: task

## Context

Domain rule (CONTEXT.md, 2026-09-26): NPCs in a **Danger tier** use Abilities of the matching **Rank**, and a
Character needs Abilities of that Rank to survive there. The code does not enforce this: an NPC's Rank is static
per definition, independent of where it spawns.

## Current state

- `server/.../game/npc/NpcAttackSlot.kt`: `NpcAttackSlot(attackId, rank = 1)`. The Rank is fixed in the entity yaml
  (58 of the 81 `resources/entities/*/*.yaml` files set `rank:`, e.g. `wolf_man.yaml`: `wolf_bite` rank 3, `shadow_claw` rank 2).
- `NpcSpawner.kt:97-104`: filters by `def.minLevel..maxLevel` against `zoneLevelAt`, and derives `instanceLevel = zoneLevel ± 3`.
  **The Rank is never derived from the zone.**
- `CombatProcessor.kt:317-347`: NPC attacks resolve `aDef.ranks[slot.rank]` and use it as the cooldown key.
- `ZoneTier.fromZoneLevel` (`core/.../game/world/ZoneTier.kt`) is only used by `QuestGiverSpawner`.
- Rank distribution across attack definitions: rank 1 ×44, 2 ×38, 3 ×25, 4 ×17, 5 ×15. Not every Ability defines every Rank.

## Scope

- Resolve an NPC instance's effective Rank per Ability from `ZoneTier.fromZoneLevel(instanceLevel)` at spawn, and store it on the NPC instance.
- The yaml `rank:` becomes either removed, or a floor/ceiling (see open questions).
- Fallback when the Ability lacks the tier's Rank: highest defined Rank ≤ tier.
- Surface the effective Rank in the world simulator (`WorldSimulator.kt:666`) and in the admin NPC views.

## Open questions

- Should the yaml `rank:` disappear (pure tier mapping), or stay as an offset/cap per NPC type (a boss one Rank above its tier)?
- Is Rank = tier strictly 1:1, or is `ZoneTier` → Rank a table in config?
- Do the Character-side unlock Levels need to line up with the tier level bands (tier N ↔ Levels 5N-4..5N)?

## Acceptance

- The same NPC type spawned in tier 1 and tier 4 attacks with Rank 1 and Rank 4 Abilities (or the fallback).
- Unit test on the Rank resolution; `NpcSpawnerTest` covers a spawn in two tiers.
- `make dc CMD="./gradlew :server:test"` passes.
