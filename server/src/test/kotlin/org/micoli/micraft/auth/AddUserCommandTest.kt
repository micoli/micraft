package org.micoli.micraft.auth

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class AddUserCommandTest {

    private fun setupProvider(): LocalAuthProvider {
        val tmp = Files.createTempFile("micraft-users", ".yaml")
        tmp.toFile().writeText("users: []\n")
        return LocalAuthProvider(tmp, GroupsConfig())
    }

    @Test
    fun execute_withoutLocalAuthProvider_notifiesInactive() = runBlocking {
        val session = testSession()
        AddUserCommand().execute(session, "a@b.com pass", testContext(authProvider = null))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("not active", ignoreCase = true))
    }

    @Test
    fun execute_missingArgs_sendsUsage() = runBlocking {
        val provider = setupProvider()
        val session = testSession()
        AddUserCommand().execute(session, "onlyemail", testContext(authProvider = provider))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("Usage", ignoreCase = true))
    }

    @Test
    fun execute_validArgs_createsUserAndConfirms() = runBlocking {
        val provider = setupProvider()
        val session = testSession()
        AddUserCommand()
            .execute(session, "bob@test.com secret Bob", testContext(authProvider = provider))

        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("bob@test.com"))

        val login = provider.login("bob@test.com", "secret")
        val result = assertNotNull(login)
        assertEquals("Bob", result.displayName)
    }

    @Test
    fun execute_duplicateUser_sendsFailureNotification() = runBlocking {
        val provider = setupProvider()
        provider.addUser("bob@test.com", "secret", "Bob")
        val session = testSession()
        AddUserCommand()
            .execute(session, "bob@test.com other Bob2", testContext(authProvider = provider))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("Failed", ignoreCase = true))
    }
}
