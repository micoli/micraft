package org.micoli.micraft.game

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.ChunkManager
import org.micoli.micraft.HttpChunkFetcher
import org.micoli.micraft.LocalPlayerController
import org.micoli.micraft.RemotePlayerManager
import org.micoli.micraft.babylon.*
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.gameChunkManager
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.CHUNK_HANDSHAKE_SEPARATOR
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.PROTOCOL_FINGERPRINT
import org.micoli.micraft.protocol.PROTOCOL_MISMATCH_CLOSE_CODE
import org.micoli.micraft.protocol.SUPERSEDED_CONNECTION_CLOSE_CODE
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.micoli.micraft.ui.LayoutSyncPayload
import org.micoli.micraft.ui.McUiState

private const val SKY_R = 0.53
private const val SKY_G = 0.81
private const val SKY_B = 0.98

// Minimum camera yaw delta (radians, ~3°) before impostor promotion/demotion is re-evaluated
// off a look-direction change alone — keeps reevaluateImpostors() off the hot per-frame path
// while still tracking mouse-look rotation, not just chunk-boundary crossings.
private const val IMPOSTOR_YAW_REEVAL_THRESHOLD = 0.05

// After a long backgrounded tab, Firefox keeps buffering incoming WS frames at the
// network layer while throttling JS timers — on foreground, this yields the JS thread
// every ~8ms instead of draining a huge backlog synchronously in one blocking burst.
private const val INCOMING_FRAME_BUDGET_MS = 8.0

