package org.micoli.micraft.placeable.panel

import org.micoli.micraft.game.world.EntityType

/**
 * Plain load/get/keys registry, mirrors [org.micoli.micraft.placeable.furniture.FurnitureRegistry].
 */
object PanelRegistry {
    private val defs: MutableMap<EntityType, PanelDefinition> = mutableMapOf()

    fun load(incoming: Map<EntityType, PanelDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun keys(): Set<EntityType> = defs.keys.toSet()

    fun get(type: EntityType): PanelDefinition? = defs[type]
}
