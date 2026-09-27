@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.game

import kotlin.js.JsAny
import org.micoli.micraft.babylon.jsShowBreakOverlay
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.ui.McUiState

/** Chat/notification feed, channel list and the inventory/block-break HUD state it drives. */
class ChatNotificationHandler(
    private val scene: JsAny,
    private val uiState: McUiState,
    private val onNotification: (String) -> Unit,
) : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.Notification -> {
                uiState.pushNotification(msg.message)
                uiState.pushLog(msg.message, msg.channel)
                onNotification(msg.message)
            }
            is ServerMessage.ChatMessage ->
                uiState.pushChatMessage(msg.channel, msg.sender, msg.message)
            is ServerMessage.ChannelsSync ->
                uiState.setChannelsSync(msg.subscribedChannels, msg.knownChannels)
            is ServerMessage.BlockBreakProgress -> {
                val alpha = 1.0 - msg.progress.toDouble() / msg.hardness.toDouble()
                jsShowBreakOverlay(scene, msg.pos.x, msg.pos.y, msg.pos.z, alpha)
            }
            is ServerMessage.InventoryUpdate -> uiState.inventory = msg.inventory
            else -> Unit
        }
}
