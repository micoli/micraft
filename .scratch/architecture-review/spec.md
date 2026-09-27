# Architecture review — deepening opportunities (2026-09-26)

Source: `/improve-codebase-architecture` on branch `framework-improve`. Visual report: `report.html`.
Vocabulary: module, interface, depth, seam, adapter, leverage, locality (`/codebase-design`).

Evidence was gathered by reading files. Three findings were verified by hand: the client's
hardcoded gravity constants, `ExperienceProcessor.kt:73` computing stats without equipment
bonuses, and the `SpellProcessor` cooldown map. Line numbers and call counts may have drifted since.

| # | Candidate | Strength | Issue |
|---|-----------|----------|-------|
| 1 | Single player-kinematics module in `core` | Strong | [01](issues/01-player-kinematics-core.md) |
| 2 | CharacterStats: one source for Effective and Derived stats (resolved) | Strong | [02](issues/02-character-stats.md) |
| 3 | Single ability gate for attack / spell / AoE | Strong | [03](issues/03-ability-gate.md) |
| 4 | GameWorld builds its own subsystems | Worth exploring | [04](issues/04-gameworld-self-assembly.md) |
| 5 | Admin routes: auth + world + player edit behind one module | Worth exploring | [05](issues/05-admin-route-module.md) |
| 6 | Single Authorizer; CommandContext typed per need | Worth exploring | [06](issues/06-authorizer.md) |
| 7 | NpcInteraction with always-present dependencies | Worth exploring | [07](issues/07-npc-interaction.md) |
| 8 | Remove stringified client input-event layer | Speculative | [08](issues/08-typed-client-messages.md) |
| 9 | Loadout: ownership grants and equip rules (split from 02, resolved) | Worth exploring | [09](issues/09-loadout-grants.md) |

Top recommendation: #1 (riskiest seam, active drift, zero client tests), then #2 (fixes a real level-up bug).
