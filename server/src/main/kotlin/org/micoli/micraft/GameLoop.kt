package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.tick.BlockBreaker
import org.micoli.micraft.tick.ChunkStreamer
import org.micoli.micraft.tick.IntentCollector
import org.micoli.micraft.tick.MovementProcessor
import org.micoli.micraft.world.ChunkGenerator
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState
import java.nio.file.Path
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("GameLoop")

class GameLoop(
    private val world: WorldState,
    private val persistence: WorldPersistence? = null,
    private val reloadBiomes: (() -> ChunkGenerator)? = null,
    val i18n: I18nConfig = I18nConfig(Path.of("data/i18n")),
) {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    private var saveTickCounter = 0

    private val commands: Map<String, CommandHandler> =
        java.util.ServiceLoader.load(CommandHandler::class.java).associateBy { it.command }

    private val dropConfig = DropConfig(Path.of("data/drops/drops.yaml"))
    private val worldItems = WorldItemManager(
        dropConfig,
        broadcast = { msg -> sessions.values.forEach { it.send(msg) } },
        savePlayer = ::savePlayer,
        i18n = i18n,
    )

    private val commandContext = CommandContext(
        world = world,
        persistence = persistence,
        i18n = i18n,
        broadcast = { msg -> sessions.values.forEach { it.send(msg) } },
        sessions = { sessions.values },
        kickSession = { playerName ->
            sessions.values.find { it.state.name == playerName }
                ?.socket?.close(CloseReason(CloseReason.Codes.NORMAL, "Kicked by server"))
        },
        reloadConfig = ::reload,
        commands = { commands.values },
        savePlayer = ::savePlayer,
        worldItems = worldItems,
    )
    private val blockBreaker = BlockBreaker(world, { msg -> sessions.values.forEach { it.send(msg) } }, worldItems)
    private val intentCollector = IntentCollector(blockBreaker, ::handleCommand)
    private val movementProcessor = MovementProcessor(world)
    private val chunkStreamer = ChunkStreamer(world)

    private suspend fun reload(): String {
        val lines = mutableListOf<String>()
        val dropCount = dropConfig.reload()
        lines += "Drops: $dropCount block types"
        if (reloadBiomes != null) {
            world.generator = reloadBiomes.invoke()
            lines += "Biomes reloaded"
        }
        i18n.reload()
        lines += "i18n: ${i18n.locales.size} locales"
        return lines.joinToString(", ")
    }

    fun start(app: Application) {
        log.info("GameLoop starting (tick=${TICK_MS}ms, gravity=$GRAVITY)")
        app.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
                saveTickCounter++
                if (saveTickCounter >= SAVE_INTERVAL_TICKS) {
                    saveTickCounter = 0
                    world.flushDirty()
                }
            }
        }
    }

    fun shutdown() {
        world.flushDirty()
        sessions.values.forEach { session -> savePlayer(session) }
        log.info("World saved on shutdown")
    }

    private fun savePlayer(session: PlayerSession) {
        persistence?.savePlayerState(
            session.state.name,
            session.state.copy(inventory = session.inventory.toMap()),
        )
    }

    private suspend fun tick() {
        sessions.values.forEach { session ->
            val input = intentCollector.collect(session)
            blockBreaker.tick(session)
            val newState = movementProcessor.process(session, input)
            if (newState != session.state) {
                session.state = newState
                val update = ServerMessage.PlayerUpdate(newState)
                sessions.values.forEach { it.send(update) }
            }
            chunkStreamer.checkAndStream(session)
        }
        worldItems.tickCollection(sessions.values)
    }

    private suspend fun handleCommand(session: PlayerSession, text: String) {
        val trimmed = text.trim()
        val name = trimmed.substringBefore(' ').lowercase()
        val args = trimmed.substringAfter(' ', "")
        val handler = commands[name]
        if (handler != null) handler.execute(session, args, commandContext)
        else session.send(ServerMessage.Notification(i18n.t(session.state.language, "commands:server:unknown", trimmed)))
    }

    suspend fun onConnect(socket: DefaultWebSocketSession) {
        val id = UUID.randomUUID().toString()

        val connectMsg = runCatching {
            val firstFrame = socket.incoming.receive()
            if (firstFrame is Frame.Text) {
                val msg = Json.decodeFromString<ClientMessage>(firstFrame.readText())
                if (msg is ClientMessage.Connect) msg else null
            } else null
        }.getOrNull()
        val playerName = connectMsg?.playerName ?: "Player"
        val userName = connectMsg?.userName ?: playerName
        val preferredLanguage = connectMsg?.preferredLanguage?.let { if (it in i18n.locales) it else "en" } ?: "en"

        val saved = persistence?.loadPlayerState(playerName)
        val spawn = saved?.pos ?: Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z)
        val language = saved?.language?.let { if (it in i18n.locales) it else "en" } ?: preferredLanguage
        val state = PlayerState(
            id = id,
            name = playerName,
            pos = spawn,
            orientation = saved?.orientation ?: Orientation(0f, 0f),
            stance = saved?.stance ?: PlayerStance.STANDING,
            flying = saved?.flying ?: DEBUG_WORLD,
            speedMultiplier = saved?.speedMultiplier ?: 1f,
            language = language,
        )
        val session = PlayerSession(id, userName, socket, state)
        saved?.inventory?.forEach { (type, count) -> session.inventory[type] = count }
        sessions[id] = session
        log.info("player connected: {} name={} user={} (total={})", id.take(8), playerName, userName, sessions.size)

        session.send(ServerMessage.Welcome(id, playerName, spawn, language))
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))

        val spawnCp = ChunkPos(
            Math.floorDiv(spawn.x.toInt(), WorldConstants.CHUNK_SIZE),
            Math.floorDiv(spawn.z.toInt(), WorldConstants.CHUNK_SIZE),
        )
        session.lastChunkPos = spawnCp
        chunkStreamer.streamAround(session, spawnCp.cx, spawnCp.cz)
        log.info("{} chunks sent to {}", session.loadedChunks.size, id.take(8))

        sessions.values.filter { it.id != id }.forEach { other ->
            session.send(ServerMessage.PlayerUpdate(other.state))
            other.send(ServerMessage.PlayerUpdate(state))
        }

        try {
            socket.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    runCatching { Json.decodeFromString<ClientMessage>(frame.readText()) }
                        .onFailure { log.warn("bad frame from {}: {}", id.take(8), it.message) }
                        .getOrNull()
                        ?.let { msg ->
                            when (msg) {
                                is ClientMessage.Disconnect -> return@consumeEach
                                is ClientMessage.ChunkUnload -> {
                                    msg.positions.forEach { session.loadedChunks.remove(it) }
                                    log.debug("{} chunks unloaded by {}", msg.positions.size, session.id.take(8))
                                }
                                else -> session.intents.trySend(msg)
                            }
                        }
                }
            }
        } finally {
            sessions.remove(id)
            savePlayer(session)
            log.info("player disconnected: {} name={} (total={})", id.take(8), session.state.name, sessions.size)
            val left = ServerMessage.PlayerLeft(id)
            sessions.values.forEach { it.send(left) }
        }
    }
}
