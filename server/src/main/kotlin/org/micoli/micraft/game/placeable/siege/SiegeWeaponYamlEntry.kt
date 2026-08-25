package org.micoli.micraft.game.placeable.siege

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot

/**
 * One `resources/siege/weapons/<name>/<name>.yaml` file — mirrors
 * [org.micoli.micraft.game.world.block.BlockYamlEntry]'s per-directory shape. Flattens the generic
 * placeable render properties ([bbmodelFile]/[width]/[height]) together with the siege-specific
 * stats — there's no separate generic placeable yaml layer any more (see
 * [org.micoli.micraft.placeable.siege.SiegeWeaponDefinition]).
 */
@Serializable
@JsonSchemaRoot(file = "siege_weapons.schema.json")
data class SiegeWeaponYamlEntry(
    val bbmodelFile: String = "",
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val width: Float = 0.8f,
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val height: Float = 0.8f,
    val projectileType: String = "",
    val ammoItem: String = "",
    val muzzleOffset: Vec3 = Vec3(0f, 1f, 0f),
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val launchPower: Float = 10f,
    val launchPitchDeg: Float = 45f,
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val impactRadius: Float = 3f,
    @JsonSchemaConstraint(minimum = 0.0) val impactDamage: Int = 20,
    @JsonSchemaConstraint(minimum = 0.0) val cooldownMs: Long = 3000,
    @JsonSchemaConstraint(minimum = 1.0) val pitchStepRange: Int = 10,
    @JsonSchemaConstraint(minimum = 1.0) val powerStepRange: Int = 10,
)

/** `data/resources/siege/weapons/<name>/<name>.yaml` override — every field optional. */
@Serializable
data class SiegeWeaponYamlOverride(
    val bbmodelFile: String? = null,
    val width: Float? = null,
    val height: Float? = null,
    val projectileType: String? = null,
    val ammoItem: String? = null,
    val muzzleOffset: Vec3? = null,
    val launchPower: Float? = null,
    val launchPitchDeg: Float? = null,
    val impactRadius: Float? = null,
    val impactDamage: Int? = null,
    val cooldownMs: Long? = null,
    val pitchStepRange: Int? = null,
    val powerStepRange: Int? = null,
)
