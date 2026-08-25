package org.micoli.micraft.placeable.siege

import kotlinx.serialization.Serializable

/**
 * Static, YAML-configured render/physics constants of a siege projectile type — keyed externally by
 * [org.micoli.micraft.game.world.EntityType] in [SiegeProjectileRegistry]. Everything shot-specific
 * (impact damage/radius, launch power) lives on [SiegeWeaponDefinition] instead — a projectile
 * "type" (e.g. BOULDER) is shared across weapons that may fire it with different stats.
 */
@Serializable
data class SiegeProjectileDefinition(
    val bbmodelFile: String = "",
    /**
     * Collision sphere radius, in blocks — used for in-flight terrain/entity hit detection only.
     */
    val radius: Float = 0.3f,
)
