package org.micoli.micraft.game

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlin.reflect.KClass
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.micoli.micraft.ChunkManager
import org.micoli.micraft.HttpChunkFetcher
import org.micoli.micraft.LocalPlayerController
import org.micoli.micraft.RemotePlayerManager
import org.micoli.micraft.babylon.*
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.micoli.micraft.ui.LayoutSyncPayload
import org.micoli.micraft.ui.McUiState

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
    private val npcManager = NpcManager(scene) { localPlayerId }
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
            playerId = { localPlayerId ?: "" },
            npcManager = npcManager,
        )

    init {
        localController.nearestRemoteLightBoost = {
            remotePlayerManager.nearestLightBoostPosition(
                localController.predX, localController.predZ)
        }
    }

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

    private val dispatchMap: Map<KClass<out ServerMessage>, ServerMessageHandler> by lazy {
        buildDispatchMap()
    }

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
            var currentUsername = username
            var currentPlayerNameLocal = playerName
            var currentLang = preferredLanguage
            var currentToken = token
            while (isActive) {
                var sessionWelcomed = false
                try {
                    uiState.disconnectMessage = null
                    jsLog("WS connecting to ws://$serverHost:$serverPort/game")
                    val client = HttpClient(Js) { install(WebSockets) }
                    client.webSocket(host = serverHost, port = serverPort, path = "/game") {
                        jsLog(
                            "WS connected, sending Connect(playerName=$currentPlayerNameLocal, userName=$currentUsername)")
                        send(
                            Frame.Binary(
                                true,
                                ClientMessageCodec.encode(
                                    ClientMessage.Connect(
                                        playerName = currentPlayerNameLocal,
                                        userName = currentUsername,
                                        preferredLanguage = currentLang,
                                        token = currentToken))))

                        val inputJob = launch {
                            while (isActive) {
                                delay(50)
                                if (localController.disconnectRequested) {
                                    localController.disconnectRequested = false
                                    close(CloseReason(CloseReason.Codes.NORMAL, "disconnect"))
                                    break
                                }
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
                                runCatching { handleMessage(msg) }
                                    .onFailure { e ->
                                        jsError(
                                            "handleMessage error on ${msg::class.simpleName}: ${e.message}")
                                    }
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
                resetForReconnect()
                if (sessionWelcomed) {
                    retryDelay = 1000L
                    jsLog("WS disconnected after session — returning to login")
                    jsShowLoginOverlay()
                    var loginResult = ""
                    while (loginResult.isEmpty()) {
                        delay(100)
                        loginResult = jsConsumeLoginResult()
                    }
                    val parts = loginResult.split("\t")
                    currentUsername = parts[0]
                    currentPlayerNameLocal = if (parts.size > 1) parts[1] else parts[0]
                    currentLang = if (parts.size > 2) parts[2] else "en"
                    currentToken = if (parts.size > 3) parts[3] else ""
                    currentPlayerName = currentPlayerNameLocal
                    this@GameClient.token = currentToken
                    jsFetchI18n(currentLang)
                    jsHideLoginOverlay()
                } else {
                    jsLog("WS resetForReconnect, retryDelay=${retryDelay}ms")
                    val retrySec = retryDelay / 1000
                    uiState.disconnectMessage = "Reconnecting in ${retrySec}s…"
                    delay(retryDelay)
                    retryDelay = minOf(retryDelay * 2, 8000L)
                }
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
        dispatchMap[msg::class]?.handle(msg)
    }

    private fun buildDispatchMap(): Map<KClass<out ServerMessage>, ServerMessageHandler> =
        buildMap {
            // Session / init
            put(
                ServerMessage.Welcome::class,
                typedHandler { msg: ServerMessage.Welcome ->
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
                                httpChunkFetcher?.trigger(
                                    currentPlayerCx, currentPlayerCz, currentYaw)
                            }
                        }
                    }
                    playerIdReady.complete(msg.playerId)
                    uiState.playerId = msg.playerId
                    uiState.consolePlayerName = msg.playerName
                    jsFetchI18n(msg.language)
                    jsFetchBiomeColors()
                    chunkManager.setShadersEnabled(msg.shadersEnabled)
                    jsSyncLayouts(
                        Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
                    localController.setViewMode(msg.viewMode)
                    localController.setReconcileTolerances(
                        msg.reconcileToleranceXz, msg.reconcileToleranceY)
                })
            put(ServerMessage.ItemsSpawned::class, ServerMessageHandler {})
            put(ServerMessage.ItemDespawned::class, ServerMessageHandler {})

            // Chunk
            put(
                ServerMessage.ChunkData::class,
                typedHandler { msg: ServerMessage.ChunkData ->
                    chunkManager.enqueueChunk(
                        Chunk.decodeWire(msg.pos, msg.topY, msg.wireBlocks), msg.topY)
                })
            put(
                ServerMessage.ShadersUpdate::class,
                typedHandler { msg: ServerMessage.ShadersUpdate ->
                    chunkManager.setShadersEnabled(msg.enabled)
                })
            put(
                ServerMessage.LightBoostUpdate::class,
                typedHandler { msg: ServerMessage.LightBoostUpdate ->
                    localController.lightBoostEnabled = msg.enabled
                })
            put(
                ServerMessage.WorldUpdate::class,
                typedHandler { msg: ServerMessage.WorldUpdate ->
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
                })

            // Player
            put(
                ServerMessage.PlayerUpdate::class,
                typedHandler { msg: ServerMessage.PlayerUpdate ->
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
                })
            put(
                ServerMessage.PlayerLeft::class,
                typedHandler { msg: ServerMessage.PlayerLeft ->
                    remotePlayerManager.remove(msg.playerId)
                })
            put(
                ServerMessage.GameConfigSync::class,
                typedHandler { msg: ServerMessage.GameConfigSync ->
                    localController.setReconcileTolerances(
                        msg.reconcileToleranceXz, msg.reconcileToleranceY)
                })
            put(
                ServerMessage.ShortcutBarUpdate::class,
                typedHandler { msg: ServerMessage.ShortcutBarUpdate ->
                    for (i in 0..9) localController.shortcutBar[i] = null
                    msg.slots.forEach { (i, item) ->
                        if (i in 0..9) localController.shortcutBar[i] = item
                    }
                    localController.syncShortcutBarToUi()
                })
            put(
                ServerMessage.TimeUpdate::class,
                typedHandler { msg: ServerMessage.TimeUpdate ->
                    localController.currentGameTicks = msg.gameTicks
                })
            put(
                ServerMessage.CombatTargetUpdate::class,
                typedHandler { msg: ServerMessage.CombatTargetUpdate ->
                    localController.currentCombatTargetId = msg.targetId
                    jsCombatTargetUpdate(Json.encodeToString(msg))
                })

            // NPC — single handler object registered for all NPC message types
            put(ServerMessage.NpcSpawned::class, npcManager)
            put(ServerMessage.NpcUpdate::class, npcManager)
            put(ServerMessage.NpcDespawned::class, npcManager)
            put(ServerMessage.NpcInteractResult::class, npcManager)

            // UI / notifications
            put(
                ServerMessage.Notification::class,
                typedHandler { msg: ServerMessage.Notification ->
                    uiState.pushNotification(msg.message)
                    uiState.pushLog(msg.message, msg.channel)
                })
            put(
                ServerMessage.ChatMessage::class,
                typedHandler { msg: ServerMessage.ChatMessage ->
                    uiState.pushChatMessage(msg.channel, msg.sender, msg.message)
                })
            put(
                ServerMessage.ChannelsSync::class,
                typedHandler { msg: ServerMessage.ChannelsSync ->
                    uiState.setChannelsSync(msg.subscribedChannels, msg.knownChannels)
                })
            put(
                ServerMessage.BlockBreakProgress::class,
                typedHandler { msg: ServerMessage.BlockBreakProgress ->
                    val alpha = 1.0 - msg.progress.toDouble() / msg.hardness.toDouble()
                    jsShowBreakOverlay(scene, msg.pos.x, msg.pos.y, msg.pos.z, alpha)
                })
            put(
                ServerMessage.InventoryUpdate::class,
                typedHandler { msg: ServerMessage.InventoryUpdate ->
                    uiState.inventory = msg.inventory
                })

            // Layouts / UI panels
            put(
                ServerMessage.LayoutsSync::class,
                typedHandler { msg: ServerMessage.LayoutsSync ->
                    jsSyncLayouts(
                        Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
                })
            put(
                ServerMessage.OpenLayoutEditor::class,
                ServerMessageHandler { jsShowLayoutEditor() })
            put(ServerMessage.OpenPreferences::class, ServerMessageHandler { jsShowPreferences() })
            put(ServerMessage.OpenCodex::class, ServerMessageHandler { jsOpenCodex() })
            put(ServerMessage.OpenCraft::class, ServerMessageHandler { jsOpenCraft() })
            put(
                ServerMessage.RecipeSync::class,
                typedHandler { msg: ServerMessage.RecipeSync ->
                    jsRecipeSync(Json.encodeToString(msg))
                })
            put(ServerMessage.ToggleBiomeMap::class, ServerMessageHandler { jsToggleBiomeMap() })
            put(
                ServerMessage.RegistrySync::class,
                typedHandler { msg: ServerMessage.RegistrySync ->
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
                    if (msg.npcWalkBones.isNotEmpty())
                        jsInitNpcWalkBones(Json.encodeToString(msg.npcWalkBones))
                    if (msg.npcDefinitions.isNotEmpty())
                        jsSetNpcDefinitions(Json.encodeToString(msg.npcDefinitions))
                    jsReloadAttackMeta()
                })

            // Trade
            put(
                ServerMessage.OpenTrade::class,
                typedHandler { msg: ServerMessage.OpenTrade ->
                    jsOpenTrade(msg.tradeId, msg.otherPlayerName, msg.myRole)
                })
            put(
                ServerMessage.TradeUpdate::class,
                typedHandler { msg: ServerMessage.TradeUpdate ->
                    jsTradeUpdate(Json.encodeToString(msg))
                })
            put(
                ServerMessage.TradeClosed::class,
                typedHandler { msg: ServerMessage.TradeClosed ->
                    jsTradeClosed(msg.tradeId, msg.reason)
                })

            // Character / combat / status
            put(
                ServerMessage.CharacterCreationRequired::class,
                ServerMessageHandler { jsShowCharacterCreation() })
            put(
                ServerMessage.CharacterSync::class,
                typedHandler { msg: ServerMessage.CharacterSync ->
                    jsCharacterSync(Json.encodeToString(msg))
                })
            put(
                ServerMessage.HealthUpdate::class,
                typedHandler { msg: ServerMessage.HealthUpdate ->
                    jsHealthUpdate(Json.encodeToString(msg))
                })
            put(
                ServerMessage.PlayerStatusUpdate::class,
                typedHandler { msg: ServerMessage.PlayerStatusUpdate ->
                    jsPlayerStatusUpdate(Json.encodeToString(msg))
                })
            put(
                ServerMessage.StatusEffectUpdate::class,
                typedHandler { msg: ServerMessage.StatusEffectUpdate ->
                    jsStatusEffectUpdate(Json.encodeToString(msg))
                })
            put(
                ServerMessage.PlayerDowned::class,
                typedHandler { msg: ServerMessage.PlayerDowned -> jsPlayerDowned(msg.playerId) })
            put(
                ServerMessage.PlayerRespawned::class,
                typedHandler { msg: ServerMessage.PlayerRespawned ->
                    jsPlayerRespawned(Json.encodeToString(msg))
                })
            put(
                ServerMessage.XpGained::class,
                typedHandler { msg: ServerMessage.XpGained ->
                    jsXpGained(Json.encodeToString(msg))
                })
            put(
                ServerMessage.WeatherUpdate::class,
                typedHandler { msg: ServerMessage.WeatherUpdate ->
                    jsSetWeatherZones(Json.encodeToString(msg.zones))
                })
            put(
                ServerMessage.PreferencesSync::class,
                typedHandler { msg: ServerMessage.PreferencesSync ->
                    jsCameraSetFov(camera, msg.fieldOfView)
                    uiState.setPreferencesSync(
                        Json.encodeToString<ServerMessage.PreferencesSync>(msg))
                })
        }
}
