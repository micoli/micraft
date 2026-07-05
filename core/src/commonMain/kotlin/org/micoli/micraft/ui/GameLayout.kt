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
    ATTACK_PANEL,
    PLAYER_STATUS,
    COMBAT_TARGET,
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
)

val WIDGET_REGISTRY: List<WidgetRegistryEntry> =
    listOf(
        WidgetRegistryEntry(
            WidgetType.MINIMAP,
            x = 0,
            y = 0,
            w = 8,
            h = 10,
            minW = 4,
            minH = 4,
            editorLabel = "Minimap",
            editorColor = "rgba(60,120,200,0.75)"),
        WidgetRegistryEntry(
            WidgetType.HUD,
            x = 37,
            y = 0,
            w = 11,
            h = 6,
            minW = 6,
            minH = 3,
            editorLabel = "HUD",
            editorColor = "rgba(200,120,40,0.75)"),
        WidgetRegistryEntry(
            WidgetType.CHAT_HISTORY,
            x = 0,
            y = 36,
            w = 20,
            h = 9,
            minW = 8,
            minH = 3,
            editorLabel = "Chat History",
            editorColor = "rgba(140,60,200,0.75)"),
        WidgetRegistryEntry(
            WidgetType.INPUT_BOX,
            x = 0,
            y = 45,
            w = 20,
            h = 3,
            minW = 8,
            minH = 2,
            editorLabel = "Input Box",
            editorColor = "rgba(200,60,100,0.75)"),
        WidgetRegistryEntry(
            WidgetType.SHORTCUT_BAR,
            x = 15,
            y = 45,
            w = 18,
            h = 3,
            minW = 8,
            minH = 2,
            editorLabel = "Shortcut Bar",
            editorColor = "rgba(60,160,80,0.75)"),
        WidgetRegistryEntry(
            WidgetType.ATTACK_PANEL,
            x = 15,
            y = 40,
            w = 18,
            h = 5,
            minW = 6,
            minH = 4,
            editorLabel = "Attack Panel",
            editorColor = "rgba(180,60,60,0.75)"),
        WidgetRegistryEntry(
            WidgetType.INVENTORY,
            x = 16,
            y = 33,
            w = 16,
            h = 12,
            minW = 6,
            minH = 4,
            editorLabel = "Inventory",
            editorColor = "rgba(180,160,40,0.75)"),
        WidgetRegistryEntry(
            WidgetType.CHUNK_DEBUG,
            x = 40,
            y = 8,
            w = 8,
            h = 10,
            minW = 5,
            minH = 6,
            editorLabel = "Chunk Debug",
            editorColor = "rgba(40,180,180,0.75)"),
        WidgetRegistryEntry(
            WidgetType.INGAME_MAP,
            x = 17,
            y = 5,
            w = 19,
            h = 19,
            minW = 5,
            minH = 6,
            editorLabel = "Ingame Map",
            editorColor = "rgba(80,160,60,0.75)"),
        WidgetRegistryEntry(
            WidgetType.PLAYER_STATUS,
            x = 16,
            y = 43,
            w = 16,
            h = 5,
            minW = 8,
            minH = 3,
            editorLabel = "Player Status",
            editorColor = "rgba(192,57,43,0.75)"),
        WidgetRegistryEntry(
            WidgetType.COMBAT_TARGET,
            x = 17,
            y = 2,
            w = 14,
            h = 6,
            minW = 8,
            minH = 3,
            editorLabel = "Combat Target",
            editorColor = "rgba(230,126,34,0.75)"),
    )

val DEFAULT_WIDGETS: List<LayoutWidget> =
    WIDGET_REGISTRY.map { LayoutWidget(it.type, it.x, it.y, it.w, it.h) }

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
