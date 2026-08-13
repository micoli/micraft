package org.micoli.micraft.game.world.block

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "blocks.schema.json")
data class BlockYamlEntry(
    val hardness: Float = 1f,
    val solid: Boolean = true,
    val transparent: Boolean = false,
    @JsonSchemaConstraint(minItems = 3, maxItems = 3, itemMinimum = 0.0, itemMaximum = 255.0)
    val minimapColor: List<Int> = listOf(128, 128, 128),
    val modelElement: String = "",
    val gltfModel: String = "",
    val liquid: Boolean = false,
    @JsonSchemaConstraint(minimum = 0.0) val viscosity: Int = 0,
    val replaceable: Boolean = false,
    val vegetationHost: Boolean = false,
    val treeAllowed: Boolean = true,
    val minimapVisible: Boolean = true,
    val drops: List<DropEntry> = emptyList(),
    val rotatable: Boolean = false,
    val hasStuds: Boolean = false,
    @JsonSchemaConstraint(minItems = 2, maxItems = 3, itemExclusiveMinimum = 0.0)
    val brickSize: List<Float> = listOf(1f, 1f, 1f),
    @JsonSchemaConstraint(minimum = 0.0, maximum = 1.0) val heightFraction: Float = 1.0f,
    val plainColorable: Boolean = false,
    val isCubic: Boolean = true,
)
