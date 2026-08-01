package org.micoli.micraft.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.UUID
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcTuning
import org.micoli.micraft.simulation.SimCommand
import org.micoli.micraft.simulation.SimMessage
import org.micoli.micraft.simulation.SimulationRegistry
import org.micoli.micraft.simulation.WorldSimulator
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SimulationController::class.java)

private val simJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "t"
}

/** Defaults the admin UI prefills its editors with. */
@Serializable
data class SimulationDefaultsDto(
    val tuning: NpcTuning,
    val npcTypes: List<String>,
    val liveSimulations: Int,
)

/**
 * Drives the admin world simulator over its own websocket. Frames are pushed at a fixed rate,
 * decoupled from the simulation tick rate, so a 5000 ticks/s arena does not flood the browser.
 */
class SimulationController(
    private val registry: SimulationRegistry,
    private val npcTypesProvider: () -> List<String>,
    private val tokenStore: TokenStore? = null,
) {
    private suspend fun RoutingContext.requireAdmin(): Boolean {
        tokenStore ?: return true
        val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
        val auth = if (token != null) tokenStore.validate(token) else null
        if (auth == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return false
        }
        if ("*" !in auth.permissions && "admin" !in auth.permissions) {
            call.respond(HttpStatusCode.Forbidden)
            return false
        }
        return true
    }

    fun register(route: Route) =
        route.apply {
            get("/api/admin/simulation/defaults") {
                if (!requireAdmin()) return@get
                val payload =
                    SimulationDefaultsDto(
                        tuning = NpcConstants.live,
                        npcTypes = npcTypesProvider(),
                        liveSimulations = registry.count,
                    )
                call.respondText(
                    simJson.encodeToString(SimulationDefaultsDto.serializer(), payload),
                    ContentType.Application.Json,
                )
            }
        }

    fun registerWs(route: Route) =
        route.webSocket("/api/admin/ws/simulation") {
            if (tokenStore != null) {
                val token = call.request.queryParameters["token"]
                val auth = token?.let { tokenStore.validate(it) }
                if (auth == null || ("*" !in auth.permissions && "admin" !in auth.permissions)) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                    return@webSocket
                }
            }

            val key = UUID.randomUUID().toString()
            var lastEventSeq = 0L

            suspend fun push(message: SimMessage) {
                runCatching { send(simJson.encodeToString(SimMessage.serializer(), message)) }
            }

            suspend fun pushSnapshot(simulator: WorldSimulator) {
                val events = simulator.events.snapshot()
                lastEventSeq = events.lastOrNull()?.seq ?: 0L
                push(
                    SimMessage.Snapshot(
                        arena = simulator.arenaDto(),
                        config = simulator.config,
                        npcs = simulator.npcDtos(),
                        players = simulator.playerDtos(),
                        stats = simulator.statsDto(),
                        events = events,
                    ))
            }

            // Frame pusher: fixed cadence, independent of the simulation speed.
            val pusher = launch {
                while (isActive) {
                    kotlinx.coroutines.delay(FRAME_INTERVAL_MS)
                    val simulator = registry[key] ?: continue
                    val newEvents = simulator.events.since(lastEventSeq)
                    if (newEvents.isNotEmpty()) lastEventSeq = newEvents.last().seq
                    push(
                        SimMessage.Frame(
                            npcs = simulator.npcDtos(),
                            players = simulator.playerDtos(),
                            stats = simulator.statsDto(),
                            events = newEvents,
                        ))
                }
            }

            try {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val command =
                        runCatching { simJson.decodeFromString(SimCommand.serializer(), text) }
                            .onFailure {
                                log.warn("bad simulation command: {}", it.message)
                                push(SimMessage.Error("commande invalide: ${it.message}"))
                            }
                            .getOrNull() ?: continue

                    when (command) {
                        is SimCommand.Init -> {
                            val simulator =
                                runCatching { registry.start(key, command.config) }
                                    .onFailure {
                                        log.error("simulation init failed", it)
                                        push(SimMessage.Error("init impossible: ${it.message}"))
                                    }
                                    .getOrNull()
                            if (simulator != null) {
                                lastEventSeq = 0L
                                pushSnapshot(simulator)
                            }
                        }
                        is SimCommand.Restart -> {
                            val simulator = registry.restart(key)
                            if (simulator == null)
                                push(SimMessage.Error("aucune simulation à redémarrer"))
                            else {
                                lastEventSeq = 0L
                                pushSnapshot(simulator)
                            }
                        }
                        is SimCommand.Stop -> {
                            registry.stop(key)
                            push(SimMessage.Stopped)
                        }
                        is SimCommand.Speed -> registry[key]?.setSpeed(command.ticksPerSecond)
                        is SimCommand.Step -> registry[key]?.stepOnce(command.count)
                        is SimCommand.Spawn ->
                            registry[key]?.spawn(
                                command.type, command.x, command.z, command.count, command.level)
                        is SimCommand.Inspect -> {
                            val detail = registry[key]?.npcDetail(command.npcId)
                            if (detail == null) push(SimMessage.Error("NPC introuvable"))
                            else push(SimMessage.NpcDetail(detail))
                        }
                        is SimCommand.PlayerInput ->
                            registry[key]?.applyPlayerInput(
                                command.name, command.dx, command.dz, command.yaw, command.jump)
                        is SimCommand.Tuning -> registry[key]?.setTuning(command.tuning)
                        is SimCommand.Defs ->
                            registry[key]?.applyDefinitionOverrides(command.overrides)
                    }
                }
            } finally {
                pusher.cancel()
                registry.stop(key)
            }
        }

    companion object {
        /** ~20 frames per second, whatever the simulation does internally. */
        private const val FRAME_INTERVAL_MS = 50L
    }
}
