package org.micoli.micraft.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.support.testWorld

class MetricsRoutesTest {

    @Test
    fun `metrics endpoint exposes prometheus gauges`() = testApplication {
        val gameLoop = GameLoop(testWorld())
        application { routing { metricsRoutes(gameLoop) } }

        val r = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.contentType()!!.match(ContentType.Text.Plain))
        val body = r.bodyAsText()
        assertTrue(body.contains("micraft_connected_players 0"))
        assertTrue(body.contains("micraft_game_ticks_total"))
        assertTrue(body.contains("jvm_heap_used_bytes"))
    }

    @Test
    fun `status endpoint renders html snapshot`() = testApplication {
        val gameLoop = GameLoop(testWorld())
        application { routing { metricsRoutes(gameLoop) } }

        val r = client.get("/status")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.contentType()!!.match(ContentType.Text.Html))
        val body = r.bodyAsText()
        assertTrue(body.contains("MicCraft Server Status"))
        assertTrue(body.contains("none"))
    }

    @Test
    fun buildStatusSnapshot_reflectsEmptyGameLoop() {
        val gameLoop = GameLoop(testWorld())
        val snapshot = buildStatusSnapshot(gameLoop)
        assertEquals(0, snapshot.connectedPlayers)
        assertTrue(snapshot.playerNames.isEmpty())
        assertEquals(0, snapshot.npcTotal)
        assertEquals(0L, snapshot.activeLiquids.toLong())
    }

    @Test
    fun buildPrometheusMetrics_escapesNpcTypeLabels() {
        val gameLoop = GameLoop(testWorld())
        val metrics = buildPrometheusMetrics(gameLoop)
        assertTrue(metrics.contains("micraft_npc_total 0"))
    }
}
