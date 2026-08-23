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
import org.micoli.micraft.game.world.BlockEntity
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColor
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.rail.RailConnectionPoint
import org.micoli.micraft.game.world.rail.RailDefinition
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.micoli.micraft.ui.LayoutSyncPayload
import org.micoli.micraft.ui.McUiState

private const val SKY_R = 0.53
private const val SKY_G = 0.81
private const val SKY_B = 0.98

// Compiled-in client defaults, restored when a graphics-preference override is cleared.
private const val DEFAULT_VIEW_RADIUS = 3
private const val DEFAULT_FORWARD_VIEW_RADIUS = 7
private const val DEFAULT_USE_IMPOSTOR = true
private const val DEFAULT_IMPOSTOR_RADIUS_CHUNKS = 5
private const val DEFAULT_IMPOSTOR_FOV_BONUS_CHUNKS = 2

class GameClient
@OptIn(ExperimentalWasmJsInterop::class)
constructor(private val scene: JsAny, private val camera: JsAny, private val uiState: McUiState) {
    private val outMessages = Channel<ClientMessage>(Channel.BUFFERED)
    private val networkStats = NetworkStats()
    private val chunkManager = ChunkManager(scene)
    private val remotePlayerManager = RemotePlayerManager(scene)
    private val npcManager = NpcManager(scene) { localPlayerId }
    private val vehicleManager = VehicleManager(scene)

    init {
        npcManager.registerExternalTargets(
            { vehicleManager.positionsMap() }, { vehicleManager.modelsMap() })
    }

    private var currentPlayerName = ""
    private var nextIntentSeq = 0L
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
            isVehicleTarget = { id -> id in vehicleManager.modelsMap() },
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
        jsInitPlayerModel("articulated")
        jsInitSkinConfig("articulated")
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
                npcManager.playerX = localController.predX
                npcManager.playerZ = localController.predZ
                npcManager.playerYaw = currentYaw.toDouble()
                npcManager.tick()
                vehicleManager.tick()
                remotePlayerManager.tick()
            }
        }

        scope.launch {
            while (isActive) {
                delay(16)
                if (localController.hasPrediction) {
                    // Skip meshing until the real server position lands — otherwise chunks near
                    // spawn get judged against the 0,0 placeholder and wrongly meshed as impostors.
                    chunkManager.drainPendingChunks(
                        playerCx = currentPlayerCx,
                        playerCz = currentPlayerCz,
                        yaw = currentYaw.toDouble(),
                        budgetMs = if (isInitialLoading) 4.0 else 2.0,
                    )
                }
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
                        send(Frame.Text(token.ifEmpty { pid }))
                        for (frame in incoming) {
                            if (frame !is Frame.Binary) continue
                            val data = frame.readBytes()
                            networkStats.bytesIn += data.size
                            val msg =
                                runCatching { ServerMessageCodec.decode(data) }.getOrNull()
                                    ?: continue
                            if (msg is ServerMessage.ChunkData) {
                                chunkManager.enqueueChunk(
                                    Chunk.decodeWire(
                                        msg.pos,
                                        msg.topY,
                                        msg.wireBlocks,
                                        msg.wireStates.takeIf { it.isNotEmpty() },
                                        msg.wireExtraStates.takeIf { it.isNotEmpty() }),
                                    msg.topY)
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
                var lastCloseCode: Short? = null
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
                                    val seq = ++nextIntentSeq
                                    val sentIntent = intent.copy(seq = seq)
                                    localController.recordSentIntent(seq)
                                    val intentBytes = ClientMessageCodec.encode(sentIntent)
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
                                runCatching {
                                        val bytes = ClientMessageCodec.encode(msg)
                                        send(Frame.Binary(true, bytes))
                                        networkStats.bytesOut += bytes.size
                                    }
                                    .onFailure { e ->
                                        jsError(
                                            "breakJob send error [${msg::class.simpleName}]: ${e::class.simpleName}: ${e.message}")
                                    }
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
                        val reason = closeReason.await()
                        lastCloseCode = reason?.code
                        jsLog(
                            "WS incoming loop ended after $frameCount frames (closeReason=$reason)")
                        inputJob.cancel()
                        breakJob.cancel()
                    }
                    jsLog("WS session closed normally")
                } catch (e: Throwable) {
                    jsError("WS error: ${e::class.simpleName}: ${e.message}")
                }

                if (!isActive) break
                resetForReconnect()
                val authRejected = lastCloseCode == CloseReason.Codes.VIOLATED_POLICY.code
                if (sessionWelcomed || authRejected) {
                    retryDelay = 1000L
                    if (authRejected) {
                        jsLog("WS auth rejected (1008) — clearing token, returning to login")
                        jsClearStoredToken()
                        currentToken = ""
                    } else {
                        jsLog("WS disconnected after session — returning to login")
                    }
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
                    jsSetServerBuildTimestamp(msg.buildTimestamp)
                    jsFetchI18n(msg.language)
                    jsFetchBiomeColors()
                    chunkManager.setShadersEnabled(msg.shadersEnabled)
                    jsSyncLayouts(
                        Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
                    localController.setViewMode(msg.viewMode)
                    localController.setReconcileTolerances(
                        msg.reconcileToleranceXz, msg.reconcileToleranceY)
                    localController.maxInteractionDistance = msg.maxInteractionDistance.toFloat()
                })
            put(ServerMessage.ItemsSpawned::class, ServerMessageHandler {})
            put(ServerMessage.ItemDespawned::class, ServerMessageHandler {})

            // Chunk
            put(
                ServerMessage.ChunkData::class,
                typedHandler { msg: ServerMessage.ChunkData ->
                    chunkManager.enqueueChunk(
                        Chunk.decodeWire(
                            msg.pos,
                            msg.topY,
                            msg.wireBlocks,
                            msg.wireStates.takeIf { it.isNotEmpty() },
                            msg.wireExtraStates.takeIf { it.isNotEmpty() },
                            msg.entities),
                        msg.topY)
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
                ServerMessage.GodModeUpdate::class,
                typedHandler { msg: ServerMessage.GodModeUpdate -> jsGodModeUpdate(msg.enabled) })
            put(
                ServerMessage.EditModeUpdate::class,
                typedHandler { msg: ServerMessage.EditModeUpdate ->
                    jsEditModeUpdate(msg.mode.name.lowercase())
                })
            put(
                ServerMessage.WalletUpdate::class,
                typedHandler { msg: ServerMessage.WalletUpdate -> jsWalletUpdate(msg.copper) })
            put(
                ServerMessage.WorldUpdate::class,
                typedHandler { msg: ServerMessage.WorldUpdate ->
                    // Collect affected chunk positions for re-enqueue after applying all changes
                    val affectedChunks = mutableMapOf<ChunkPos, Pair<Chunk, Int>>()

                    msg.changes.forEach { change ->
                        val cx = change.pos.x.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cz = change.pos.z.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cp = ChunkPos(cx, cz)
                        val (existing, existingTopY) =
                            affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
                        val lx = change.pos.x - cx * WorldConstants.CHUNK_SIZE
                        val lz = change.pos.z - cz * WorldConstants.CHUNK_SIZE
                        val updated =
                            existing.withBlock(
                                lx, change.pos.y, lz, change.type, change.state, change.extraState)
                        val newTopY =
                            if (change.type != BlockType.AIR) maxOf(existingTopY, change.pos.y)
                            else existingTopY
                        affectedChunks[cp] = Pair(updated, newTopY)
                        if (change.type == BlockType.AIR) localController.onBlockBroken(change.pos)
                    }

                    msg.entityAdds.forEach { proto ->
                        val cx = proto.worldX.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cz = proto.worldZ.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cp = ChunkPos(cx, cz)
                        val (existing, topY) =
                            affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
                        val localX = proto.worldX - cx * WorldConstants.CHUNK_SIZE
                        val localZ = proto.worldZ - cz * WorldConstants.CHUNK_SIZE
                        val masterIdx = Chunk.index(localX, proto.worldY, localZ)
                        val entity =
                            BlockEntity(
                                masterIdx = masterIdx,
                                type = BlockType(proto.type),
                                sizeX = proto.sizeX,
                                sizeY = proto.sizeY,
                                sizeZ = proto.sizeZ,
                                rotation = proto.rotation,
                                yOffset = proto.yOffset,
                                xOffset = proto.xOffset,
                                zOffset = proto.zOffset,
                                colorIndex = proto.colorIndex,
                            )
                        affectedChunks[cp] = Pair(existing.addEntity(entity), topY)
                    }

                    msg.entityRemoves.forEach { masterWorldPos ->
                        val cx = masterWorldPos.x.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cz = masterWorldPos.z.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cp = ChunkPos(cx, cz)
                        val (existing, topY) =
                            affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
                        val localX = masterWorldPos.x - cx * WorldConstants.CHUNK_SIZE
                        val localZ = masterWorldPos.z - cz * WorldConstants.CHUNK_SIZE
                        val masterIdx = Chunk.index(localX, masterWorldPos.y, localZ)
                        affectedChunks[cp] = Pair(existing.removeEntity(masterIdx), topY)
                    }

                    msg.entityRemovesAt.forEach { spec ->
                        val cx = spec.pos.x.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cz = spec.pos.z.floorDiv(WorldConstants.CHUNK_SIZE)
                        val cp = ChunkPos(cx, cz)
                        val (existing, topY) =
                            affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
                        val localX = spec.pos.x - cx * WorldConstants.CHUNK_SIZE
                        val localZ = spec.pos.z - cz * WorldConstants.CHUNK_SIZE
                        val masterIdx = Chunk.index(localX, spec.pos.y, localZ)
                        affectedChunks[cp] =
                            Pair(
                                existing.removeEntityAt(
                                    masterIdx, spec.yOffset, spec.xOffset, spec.zOffset),
                                topY)
                    }

                    affectedChunks.forEach { (_, pair) ->
                        chunkManager.updateAndEnqueue(pair.first, pair.second)
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
                        localController.updateFromServer(s, msg.lastProcessedSeq) { cx, cz ->
                            chunkManager.unloadDistantChunks(cx, cz)
                            chunkManager.reevaluateImpostors(cx, cz, currentYaw.toDouble())
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
                    localController.maxInteractionDistance = msg.maxInteractionDistance.toFloat()
                })
            put(
                ServerMessage.ShortcutBarUpdate::class,
                typedHandler { msg: ServerMessage.ShortcutBarUpdate ->
                    for (page in 0..9) for (i in 0..9) localController.shortcutBarPages[page][i] =
                        null
                    msg.pages.forEach { (page, slots) ->
                        if (page in 0..9)
                            slots.forEach { (i, item) ->
                                if (i in 0..9) localController.shortcutBarPages[page][i] = item
                            }
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

            // Vehicle — single handler object registered for all vehicle message types
            put(ServerMessage.VehicleSpawned::class, vehicleManager)
            put(ServerMessage.VehicleUpdate::class, vehicleManager)
            put(ServerMessage.VehicleDespawned::class, vehicleManager)

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
            put(ServerMessage.ToggleIngameMap::class, ServerMessageHandler { jsToggleIngameMap() })
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
                                        topColor = info.topColor,
                                        sideColor = info.sideColor,
                                        modelElement = info.modelElement,
                                        gltfModel = info.gltfModel,
                                        liquid = info.liquid,
                                        viscosity = info.viscosity,
                                        minimapVisible = info.minimapVisible,
                                        rotatable = info.rotatable,
                                        hasStuds = info.hasStuds,
                                        brickSize = info.brickSize,
                                        plainColorable = info.plainColorable,
                                        isCubic = info.isCubic,
                                        rail =
                                            info.rail?.let { rail ->
                                                RailDefinition(
                                                    connections =
                                                        rail.connections.map { group ->
                                                            group.map {
                                                                RailConnectionPoint.parse(it)
                                                            }
                                                        },
                                                    height = rail.height,
                                                )
                                            },
                                    )
                            }
                            .toMap()
                    PlainColorRegistry.load(
                        msg.plainColors.mapNotNull { PlainColor.fromHex(it.name, it.hex) })
                    BlockRegistry.load(blockDefs, msg.blocks.map { BlockType(it.name) })
                    chunkManager.repushAllToMinimap()
                    val itemDefs =
                        msg.items.entries.associate { (key, info) ->
                            ItemType(key) to
                                ItemDefinition(
                                    buildable = info.buildable,
                                    placesBlock =
                                        info.placesBlock?.let {
                                            runCatching { BlockType(it) }.getOrNull()
                                        },
                                    plainColor = info.plainColor,
                                    consumable = info.consumable,
                                )
                        }
                    ItemRegistry.load(itemDefs)
                    WorldConstants.IMPOSTOR_SKIRT_DEPTH = msg.impostorSkirtDepth
                    jsSetImpostorSkirtDepth(msg.impostorSkirtDepth)
                    jsSetPlainColors(Json.encodeToString(msg.plainColors))
                    jsSetBlockRegistry(Json.encodeToString(msg.blocks))
                    jsSetItemRegistry(Json.encodeToString(msg.items))
                    if (msg.npcs.isNotEmpty()) jsInitNpcModels(Json.encodeToString(msg.npcs))
                    if (msg.npcWalkBones.isNotEmpty())
                        jsInitNpcWalkBones(Json.encodeToString(msg.npcWalkBones))
                    if (msg.npcDefinitions.isNotEmpty())
                        jsSetNpcDefinitions(Json.encodeToString(msg.npcDefinitions))
                    if (msg.vehicles.isNotEmpty())
                        jsInitVehicleModels(Json.encodeToString(msg.vehicles))
                    if (msg.vehicleDefinitions.isNotEmpty())
                        jsSetVehicleDefinitions(Json.encodeToString(msg.vehicleDefinitions))
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
                ServerMessage.QuestSync::class,
                typedHandler { msg: ServerMessage.QuestSync ->
                    jsQuestSync(Json.encodeToString(msg))
                })
            put(
                ServerMessage.QuestUpdate::class,
                typedHandler { msg: ServerMessage.QuestUpdate ->
                    jsQuestUpdate(Json.encodeToString(msg))
                })
            put(
                ServerMessage.OpenQuestJournal::class,
                typedHandler { _: ServerMessage.OpenQuestJournal -> jsOpenQuestJournal() })
            put(
                ServerMessage.AoEEffect::class,
                typedHandler { msg: ServerMessage.AoEEffect ->
                    jsAoEEffect(scene, msg.x, msg.y, msg.z, msg.radius)
                })
            put(
                ServerMessage.WeatherUpdate::class,
                typedHandler { msg: ServerMessage.WeatherUpdate ->
                    jsSetWeatherZones(Json.encodeToString(msg.zones))
                })
            put(
                ServerMessage.MailSync::class,
                typedHandler { msg: ServerMessage.MailSync ->
                    jsMailSync(Json.encodeToString(msg))
                })
            put(
                ServerMessage.MailReceived::class,
                typedHandler { msg: ServerMessage.MailReceived ->
                    jsMailReceived(Json.encodeToString(msg))
                })
            put(
                ServerMessage.MailUpdate::class,
                typedHandler { msg: ServerMessage.MailUpdate ->
                    jsMailUpdate(Json.encodeToString(msg))
                })
            put(
                ServerMessage.MailDeleted::class,
                typedHandler { msg: ServerMessage.MailDeleted -> jsMailDeleted(msg.mailId) })
            put(
                ServerMessage.OpenMailbox::class,
                typedHandler { _: ServerMessage.OpenMailbox -> jsOpenMailbox() })
            put(
                ServerMessage.AdminZoneWireframe::class,
                typedHandler { msg: ServerMessage.AdminZoneWireframe ->
                    jsAdminZoneWireframe(Json.encodeToString(msg))
                })
            put(
                ServerMessage.InstanceZonesSync::class,
                typedHandler { msg: ServerMessage.InstanceZonesSync ->
                    jsInstanceZonesSync(Json.encodeToString(msg))
                })
            put(
                ServerMessage.ScenesSync::class,
                typedHandler { msg: ServerMessage.ScenesSync ->
                    jsScenesSync(Json.encodeToString(msg))
                })
            put(
                ServerMessage.ScenePreviewData::class,
                typedHandler { msg: ServerMessage.ScenePreviewData ->
                    jsScenePreviewData(Json.encodeToString(msg))
                })
            put(
                ServerMessage.PreferencesSync::class,
                typedHandler { msg: ServerMessage.PreferencesSync ->
                    jsCameraSetFov(camera, msg.fieldOfView)
                    localController.autoTargetEnabled = msg.autoTargetEnabled
                    localController.continuousBreak = msg.continuousBreak
                    jsSetContinuousBreak(msg.continuousBreak)
                    jsSetShadowAngleDeg(msg.shadowAngleDeg)
                    WorldConstants.VIEW_RADIUS = msg.overrideViewRadius ?: DEFAULT_VIEW_RADIUS
                    WorldConstants.FORWARD_VIEW_RADIUS =
                        msg.overrideForwardViewRadius ?: DEFAULT_FORWARD_VIEW_RADIUS
                    chunkManager.useImpostor = msg.overrideUseImpostor ?: DEFAULT_USE_IMPOSTOR
                    chunkManager.impostorRadiusChunks =
                        msg.overrideImpostorRadiusChunks ?: DEFAULT_IMPOSTOR_RADIUS_CHUNKS
                    chunkManager.impostorFovBonusChunks =
                        msg.overrideImpostorFovBonusChunks ?: DEFAULT_IMPOSTOR_FOV_BONUS_CHUNKS
                    // Apply the new radii/impostor settings right away instead of waiting for
                    // the player to cross a chunk boundary (see onChunkChanged above).
                    chunkManager.unloadDistantChunks(currentPlayerCx, currentPlayerCz)
                    chunkManager.reevaluateImpostors(
                        currentPlayerCx, currentPlayerCz, currentYaw.toDouble())
                    httpChunkFetcher?.trigger(currentPlayerCx, currentPlayerCz, currentYaw)
                    uiState.setPreferencesSync(
                        Json.encodeToString<ServerMessage.PreferencesSync>(msg))
                })
        }
}
