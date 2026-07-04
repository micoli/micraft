package org.micoli.micraft.ui

import kotlinx.serialization.Serializable

@Serializable
enum class WidgetType {
    MINIMAP,
    HUD,
    SHORTCUT_BAR,
    CHAT_HISTORY,
    INPUT_BOX,
    INVENTORY,
    CHUNK_DEBUG,
    INGAME_MAP,
}

@Serializable
data class LayoutWidget(
    val type: WidgetType,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

// name is the unique key per player — no two layouts for the same player share a name
@Serializable data class GameLayout(val name: String, val widgets: List<LayoutWidget>)

val DEFAULT_WIDGETS =
    listOf(
        LayoutWidget(WidgetType.MINIMAP, x = 0, y = 0, w = 8, h = 10),
        LayoutWidget(WidgetType.HUD, x = 37, y = 0, w = 11, h = 6),
        LayoutWidget(WidgetType.CHAT_HISTORY, x = 0, y = 36, w = 20, h = 9),
        LayoutWidget(WidgetType.INPUT_BOX, x = 0, y = 45, w = 20, h = 3),
        LayoutWidget(WidgetType.SHORTCUT_BAR, x = 15, y = 45, w = 18, h = 3),
        LayoutWidget(WidgetType.INVENTORY, x = 16, y = 33, w = 16, h = 12),
    )

fun defaultLayout() = GameLayout("default", DEFAULT_WIDGETS)

@Serializable data class LayoutSyncPayload(val layouts: List<GameLayout>, val activeLayout: String)

fun validateLayouts(layouts: List<GameLayout>, activeLayout: String): String? {
    if (layouts.isEmpty()) return "At least one layout is required"
    val names = layouts.map { it.name }
    if (names.size != names.toSet().size) return "Layout names must be unique"
    if (layouts.none { it.name == activeLayout })
        return "Active layout '$activeLayout' not found in layouts"
    return null
}
