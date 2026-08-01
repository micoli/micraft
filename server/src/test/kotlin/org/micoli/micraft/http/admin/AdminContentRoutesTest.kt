package org.micoli.micraft.http.admin

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testWorld

class AdminContentRoutesTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    private fun controller(store: TokenStore? = null) =
        AdminController(null, null, null, GameLoop(testWorld()), "data", store)

    @Test
    fun `api_admin_blocks_returns_200_with_json_array`() = testApplication {
        application { routing { controller().register(this) } }
        val r = client.get("/api/admin/blocks")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.startsWith("["), "Expected JSON array, got: ${body.take(40)}")
        assertTrue(body.contains("\"name\""), "Expected name field")
        assertTrue(body.contains("\"hardness\""), "Expected hardness field")
    }

    @Test
    fun `api_admin_npc_types_returns_200_with_json_object`() = testApplication {
        application { routing { controller().register(this) } }
        val r = client.get("/api/admin/npc-types")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.startsWith("{"), "Expected JSON object, got: ${body.take(40)}")
    }

    @Test
    fun `api_admin_items_returns_200_with_json_object`() = testApplication {
        application { routing { controller().register(this) } }
        val r = client.get("/api/admin/items")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.startsWith("{"), "Expected JSON object, got: ${body.take(40)}")
    }

    @Test
    fun `api_admin_blocks_requires_auth_when_token_store_enabled`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }
        val r = client.get("/api/admin/blocks")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_admin_npc_types_requires_auth_when_token_store_enabled`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }
        val r = client.get("/api/admin/npc-types")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_admin_blocks_with_admin_token_returns_200`() = testApplication {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(playerId = "p1", displayName = "Admin", permissions = setOf("admin")))
        application { routing { controller(store).register(this) } }
        val r =
            client.get("/api/admin/blocks") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, r.status)
    }
}
