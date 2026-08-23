package org.micoli.micraft.game.combat

import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.equipmentBonuses
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats

fun PlayerSession.computeDerived(
    armorRegistry: Map<String, ArmorDefinition>,
    charData: CharacterData,
    effectNames: Set<String> = emptySet(),
    weaponRegistry: Map<String, WeaponDefinition> = emptyMap(),
    toolRegistry: Map<String, ToolDefinition> = emptyMap(),
): DerivedStats {
    val bonuses = state.equipmentBonuses(armorRegistry, weaponRegistry, toolRegistry)
    return DerivedStatsCalculator.compute(charData, bonuses, effectNames)
}
