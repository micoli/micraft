package org.micoli.micraft.game.armor

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaConstraint

/** One roll in an NPC's armor loot table: a piece it may drop on death, at [dropRate] percent. */
@Serializable
data class ArmorDropEntry(
    val armor: String,
    @JsonSchemaConstraint(minimum = 0.0, maximum = 100.0) val dropRate: Int = 100,
)
