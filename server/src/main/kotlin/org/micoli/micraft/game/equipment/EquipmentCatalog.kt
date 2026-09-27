package org.micoli.micraft.game.equipment

import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.world.EquipmentCategory

/** The armor, weapon and tool definitions a Loadout draws from, refreshed by `/reload`. */
class EquipmentCatalog(
    armors: Map<String, ArmorDefinition> = emptyMap(),
    weapons: Map<String, WeaponDefinition> = emptyMap(),
    tools: Map<String, ToolDefinition> = emptyMap(),
    weaponCategories: Map<EquipmentCategory, WeaponCategoryDefinition> = emptyMap(),
    toolCategories: Map<EquipmentCategory, ToolCategoryDefinition> = emptyMap(),
) {
    @Volatile
    var armors: Map<String, ArmorDefinition> = armors
        private set

    @Volatile
    var weapons: Map<String, WeaponDefinition> = weapons
        private set

    @Volatile
    var tools: Map<String, ToolDefinition> = tools
        private set

    @Volatile
    var weaponCategories: Map<EquipmentCategory, WeaponCategoryDefinition> = weaponCategories
        private set

    @Volatile
    var toolCategories: Map<EquipmentCategory, ToolCategoryDefinition> = toolCategories
        private set

    fun reload(
        armors: Map<String, ArmorDefinition> = this.armors,
        weapons: Map<String, WeaponDefinition> = this.weapons,
        tools: Map<String, ToolDefinition> = this.tools,
    ) {
        this.armors = armors
        this.weapons = weapons
        this.tools = tools
    }

    fun reloadCategories(
        weaponCategories: Map<EquipmentCategory, WeaponCategoryDefinition>,
        toolCategories: Map<EquipmentCategory, ToolCategoryDefinition>,
    ) {
        this.weaponCategories = weaponCategories
        this.toolCategories = toolCategories
    }

    fun names(): Set<String> = armors.keys + weapons.keys + tools.keys

    fun resolve(name: String): String? = names().firstOrNull { it.equals(name, ignoreCase = true) }
}
