package org.micoli.micraft.ui

import kotlinx.serialization.Serializable

@Serializable
data class WidgetRegistryEntry(
    val type: WidgetType,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val minW: Int,
    val minH: Int,
    val editorLabel: String,
    val editorColor: String,
    val flow: Boolean = false,
)
