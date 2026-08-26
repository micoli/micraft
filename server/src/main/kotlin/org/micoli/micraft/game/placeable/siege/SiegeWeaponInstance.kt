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

    /**
     * Current nudge direction (+1/-1) for [SiegeWeaponManager.handleNudgePitch] — flips whenever a
     * nudge would push `launchPitchDeg + pitchStep` past [SiegeWeaponDefinition.launchPitchDegMin]/
     * [SiegeWeaponDefinition.launchPitchDegMax], so repeated presses of the single pitch key bounce
     * back and forth between the two bounds instead of sticking once clamped.
     */
    var pitchDirection: Int = 1

    /**
     * Current nudge direction (+1/-1) for [SiegeWeaponManager.handleNudgePower] — same bounce
     * behavior as [pitchDirection], flipping at [SiegeWeaponDefinition.launchPowerMin]/
     * [SiegeWeaponDefinition.launchPowerMax].
     */
    var powerDirection: Int = 1

    fun toState(): SiegeWeaponState =
        SiegeWeaponState(
            id = id,
            placeableId = placeableId,
            pitchStep = pitchStep,
            powerStep = powerStep,
            cooldownUntilMs = cooldownUntilMs,
        )
}
