package org.micoli.micraft.game.world

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Identity of a spawnable non-block entity — first (and currently only) use: rail vehicles (see
 * [ItemDefinition.spawnsEntity]). Mirrors [BlockType]/[ItemType]'s shape: the enum-like value is
 * just an identity key, properties are loaded from YAML into a registry
 * (`org.micoli.micraft.vehicle.VehicleRegistry`).
 */
@JvmInline
@Serializable(with = EntityTypeSerializer::class)
value class EntityType(val id: String) {
    override fun toString(): String = id
}
