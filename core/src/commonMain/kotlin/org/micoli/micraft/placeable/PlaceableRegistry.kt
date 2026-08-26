package org.micoli.micraft.placeable

import org.micoli.micraft.game.world.EntityType

/**
 * Plain load/get/keys registry over every placeable kind, mirrors
 * [org.micoli.micraft.vehicle.VehicleRegistry]'s shape. Populated by merging each kind-specific
 * registry (e.g. [org.micoli.micraft.placeable.siege.SiegeWeaponRegistry]) after it loads — see
 * `RegistryModule.kt`. Callers that only need "is this entity type placeable, and what model does
 * it use" (spawn gating, client ghost preview, model-cache population) should use this instead of
 * reaching into a specific kind's registry.
 */
object PlaceableRegistry {
    private val defs: MutableMap<EntityType, PlaceableDefinition> = mutableMapOf()

    fun load(incoming: Map<EntityType, PlaceableDefinition>) {
        defs.clear()
        defs.putAll(incoming)
    }

    fun keys(): Set<EntityType> = defs.keys.toSet()

    fun get(type: EntityType): PlaceableDefinition? = defs[type]
}
