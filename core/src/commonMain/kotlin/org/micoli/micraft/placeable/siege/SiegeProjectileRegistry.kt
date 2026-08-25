package org.micoli.micraft.placeable.siege

import org.micoli.micraft.game.world.EntityType

/** Plain load/get/keys registry, mirrors [org.micoli.micraft.vehicle.VehicleRegistry]'s shape. */
object SiegeProjectileRegistry {
    private val defs: MutableMap<EntityType, SiegeProjectileDefinition> = mutableMapOf()

    fun load(incoming: Map<EntityType, SiegeProjectileDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun keys(): Set<EntityType> = defs.keys.toSet()

    fun get(type: EntityType): SiegeProjectileDefinition? = defs[type]
}
