package org.micoli.micraft.http.admin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testWorld

class AdminClaimRoutesTest {
    private fun controller(gameLoop: GameLoop = GameLoop(testWorld())) =
        AdminController(null, null, null, gameLoop, "data") to gameLoop

    @Test
    fun `api_admin_claims_returns_200_with_created_claim`() = testApplication {
        val (controller, gameLoop) = controller()
        gameLoop.claims().create(setOf(ChunkPos(0, 0)), 0, 10, "alice-id", "Alice")
        application { routing { controller.register(this) } }

        val r = client.get("/api/admin/claims")

        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"ownerName\":\"Alice\""))
    }

    @Test
    fun `api_admin_claims_by_id_404_when_missing`() = testApplication {
        val (controller, _) = controller()
        application { routing { controller.register(this) } }

        val r = client.get("/api/admin/claims/nope")

        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test
    fun `bounds_update_succeeds_and_is_reflected_in_registry`() = testApplication {
        val (controller, gameLoop) = controller()
        val claim = gameLoop.claims().create(setOf(ChunkPos(0, 0)), 0, 10, "alice-id", "Alice")
        application { routing { controller.register(this) } }

        val r =
            client.put("/api/admin/claims/${claim.id}/bounds") {
                contentType(ContentType.Application.Json)
                setBody("""{"yMin":5,"yMax":20}""")
            }

        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(5, gameLoop.claims().get(claim.id)?.yMin)
        assertEquals(20, gameLoop.claims().get(claim.id)?.yMax)
    }

    @Test
    fun `bounds_update_conflicting_with_another_claim_returns_409`() = testApplication {
        val (controller, gameLoop) = controller()
        gameLoop.claims().create(setOf(ChunkPos(1, 1)), 0, 10, "bob-id", "Bob")
        val claim = gameLoop.claims().create(setOf(ChunkPos(1, 1)), 20, 30, "alice-id", "Alice")
        application { routing { controller.register(this) } }

        val r =
            client.put("/api/admin/claims/${claim.id}/bounds") {
                contentType(ContentType.Application.Json)
                setBody("""{"yMin":0,"yMax":10}""")
            }

        assertEquals(HttpStatusCode.Conflict, r.status)
    }

    @Test
    fun `trust_unknown_player_returns_404`() = testApplication {
        val (controller, gameLoop) = controller()
        val claim = gameLoop.claims().create(setOf(ChunkPos(0, 0)), 0, 10, "alice-id", "Alice")
        application { routing { controller.register(this) } }

        val r =
            client.put("/api/admin/claims/${claim.id}/trust") {
                contentType(ContentType.Application.Json)
                setBody("""{"playerName":"Ghost","trusted":true}""")
            }

        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test
    fun `delete_removes_claim`() = testApplication {
        val (controller, gameLoop) = controller()
        val claim = gameLoop.claims().create(setOf(ChunkPos(0, 0)), 0, 10, "alice-id", "Alice")
        application { routing { controller.register(this) } }

        val r = client.delete("/api/admin/claims/${claim.id}")

        assertEquals(HttpStatusCode.NoContent, r.status)
        assertEquals(null, gameLoop.claims().get(claim.id))
    }
}
