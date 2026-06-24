package org.micoli.micraft

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsAddChatMessage
import org.micoli.micraft.babylon.jsAddServerLog
import org.micoli.micraft.babylon.jsChannelsSync
import org.micoli.micraft.babylon.jsConsoleSetPlayer
import org.micoli.micraft.babylon.jsHideDisconnectedOverlay
import org.micoli.micraft.babylon.jsPreferencesSync
import org.micoli.micraft.babylon.jsShowDisconnectedOverlay
import org.micoli.micraft.babylon.jsShowNotification
import org.micoli.micraft.babylon.jsUpdateHotbar
import org.micoli.micraft.ui.McUiState
import org.micoli.micraft.world.ItemType

class WebUiBridge(private val state: McUiState, private val scope: CoroutineScope) {
    fun start() {
        scope.launch {
            state.inventoryFlow.collect { inv ->
                jsUpdateHotbar(Json.encodeToString<Map<ItemType, Int>>(inv))
            }
        }
        scope.launch {
            state.notificationFlow.collect { n ->
                if (n != null) jsShowNotification(n.first)
            }
        }
        scope.launch {
            state.latestLogFlow.collect { entry ->
                if (entry != null) jsAddServerLog(entry.first, entry.second)
            }
        }
        scope.launch {
            state.latestChatMessageFlow.collect { entry ->
                if (entry != null) jsAddChatMessage(entry.first, entry.second, entry.third)
            }
        }
        scope.launch {
            state.channelsSyncFlow.collect { sync ->
                if (sync != null) {
                    fun List<String>.toJson() = "[${joinToString(",") { "\"$it\"" }}]"
                    jsChannelsSync(sync.first.toJson(), sync.second.toJson())
                }
            }
        }
        scope.launch {
            state.disconnectMessageFlow.collect { msg ->
                if (msg != null) jsShowDisconnectedOverlay(msg)
                else jsHideDisconnectedOverlay()
            }
        }
        scope.launch {
            state.consolePlayerNameFlow.collect { name ->
                if (name.isNotEmpty()) jsConsoleSetPlayer(name)
            }
        }
        scope.launch {
            state.preferencesSyncFlow.collect { json ->
                if (json != null) jsPreferencesSync(json)
            }
        }
    }
}
