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
    XP_BAR,
    AGGRO_INDICATORS,
}
