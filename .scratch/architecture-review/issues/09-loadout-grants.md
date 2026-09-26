# Loadout: ownership grants and equip rules behind one module

Status: needs-triage
Strength: Worth exploring

Split out of [02](02-character-stats.md) on 2026-09-26. Depends on 02 (`CharacterStats.resync`).

## Files
- Ownership grants (`ownedArmors + x` / `ownedWeapons + x`): `GiveCommand:93-95`, `AdminController:1833-1845`,
  `ArmorLootGranter:38`, `QuestManager:252`
- Equip/unequip: `EquipCommand`, `UnequipCommand` (slot overlap, ownership checks, broadcast, save)

## Problem
Each grant site edits the owned-item sets by hand, and each equip path repeats checks, `PlayerUpdate` broadcast and save.
A new item kind (weapon, tool) or a new grant source means touching every site.

## Solution
A Loadout module (`grant(state, item)`, `equip(session, item)`, `unequip(session, item)`) that owns ownership rules, slot
conflicts, broadcast and persistence, and calls `CharacterStats.resync` after an equip change.

## Tests
Grant and equip paths tested through the module; today each command is tested separately.
