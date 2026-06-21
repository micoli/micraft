package org.micoli.micraft

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsAddServerLog
import org.micoli.micraft.babylon.jsConsoleSetPlayer
import org.micoli.micraft.babylon.jsHideDisconnectedOverlay
import org.micoli.micraft.babylon.jsShowDisconnectedOverlay
import org.micoli.micraft.babylon.jsShowNotification
import org.micoli.micraft.babylon.jsUpdateHotbar
import org.micoli.micraft.babylon.jsUpdateHUD
import org.micoli.micraft.ui.McUiState
import org.micoli.micraft.world.ItemType

class WebUiBridge(private val state: McUiState, private val scope: CoroutineScope) {
    fun start() {
        scope.launch {
            snapshotFlow { state.hud }.distinctUntilChanged().collect { h ->
                jsUpdateHUD(h.x, h.y, h.z, h.yaw, h.pitch,
                    h.stance, h.speed, h.fps, h.kbIn, h.kbOut, h.biome, h.targetBlock)
            }
        }
        scope.launch {
            snapshotFlow { state.inventory }.distinctUntilChanged().collect { inv ->
                jsUpdateHotbar(Json.encodeToString<Map<ItemType, Int>>(inv))
            }
        }
        scope.launch {
            snapshotFlow { state.notification }.distinctUntilChanged().collect { n ->
                if (n != null) jsShowNotification(n.first)
            }
        }
        scope.launch {
            snapshotFlow { state.latestLog }.distinctUntilChanged().collect { entry ->
                if (entry != null) jsAddServerLog(entry.first)
            }
        }
        scope.launch {
            snapshotFlow { state.disconnectMessage }.distinctUntilChanged().collect { msg ->
                if (msg != null) jsShowDisconnectedOverlay(msg)
                else jsHideDisconnectedOverlay()
            }
        }
        scope.launch {
            snapshotFlow { state.consolePlayerName }.distinctUntilChanged().collect { name ->
                if (name.isNotEmpty()) jsConsoleSetPlayer(name)
            }
        }
    }
}
