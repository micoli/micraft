package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

/**
 * Weapon and tool categories. Shared by weapon/tool definitions and
 * [BlockDefinition.requiredEquipment].
 */
@Serializable
enum class EquipmentCategory {
    SWORD,
    LONGSWORD,
    DAGGER,
    LONG_DAGGER,
    DUAL,
    STAFF,
    AXE,
    DOUBLE_AXE,
    CLUB,
    BOW,
    CROSSBOW,
    HAMMER,
}
