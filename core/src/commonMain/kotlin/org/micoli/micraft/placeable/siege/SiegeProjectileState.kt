package org.micoli.micraft.placeable.siege

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.player.Vec3

/** Network-facing snapshot of a flying siege projectile. */
@Serializable
data class SiegeProjectileState(
    val id: String,
    val projectileType: EntityType,
    val pos: Vec3,
    val velocity: Vec3,
)
