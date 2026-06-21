package org.micoli.micraft.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.micoli.micraft.world.ItemType

data class HudData(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val stance: String = "STANDING",
    val speed: Double = 1.0,
    val fps: Int = 0,
    val kbIn: Double = 0.0,
    val kbOut: Double = 0.0,
    val biome: String = "",
    val targetBlock: String = "",
)

class McUiState {
    var hud by mutableStateOf(HudData())
    var inventory by mutableStateOf(mapOf<ItemType, Int>())
    var notification by mutableStateOf<Pair<String, Int>?>(null)
        private set
    var latestLog by mutableStateOf<Pair<String, Int>?>(null)
        private set
    var disconnectMessage by mutableStateOf<String?>(null)
    var consolePlayerName by mutableStateOf("")

    private var notifSeq = 0
    private var logSeq = 0

    fun pushNotification(msg: String) { notification = msg to ++notifSeq }
    fun pushLog(msg: String) { latestLog = msg to ++logSeq }
}
