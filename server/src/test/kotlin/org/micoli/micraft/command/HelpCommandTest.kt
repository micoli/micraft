package org.micoli.micraft.command

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class HelpCommandTest {
    private val cmd = HelpCommand()

    private fun fakeHandler(
        cmdName: String,
        desc: String = "",
        opts: List<String> = emptyList()
    ): CommandHandler =
        object : CommandHandler {
            override val id: UUID = UUID.randomUUID()
            override val name = cmdName
            override val description = desc
            override val options = opts

            override suspend fun execute(
                session: PlayerSession,
                args: String,
                context: CommandContext
            ) {}
        }

    @Test
    fun noArgs_listsAllCommands() = runBlocking {
        val session = testSession()
        val handlers = listOf(fakeHandler("foo", "Foo command"), fakeHandler("bar", "Bar command"))
        val ctx = testContext(sessions = listOf(session)).copy(commands = { handlers })
        cmd.execute(session, "", ctx)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("/foo"))
        assertTrue(notif.message.contains("/bar"))
    }

    @Test
    fun specificCommand_showsUsageAndDesc() = runBlocking {
        val session = testSession()
        val handlers = listOf(fakeHandler("foo", "Does foo things"))
        val ctx = testContext(sessions = listOf(session)).copy(commands = { handlers })
        cmd.execute(session, "/foo", ctx)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("Does foo things"))
    }

    @Test
    fun specificCommand_withoutSlash_resolves() = runBlocking {
        val session = testSession()
        val handlers = listOf(fakeHandler("foo", "Does foo"))
        val ctx = testContext(sessions = listOf(session)).copy(commands = { handlers })
        cmd.execute(session, "foo", ctx)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("Does foo"))
    }

    @Test
    fun unknownCommand_sendsError() = runBlocking {
        val session = testSession()
        val ctx = testContext(sessions = listOf(session)).copy(commands = { emptyList() })
        cmd.execute(session, "/unknown", ctx)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("Unknown") || notif.message.contains("unknown"))
    }

    @Test
    fun commandWithOptions_listsOptions() = runBlocking {
        val session = testSession()
        val handlers = listOf(fakeHandler("foo", "Foo", listOf("opt1 — thing1", "opt2 — thing2")))
        val ctx = testContext(sessions = listOf(session)).copy(commands = { handlers })
        cmd.execute(session, "/foo", ctx)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("opt1"))
        assertTrue(notif.message.contains("opt2"))
    }
}
