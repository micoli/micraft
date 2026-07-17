package org.micoli.micraft.http.map

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.http.MapController
import org.micoli.micraft.support.testWorld

class MapControllerAuthTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    private fun controller(tokenStore: TokenStore?) =
        MapController(GameLoop(testWorld()), tokenStore)

    @Test
    fun `api_map_state_no_bearer_returns_401_when_auth_enabled`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        val r = client.get("/api/map/state")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_map_state_invalid_token_returns_401`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/map/state") {
                headers.append(HttpHeaders.Authorization, "Bearer garbage-token")
            }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_map_state_expired_token_returns_401`() = testApplication {
        val store = TokenStore(scope, ttlSeconds = -1)
        val token = store.issue(AuthResult(playerId = "p1", displayName = "Player"))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/map/state") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_map_state_valid_token_returns_200`() = testApplication {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult(playerId = "p1", displayName = "Player"))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/map/state") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `api_map_state_any_permission_level_is_accepted`() = testApplication {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(playerId = "p1", displayName = "Guest", permissions = emptySet()))
        application { routing { controller(store).register(this) } }

        val r =
            client.get("/api/map/state") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `api_map_no_token_store_allows_unauthenticated_access`() = testApplication {
        application { routing { controller(null).register(this) } }

        val r = client.get("/api/map/state")
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `api_map_token_from_different_store_returns_401`() = testApplication {
        val store1 = TokenStore(scope)
        val store2 = TokenStore(scope)
        val token = store1.issue(AuthResult(playerId = "p1", displayName = "Player"))
        application { routing { controller(store2).register(this) } }

        val r =
            client.get("/api/map/state") {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_map_voronoi_also_requires_auth`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        val r = client.get("/api/map/voronoi")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `api_map_terrain_also_requires_auth`() = testApplication {
        val store = TokenStore(scope)
        application { routing { controller(store).register(this) } }

        val r = client.get("/api/map/terrain")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
