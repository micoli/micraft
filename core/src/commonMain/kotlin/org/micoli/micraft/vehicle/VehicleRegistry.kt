package org.micoli.micraft.vehicle

import org.micoli.micraft.game.world.EntityType

/** Mirrors [org.micoli.micraft.game.world.ItemRegistry]'s shape: a plain load/get/keys registry. */
object VehicleRegistry {
    private val defs: MutableMap<EntityType, VehicleDefinition> = mutableMapOf()

    fun load(incoming: Map<EntityType, VehicleDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun keys(): Set<EntityType> = defs.keys.toSet()

    fun get(type: EntityType): VehicleDefinition? = defs[type]
}
