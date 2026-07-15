package org.micoli.micraft.ui

import kotlinx.serialization.Serializable

// name is the unique key per player — no two layouts for the same player share a name
@Serializable data class GameLayout(val name: String, val widgets: List<LayoutWidget>)

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
            x = 20,
            y = 43,
            w = 18,
            h = 3,
            minW = 8,
            minH = 2,
            editorLabel = "Shortcut Bar",
            editorColor = "rgba(60,160,80,0.75)"),
        WidgetRegistryEntry(
            WidgetType.ATTACK_PANEL,
            x = 40,
            y = 33,
            w = 8,
            h = 15,
            minW = 6,
            minH = 4,
            editorLabel = "Attack Panel",
            editorColor = "rgba(180,60,60,0.75)"),
        WidgetRegistryEntry(
            WidgetType.INVENTORY,
            x = 7,
            y = 21,
            w = 33,
            h = 14,
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
            x = 9,
            y = 8,
            w = 29,
            h = 20,
            minW = 5,
            minH = 6,
            editorLabel = "Ingame Map",
            editorColor = "rgba(80,160,60,0.75)"),
        WidgetRegistryEntry(
            WidgetType.PLAYER_STATUS,
            x = 8,
            y = 0,
            w = 15,
            h = 6,
            minW = 8,
            minH = 3,
            editorLabel = "Player Status",
            editorColor = "rgba(192,57,43,0.75)"),
        WidgetRegistryEntry(
            WidgetType.AGGRO_INDICATORS,
            x = 0,
            y = 11,
            w = 7,
            h = 4,
            minW = 7,
            minH = 4,
            editorLabel = "Aggro Indicators",
            editorColor = "rgba(192,77,23,0.75)"),
        WidgetRegistryEntry(
            WidgetType.COMBAT_TARGET,
            x = 23,
            y = 0,
            w = 14,
            h = 6,
            minW = 8,
            minH = 3,
            editorLabel = "Combat Target",
            editorColor = "rgba(230,126,34,0.75)"),
        WidgetRegistryEntry(
            WidgetType.XP_BAR,
            x = 20,
            y = 46,
            w = 18,
            h = 2,
            minW = 8,
            minH = 1,
            editorLabel = "XP Bar",
            editorColor = "rgba(46,204,113,0.75)"),
    )

val DEFAULT_WIDGETS: List<LayoutWidget> =
    WIDGET_REGISTRY.map { LayoutWidget(it.type, it.x, it.y, it.w, it.h) }

fun defaultLayout() = GameLayout("default", DEFAULT_WIDGETS)

fun validateLayouts(layouts: List<GameLayout>, activeLayout: String): String? {
    if (layouts.isEmpty()) return "At least one layout is required"
    val names = layouts.map { it.name }
    if (names.size != names.toSet().size) return "Layout names must be unique"
    if (layouts.none { it.name == activeLayout })
        return "Active layout '$activeLayout' not found in layouts"
    return null
}
