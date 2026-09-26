# Loadout module for character resync

Status: needs-triage
Strength: Strong

## Files
- `game/rpg/DerivedStatsCalculator.kt`: two `compute` overloads with a duplicated body; 21 callers
- `ServerMessage.CharacterSync(` is built in 7 places: `EquipCommand`, `UnequipCommand`, `GameLoop:2118`, `ExperienceProcessor:73-74`, `CharacterCommand`, `CreateCharacterCommand`, `AdminController`
- Ownership grants (`ownedArmors + x` / `ownedWeapons + x`): `GiveCommand:93-95`, `AdminController:1833-1845`, `ArmorLootGranter:38`, `QuestManager:252`

## Problem
A pure function was extracted for testability, and the bugs hide in its callers:
- **Likely bug**: on level-up, `ExperienceProcessor` sends `CharacterSync` from `compute(updated)` without equipment bonuses, using `baseStats` instead of `effectiveBaseStats`.
- No caller passes `activeEffects`, so the HpBoost/ManaBoost branches are dead in practice.

## Solution
A Loadout module (`resync(session)`, `grant(state, item)`) that owns bonuses, effects, derived stats, the client push and equipment grants.

## Tests
Stats and sync are tested through one interface; the level-up regression is covered.
