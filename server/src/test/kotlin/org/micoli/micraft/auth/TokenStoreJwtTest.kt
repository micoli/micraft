package org.micoli.micraft.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TokenStoreJwtTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun issue_returnsThreePartJwt() {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult(playerId = "p1", displayName = "Player One"))
        val parts = token.split(".")
        assertEquals(3, parts.size)
        parts.forEach { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun issue_tokensAreUniqueAcrossCalls() {
        val store = TokenStore(scope)
        val result = AuthResult(playerId = "p1", displayName = "Player One")
        assertNotEquals(store.issue(result), store.issue(result))
    }

    @Test
    fun validate_tokenFromDifferentStore_returnsNull() {
        val store1 = TokenStore(scope)
        val store2 = TokenStore(scope)
        val token = store1.issue(AuthResult(playerId = "p1", displayName = "Player One"))
        assertNull(store2.validate(token))
    }

    @Test
    fun validate_returnsCorrectClaims() {
        val store = TokenStore(scope)
        val issued =
            store.issue(
                AuthResult(
                    playerId = "p1",
                    displayName = "Alice",
                    email = "alice@test.com",
                    permissions = setOf("admin"),
                ))
        val result = assertNotNull(store.validate(issued))
        assertEquals("p1", result.playerId)
        assertEquals("Alice", result.displayName)
        assertEquals("alice@test.com", result.email)
        assertEquals(setOf("admin"), result.permissions)
        assertEquals(issued, result.token)
    }

    @Test
    fun validate_expiredToken_returnsNull() {
        val store = TokenStore(scope, ttlSeconds = -1)
        val token = store.issue(AuthResult(playerId = "p1", displayName = "Player One"))
        assertNull(store.validate(token))
        assertNull(store.validate(token))
    }

    @Test
    fun validate_wildcardPermission_roundTrips() {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(playerId = "p1", displayName = "Super", permissions = setOf("*")))
        val result = assertNotNull(store.validate(token))
        assertTrue("*" in result.permissions)
    }

    @Test
    fun validate_multiplePermissions_roundTrip() {
        val store = TokenStore(scope)
        val perms = setOf("admin", "build", "fly")
        val token =
            store.issue(AuthResult(playerId = "p1", displayName = "Player", permissions = perms))
        val result = assertNotNull(store.validate(token))
        assertEquals(perms, result.permissions)
    }

    @Test
    fun validate_emptyPermissions_returnsEmptySet() {
        val store = TokenStore(scope)
        val token =
            store.issue(
                AuthResult(playerId = "p1", displayName = "Player", permissions = emptySet()))
        val result = assertNotNull(store.validate(token))
        assertTrue(result.permissions.isEmpty())
    }

    @Test
    fun validate_malformedString_returnsNull() {
        val store = TokenStore(scope)
        assertNull(store.validate("not.a.jwt"))
        assertNull(store.validate(""))
        assertNull(store.validate("totally-invalid"))
    }
}
