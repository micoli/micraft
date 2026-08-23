package org.micoli.micraft.game.rpg

import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.player.PlayerState

/** Combined stat bonuses from worn armor and wielded weapon/tool. */
fun PlayerState.equipmentBonuses(
    armorRegistry: Map<String, ArmorDefinition>,
    weaponRegistry: Map<String, WeaponDefinition> = emptyMap(),
    toolRegistry: Map<String, ToolDefinition> = emptyMap(),
): List<StatBonus> {
    val armorBonuses = armors.mapNotNull { armorRegistry[it]?.statBonus }
    val handItems = listOfNotNull(rightHandItem, leftHandItem)
    val weaponBonuses = handItems.mapNotNull { weaponRegistry[it]?.statBonus }
    val toolBonuses = handItems.mapNotNull { toolRegistry[it]?.statBonus }
    return armorBonuses + weaponBonuses + toolBonuses
}
