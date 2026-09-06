package org.micoli.micraft.placeable.furniture

import org.micoli.micraft.game.world.EntityType

/**
 * Plain load/get/keys registry, mirrors [org.micoli.micraft.placeable.siege.SiegeWeaponRegistry].
 */
object FurnitureRegistry {
    private val defs: MutableMap<EntityType, FurnitureDefinition> = mutableMapOf()

    fun load(incoming: Map<EntityType, FurnitureDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun keys(): Set<EntityType> = defs.keys.toSet()

    fun get(type: EntityType): FurnitureDefinition? = defs[type]
}
