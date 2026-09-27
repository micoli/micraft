# Loadout: ownership grants and equip rules behind one module

Status: resolved
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

## Decisions (2026-09-27)

1. **Scope**: grants (Give, admin give, armor loot, quest armor rewards), equip/unequip, and wield/unwield.
   `/wield` and `/unwield` never resynced although weapons and tools carry a `statBonus`.
2. **`Loadout` module** (`game/equipment`): pure `grant(state, name) → GrantResult` (Granted / AlreadyOwned / Unknown)
   and `grantAll` for quests, usable offline; session `equip` / `unequip` / `wield` / `unwield` return typed results
   and own the state change, `PlayerUpdate` broadcast, save and `CharacterStats.resync`. Commands only map results to
   i18n messages; the module knows no i18n.
3. **`EquipmentCatalog`**: armor, weapon and tool definitions plus their categories, with `reload()`, shared by
   `CharacterStats` and `Loadout`. One place to reload.
4. **Tests**: `LoadoutTest` on pure grants and on session ops (including the wield CON-bonus resync regression);
   existing `EquipCommandTest`, `GiveCommandTest`, `ArmorLootGranterTest`, `AdminPlayerEquipmentRoutesTest` and quest
   tests stay green without changing their assertions.

## Answer

Done in `8e84d8de`. `Loadout` and `EquipmentCatalog` live in `game/equipment`. Loot and quest rewards use the
armor-only `grantArmor` / `grantArmors` (exact names), while Give and admin give resolve any kind case-insensitively.
`EquipmentCatalog` is on `SharedGameServices`, so Test worlds share the catalog `/reload` refreshes. Suite: 2198 tests
green.

Left open:
- `CommandContext` still exposes the `armorRegistry` / `weaponRegistry` / `toolRegistry` / category closures beside
  `equipmentCatalog` (used by completions and other commands).
- `QuestManager` and `Loadout` default to an empty catalog when not wired; production passes the shared one.
- `/wield <item> <bad-hand>` now answers with the usage message before checking the item.

