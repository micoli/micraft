package org.micoli.micraft.game.placeable.siege

import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.placeable.siege.SiegeWeaponState

/**
 * Server-side runtime state of a spawned siege weapon — composes with (references) a
 * [org.micoli.micraft.game.placeable.PlaceableInstance] via [placeableId] for position/orientation
 * rather than duplicating it. Own fields are strictly siege-specific. [type] is a denormalized copy
 * of the linked placeable's type — kept only to look up [SiegeWeaponRegistry] stats without a
 * back-reference to [org.micoli.micraft.game.placeable.PlaceableManager].
 */
class SiegeWeaponInstance(val id: String, val placeableId: String, val type: EntityType) {
    var pitchStep: Int = 0
    var powerStep: Int = 0
    var cooldownUntilMs: Long = 0

    fun toState(): SiegeWeaponState =
        SiegeWeaponState(
            id = id,
            placeableId = placeableId,
            pitchStep = pitchStep,
            powerStep = powerStep,
            cooldownUntilMs = cooldownUntilMs,
        )
}
