package org.micoli.micraft.placeable.panel

import kotlinx.serialization.Serializable

/**
 * Static, YAML-configured properties of a panel type — keyed externally by
 * [org.micoli.micraft.game.world.EntityType] in [PanelRegistry]. A panel is a placeable that shows
 * navigable HTML on a [widthBlocks]×[heightBlocks] surface rendered at [pixelWidth]×[pixelHeight].
 */
@Serializable
data class PanelDefinition(
    val bbmodelFile: String = "",
    val widthBlocks: Float = 2f,
    val heightBlocks: Float = 1.5f,
    val pixelWidth: Int = PanelConstants.DEFAULT_PIXEL_WIDTH,
    val pixelHeight: Int = PanelConstants.DEFAULT_PIXEL_HEIGHT,
    val rotatable: Boolean = true,
)
