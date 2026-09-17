package org.micoli.micraft.http.admin

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testWorld

class AdminSocialRoutesTest {
    private fun controller(gameLoop: GameLoop = GameLoop(testWorld())) =
        AdminController(null, null, gameLoop) to gameLoop

    @Test
    fun `groups and guilds list empty by default`() = testApplication {
        val (controller, _) = controller()
        application { routing { controller.register(this) } }

        assertEquals(HttpStatusCode.OK, client.get("/api/admin/social/groups").status)
        assertEquals("[]", client.get("/api/admin/social/guilds").bodyAsText())
    }

    @Test
    fun `faction definition CRUD over http`() = testApplication {
        val (controller, _) = controller()
        application { routing { controller.register(this) } }

        val created =
            client.post("/api/admin/social/factions") {
                contentType(ContentType.Application.Json)
                setBody("""{"id":"red","name":"Red","color":"#f00","description":""}""")
            }
        assertEquals(HttpStatusCode.NoContent, created.status)

        val list = client.get("/api/admin/social/factions").bodyAsText()
        assertTrue(list.contains("\"id\":\"red\""))

        val deleted = client.delete("/api/admin/social/factions/red")
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertTrue(
            !client.get("/api/admin/social/factions").bodyAsText().contains("\"id\":\"red\""))
    }

    @Test
    fun `guild create with unknown owner is 400`() = testApplication {
        val (controller, _) = controller()
        application { routing { controller.register(this) } }

        val r =
            client.post("/api/admin/social/guilds") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Ghosts","tag":"GHO","ownerName":"nobody"}""")
            }

        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `faction settings update over http`() = testApplication {
        val (controller, _) = controller()
        application { routing { controller.register(this) } }

        val r =
            client.put("/api/admin/social/factions/settings") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"enabled":true,"friendlyFire":false,"changeCooldownSeconds":30,"spawnRingRadius":384.0}""")
            }
        assertEquals(HttpStatusCode.NoContent, r.status)
        assertTrue(
            client.get("/api/admin/social/factions").bodyAsText().contains("\"enabled\":true"))
    }
}
