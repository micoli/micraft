# CharacterStats: one source for Effective and Derived stats

Status: resolved
Strength: Strong

Scope settled by `/grill-with-docs` on 2026-09-26. Ownership grants and equip/unequip were split out to
[09](09-loadout-grants.md). Vocabulary: **Effective stats**, **Derived stats**, **Loadout** (`CONTEXT.md`).

## Problem

`DerivedStatsCalculator` is pure, but each caller assembles its own inputs, and many of them get it wrong:
- **Level-up bug**: `ExperienceProcessor.kt:73-74` computes without equipment bonuses and sends raw `baseStats` in `CharacterSync`.
- Equipment bonuses are dropped (`compute(charData, emptyList())`) at `GameLoop.kt:412` (StatusUpdate), `GameLoop.kt:1585`
  and `DrinkCommand.kt:62` (drink clamps to a `maxHp` without armor), `GameLoopModule.kt:467`, `GameWorldFactory.kt:277`.
- Active effects are passed only by `GameLoop.kt:871`, `RegenProcessor.kt:52` and `CombatExtensions.computeDerived`, so
  `CharacterSync` and the post-spell StatusUpdate show a different `maxHp` from the one regen caps at.
- Effects are matched by string (`effect::class.simpleName == "HpBoost"`) in 5 files.
- `UnequipCommand` doesn't clamp `currentHp`/`currentMana` when the max drops; effect expiry
  (`StatusEffectProcessor.tick`) never resyncs.
- The formula is duplicated in `DerivedStatsCalculator`; the `(baseStats, level)` overload (NPCs only) ignores `acBonus`.
- `CharacterSync` is built by hand in 7 places; `CharacterSync` and `PlayerStatusUpdate` both carry `maxHp`/`maxMana`
  but callers often send only one.

## Decisions

1. **One formula**: `DerivedStatsCalculator` keeps a single computation `(baseStats, level, acBonus, effects)`. The
   Character path and `NpcInstance.kt:96` both go through it.
2. **Typed effects**: effects are read as `StatusEffect` types (`is StatusEffect.HpBoost`), never by class-name strings.
   `CharacterStats` owns which effects affect stats (HpBoost, ManaBoost, HpRegenBoost, ManaRegenBoost).
3. **`CharacterStats` module** (server), built once with the armor, weapon and tool registries, shared through the
   shared game services and exposed on `CommandContext`:
   - **Pure function**: `(CharacterData, PlayerState, effects) → Effective stats + Derived stats`.
   - **`resync(session)`**: computes with the session's equipment and active effects, clamps `currentHp`/`currentMana`
     to the new max (and saves the Character if clamped), then sends **both** `CharacterSync` (always with Effective
     stats) and `PlayerStatusUpdate`.
4. **Resync triggers**: equip, unequip, level-up, application **and expiry** of a stat-affecting effect, `/set`, admin
   edit of a connected Character, connect, resurrect.
5. **Current-value only changes** (drink, regen, rest) read the max through `CharacterStats` and send only a StatusUpdate.
6. **Offline admin edits** (no session) use the pure function with no effects (effects aren't persisted).
7. No caller outside `CharacterStats` calls `DerivedStatsCalculator` for a Character or builds `CharacterSync`.

Out of scope: typing `CommandContext` per command (issue 06), ownership grants and equip rules (issue 09).

## Tests

- `CharacterStatsTest` (pure function): armor bonuses into Effective stats, `acBonus` into `armorClass`, each of the 4
  stat-affecting effects.
- Session regressions, one per bug:
  - level-up while wearing CON armor → `CharacterSync.derived.maxHp` includes the bonus, `effectiveBaseStats` too;
  - unequip CON armor at full HP → `currentHp` clamped to the new `maxHp`;
  - HpBoost expiry → resync sent, `currentHp` clamped;
  - drink while wearing CON armor → clamp uses the armored `maxHp`.
- NPC `maxMana` unchanged after the formula merge.
- No E2E.

## Files

- `game/rpg/DerivedStatsCalculator.kt`, `game/combat/CombatExtensions.kt`
- Callers of `DerivedStatsCalculator.compute` / `effectiveBaseStats` / `CharacterSync(`: `GameLoop`, `GameLoopModule`,
  `GameWorldFactory`, `ExperienceProcessor`, `SpellProcessor`, `RegenProcessor`, `StatusEffectProcessor`,
  `CombatProcessor`, `RpgCharacterBuilder`, `CreateCharacterCommand`, `CharacterCommand`, `EquipCommand`,
  `UnequipCommand`, `SetCommand`, `ResurectCommand`, `RestCommand`, `DrinkCommand`, `AdminController`, `NpcInstance`

## Answer

Done in `fc609ee6`. `CharacterStats` (`game/rpg/CharacterStats.kt`) owns the Loadout registries and `maxRage`, and is
reloaded by `/reload`. `resync` and `sendStatus` replace `CombatProcessor.makeStatusUpdate`, `CombatExtensions.computeDerived`
and `CommandContext.sendStatusUpdate`. Suite: 2188 tests green.

Left open:
- One instance per World (default world via Koin, one per `buildGameWorld`), not one in `SharedGameServices`: the clamp
  save goes through each World's `PlayerPersister`. `/reload` only refreshes the default World's registries (as before).
- The admin Character edit (class / base stats) still only writes the player file; it neither resyncs a connected
  Character nor clamps HP/mana offline.
- `RegenProcessor` still exposes effect class names to its JEXL formulas (`activeEffects`), by design of the formula API.

