package org.micoli.micraft

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.ui.LayoutSyncPayload
import org.micoli.micraft.ui.McUiState
import org.micoli.micraft.world.*

private const val SKY_R = 0.53
private const val SKY_G = 0.81
private const val SKY_B = 0.98

class GameClient
@OptIn(ExperimentalWasmJsInterop::class)
constructor(private val scene: JsAny, private val camera: JsAny, private val uiState: McUiState) {
    private val outMessages = Channel<ClientMessage>(Channel.BUFFERED)
    private val networkStats = NetworkStats()
    private val chunkManager = ChunkManager(scene)
    private val remotePlayerManager = RemotePlayerManager(scene)
    private val npcManager = NpcManager(scene)
    private val localController =
        LocalPlayerController(
            scene = scene,
            camera = camera,
            outMessages = outMessages,
            chunkManager = chunkManager,
            uiState = uiState,
            networkStats = networkStats,
            serverHost = { serverHost },
            serverPort = { serverPort },
        )

    private val scope = CoroutineScope(Dispatchers.Default)
    private var localPlayerId: String? = null
    private var playerIdReady = CompletableDeferred<String>()
    private var serverHost = ""
    private var serverPort = 0

    init {
        jsOptimizeScene(scene)
        jsSetupFog(scene, SKY_R, SKY_G, SKY_B)
        jsSetupRenderPipeline(scene, camera)
        jsInitPlayerModel()
        jsInitBlockDefs()
    }

    fun connect(
        host: String,
        port: Int,
        username: String,
        playerName: String,
        preferredLanguage: String = "en",
        token: String = "",
    ) {
        serverHost = host
        serverPort = port

        scope.launch {
            while (isActive) {
                delay(16)
                if (localController.hasPrediction) localController.tick()
                chunkManager.drainPendingChunks()
                npcManager.tick()
                remotePlayerManager.tick()
            }
        }

        scope.launch {
            var chunkRetryDelay = 1000L
            while (isActive) {
                try {
                    val pid = playerIdReady.await()
                    val chunkClient = HttpClient(Js) { install(WebSockets) }
                    chunkClient.webSocket(host = host, port = port, path = "/chunks") {
                        send(Frame.Text(pid))
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            networkStats.bytesIn += text.length
                            val msg =
                                runCatching { Json.decodeFromString<ServerMessage>(text) }
                                    .getOrNull() ?: continue
                            if (msg is ServerMessage.ChunkData) {
                                chunkManager.enqueueChunk(
                                    Chunk.decodeWire(msg.pos, msg.topY, msg.wireBlocks), msg.topY)
                            }
                        }
                    }
                    chunkRetryDelay = 1000L
                } catch (_: Throwable) {}
                if (!isActive) break
                delay(chunkRetryDelay)
                chunkRetryDelay = minOf(chunkRetryDelay * 2, 8000L)
            }
        }

        scope.launch {
            var retryDelay = 1000L
            while (isActive) {
                var sessionWelcomed = false
                try {
                    uiState.disconnectMessage = null
                    jsLog("WS connecting to ws://$host:$port/game")
                    val client = HttpClient(Js) { install(WebSockets) }
                    client.webSocket(host = host, port = port, path = "/game") {
                        jsLog(
                            "WS connected, sending Connect(playerName=$playerName, userName=$username)")
                        send(
                            Frame.Text(
                                Json.encodeToString<ClientMessage>(
                                    ClientMessage.Connect(
                                        playerName = playerName,
                                        userName = username,
                                        preferredLanguage = preferredLanguage,
                                        token = token))))

                        val inputJob = launch {
                            while (isActive) {
                                delay(50)
                                val intent = localController.buildMoveIntent()
                                val idle =
                                    intent.dx == 0f &&
                                        intent.dz == 0f &&
                                        intent.dy == 0f &&
                                        !intent.jump &&
                                        !intent.flyToggle &&
                                        !intent.speedUp &&
                                        !intent.speedDown
                                if (!idle || intent != localController.lastSentIntent) {
                                    localController.lastSentIntent = intent
                                    val intentText = Json.encodeToString<ClientMessage>(intent)
                                    send(Frame.Text(intentText))
                                    networkStats.bytesOut += intentText.length
                                }
                                val unloads = chunkManager.collectAndClearUnloads()
                                if (unloads.isNotEmpty()) {
                                    val unloadText =
                                        Json.encodeToString<ClientMessage>(
                                            ClientMessage.ChunkUnload(unloads))
                                    send(Frame.Text(unloadText))
                                    networkStats.bytesOut += unloadText.length
                                }
                            }
                        }

                        val breakJob = launch {
                            for (msg in outMessages) {
                                val text = Json.encodeToString<ClientMessage>(msg)
                                send(Frame.Text(text))
                                networkStats.bytesOut += text.length
                            }
                        }

                        var frameCount = 0
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                networkStats.bytesIn += text.length
                                frameCount++
                                if (frameCount <= 3)
                                    jsLog(
                                        "WS frame #$frameCount (${text.length}B): ${text.take(200)}")
                                val msg =
                                    runCatching { Json.decodeFromString<ServerMessage>(text) }
                                        .onFailure { e ->
                                            jsError(
                                                "JSON parse error on frame #$frameCount: ${e.message} | raw=${text.take(300)}")
                                        }
                                        .getOrNull() ?: continue
                                if (msg is ServerMessage.Welcome) sessionWelcomed = true
                                handleMessage(msg)
                            } else {
                                jsLog("WS non-text frame: ${frame::class.simpleName}")
                            }
                        }
                        jsLog(
                            "WS incoming loop ended after $frameCount frames (closeReason=${closeReason.await()})")
                        inputJob.cancel()
                        breakJob.cancel()
                    }
                    jsLog("WS session closed normally")
                } catch (e: Throwable) {
                    jsError("WS error: ${e::class.simpleName}: ${e.message}")
                }

                if (!isActive) break
                if (sessionWelcomed) retryDelay = 1000L
                jsLog("WS resetForReconnect, retryDelay=${retryDelay}ms")
                resetForReconnect()
                val retrySec = retryDelay / 1000
                uiState.disconnectMessage = "Reconnecting in ${retrySec}s…"
                delay(retryDelay)
                retryDelay = minOf(retryDelay * 2, 8000L)
            }
        }
    }

    private fun resetForReconnect() {
        uiState.inventory = emptyMap()
        localPlayerId = null
        playerIdReady = CompletableDeferred()
        localController.reset()
        chunkManager.clear()
        remotePlayerManager.clear()
        npcManager.clear()
    }

    private fun handleMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Welcome -> {
                localPlayerId = msg.playerId
                playerIdReady.complete(msg.playerId)
                uiState.consolePlayerName = msg.playerName
                jsFetchI18n(msg.language)
                jsFetchBiomeColors()
                chunkManager.setShadersEnabled(msg.shadersEnabled)
                jsSyncLayouts(Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
                localController.setViewMode(msg.viewMode)
            }
            is ServerMessage.ShadersUpdate -> chunkManager.setShadersEnabled(msg.enabled)
            is ServerMessage.ChunkData ->
                chunkManager.renderChunk(
                    Chunk.decodeWire(msg.pos, msg.topY, msg.wireBlocks), msg.topY)
            is ServerMessage.PlayerUpdate -> {
                val s = msg.state
                if (s.id == localPlayerId) {
                    localController.updateFromServer(s) { cx, cz ->
                        chunkManager.unloadDistantChunks(cx, cz)
                    }
                } else {
                    remotePlayerManager.updateFromServer(s)
                }
            }
            is ServerMessage.PlayerLeft -> remotePlayerManager.remove(msg.playerId)
            is ServerMessage.Notification -> {
                uiState.pushNotification(msg.message)
                uiState.pushLog(msg.message, msg.channel)
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
            is ServerMessage.ShortcutBarUpdate -> {
                msg.slots.forEachIndexed { i, item ->
                    if (i in 0..9) localController.shortcutBar[i] = item
                }
                localController.syncShortcutBarToUi()
            }
            is ServerMessage.TimeUpdate -> localController.currentGameTicks = msg.gameTicks
            is ServerMessage.LayoutsSync ->
                jsSyncLayouts(Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
            is ServerMessage.OpenLayoutEditor -> jsShowLayoutEditor()
            is ServerMessage.OpenPreferences -> jsShowPreferences()
            is ServerMessage.RegistrySync -> {
                val blockDefs =
                    msg.blocks
                        .mapIndexed { i, info ->
                            BlockType.entries[i] to
                                BlockDefinition(
                                    hardness = info.hardness,
                                    solid = info.solid,
                                    transparent = info.transparent,
                                    minimapColor = info.minimapColor,
                                    modelElement = info.modelElement,
                                    liquid = info.liquid,
                                    viscosity = info.viscosity,
                                )
                        }
                        .toMap()
                BlockRegistry.load(blockDefs)
                val itemDefs =
                    msg.items.entries
                        .mapNotNull { (key, info) ->
                            runCatching {
                                    ItemType.valueOf(key) to
                                        ItemDefinition(
                                            buildable = info.buildable,
                                            placesBlock =
                                                info.placesBlock?.let {
                                                    runCatching { BlockType.valueOf(it) }
                                                        .getOrNull()
                                                },
                                        )
                                }
                                .getOrNull()
                        }
                        .toMap()
                ItemRegistry.load(itemDefs)
                jsSetBlockRegistry(Json.encodeToString(msg.blocks))
                if (msg.npcs.isNotEmpty()) jsInitNpcModels(Json.encodeToString(msg.npcs))
            }
            is ServerMessage.ItemsSpawned -> Unit
            is ServerMessage.ItemDespawned -> Unit
            is ServerMessage.NpcSpawned -> npcManager.handleSpawned(msg.npc)
            is ServerMessage.NpcUpdate -> npcManager.handleUpdate(msg.npc)
            is ServerMessage.NpcDespawned -> npcManager.handleDespawned(msg.id)
            is ServerMessage.NpcInteractResult -> jsOpenNpcDialog(msg.payload)
            is ServerMessage.WeatherUpdate -> jsSetWeatherZones(Json.encodeToString(msg.zones))
            is ServerMessage.PreferencesSync ->
                uiState.setPreferencesSync(Json.encodeToString<ServerMessage.PreferencesSync>(msg))
            is ServerMessage.WorldUpdate ->
                msg.changes.forEach { change ->
                    val cx = change.pos.x.floorDiv(WorldConstants.CHUNK_SIZE)
                    val cz = change.pos.z.floorDiv(WorldConstants.CHUNK_SIZE)
                    val cp = ChunkPos(cx, cz)
                    val (existing, existingTopY) = chunkManager.chunkData[cp] ?: return@forEach
                    val lx = change.pos.x - cx * WorldConstants.CHUNK_SIZE
                    val lz = change.pos.z - cz * WorldConstants.CHUNK_SIZE
                    val updated = existing.withBlock(lx, change.pos.y, lz, change.type)
                    val newTopY =
                        if (change.type != BlockType.AIR) maxOf(existingTopY, change.pos.y)
                        else existingTopY
                    chunkManager.renderChunk(updated, newTopY)
                    if (change.type == BlockType.AIR) localController.onBlockBroken(change.pos)
                }
        }
    }
}
