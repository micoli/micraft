package org.micoli.micraft.http.admin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.GameWorldRegistry
import org.micoli.micraft.game.world.buildE2eGameWorld
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testWorld

class AdminSessionRoutingTest {

    private val shared by lazy { SharedGameServices.default() }

    private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

    private fun controller(e2e: Boolean): AdminController {
        val gameLoop = GameLoop(testWorld())
        val registry =
            GameWorldRegistry(
                defaultWorld = gameLoop.defaultWorld,
                e2eEnabled = e2e,
                factory = { id -> buildE2eGameWorld(id, gen(), shared) },
            )
        return AdminController(null, null, null, gameLoop, "data", null, registry)
    }

    private suspend fun gameTicks(client: io.ktor.client.HttpClient, session: String?): Long {
        val r =
            client.get("/api/admin/status") {
                if (session != null) headers.append(AdminController.GAME_SESSION_HEADER, session)
            }
        assertEquals(HttpStatusCode.OK, r.status)
        return Json.parseToJsonElement(r.bodyAsText())
            .jsonObject["gameTicks"]!!
            .jsonPrimitive
            .content
            .toLong()
    }

    @Test
    fun `game-session header targets an isolated world`() = testApplication {
        application { routing { controller(e2e = true).register(this) } }

        val before = gameTicks(client, "w1")

        val put =
            client.put("/api/admin/gametime") {
                headers.append(AdminController.GAME_SESSION_HEADER, "w1")
                contentType(ContentType.Application.Json)
                setBody("""{"hour":10}""")
            }
        assertEquals(HttpStatusCode.NoContent, put.status)

        val w1 = gameTicks(client, "w1")
        val default = gameTicks(client, null)

        assertEquals(30_000L, w1, "w1 world reflects the admin write")
        assertEquals(before, default, "the default world is untouched")
    }

    @Test
    fun `header is ignored outside e2e mode`() = testApplication {
        application { routing { controller(e2e = false).register(this) } }

        client.put("/api/admin/gametime") {
            headers.append(AdminController.GAME_SESSION_HEADER, "w1")
            contentType(ContentType.Application.Json)
            setBody("""{"hour":10}""")
        }

        // e2e off => the header is ignored, the write lands on the default world.
        assertEquals(30_000L, gameTicks(client, null))
        assertEquals(30_000L, gameTicks(client, "w1"))
    }
}
