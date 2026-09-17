package org.micoli.micraft.http.admin

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.path
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.Permission
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.support.testWorld

class AdminControllerAuthTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    private fun controller(tokenStore: TokenStore?) =
        AdminController(null, null, GameLoop(testWorld()), tokenStore)

    @Test
    fun `api_admin_no_bearer_returns_401_when_auth_enabled`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        val r = client.get("/api/admin/status")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_admin_invalid_token_returns_401`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/admin/status") {
                headers.append(HttpHeaders.Authorization, "Bearer not-a-real-jwt")
            }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_admin_expired_token_returns_401`() = testApplication {
        val store = TokenStore(scope, ttlSeconds = -1)
        val token =
            store.issue(
                AuthResult(
                    playerId = "p1",
                    displayName = "Admin",
                    permissions = setOf(Permission("admin"))))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/admin/status") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_admin_valid_token_no_admin_permission_returns_403`() = testApplication {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(playerId = "p1", displayName = "Player", permissions = emptySet()))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/admin/status") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `api_admin_non_admin_permission_returns_403`() = testApplication {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(
                    playerId = "p1",
                    displayName = "Builder",
                    permissions = setOf(Permission("build"))))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/admin/status") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `api_admin_admin_permission_passes_auth_and_returns_200`() = testApplication {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(
                    playerId = "p1",
                    displayName = "Admin",
                    permissions = setOf(Permission("admin"))))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/admin/status") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `api_admin_wildcard_permission_passes_auth_and_returns_200`() = testApplication {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(
                    playerId = "p1",
                    displayName = "Super",
                    permissions = setOf(Permission.WILDCARD)))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/admin/status") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `api_admin_no_token_store_allows_unauthenticated_access`() = testApplication {
        application { routing { controller(null).register(this) } }

        val r = client.get("/api/admin/status")
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `admin_static_assets_bypass_auth_guard`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        listOf("/admin.js", "/admin.css").forEach { path ->
            val r = client.get(path)
            assertNotEquals(HttpStatusCode.Unauthorized, r.status, "$path must not require auth")
            assertNotEquals(HttpStatusCode.Forbidden, r.status, "$path must not require auth")
        }
    }

    @Test
    fun `api_admin_token_from_different_store_returns_401`() = testApplication {
        val store1 = TokenStore(scope)
        val store2 = TokenStore(scope)
        val token =
            store1.issue(
                AuthResult(
                    playerId = "p1",
                    displayName = "Admin",
                    permissions = setOf(Permission("admin"))))
        application { routing { controller(store2).register(this) } }

        val r =
            client.get("/api/admin/status") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
