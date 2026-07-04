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
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
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
    private var currentPlayerName = ""
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
            playerName = { currentPlayerName },
        )

    private val scope = CoroutineScope(Dispatchers.Default)
    private var localPlayerId: String? = null
    private var playerIdReady = CompletableDeferred<String>()
    private var serverHost = ""
    private var serverPort = 0
    private var token = ""
    private var chunkTransportMode = "websocket"
    private var httpChunkFetcher: HttpChunkFetcher? = null
    private var currentPlayerCx = 0
    private var currentPlayerCz = 0
    private var currentYaw = 0f
    private var isInitialLoading = false
    private val expectedChunkCount
        get() = (2 * WorldConstants.CLIENT_VIEW_RADIUS + 1).let { it * it }

    init {
        jsOptimizeScene(scene)
        jsSetupFog(scene, SKY_R, SKY_G, SKY_B)
        jsSetupRenderPipeline(scene, camera)
        jsInitPlayerModel("player")
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
        currentPlayerName = playerName
        this.token = token

        scope.launch {
            while (isActive) {
                delay(16)
                if (localController.hasPrediction) {
                    localController.chunkDownloading = httpChunkFetcher?.inFlightCount ?: 0
                    localController.chunkMeshing = chunkManager.pendingRenderCount
                    localController.tick()
                }
                if (isInitialLoading) {
                    val meshed = chunkManager.loadedChunks.size
                    val pending = chunkManager.pendingRenderCount
                    uiState.chunkLoadingProgress = Triple(meshed, pending, expectedChunkCount)
                    if (localController.hasPrediction &&
                        chunkManager.allFovChunksMeshed(
                            currentPlayerCx, currentPlayerCz, currentYaw.toDouble())) {
                        isInitialLoading = false
                        uiState.chunkLoadingProgress = null
                    }
                }
                npcManager.tick()
                remotePlayerManager.tick()
            }
        }

        scope.launch {
            while (isActive) {
                delay(16)
                chunkManager.drainPendingChunks(
                    playerCx = currentPlayerCx,
                    playerCz = currentPlayerCz,
                    yaw = currentYaw.toDouble(),
                    budgetMs = 4.0,
                )
                chunkManager.drainOneMinimapPush()
            }
        }

        scope.launch {
            var chunkRetryDelay = 1000L
            while (isActive) {
                try {
                    val pid = playerIdReady.await()
                    if (chunkTransportMode != "websocket") break
                    val chunkClient = HttpClient(Js) { install(WebSockets) }
                    chunkClient.webSocket(host = host, port = port, path = "/chunks") {
                        send(Frame.Text(pid))
                        for (frame in incoming) {
                            if (frame !is Frame.Binary) continue
                            val data = frame.readBytes()
                            networkStats.bytesIn += data.size
                            val msg =
                                runCatching { ServerMessageCodec.decode(data) }.getOrNull()
                                    ?: continue
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
                            Frame.Binary(
                                true,
                                ClientMessageCodec.encode(
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
                                    val intentBytes = ClientMessageCodec.encode(intent)
                                    send(Frame.Binary(true, intentBytes))
                                    networkStats.bytesOut += intentBytes.size
                                }
                                val unloads = chunkManager.collectAndClearUnloads()
                                if (unloads.isNotEmpty()) {
                                    val unloadBytes =
                                        ClientMessageCodec.encode(
                                            ClientMessage.ChunkUnload(unloads))
                                    send(Frame.Binary(true, unloadBytes))
                                    networkStats.bytesOut += unloadBytes.size
                                }
                            }
                        }

                        val breakJob = launch {
                            for (msg in outMessages) {
                                val bytes = ClientMessageCodec.encode(msg)
                                send(Frame.Binary(true, bytes))
                                networkStats.bytesOut += bytes.size
                            }
                        }

                        var frameCount = 0
                        for (frame in incoming) {
                            if (frame is Frame.Binary) {
                                val data = frame.readBytes()
                                networkStats.bytesIn += data.size
                                frameCount++
                                val msg =
                                    runCatching { ServerMessageCodec.decode(data) }
                                        .onFailure { e ->
                                            jsError(
                                                "Protobuf decode error on frame #$frameCount: ${e.message}")
                                        }
                                        .getOrNull() ?: continue
                                if (msg is ServerMessage.Welcome) sessionWelcomed = true
                                handleMessage(msg)
                            } else {
                                jsLog("WS non-binary frame: ${frame::class.simpleName}")
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
        isInitialLoading = false
        uiState.chunkLoadingProgress = null
        uiState.inventory = emptyMap()
        localPlayerId = null
        playerIdReady = CompletableDeferred()
        chunkTransportMode = "websocket"
        httpChunkFetcher = null
        localController.reset()
        chunkManager.clear()
        remotePlayerManager.clear()
        npcManager.clear()
    }

    private fun handleMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Welcome -> {
                isInitialLoading = true
                uiState.chunkLoadingProgress = Triple(0, 0, expectedChunkCount)
                localPlayerId = msg.playerId
                chunkTransportMode = msg.chunkTransport
                if (msg.chunkTransport == "http") {
                    httpChunkFetcher =
                        HttpChunkFetcher(
                            chunkManager = chunkManager,
                            token = token,
                            scope = scope,
                        )
                    scope.launch {
                        while (isActive) {
                            delay(2000)
                            httpChunkFetcher?.trigger(currentPlayerCx, currentPlayerCz, currentYaw)
                        }
                    }
                }
                playerIdReady.complete(msg.playerId)
                uiState.consolePlayerName = msg.playerName
                jsFetchI18n(msg.language)
                jsFetchBiomeColors()
                chunkManager.setShadersEnabled(msg.shadersEnabled)
                jsSyncLayouts(Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
                localController.setViewMode(msg.viewMode)
                localController.setReconcileTolerances(
                    msg.reconcileToleranceXz, msg.reconcileToleranceY)
            }
            is ServerMessage.GameConfigSync ->
                localController.setReconcileTolerances(
                    msg.reconcileToleranceXz, msg.reconcileToleranceY)
            is ServerMessage.ShadersUpdate -> chunkManager.setShadersEnabled(msg.enabled)
            is ServerMessage.ChunkData ->
                chunkManager.enqueueChunk(
                    Chunk.decodeWire(msg.pos, msg.topY, msg.wireBlocks), msg.topY)
            is ServerMessage.PlayerUpdate -> {
                val s = msg.state
                if (s.id == localPlayerId) {
                    currentPlayerCx = s.pos.x.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
                    currentPlayerCz = s.pos.z.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
                    currentYaw = s.orientation.yaw
                    httpChunkFetcher?.trigger(currentPlayerCx, currentPlayerCz, currentYaw)
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
                for (i in 0..9) localController.shortcutBar[i] = null
                msg.slots.forEach { (i, item) ->
                    if (i in 0..9) localController.shortcutBar[i] = item
                }
                localController.syncShortcutBarToUi()
            }
            is ServerMessage.TimeUpdate -> localController.currentGameTicks = msg.gameTicks
            is ServerMessage.LayoutsSync ->
                jsSyncLayouts(Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
            is ServerMessage.OpenLayoutEditor -> jsShowLayoutEditor()
            is ServerMessage.OpenPreferences -> jsShowPreferences()
            is ServerMessage.OpenCodex -> jsOpenCodex()
            is ServerMessage.OpenCraft -> jsOpenCraft()
            is ServerMessage.RecipeSync -> jsRecipeSync(Json.encodeToString(msg))
            is ServerMessage.ToggleBiomeMap -> jsToggleBiomeMap()
            is ServerMessage.RegistrySync -> {
                val blockDefs =
                    msg.blocks
                        .mapIndexed { _, info ->
                            BlockType(info.name) to
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
                    msg.items.entries.associate { (key, info) ->
                        ItemType(key) to
                            ItemDefinition(
                                buildable = info.buildable,
                                placesBlock =
                                    info.placesBlock?.let {
                                        runCatching { BlockType(it) }.getOrNull()
                                    },
                            )
                    }
                ItemRegistry.load(itemDefs)
                jsSetBlockRegistry(Json.encodeToString(msg.blocks))
                jsSetItemRegistry(Json.encodeToString(msg.items))
                if (msg.npcs.isNotEmpty()) jsInitNpcModels(Json.encodeToString(msg.npcs))
                if (msg.npcDefinitions.isNotEmpty())
                    jsSetNpcDefinitions(Json.encodeToString(msg.npcDefinitions))
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
                    chunkManager.updateAndEnqueue(updated, newTopY)
                    if (change.type == BlockType.AIR) localController.onBlockBroken(change.pos)
                }
        }
    }
}
