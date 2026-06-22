package org.micoli.micraft.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val gameTime: String = "",
)

class McUiState {
    private val _hud = MutableStateFlow(HudData())
    val hudFlow: StateFlow<HudData> = _hud
    var hud: HudData
        get() = _hud.value
        set(value) { _hud.value = value }

    private val _inventory = MutableStateFlow(mapOf<ItemType, Int>())
    val inventoryFlow: StateFlow<Map<ItemType, Int>> = _inventory
    var inventory: Map<ItemType, Int>
        get() = _inventory.value
        set(value) { _inventory.value = value }

    private val _notification = MutableStateFlow<Pair<String, Int>?>(null)
    val notificationFlow: StateFlow<Pair<String, Int>?> = _notification

    private val _latestLog = MutableStateFlow<Pair<String, Int>?>(null)
    val latestLogFlow: StateFlow<Pair<String, Int>?> = _latestLog

    private val _disconnectMessage = MutableStateFlow<String?>(null)
    val disconnectMessageFlow: StateFlow<String?> = _disconnectMessage
    var disconnectMessage: String?
        get() = _disconnectMessage.value
        set(value) { _disconnectMessage.value = value }

    private val _consolePlayerName = MutableStateFlow("")
    val consolePlayerNameFlow: StateFlow<String> = _consolePlayerName
    var consolePlayerName: String
        get() = _consolePlayerName.value
        set(value) { _consolePlayerName.value = value }

    private var notifSeq = 0
    private var logSeq = 0

    fun pushNotification(msg: String) { _notification.value = msg to ++notifSeq }
    fun pushLog(msg: String) { _latestLog.value = msg to ++logSeq }
}
