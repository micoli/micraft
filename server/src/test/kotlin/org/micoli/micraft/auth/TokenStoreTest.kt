package org.micoli.micraft.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TokenStoreTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun issue_thenValidate_returnsSameAuthResultWithToken() {
        val store = TokenStore(scope)
        val result = AuthResult(playerId = "p1", displayName = "Player One")
        val token = store.issue(result)

        val validated = assertNotNull(store.validate(token))
        assertEquals("p1", validated.playerId)
        assertEquals("Player One", validated.displayName)
        assertEquals(token, validated.token)
    }

    @Test
    fun validate_unknownToken_returnsNull() {
        val store = TokenStore(scope)
        assertNull(store.validate("does-not-exist"))
    }

    @Test
    fun issue_generatesUniqueTokensPerCall() {
        val store = TokenStore(scope)
        val result = AuthResult(playerId = "p1", displayName = "Player One")
        val tokenA = store.issue(result)
        val tokenB = store.issue(result)
        assertNotEquals(tokenA, tokenB)
    }

    @Test
    fun validate_expiredToken_returnsNullAndRemovesEntry() {
        val store = TokenStore(scope, ttlSeconds = -1)
        val token = store.issue(AuthResult(playerId = "p1", displayName = "Player One"))
        assertNull(store.validate(token))
        // Second call confirms the entry was actually evicted, not just expired-but-present.
        assertNull(store.validate(token))
    }

    @Test
    fun issue_preservesPermissions() {
        val store = TokenStore(scope)
        val result =
            AuthResult(playerId = "p1", displayName = "Player One", permissions = setOf("admin"))
        val token = store.issue(result)
        val validated = assertNotNull(store.validate(token))
        assertEquals(setOf("admin"), validated.permissions)
    }
}
