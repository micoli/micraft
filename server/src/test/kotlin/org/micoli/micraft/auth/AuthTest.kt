package org.micoli.micraft.auth

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AuthTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun addUserAndLogin() =
        runBlocking<Unit> {
            val tmp = Files.createTempFile("micraft-users", ".yaml")
            tmp.toFile().writeText("users: []\n")
            val provider = LocalAuthProvider(tmp, GroupsConfig())
            provider.addUser("test@example.com", "secret123", "Test User")

            val result = provider.login("test@example.com", "secret123")
            assertNotNull(result)
            assertEquals("test@example.com", result.playerId)
            assertEquals("Test User", result.displayName)

            tmp.toFile().delete()
        }

    @Test
    fun loginWrongPassword() =
        runBlocking<Unit> {
            val tmp = Files.createTempFile("micraft-users", ".yaml")
            tmp.toFile().writeText("users: []\n")
            val provider = LocalAuthProvider(tmp, GroupsConfig())
            provider.addUser("user@example.com", "correct", "User")

            val result = provider.login("user@example.com", "wrong")
            assertNull(result)

            tmp.toFile().delete()
        }

    @Test
    fun loginUnknownEmail() =
        runBlocking<Unit> {
            val tmp = Files.createTempFile("micraft-users", ".yaml")
            tmp.toFile().writeText("users: []\n")
            val provider = LocalAuthProvider(tmp, GroupsConfig())

            val result = provider.login("nobody@example.com", "any")
            assertNull(result)

            tmp.toFile().delete()
        }

    @Test
    fun tokenStoreIssueAndValidate() {
        val store = TokenStore(scope)
        val result = AuthResult(playerId = "user1", displayName = "User One")
        val token = store.issue(result)
        assertNotNull(token)

        val validated = store.validate(token)
        assertNotNull(validated)
        assertEquals("user1", validated.playerId)
        assertEquals("User One", validated.displayName)
        assertEquals(token, validated.token)
    }

    @Test
    fun tokenStoreValidateUnknownReturnsNull() {
        val store = TokenStore(scope)
        assertNull(store.validate("not-a-real-token"))
    }
}
