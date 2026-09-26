package org.micoli.micraft.http.admin

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.GameWorldRegistry
import org.micoli.micraft.game.world.buildE2eGameWorld
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testWorld

class AdminPerfRoutesTest {

    private val shared by lazy { SharedGameServices.default() }

    private fun gameLoopWithE2e(): Pair<GameLoop, GameWorldRegistry> {
        val gameLoop = GameLoop(testWorld())
        val registry =
            GameWorldRegistry(
                defaultWorld = gameLoop.defaultWorld,
                e2eEnabled = true,
                factory = { id ->
                    buildE2eGameWorld(
                        id, EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1), shared)
                },
            )
        return gameLoop to registry
    }

    private fun totalPhase(body: String) =
        Json.parseToJsonElement(body)
            .jsonObject["tick"]!!
            .jsonArray
            .map { it.jsonObject }
            .single { it["name"]!!.jsonPrimitive.content == "total" }

    @Test
    fun `snapshot reports the tick percentiles recorded since reset`() = testApplication {
        val gameLoop = GameLoop(testWorld())
        application { routing { AdminController(null, null, gameLoop).register(this) } }
        val profiler = gameLoop.defaultWorld.tickProfiler
        profiler.record("total", 40_000_000L)

        assertEquals(HttpStatusCode.NoContent, client.post("/api/admin/perf/reset").status)
        profiler.record("total", 5_000_000L)

        val r = client.get("/api/admin/perf/snapshot")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        val total = totalPhase(body)
        assertEquals(1, total["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(5.0, total["maxMs"]!!.jsonPrimitive.content.toDouble(), 0.001)
        val root = Json.parseToJsonElement(body).jsonObject
        assertTrue("process" in root, "process-level run metrics are included")
        assertEquals(0, root["characters"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `game-session header scopes reset and snapshot to that world`() = testApplication {
        val (gameLoop, registry) = gameLoopWithE2e()
        application {
            routing {
                AdminController(null, null, gameLoop, gameWorldRegistry = registry).register(this)
            }
        }
        registry.resolve("w1").tickProfiler.record("total", 7_000_000L)
        gameLoop.defaultWorld.tickProfiler.record("total", 30_000_000L)

        val r =
            client.get("/api/admin/perf/snapshot") {
                headers.append(AdminController.GAME_SESSION_HEADER, "w1")
            }

        assertEquals(
            7.0, totalPhase(r.bodyAsText())["maxMs"]!!.jsonPrimitive.content.toDouble(), 0.001)
    }

    @Test
    fun `perf routes require an admin token when auth is enabled`() = testApplication {
        val store = TokenStore(CoroutineScope(Dispatchers.Default))
        application {
            routing { AdminController(null, null, GameLoop(testWorld()), store).register(this) }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/admin/perf/reset").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/perf/snapshot").status)
    }
}
