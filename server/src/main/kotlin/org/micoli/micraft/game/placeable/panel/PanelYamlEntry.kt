package org.micoli.micraft.game.placeable.panel

import kotlinx.serialization.Serializable
import org.micoli.micraft.placeable.panel.PanelConstants
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot

/** One `resources/panels/<name>/<name>.yaml` file. */
@Serializable
@JsonSchemaRoot(file = "panel.schema.json")
data class PanelYamlEntry(
    val bbmodelFile: String = "",
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val widthBlocks: Float = 2f,
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val heightBlocks: Float = 1.5f,
    @JsonSchemaConstraint(minimum = 64.0) val pixelWidth: Int = PanelConstants.DEFAULT_PIXEL_WIDTH,
    @JsonSchemaConstraint(minimum = 64.0)
    val pixelHeight: Int = PanelConstants.DEFAULT_PIXEL_HEIGHT,
    val rotatable: Boolean = true,
)

/** `data/resources/panels/<name>/<name>.yaml` override — every field optional. */
@Serializable
data class PanelYamlOverride(
    val bbmodelFile: String? = null,
    val widthBlocks: Float? = null,
    val heightBlocks: Float? = null,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val rotatable: Boolean? = null,
)
