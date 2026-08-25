package org.micoli.micraft.placeable.siege

import org.micoli.micraft.game.world.EntityType

/** Plain load/get/keys registry, mirrors [org.micoli.micraft.vehicle.VehicleRegistry]'s shape. */
object SiegeWeaponRegistry {
    private val defs: MutableMap<EntityType, SiegeWeaponDefinition> = mutableMapOf()

    fun load(incoming: Map<EntityType, SiegeWeaponDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun keys(): Set<EntityType> = defs.keys.toSet()

    fun get(type: EntityType): SiegeWeaponDefinition? = defs[type]
}