class GameClient
@OptIn(ExperimentalWasmJsInterop::class)
constructor(private val scene: JsAny, private val camera: JsAny, private val uiState: McUiState) {
    private val outMessages = Channel<ClientMessage>(Channel.BUFFERED)
    private val networkStats = NetworkStats()
    private val chunkManager = ChunkManager(scene)
    private val remotePlayerManager = RemotePlayerManager(scene)
    private val npcManager = NpcManager(scene) { localPlayerId }
    private val actionBlockManager = ActionBlockManager(scene)
    private val vehicleManager = VehicleManager(scene)
    private val placeableManager = PlaceableManager(scene)
    private val panelManager = PanelManager()
    private val siegeWeaponManager = SiegeWeaponManager()
    private val siegeProjectileManager = SiegeProjectileManager(scene)
    private val registrySyncHandler = RegistrySyncHandler(chunkManager)

    /** No-op for a non-panel placeable — the DOM layer only tracks transforms it was told about. */
    private fun pushPanelTransform(placeableId: String) {
        if (!panelManager.isPanel(placeableId)) return
        val pos = placeableManager.getPosition(placeableId) ?: return
        val rotationStep = placeableManager.getRotationStep(placeableId) ?: 0
        jsSetPanelTransform(
            placeableId, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), rotationStep)
    }

    private fun siegeWeaponMuzzleAndVelocity(placeableId: String): Pair<Vec3, Vec3>? {
        val type = placeableManager.getType(placeableId) ?: return null
        val def = registrySyncHandler.siegeWeaponDefs[type] ?: return null
        val pos = placeableManager.getPosition(placeableId) ?: return null
        val rotationStep = placeableManager.getRotationStep(placeableId) ?: 0
        val weapon = siegeWeaponManager.getByPlaceableId(placeableId) ?: return null
        return SiegeTrajectoryMath.computeMuzzleAndVelocity(
            placeablePos = pos,
            rotationStep = rotationStep,
            muzzleOffset = def.muzzleOffset,
            launchPitchDeg = def.launchPitchDeg,
            pitchStep = weapon.pitchStep,
            launchPower = def.launchPower,
            powerStep = weapon.powerStep,
        )
    }

    init {
        gameChunkManager = chunkManager
        jsInitPanelSurface(scene, camera)
        npcManager.registerExternalTargets(
            { vehicleManager.positionsMap() + placeableManager.positionsMap() },
            { vehicleManager.modelsMap() + placeableManager.modelsMap() })
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
            actionBlockManager = actionBlockManager,
            isVehicleTarget = { id -> id in vehicleManager.modelsMap() },
            vehiclePositionOf = { id -> vehicleManager.positionsMap()[id] },
            isPlaceableTarget = { id -> id in placeableManager.modelsMap() },
            isPanelTarget = panelManager::isPanel,
            siegeWeaponMuzzleAndVelocityOf = ::siegeWeaponMuzzleAndVelocity,
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
    private var refreshToken = ""
    private val e2eSession: String = if (jsE2eEnabled()) jsE2eSessionId() else ""
    // Random id generated once per client instance (tab/window) — sent in every Connect so the
    // server can tell a genuine reconnect apart from a second tab racing for the same player id.
    private val connectionId: String =
        List(4) { Random.nextInt(0x10000000, Int.MAX_VALUE).toString(16) }.joinToString("")
    private val needsWorld: Boolean = if (jsE2eEnabled()) jsE2eNeedsWorld() else true
    /** Rolling window of `ServerMessage.Notification` texts, mirrored into the e2e snapshot. */
    private val e2eNotifications = ArrayDeque<String>()

    private val chunkWorldHandler = ChunkWorldHandler(chunkManager, localController, e2eSession)
    private val playerStateHandler =
        PlayerStateHandler(
            localController = localController,
            remotePlayerManager = remotePlayerManager,
            chunkManager = chunkManager,
            localPlayerId = { localPlayerId },
            httpChunkFetcher = { httpChunkFetcher },
            onPlayerPositionChanged = { cx, cz, yaw ->
                currentPlayerCx = cx
                currentPlayerCz = cz
                currentYaw = yaw
            },
        )
    private val npcDialogHandler = NpcDialogHandler()
    private val chatNotificationHandler =
        ChatNotificationHandler(scene, uiState) { text ->
            if (e2eSession.isNotEmpty()) {
                e2eNotifications.addLast(text)
                while (e2eNotifications.size > 50) e2eNotifications.removeFirst()
            }
        }
    private val panelUiHandler = PanelUiHandler()
    private val tradeHandler = TradeHandler()
    private val characterStatusHandler = CharacterStatusHandler(scene)
    private val mailHandler = MailHandler()
    private val socialAdminHandler = SocialAdminHandler()
    private val preferencesHandler =
        PreferencesHandler(
            camera = camera,
            chunkManager = chunkManager,
            localController = localController,
            uiState = uiState,
            httpChunkFetcher = { httpChunkFetcher },
            currentPlayerPos = { Triple(currentPlayerCx, currentPlayerCz, currentYaw) },
        )
    private val petRosterHandler = PetRosterHandler()

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun applyE2eLook() {
        val look = jsE2eConsumeLook()
        if (look.isEmpty()) return
        val parts = look.split(",")
        val yaw = parts.getOrNull(0)?.toDoubleOrNull() ?: return
        val pitch = parts.getOrNull(1)?.toDoubleOrNull() ?: return
        jsSetCameraRotationY(camera, yaw)
        jsSetCameraRotationX(camera, pitch)
        currentYaw = yaw.toFloat()
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun emitE2eSnapshot() {
        val lc = localController
        val ready = playerIdReady.isCompleted && chunkManager.loadedChunks.isNotEmpty()
        val chunks =
            chunkManager.loadedChunks.joinToString(prefix = "[", postfix = "]") {
                """{"cx":${it.cx},"cz":${it.cz}}"""
            }
        val tb = lc.e2eTarget
        val target = if (tb == null) "null" else """{"x":${tb.x},"y":${tb.y},"z":${tb.z}}"""
        val json =
            """{"ready":$ready,"playerId":"${localPlayerId ?: ""}","playerName":"$currentPlayerName",""" +
                """"position":{"x":${lc.predX},"y":${lc.predY},"z":${lc.predZ}},""" +
                """"serverPosition":{"x":${lc.serverX},"y":${lc.serverY},"z":${lc.serverZ}},""" +
                """"yaw":${currentYaw.toDouble()},"pitch":${jsGetCameraRotationX(camera)},""" +
                """"stance":"${lc.localStance.name.lowercase()}","hasPrediction":${lc.hasPrediction},""" +
                """"reconcile":{"xz":${lc.e2eReconcileXz},"y":${lc.e2eReconcileY}},""" +
                """"loadedChunks":$chunks,"targetBlock":$target,""" +
                """"remotePlayers":${remotePlayerManager.e2ePlayersJson()},""" +
                """"notifications":${Json.encodeToString(e2eNotifications.toList())},""" +
                """"actionBlocks":${actionBlockManager.e2eJson()},""" +
                """"actionBlockTarget":${
                    actionBlockManager.currentTarget()?.let { """{"x":${it.x},"y":${it.y},"z":${it.z}}""" } ?: "null"
                },""" +
                """"lastWorldUpdate":${chunkWorldHandler.lastWorldUpdateJson},""" +
                """"panelFocusedId":${jsPanelFocusedId()?.let { "\"$it\"" } ?: "null"}}"""
        jsUpdateE2E(json)
    }

    private var chunkTransportMode = "websocket"
    private var httpChunkFetcher: HttpChunkFetcher? = null
    private var currentPlayerCx = 0
    private var currentPlayerCz = 0
    private var currentYaw = 0f
    private var lastImpostorReevalYaw = 0f
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

    /**
     * Exchanges [refreshToken] for a fresh access + refresh token pair via `POST /auth/refresh`.
     * Returns null on any failure (network error, expired/unknown refresh token) — the caller falls
     * back to a full re-login in that case.
     */
    /**
     * False when the server reports another PROTOCOL_FINGERPRINT: this client was built from a
     * different protocol and would misread every message (ADR-0003). Unknown (server unreachable,
     * old server) counts as a match — the connection attempt then fails or succeeds on its own.
     */
    private suspend fun serverProtocolMatches(): Boolean {
        val serverFingerprint =
            runCatching {
                    val body =
                        HttpClient(Js)
                            .get("${pageHttpScheme()}://$serverHost:$serverPort/api/server/info")
                            .bodyAsText()
                    Json.parseToJsonElement(body)
                        .jsonObject["protocolFingerprint"]
                        ?.jsonPrimitive
                        ?.content
                }
                .getOrNull() ?: return true
        return serverFingerprint == PROTOCOL_FINGERPRINT
    }

    private fun onProtocolMismatch() {
        jsLog("Server protocol differs from this client's ($PROTOCOL_FINGERPRINT) — reloading")
        if (!jsReloadForProtocolMismatch()) jsShowLoginOverlay("protocol_mismatch")
    }

    private suspend fun refreshAccessToken(refreshToken: String): Pair<String, String>? {
        if (refreshToken.isEmpty()) return null
        return runCatching {
                val client = HttpClient(Js)
                val response =
                    client.post("${pageHttpScheme()}://$serverHost:$serverPort/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"refreshToken":"$refreshToken"}""")
                    }
                if (!response.status.isSuccess()) return null
                val obj = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val newToken = obj["token"]?.jsonPrimitive?.content ?: return null
                val newRefreshToken = obj["refreshToken"]?.jsonPrimitive?.content ?: return null
                newToken to newRefreshToken
            }
            .onFailure { e ->
                jsError("Token refresh failed: ${e::class.simpleName}: ${e.message}")
            }
            .getOrNull()
    }

    fun connect(
        host: String,
        port: Int,
        username: String,
        playerName: String,
        preferredLanguage: String = "en",
        token: String = "",
        refreshToken: String = "",
    ) {
        serverHost = host
        serverPort = port
        currentPlayerName = playerName
        this.token = token
        this.refreshToken = refreshToken

        // Keeps the stored access token from ever going stale across a network blip or page
        // reload — the WS session itself is never re-validated mid-connection (only at connect
        // time), so this only needs to run comfortably under TokenStore's 600s default TTL.
        scope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                val rt = this@GameClient.refreshToken
                if (rt.isEmpty()) continue
                val refreshed = refreshAccessToken(rt)
                if (refreshed != null) {
                    val (newToken, newRefreshToken) = refreshed
                    this@GameClient.token = newToken
                    this@GameClient.refreshToken = newRefreshToken
                    jsStoreToken(newToken)
                    jsStoreRefreshToken(newRefreshToken)
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(16)
                if (localController.hasPrediction) {
                    localController.chunkDownloading = httpChunkFetcher?.inFlightCount ?: 0
                    localController.chunkMeshing = chunkManager.pendingRenderCount
                    // An uncaught exception here would otherwise kill this while(isActive) loop
                    // for good — no more movement, no more chunk polling, nothing — while the WS
                    // connection stays up, since that's a separate coroutine.
                    runCatching { localController.tick() }
                        .onFailure { e -> jsError("localController.tick() threw: ${e.message}") }
                    // Mouse-look rotation alone never crosses a chunk boundary, so it wouldn't
                    // otherwise trigger reevaluateImpostors() — poll the live camera yaw here so
                    // turning in place still promotes/demotes chunks entering/leaving the FOV
                    // cone bonus (see DEFAULT_IMPOSTOR_FOV_BONUS_CHUNKS).
                    val liveYaw = jsGetCameraRotationY(camera).toFloat()
                    if (abs(liveYaw - lastImpostorReevalYaw) > IMPOSTOR_YAW_REEVAL_THRESHOLD) {
                        lastImpostorReevalYaw = liveYaw
                        chunkManager.reevaluateImpostors(
                            currentPlayerCx, currentPlayerCz, liveYaw.toDouble())
                    }
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
                val otherT0 = jsNow()
                npcManager.playerX = localController.predX
                npcManager.playerZ = localController.predZ
                npcManager.playerYaw = currentYaw.toDouble()
                npcManager.tick()
                vehicleManager.tick()
                placeableManager.tick()
                siegeProjectileManager.tick()
                remotePlayerManager.tick()
                localController.otherTickMs = jsNow() - otherT0
                if (e2eSession.isNotEmpty()) {
                    runCatching { applyE2eLook() }
                    runCatching { emitE2eSnapshot() }
                }
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
                    chunkClient.webSocket(
                        urlString = "${pageWsScheme()}://$host:$port/chunks",
                        request = {
                            if (e2eSession.isNotEmpty())
                                url.parameters.append("gameSession", e2eSession)
                        }) {
                            send(Frame.Text("$token$CHUNK_HANDSHAKE_SEPARATOR$pid"))
                            for (frame in incoming) {
                                if (frame !is Frame.Binary) continue
                                val data = frame.readBytes()
                                networkStats.bytesIn += data.size
                                val decodeT0 = jsPerfNow()
                                val msg =
                                    runCatching { ServerMessageCodec.decode(data) }.getOrNull()
                                        ?: continue
                                networkStats.chunkDecodeMsAccum += jsPerfNow() - decodeT0
                                networkStats.chunkDecodeCount++
                                if (msg is ServerMessage.ChunkData) {
                                    chunkManager.setGrassTints(msg.pos, msg.grassTints)
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
            var currentRefreshToken = refreshToken
            while (isActive) {
                // Pick up whatever the proactive refresh coroutine (or a prior reactive refresh
                // below) last landed, in case this attempt starts after either one ran.
                currentToken = this@GameClient.token
                currentRefreshToken = this@GameClient.refreshToken
                var sessionWelcomed = false
                var lastCloseCode: Short? = null
                if (!serverProtocolMatches()) {
                    onProtocolMismatch()
                    break
                }
                try {
                    uiState.disconnectMessage = null
                    val gameUrl = "${pageWsScheme()}://$serverHost:$serverPort/game"
                    jsLog("WS connecting to $gameUrl")
                    val client =
                        HttpClient(Js) { install(WebSockets) { pingInterval = 15.seconds } }
                    client.webSocket(
                        urlString = gameUrl,
                        request = {
                            if (e2eSession.isNotEmpty())
                                url.parameters.append("gameSession", e2eSession)
                        }) {
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
                                            token = currentToken,
                                            needsWorld = needsWorld,
                                            connectionId = connectionId))))

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
                            var batchDeadline = jsNow() + INCOMING_FRAME_BUDGET_MS
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
                                if (jsNow() >= batchDeadline) {
                                    yield()
                                    batchDeadline = jsNow() + INCOMING_FRAME_BUDGET_MS
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
                if (lastCloseCode == PROTOCOL_MISMATCH_CLOSE_CODE) {
                    onProtocolMismatch()
                    break
                }
                val superseded = lastCloseCode == SUPERSEDED_CONNECTION_CLOSE_CODE
                if (superseded) {
                    // Another tab/session took over this player — do NOT auto-reconnect: racing
                    // the newer connection for the same player id is what caused the reconnect
                    // ping-pong loop. Stay disconnected until the user acts.
                    jsLog("WS superseded by newer connection — returning to character select")
                    jsShowLoginOverlay("superseded")
                    break
                }
                var authRejected = lastCloseCode == CloseReason.Codes.VIOLATED_POLICY.code
                if (authRejected && currentRefreshToken.isNotEmpty()) {
                    jsLog("WS auth rejected (1008) — trying refresh token before forcing re-login")
                    val refreshed = refreshAccessToken(currentRefreshToken)
                    if (refreshed != null) {
                        val (newToken, newRefreshToken) = refreshed
                        currentToken = newToken
                        currentRefreshToken = newRefreshToken
                        this@GameClient.token = newToken
                        this@GameClient.refreshToken = newRefreshToken
                        jsStoreToken(newToken)
                        jsStoreRefreshToken(newRefreshToken)
                        authRejected = false
                        retryDelay = 1000L
                        jsLog("Token refreshed — retrying connection silently")
                        delay(retryDelay)
                        continue
                    }
                    jsLog("Refresh token invalid/expired — falling back to full re-login")
                }
                if (sessionWelcomed || authRejected) {
                    retryDelay = 1000L
                    if (authRejected) {
                        jsLog("WS auth rejected (1008) — clearing token, returning to login")
                        jsClearStoredToken()
                        jsClearStoredRefreshToken()
                        currentToken = ""
                        currentRefreshToken = ""
                        this@GameClient.token = ""
                        this@GameClient.refreshToken = ""
                        // A non-empty reason skips showLoginOverlay's silent-reconnect fast path in
                        // GameUI.tsx, which would otherwise immediately retry with the (now empty)
                        // stored token and loop forever without ever showing a login screen.
                        jsShowLoginOverlay("auth")
                    } else {
                        jsLog("WS disconnected after session — returning to login")
                        jsShowLoginOverlay()
                    }
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
                    currentRefreshToken = if (parts.size > 4) parts[4] else ""
                    currentPlayerName = currentPlayerNameLocal
                    this@GameClient.token = currentToken
                    this@GameClient.refreshToken = currentRefreshToken
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
        actionBlockManager.clear()
    }

    private fun handleMessage(msg: ServerMessage) {
        dispatchMap[msg::class]?.handle(msg)
    }

    private fun handleWelcome(msg: ServerMessage.Welcome) {
        isInitialLoading = needsWorld
        uiState.chunkLoadingProgress = if (needsWorld) Triple(0, 0, expectedChunkCount) else null
        localPlayerId = msg.playerId
        chunkTransportMode = msg.chunkTransport
        if (msg.chunkTransport == "http") {
            httpChunkFetcher =
                HttpChunkFetcher(chunkManager = chunkManager, token = token, scope = scope)
            scope.launch {
                while (isActive) {
                    delay(2000)
                    httpChunkFetcher?.trigger(currentPlayerCx, currentPlayerCz, currentYaw)
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
        jsSyncLayouts(Json.encodeToString(LayoutSyncPayload(msg.layouts, msg.activeLayout)))
        localController.setViewMode(msg.viewMode)
        localController.setReconcileTolerances(msg.reconcileToleranceXz, msg.reconcileToleranceY)
        localController.maxInteractionDistance = msg.maxInteractionDistance.toFloat()
        localController.kinematicTuning = msg.kinematics
    }

    private fun buildDispatchMap(): Map<KClass<out ServerMessage>, ServerMessageHandler> =
        buildMap {
            // Session / init
            put(ServerMessage.Welcome::class, typedHandler(::handleWelcome))
            put(ServerMessage.ItemsSpawned::class, ServerMessageHandler {})
            put(ServerMessage.ItemDespawned::class, ServerMessageHandler {})

            // Chunk / world
            put(ServerMessage.ChunkData::class, chunkWorldHandler)
            put(ServerMessage.ShadersUpdate::class, chunkWorldHandler)
            put(ServerMessage.LightBoostUpdate::class, chunkWorldHandler)
            put(ServerMessage.GodModeUpdate::class, chunkWorldHandler)
            put(ServerMessage.MountUpdate::class, playerStateHandler)
            put(ServerMessage.EditModeUpdate::class, chunkWorldHandler)
            put(ServerMessage.WalletUpdate::class, chunkWorldHandler)
            put(ServerMessage.WorldUpdate::class, chunkWorldHandler)

            // Player
            put(ServerMessage.PlayerUpdate::class, playerStateHandler)
            put(ServerMessage.PlayerLeft::class, playerStateHandler)
            put(ServerMessage.GameConfigSync::class, playerStateHandler)
            put(ServerMessage.ShortcutBarUpdate::class, playerStateHandler)
            put(ServerMessage.TimeUpdate::class, playerStateHandler)
            put(ServerMessage.CombatTargetUpdate::class, playerStateHandler)

            // NPC — single handler object registered for all NPC message types
            put(ServerMessage.NpcSpawned::class, npcManager)
            put(ServerMessage.NpcUpdate::class, npcManager)
            put(ServerMessage.NpcDespawned::class, npcManager)
            put(ServerMessage.NpcInteractResult::class, npcManager)
            put(ServerMessage.QuestGiverDialog::class, npcDialogHandler)
            put(ServerMessage.NpcChatReply::class, npcDialogHandler)

            // Vehicle — single handler object registered for all vehicle message types
            put(ServerMessage.VehicleSpawned::class, vehicleManager)
            put(ServerMessage.VehicleUpdate::class, vehicleManager)
            put(ServerMessage.VehicleDespawned::class, vehicleManager)

            // Placeable — single handler object registered for all placeable message types.
            // Spawned/Update also push a panel transform when the placeable is a panel (a panel's
            // DOM layer needs its own position/rotation, tracked separately from the mesh).
            put(
                ServerMessage.PlaceableSpawned::class,
                typedHandler { msg: ServerMessage.PlaceableSpawned ->
                    placeableManager.handleSpawned(msg.state)
                    pushPanelTransform(msg.state.id)
                })
            put(
                ServerMessage.PlaceableUpdate::class,
                typedHandler { msg: ServerMessage.PlaceableUpdate ->
                    placeableManager.handleUpdate(msg.state)
                    pushPanelTransform(msg.state.id)
                })
            put(ServerMessage.PlaceableDespawned::class, placeableManager)
            // Panel — content (this map) always arrives after the placeable's own spawn/update, so
            // each handler also (re)pushes that placeable's transform once panelManager knows it.
            put(
                ServerMessage.PanelSync::class,
                typedHandler { msg: ServerMessage.PanelSync ->
                    panelManager.handle(msg)
                    msg.panels.forEach { pushPanelTransform(it.placeableId) }
                })
            put(
                ServerMessage.PanelChanged::class,
                typedHandler { msg: ServerMessage.PanelChanged ->
                    panelManager.handle(msg)
                    pushPanelTransform(msg.info.placeableId)
                })
            put(ServerMessage.PanelRemoved::class, panelManager)
            put(ServerMessage.PanelEditOpen::class, panelManager)
            put(ServerMessage.SiegeWeaponUpdate::class, siegeWeaponManager)
            put(ServerMessage.SiegeProjectileSpawned::class, siegeProjectileManager)
            put(ServerMessage.SiegeProjectileUpdate::class, siegeProjectileManager)
            put(ServerMessage.SiegeProjectileImpact::class, siegeProjectileManager)

            // UI / notifications
            put(ServerMessage.Notification::class, chatNotificationHandler)
            put(ServerMessage.ChatMessage::class, chatNotificationHandler)
            put(ServerMessage.ChannelsSync::class, chatNotificationHandler)
            put(ServerMessage.BlockBreakProgress::class, chatNotificationHandler)
            put(ServerMessage.InventoryUpdate::class, chatNotificationHandler)

            // Layouts / UI panels
            put(ServerMessage.LayoutsSync::class, panelUiHandler)
            put(ServerMessage.OpenLayoutEditor::class, panelUiHandler)
            put(ServerMessage.OpenPreferences::class, panelUiHandler)
            put(ServerMessage.OpenCodex::class, panelUiHandler)
            put(ServerMessage.OpenCraft::class, panelUiHandler)
            put(ServerMessage.RecipeSync::class, panelUiHandler)
            put(ServerMessage.ToggleIngameMap::class, panelUiHandler)
            put(ServerMessage.RegistrySync::class, registrySyncHandler)

            // Trade
            put(ServerMessage.OpenTrade::class, tradeHandler)
            put(ServerMessage.TradeUpdate::class, tradeHandler)
            put(ServerMessage.TradeClosed::class, tradeHandler)

            // Character / combat / status
            put(ServerMessage.CharacterCreationRequired::class, characterStatusHandler)
            put(ServerMessage.CharacterSync::class, characterStatusHandler)
            put(ServerMessage.HealthUpdate::class, characterStatusHandler)
            put(ServerMessage.PlayerStatusUpdate::class, characterStatusHandler)
            put(ServerMessage.StatusEffectUpdate::class, characterStatusHandler)
            put(ServerMessage.BreathUpdate::class, characterStatusHandler)
            put(ServerMessage.CompassUpdate::class, characterStatusHandler)
            put(ServerMessage.PlayerDowned::class, characterStatusHandler)
            put(ServerMessage.PlayerRespawned::class, characterStatusHandler)
            put(ServerMessage.XpGained::class, characterStatusHandler)
            put(ServerMessage.QuestSync::class, characterStatusHandler)
            put(ServerMessage.QuestUpdate::class, characterStatusHandler)
            put(ServerMessage.OpenQuestJournal::class, characterStatusHandler)
            put(ServerMessage.AoEEffect::class, characterStatusHandler)
            put(ServerMessage.WeatherUpdate::class, characterStatusHandler)
            put(ServerMessage.ActionBlockPayload::class, characterStatusHandler)

            // Mail
            put(ServerMessage.MailSync::class, mailHandler)
            put(ServerMessage.MailReceived::class, mailHandler)
            put(ServerMessage.MailUpdate::class, mailHandler)
            put(ServerMessage.MailDeleted::class, mailHandler)
            put(ServerMessage.OpenMailbox::class, mailHandler)

            // Social / economy / admin / scenes
            put(ServerMessage.OpenAuctionHouse::class, socialAdminHandler)
            put(ServerMessage.OpenCharacter::class, socialAdminHandler)
            put(ServerMessage.AuctionListingsUpdate::class, socialAdminHandler)
            put(ServerMessage.ClaimSync::class, socialAdminHandler)
            put(ServerMessage.ClaimDenied::class, socialAdminHandler)
            put(ServerMessage.GroupSync::class, socialAdminHandler)
            put(ServerMessage.GuildSync::class, socialAdminHandler)
            put(ServerMessage.FactionSync::class, socialAdminHandler)
            put(ServerMessage.SocialDenied::class, socialAdminHandler)
            put(ServerMessage.GroupInviteReceived::class, socialAdminHandler)
            put(ServerMessage.GuildInviteReceived::class, socialAdminHandler)
            put(ServerMessage.MiniGameRoomSync::class, socialAdminHandler)
            put(ServerMessage.MiniGameInviteReceived::class, socialAdminHandler)
            put(ServerMessage.MiniGameAction::class, socialAdminHandler)
            put(ServerMessage.AdminZoneWireframe::class, socialAdminHandler)
            put(ServerMessage.InstanceZonesSync::class, socialAdminHandler)
            put(ServerMessage.ScenesSync::class, socialAdminHandler)
            put(ServerMessage.ScenePreviewData::class, socialAdminHandler)

            put(ServerMessage.PreferencesSync::class, preferencesHandler)
            put(ServerMessage.PetRosterSync::class, petRosterHandler)

            // Action blocks — single handler object registered for all action-block message types
            put(ServerMessage.ActionBlockSync::class, actionBlockManager)
            put(ServerMessage.ActionBlockUpsert::class, actionBlockManager)
            put(ServerMessage.ActionBlockRemove::class, actionBlockManager)
        }
}
