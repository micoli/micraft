package org.micoli.micraft.http

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.simulation.SimulationDeps
import org.micoli.micraft.simulation.SimulationRegistry
import org.micoli.micraft.support.testI18n

private const val INIT_COMMAND =
    """{"t":"init","config":{"halfSize":20,"ticksPerSecond":0,"seed":3,"gameDayDurationSeconds":1.0,"initialSpawns":[{"type":"walker","count":2}]}}"""

private fun deps() =
    SimulationDeps(
        definitions =
            mapOf(
                "walker" to
                    NpcDefinition(
                        type = "walker",
                        behavior = RandomMovableNpcBehavior(),
                        behaviorKey = "random_movable",
                        bbmodelFile = "walker",
                        width = 0.6f,
                        height = 1.8f,
                        wanderSpeed = 4f,
                        wanderRadius = 12f,
                    )),
        combatConfig = CombatConfigData(),
        attackRegistry = emptyMap(),
        armorRegistry = emptyMap(),
        classRegistry = emptyMap(),
        i18n = testI18n(),
        vegetationConfig = VegetationConfig(),
    )

/** Poll until [condition] holds; false when it never does. */
private suspend fun awaitTrue(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMs) {
        while (!condition()) delay(20)
        true
    } ?: false

/** Read frames until one contains [needle]; null when the socket goes quiet first. */
private suspend fun DefaultClientWebSocketSession.awaitContaining(
    needle: String,
    timeoutMs: Long = 10_000,
): String? =
    withTimeoutOrNull(timeoutMs) {
        var found: String? = null
        while (found == null) {
            val text = (incoming.receive() as? Frame.Text)?.readText() ?: continue
            if (text.contains(needle)) found = text
        }
        found
    }

class SimulationControllerTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val emptyNpcRegistryLoader =
        NpcRegistryLoader(
            resourcesEntityPath = java.nio.file.Path.of("nonexistent-entities"),
            dataEntityPath = java.nio.file.Path.of("nonexistent-data-entities"),
        )

    private fun controller(tokenStore: TokenStore?) =
        SimulationController(
            registry = SimulationRegistry { deps() },
            npcTypesProvider = { listOf("walker") },
            npcRegistryLoader = emptyNpcRegistryLoader,
            tokenStore = tokenStore,
        )

    @Test
    fun frameCadence_slowsDownAsThePopulationGrows() {
        val quiet = SimulationController.frameIntervalFor(50)
        val busy = SimulationController.frameIntervalFor(500)
        val crowded = SimulationController.frameIntervalFor(3_000)
        assertEquals(50L, quiet, "a quiet arena stays at ~20 fps")
        assertTrue(busy > quiet, "a busier arena must push less often")
        assertTrue(crowded > busy, "a crowded arena must push even less often")
    }

    @Test
    fun defaults_requireAdminToken() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        assertEquals(
            HttpStatusCode.Unauthorized, client.get("/api/admin/simulation/defaults").status)
    }

    @Test
    fun defaults_rejectNonAdminToken() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Player", permissions = emptySet()))
        application { routing { controller(store).register(this) } }

        val response =
            client.get("/api/admin/simulation/defaults") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun defaults_returnTuningAndTypes() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        application { routing { controller(store).register(this) } }

        val response =
            client.get("/api/admin/simulation/defaults") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("gameDayDurationSeconds"), "tuning must be exposed: $body")
        assertTrue(body.contains("walker"), "npc types must be exposed: $body")
    }

    @Test
    fun ws_withoutToken_isClosed() = testApplication {
        val store = TokenStore(scope)
        application {
            install(WebSockets)
            routing { controller(store).registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/api/admin/ws/simulation") {
            val frame = withTimeoutOrNull(2_000) { incoming.receiveCatching().getOrNull() }
            assertEquals(null, frame as? Frame.Text, "no data should reach an unauthorized client")
        }
    }

    @Test
    fun ws_init_repliesWithSnapshot() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        application {
            install(WebSockets)
            routing { controller(store).registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send(INIT_COMMAND)
            val snapshot =
                withTimeoutOrNull(10_000) {
                    var found: String? = null
                    while (found == null) {
                        val text = (incoming.receive() as? Frame.Text)?.readText() ?: continue
                        if (text.contains("\"snapshot\"")) found = text
                    }
                    found
                }
            assertTrue(snapshot != null, "init must be answered with a snapshot")
            assertTrue(snapshot.contains("\"arena\""), "snapshot carries the arena: $snapshot")
            assertTrue(snapshot.contains("walker"), "snapshot carries the spawned NPCs")
        }
    }

    @Test
    fun ws_badCommand_repliesWithError() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        application {
            install(WebSockets)
            routing { controller(store).registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send("""{"t":"nonsense"}""")
            val error =
                withTimeoutOrNull(5_000) {
                    var found: String? = null
                    while (found == null) {
                        val text = (incoming.receive() as? Frame.Text)?.readText() ?: continue
                        if (text.contains("\"error\"")) found = text
                    }
                    found
                }
            assertTrue(error != null, "an invalid command must be reported")
        }
    }

    @Test
    fun ws_announcesRunningSimulationsOnConnect() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        application {
            install(WebSockets)
            routing { controller(store).registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            val listing = awaitContaining("\"simulations\"")
            assertTrue(listing != null, "a fresh socket must learn what is already running")
        }
    }

    @Test
    fun ws_secondSocketCanAttachToARunningSimulation() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        val registry = SimulationRegistry { deps() }
        val simController =
            SimulationController(
                registry = registry,
                npcTypesProvider = { listOf("walker") },
                npcRegistryLoader = emptyNpcRegistryLoader,
                tokenStore = store)
        application {
            install(WebSockets)
            routing { simController.registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        // first admin starts an arena
        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send(INIT_COMMAND)
            assertTrue(awaitContaining("\"snapshot\"") != null)
        }
        // the arena outlives its creator's socket
        val running = registry.list()
        assertEquals(1, running.size, "closing a tab must not stop the arena")

        // second admin attaches to it
        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send("""{"t":"attach","simulationId":"${running.first().id}"}""")
            val snapshot = awaitContaining("\"snapshot\"")
            assertTrue(snapshot != null, "attaching must answer with the arena's snapshot")
            assertTrue(
                snapshot.contains(running.first().id),
                "the snapshot must say which arena it belongs to")
        }
        registry.stopAll()
    }

    @Test
    fun ws_attachingToNothing_isReported() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        application {
            install(WebSockets)
            routing { controller(store).registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send("""{"t":"attach","simulationId":"does-not-exist"}""")
            assertTrue(awaitContaining("\"error\"") != null)
        }
    }

    @Test
    fun ws_stop_confirmsAndFreesTheSimulation() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        val registry = SimulationRegistry { deps() }
        val simController =
            SimulationController(
                registry = registry,
                npcTypesProvider = { listOf("walker") },
                npcRegistryLoader = emptyNpcRegistryLoader,
                tokenStore = store)
        application {
            install(WebSockets)
            routing { simController.registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send(INIT_COMMAND)
            withTimeoutOrNull(10_000) {
                while (true) {
                    val text = (incoming.receive() as? Frame.Text)?.readText() ?: continue
                    if (text.contains("\"snapshot\"")) break
                }
            }
            assertEquals(1, registry.count)
            send("""{"t":"stop"}""")
            withTimeoutOrNull(5_000) {
                while (true) {
                    val text = (incoming.receive() as? Frame.Text)?.readText() ?: continue
                    if (text.contains("\"stopped\"")) break
                }
            }
            assertEquals(0, registry.count, "stopping must free the arena")
        }
    }

    @Test
    fun ws_stop_closesTheTargetedArena_andLeavesTheAttachedOneAlone() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        val registry = SimulationRegistry { deps() }
        val simController =
            SimulationController(
                registry = registry,
                npcTypesProvider = { listOf("walker") },
                npcRegistryLoader = emptyNpcRegistryLoader,
                tokenStore = store)
        application {
            install(WebSockets)
            routing { simController.registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        // arena A: started from a socket that then goes away, so nothing is attached to it
        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send(INIT_COMMAND)
            assertNotNull(awaitContaining("\"snapshot\""))
        }
        val idA = registry.list().single().id

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            // arena B: this socket is attached to it
            send(INIT_COMMAND)
            assertNotNull(awaitContaining("\"snapshot\""))
            val idB = registry.list().first { it.id != idA }.id
            assertEquals(2, registry.count)

            send("""{"t":"stop","simulationId":"$idA"}""")
            assertTrue(awaitTrue { registry[idA] == null }, "the targeted arena must be closed")
            assertNotNull(registry[idB], "the attached arena must survive closing another one")
        }
        registry.stopAll()
    }

    @Test
    fun ws_restart_rebuildsTheTargetedArena_notTheAttachedOne() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult("p1", "Admin", permissions = setOf("admin")))
        val registry = SimulationRegistry { deps() }
        val simController =
            SimulationController(
                registry = registry,
                npcTypesProvider = { listOf("walker") },
                npcRegistryLoader = emptyNpcRegistryLoader,
                tokenStore = store)
        application {
            install(WebSockets)
            routing { simController.registerWs(this) }
        }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send(INIT_COMMAND)
            assertNotNull(awaitContaining("\"snapshot\""))
        }
        val idA = registry.list().single().id
        val simulatorA = registry[idA]

        client.webSocket("/api/admin/ws/simulation?token=$token") {
            send(INIT_COMMAND)
            assertNotNull(awaitContaining("\"snapshot\""))
            val idB = registry.list().first { it.id != idA }.id
            val simulatorB = registry[idB]

            send("""{"t":"restart","simulationId":"$idA"}""")
            // a restart swaps the arena behind the id, so identity is what tells them apart
            assertTrue(
                awaitTrue { registry[idA] !== simulatorA }, "the targeted arena must be rebuilt")
            assertSame(simulatorB, registry[idB], "the attached arena must be left alone")
        }
        registry.stopAll()
    }
}
