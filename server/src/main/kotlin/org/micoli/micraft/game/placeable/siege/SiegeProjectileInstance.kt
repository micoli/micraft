package org.micoli.micraft.game.placeable.siege

import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.placeable.siege.SiegeProjectileState
import org.micoli.micraft.player.Vec3

/**
 * Server-side runtime state of a flying siege projectile — transient, never persisted (see
 * [SiegeProjectileManager]: no load/save pair, unlike
 * [org.micoli.micraft.game.vehicle.VehicleInstance] or
 * [org.micoli.micraft.game.placeable.PlaceableInstance]). [impactRadius]/[impactDamage]/
 * [damageType] are copied from the firing
 * [org.micoli.micraft.placeable.siege.SiegeWeaponDefinition] at spawn time — per-shot values, not
 * shared projectile-type stats (those live in
 * [org.micoli.micraft.placeable.siege.SiegeProjectileDefinition] instead).
 */
class SiegeProjectileInstance(
    val id: String,
    val type: EntityType,
    var pos: Vec3,
    var velocity: Vec3,
    val ownerId: String,
    val impactRadius: Float,
    val impactDamage: Int,
    val damageType: DamageType = DamageType.PHYSICAL,
) {
    fun toState(): SiegeProjectileState =
        SiegeProjectileState(id = id, projectileType = type, pos = pos, velocity = velocity)
}
