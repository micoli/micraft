package org.micoli.micraft.placeable.siege

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.Vec3

/**
 * Static, YAML-configured stats of a siege weapon type — keyed externally by
 * [org.micoli.micraft.game.world.EntityType] in [SiegeWeaponRegistry]. Also carries the generic
 * placeable render properties ([bbmodelFile]/[width]/[height]) — siege weapons are the only
 * placeable sub-system today, so there's no separate generic placeable-definition layer to compose
 * with (see `org.micoli.micraft.game.placeable.PlaceableManager`, which sources this metadata
 * straight from [SiegeWeaponRegistry]).
 *
 * [projectileType]/[ammoItem] may reference identifiers with no full definition yet — the physical
 * projectile system and ammo items land in a later phase; Phase A only wires
 * spawn/despawn/orientation and pitch/power adjustment, no firing.
 */
@Serializable
data class SiegeWeaponDefinition(
    val bbmodelFile: String = "",
    val width: Float = 0.8f,
    val height: Float = 0.8f,
    val projectileType: String = "",
    val ammoItem: ItemType? = null,
    val muzzleOffset: Vec3 = Vec3(0f, 1f, 0f),
    val launchPower: Float = 10f,
    val launchPowerMin: Float = 0f,
    val launchPowerMax: Float = 100f,
    val launchPitchDeg: Float = 45f,
    val launchPitchDegMin: Float = 0f,
    val launchPitchDegMax: Float = 90f,
    val impactRadius: Float = 3f,
    val impactDamage: Int = 20,
    val cooldownMs: Long = 3000,
    val pitchStepRange: Int = 10,
    val powerStepRange: Int = 10,
)
