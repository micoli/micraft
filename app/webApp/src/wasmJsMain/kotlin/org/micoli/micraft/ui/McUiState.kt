package org.micoli.micraft.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.ChannelSubscription

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
    val reconcileXzStats: String = "",
    val reconcileYStats: String = "",
    val tickDtMs: Double = 0.0,
    val tickJitterMs: Double = 0.0,
    val tickDtMinMs: Double = 0.0,
    val tickDtMaxMs: Double = 0.0,
    val tickJitterMinMs: Double = 0.0,
    val tickJitterMaxMs: Double = 0.0,
    val chunkDownloading: Int = 0,
    val chunkMeshing: Int = 0,
)

class McUiState {
    private val _hud = MutableStateFlow(HudData())
    val hudFlow: StateFlow<HudData> = _hud
    var hud: HudData
        get() = _hud.value
        set(value) {
            _hud.value = value
        }

    private val _inventory = MutableStateFlow(mapOf<ItemType, Int>())
    val inventoryFlow: StateFlow<Map<ItemType, Int>> = _inventory
    var inventory: Map<ItemType, Int>
        get() = _inventory.value
        set(value) {
            _inventory.value = value
        }

    private val _notification = MutableStateFlow<Pair<String, Int>?>(null)
    val notificationFlow: StateFlow<Pair<String, Int>?> = _notification

    // Triple: (channel, message, seq)
    private val _latestLog = MutableStateFlow<Triple<String, String, Int>?>(null)
    val latestLogFlow: StateFlow<Triple<String, String, Int>?> = _latestLog

    private val _disconnectMessage = MutableStateFlow<String?>(null)
    val disconnectMessageFlow: StateFlow<String?> = _disconnectMessage
    var disconnectMessage: String?
        get() = _disconnectMessage.value
        set(value) {
            _disconnectMessage.value = value
        }

    private val _consolePlayerName = MutableStateFlow("")
    val consolePlayerNameFlow: StateFlow<String> = _consolePlayerName
    var consolePlayerName: String
        get() = _consolePlayerName.value
        set(value) {
            _consolePlayerName.value = value
        }

    // Pair: (subscribedChannels, knownChannels)
    private val _channelsSync =
        MutableStateFlow<Pair<List<ChannelSubscription>, List<String>>?>(null)
    val channelsSyncFlow: StateFlow<Pair<List<ChannelSubscription>, List<String>>?> = _channelsSync

    // Triple: (channel, sender, message)
    private val _latestChatMessage = MutableStateFlow<Triple<String, String, String>?>(null)
    val latestChatMessageFlow: StateFlow<Triple<String, String, String>?> = _latestChatMessage

    private var notifSeq = 0
    private var logSeq = 0
    private var chatSeq = 0

    fun pushNotification(msg: String) {
        _notification.value = msg to ++notifSeq
    }

    fun pushLog(msg: String, channel: String = "system") {
        _latestLog.value = Triple(channel, msg, ++logSeq)
    }

    fun pushChatMessage(channel: String, sender: String, msg: String) {
        _latestChatMessage.value = Triple(channel, sender, msg)
        chatSeq++
    }

    fun setChannelsSync(subscribed: List<ChannelSubscription>, known: List<String>) {
        _channelsSync.value = subscribed to known
    }

    private val _preferencesSync = MutableStateFlow<String?>(null)
    val preferencesSyncFlow: StateFlow<String?> = _preferencesSync

    fun setPreferencesSync(json: String) {
        _preferencesSync.value = json
    }

    private val _chunkLoadingProgress = MutableStateFlow<Triple<Int, Int, Int>?>(null)
    val chunkLoadingProgressFlow: StateFlow<Triple<Int, Int, Int>?> = _chunkLoadingProgress
    var chunkLoadingProgress: Triple<Int, Int, Int>?
        get() = _chunkLoadingProgress.value
        set(value) {
            _chunkLoadingProgress.value = value
        }
}
