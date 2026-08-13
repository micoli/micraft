package org.micoli.micraft.game.world.block

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.schema.JsonSchemaConstraint

@Serializable
data class DropEntry(
    val item: ItemType,
    @JsonSchemaConstraint(minimum = 0.0, maximum = 100.0) val dropRate: Int = 100,
    @JsonSchemaConstraint(minimum = 0.0) val minCount: Int = 1,
    @JsonSchemaConstraint(minimum = 0.0) val maxCount: Int = 1,
)
