package org.micoli.micraft.game

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.protocol.ServerMessage

/**
 * Client mirror of server-side panel content: which placeable ids are panels and their resolved
 * home URL — the sanitized HTML itself never crosses the wire (see `PANEL_PAGE_CSP` on the server).
 * Drives the DOM layer in `panelSurface.ts` directly; there's no model-cache readiness gate to wait
 * on like [PlaceableManager], so every call applies immediately.
 */
class PanelManager : ServerMessageHandler {
    private val panelIds = mutableSetOf<String>()

    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.PanelSync -> {
                panelIds.clear()
                panelIds.addAll(msg.panels.map { it.placeableId })
                jsPanelSync(Json.encodeToString(msg.panels))
            }
            is ServerMessage.PanelChanged -> {
                panelIds.add(msg.info.placeableId)
                jsPanelUpsert(Json.encodeToString(msg.info))
            }
            is ServerMessage.PanelRemoved -> {
                panelIds.remove(msg.placeableId)
                jsPanelRemove(msg.placeableId)
            }
            is ServerMessage.PanelEditOpen -> jsOpenPanelEditor(Json.encodeToString(msg.data))
            else -> Unit
        }

    fun isPanel(id: String): Boolean = id in panelIds

    fun clear() {
        panelIds.clear()
    }
}
