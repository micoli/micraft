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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcTuning
import org.micoli.micraft.simulation.SimCommand
import org.micoli.micraft.simulation.SimMessage
import org.micoli.micraft.simulation.SimMetricsDto
import org.micoli.micraft.simulation.SimViewport
import org.micoli.micraft.simulation.SimulationRegistry
import org.micoli.micraft.simulation.WorldSimulator
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SimulationController::class.java)

private val simJson = Json {
    encodeDefaults = true
    // nulls are the majority of the NPC payload (non-animals have no hunger, gestation, gender…)
    explicitNulls = false
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

            // this socket watches an arena; it does not own it
            var attachedId: String? = null
            var lastEventSeq = 0L
            var viewport: SimViewport? = null
            var sentFoodVersion = -1
            var sentMetricIndex = 0L

            suspend fun push(message: SimMessage) {
                runCatching { send(simJson.encodeToString(SimMessage.serializer(), message)) }
            }

            suspend fun pushSimulations() {
                registry.reapIdle()
                push(SimMessage.Simulations(registry.list(), attachedId))
            }

            fun detach() {
                attachedId?.let { registry.removeViewer(it) }
                attachedId = null
                viewport = null
                sentFoodVersion = -1
                lastEventSeq = 0L
                sentMetricIndex = 0L
            }

            suspend fun pushSnapshot(id: String, simulator: WorldSimulator) {
                val events = simulator.events.snapshot()
                lastEventSeq = events.lastOrNull()?.seq ?: 0L
                val metrics = simulator.metricsDto()
                sentMetricIndex = metrics.buckets.lastOrNull()?.index ?: 0L
                push(
                    SimMessage.Snapshot(
                        simulationId = id,
                        arena = simulator.arenaDto(),
                        config = simulator.config,
                        npcs = simulator.npcDtos(viewport),
                        players = simulator.playerDtos(),
                        stats = simulator.statsDto(),
                        events = events,
                        truncated = simulator.isTruncated(viewport),
                        food = simulator.foodPositions(),
                        foodVersion = simulator.foodVersion,
                        metrics = metrics,
                    ))
                sentFoodVersion = simulator.foodVersion
            }

            // Frame pusher: cadence independent of the simulation speed, and slowed down as the
            // population grows — a crowded arena is an overview, not something to watch at 20 fps.
            val pusher = launch {
                var sinceListPush = 0L
                var sinceMetricsPush = 0L
                while (isActive) {
                    val interval = frameIntervalFor(registry[attachedId]?.statsDto()?.npcCount ?: 0)
                    kotlinx.coroutines.delay(interval)
                    sinceListPush += interval
                    sinceMetricsPush += interval
                    if (sinceListPush >= LIST_REFRESH_MS) {
                        sinceListPush = 0
                        pushSimulations()
                    }
                    val simulator = registry[attachedId] ?: continue
                    val newEvents = simulator.events.since(lastEventSeq)
                    if (newEvents.isNotEmpty()) lastEventSeq = newEvents.last().seq
                    // charts read a trend, not a live position: sending them on every frame would
                    // add the whole open bucket to the payload 20 times a second for nothing
                    val metrics =
                        if (sinceMetricsPush >= METRICS_REFRESH_MS) {
                            sinceMetricsPush = 0
                            val buckets = simulator.metrics.since(sentMetricIndex)
                            buckets.lastOrNull()?.let { sentMetricIndex = it.index }
                            SimMetricsDto(simulator.metrics.bucketGameDays, buckets)
                        } else null
                    push(
                        SimMessage.Frame(
                            npcs = simulator.npcDtos(viewport),
                            players = simulator.playerDtos(),
                            stats = simulator.statsDto(),
                            events = newEvents,
                            truncated = simulator.isTruncated(viewport),
                            food =
                                if (simulator.foodVersion != sentFoodVersion)
                                    simulator.foodPositions()
                                else null,
                            foodVersion = simulator.foodVersion,
                            metrics = metrics,
                        ))
                    sentFoodVersion = simulator.foodVersion
                }
            }

            pushSimulations()

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
                            val id =
                                runCatching { registry.start(command.config, command.name) }
                                    .onFailure {
                                        log.warn("simulation init refused: {}", it.message)
                                        push(SimMessage.Error(it.message ?: "init impossible"))
                                    }
                                    .getOrNull()
                            val simulator = registry[id]
                            if (id != null && simulator != null) {
                                detach()
                                attachedId = id
                                registry.addViewer(id)
                                pushSnapshot(id, simulator)
                            }
                            pushSimulations()
                        }
                        is SimCommand.ListSimulations -> pushSimulations()
                        is SimCommand.Attach -> {
                            val simulator = registry[command.simulationId]
                            if (simulator == null) push(SimMessage.Error("simulation introuvable"))
                            else {
                                detach()
                                attachedId = command.simulationId
                                registry.addViewer(command.simulationId)
                                pushSnapshot(command.simulationId, simulator)
                            }
                            pushSimulations()
                        }
                        is SimCommand.Detach -> {
                            detach()
                            push(SimMessage.Stopped)
                            pushSimulations()
                        }
                        is SimCommand.Restart -> {
                            val id = command.simulationId ?: attachedId
                            val simulator = id?.let { registry.restart(it) }
                            if (id == null || simulator == null)
                                push(SimMessage.Error("aucune simulation à redémarrer"))
                            else if (id == attachedId) {
                                // the arena is a new object with a new event log: the cursors this
                                // socket kept point at the previous one
                                lastEventSeq = 0L
                                pushSnapshot(id, simulator)
                            } else pushSimulations()
                        }
                        is SimCommand.Stop -> {
                            val id = command.simulationId ?: attachedId
                            if (id == null) push(SimMessage.Error("aucune simulation à fermer"))
                            else {
                                registry.stop(id)
                                // closing an arena this socket only had in its list leaves the one
                                // it watches alone
                                if (id == attachedId) {
                                    detach()
                                    push(SimMessage.Stopped)
                                }
                            }
                            pushSimulations()
                        }
                        is SimCommand.Speed ->
                            registry[attachedId]?.setSpeed(command.ticksPerSecond)
                        is SimCommand.Step -> registry[attachedId]?.stepOnce(command.count)
                        is SimCommand.Spawn ->
                            registry[attachedId]?.spawn(
                                command.type, command.x, command.z, command.count, command.level)
                        is SimCommand.Inspect -> {
                            val detail = registry[attachedId]?.npcDetail(command.npcId)
                            if (detail == null) push(SimMessage.Error("NPC introuvable"))
                            else push(SimMessage.NpcDetail(detail))
                        }
                        is SimCommand.PlayerInput ->
                            registry[attachedId]?.applyPlayerInput(
                                command.name, command.dx, command.dz, command.yaw, command.jump)
                        is SimCommand.Viewport -> viewport = command.viewport
                        is SimCommand.Tuning -> registry[attachedId]?.setTuning(command.tuning)
                        is SimCommand.Defs ->
                            registry[attachedId]?.applyDefinitionOverrides(command.overrides)
                    }
                }
            } finally {
                pusher.cancel()
                // leaving a viewer must not stop an arena others may be watching
                detach()
            }
        }

    companion object {
        /** ~20 frames per second on a quiet arena, whatever the simulation does internally. */
        private const val FRAME_INTERVAL_MS = 50L

        /** How often the running-simulation list is refreshed on an attached socket. */
        private const val LIST_REFRESH_MS = 2_000L

        /** How often the chart buckets are pushed. */
        private const val METRICS_REFRESH_MS = 1_000L

        /**
         * Each NPC costs about a hundred bytes of JSON per frame. Past a few hundred of them, 20
         * fps saturates the socket and the page stops responding, so the cadence drops instead.
         */
        internal fun frameIntervalFor(npcCount: Int): Long =
            when {
                npcCount <= 200 -> FRAME_INTERVAL_MS
                npcCount <= 600 -> FRAME_INTERVAL_MS * 2
                npcCount <= 1_500 -> FRAME_INTERVAL_MS * 4
                else -> FRAME_INTERVAL_MS * 8
            }
    }
}
