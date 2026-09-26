# Single ability gate for attack / spell / AoE

Status: needs-triage
Strength: Strong

## Files
- `game/combat/CombatProcessor.kt` (`handleAttack` L98-151, `deductResource` L561)
- `game/combat/SpellProcessor.kt` (`handleSpell` L34-130, `handleCastAoeSpell` L193-330, `cooldowns` L32)

## Problem
Three near-identical copies of the same checks: character present → definition exists → unlock at this level → rank exists → GCD → cooldown → mana/rage cost.
- `SpellProcessor` deducts cost inline instead of reusing `deductResource`.
- Spell cooldowns live in a private `mutableMapOf` keyed `"sessionId:spell:rank"`. It is not thread-safe and never cleared, `makeStatusUpdate` can't see it, and it doesn't survive a reconnect.
- The GCD messages differ ("Attack on cooldown" vs "On global cooldown").

## Solution
`AbilityGate.tryUse(session, AbilityRef): Result` owns unlock, rank, GCD, a single cooldown store in `combatState`, and cost. The processors keep only the effect.

## Tests
CombatProcessorTest plus 3 SpellProcessor test files cover the paths separately; parity between attack and spell is not tested today.
