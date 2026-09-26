package org.micoli.micraft.game.placeable.panel

import kotlinx.serialization.Serializable

/** Persisted per-panel content, linked to its placeable by [placeableId]. */
@Serializable
data class PanelContent(
    val placeableId: String,
    val owner: String,
    val externalUrl: String = "",
    val pages: Map<String, String> = emptyMap(),
)
