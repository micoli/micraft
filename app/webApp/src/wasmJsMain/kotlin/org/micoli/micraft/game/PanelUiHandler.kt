package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsOpenCodex
import org.micoli.micraft.babylon.jsOpenCraft
import org.micoli.micraft.babylon.jsRecipeSync
import org.micoli.micraft.babylon.jsShowLayoutEditor
import org.micoli.micraft.babylon.jsShowPreferences
import org.micoli.micraft.babylon.jsSyncLayouts
import org.micoli.micraft.babylon.jsToggleIngameMap
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.ui.LayoutSyncPayload

/** Session-level UI panel commands — layouts, preferences, codex, craft, in-game map. */
class PanelUiHandler : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.LayoutsSync ->
                jsSyncLayouts(Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
            is ServerMessage.OpenLayoutEditor -> jsShowLayoutEditor()
            is ServerMessage.OpenPreferences -> jsShowPreferences()
            is ServerMessage.OpenCodex -> jsOpenCodex()
            is ServerMessage.OpenCraft -> jsOpenCraft()
            is ServerMessage.RecipeSync -> jsRecipeSync(Json.encodeToString(msg))
            is ServerMessage.ToggleIngameMap -> jsToggleIngameMap()
            else -> Unit
        }
}
