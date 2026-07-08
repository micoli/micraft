package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ConfigCommandTest {

    private fun registryWithValues(): ConfigRegistry =
        ConfigRegistry().apply {
            register("game:gravity", get = { "-20.0" }, set = { true })
            register("game:tickMs", get = { "50" })
            register("weather:enabled", get = { "true" }, set = { true })
        }

    private fun FakePlayerSession.notifications(): List<String> =
        sent.filterIsInstance<ServerMessage.Notification>().map { it.message }

    @Test
    fun autocompleteArgs_containsBothArg0AndArg1() {
        val cmd = ConfigCommand()
        assertTrue(cmd.autocompleteArgs.contains(0))
        assertTrue(cmd.autocompleteArgs.contains(1))
    }

    @Test
    fun completeArg0_returnsGetAndSet() = runBlocking {
        val cmd = ConfigCommand()
        val result = cmd.completeArg(0, "", null, testContext())
        assertEquals(listOf("get", "set"), result)
    }

    @Test
    fun completeArg0_filtersPrefix() = runBlocking {
        val cmd = ConfigCommand()
        val result = cmd.completeArg(0, "g", null, testContext())
        assertEquals(listOf("get"), result)
    }

    @Test
    fun completeArg1_returnsRegistryKeys() = runBlocking {
        val cmd = ConfigCommand()
        val ctx = testContext(configRegistry = registryWithValues())
        val result = cmd.completeArg(1, "", null, ctx)
        assertTrue(result.contains("game:gravity"))
        assertTrue(result.contains("weather:enabled"))
    }

    @Test
    fun completeArg1_filtersPrefix() = runBlocking {
        val cmd = ConfigCommand()
        val ctx = testContext(configRegistry = registryWithValues())
        val result = cmd.completeArg(1, "weather:", null, ctx)
        assertEquals(listOf("weather:enabled"), result)
        assertFalse(result.any { it.startsWith("game:") })
    }

    @Test
    fun completeArg1_withNoRegistry_returnsEmpty() = runBlocking {
        val cmd = ConfigCommand()
        val result = cmd.completeArg(1, "", null, testContext())
        assertEquals(emptyList(), result)
    }

    @Test
    fun execute_get_returnsValue() = runBlocking {
        val cmd = ConfigCommand()
        val session = testSession()
        val ctx = testContext(configRegistry = registryWithValues())
        cmd.execute(session, "get game:gravity", ctx)
        val msgs = session.notifications()
        assertTrue(msgs.any { "game:gravity" in it && "-20.0" in it })
    }

    @Test
    fun execute_get_unknownKey_returnsError() = runBlocking {
        val cmd = ConfigCommand()
        val session = testSession()
        val ctx = testContext(configRegistry = registryWithValues())
        cmd.execute(session, "get game:nonexistent", ctx)
        val msgs = session.notifications()
        assertTrue(msgs.any { "game:nonexistent" in it })
    }

    @Test
    fun execute_set_readOnly_returnsError() = runBlocking {
        val cmd = ConfigCommand()
        val session = testSession()
        val ctx = testContext(configRegistry = registryWithValues())
        cmd.execute(session, "set game:tickMs 100", ctx)
        val msgs = session.notifications()
        assertTrue(msgs.any { "game:tickMs" in it })
    }

    @Test
    fun execute_set_writableKey_succeeds() = runBlocking {
        val cmd = ConfigCommand()
        val session = testSession()
        val ctx = testContext(configRegistry = registryWithValues())
        cmd.execute(session, "set game:gravity -25.0", ctx)
        val msgs = session.notifications()
        assertTrue(msgs.any { "game:gravity" in it && "-25.0" in it })
    }

    @Test
    fun execute_unknownOp_returnsUsage() = runBlocking {
        val cmd = ConfigCommand()
        val session = testSession()
        val ctx = testContext(configRegistry = registryWithValues())
        cmd.execute(session, "list", ctx)
        val msgs = session.notifications()
        assertTrue(msgs.any { "/config" in it })
    }

    @Test
    fun execute_noRegistry_returnsUsage() = runBlocking {
        val cmd = ConfigCommand()
        val session = testSession()
        cmd.execute(session, "get game:gravity", testContext())
        val msgs = session.notifications()
        assertTrue(msgs.any { "/config" in it })
    }
}
