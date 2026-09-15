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
        AdminController(null, null, GameLoop(testWorld()), store)

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
    fun `api_admin_npc_types_reload_returns_200_with_count_and_types`() = testApplication {
        application { routing { controller().register(this) } }
        val r = client.post("/api/admin/npc-types/reload")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"count\""), "Expected count field, got: ${body.take(60)}")
        assertTrue(body.contains("\"types\""), "Expected types field, got: ${body.take(60)}")
    }

    @Test
    fun `api_admin_npc_types_reload_requires_auth_when_token_store_enabled`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }
        val r = client.post("/api/admin/npc-types/reload")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
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
    fun `api_admin_plain_colors_returns_200_with_json_array`() = testApplication {
        application { routing { controller().register(this) } }
        val r = client.get("/api/admin/plain-colors")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.startsWith("["), "Expected JSON array, got: ${body.take(40)}")
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
    fun `api_admin_plain_colors_requires_auth_when_token_store_enabled`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }
        val r = client.get("/api/admin/plain-colors")
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

    @Test
    fun `api_admin_loggers_returns_200_with_known_logger`() = testApplication {
        application { routing { controller().register(this) } }
        val r = client.get("/api/admin/loggers")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.startsWith("["), "Expected JSON array, got: ${body.take(40)}")
        assertTrue(
            body.contains("org.micoli.micraft.game.quest.QuestManager"),
            "Expected a known logger name in the registry")
    }

    @Test
    fun `api_admin_loggers_requires_auth_when_token_store_enabled`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }
        val r = client.get("/api/admin/loggers")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `put_api_admin_loggers_sets_and_resets_level`() = testApplication {
        application { routing { controller().register(this) } }
        val loggerName = "org.micoli.micraft.game.quest.QuestManager"

        val setResponse =
            client.put("/api/admin/loggers/$loggerName") {
                contentType(ContentType.Application.Json)
                setBody("""{"level":"DEBUG"}""")
            }
        assertEquals(HttpStatusCode.OK, setResponse.status)
        assertTrue(setResponse.bodyAsText().contains("\"level\":\"DEBUG\""))

        val listAfterSet = client.get("/api/admin/loggers").bodyAsText()
        assertTrue(listAfterSet.contains("\"name\":\"$loggerName\",\"level\":\"DEBUG\""))

        val resetResponse =
            client.put("/api/admin/loggers/$loggerName") {
                contentType(ContentType.Application.Json)
                setBody("""{"level":null}""")
            }
        assertEquals(HttpStatusCode.OK, resetResponse.status)
        assertTrue(resetResponse.bodyAsText().contains("\"level\":null"))
    }

    @Test
    fun `put_api_admin_loggers_unknown_level_returns_400`() = testApplication {
        application { routing { controller().register(this) } }
        val r =
            client.put("/api/admin/loggers/some.logger") {
                contentType(ContentType.Application.Json)
                setBody("""{"level":"NOPE"}""")
            }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
