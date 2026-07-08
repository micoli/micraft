package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class CraftCommandTest {
    private val cmd = CraftCommand()

    @Test
    fun execute_sendsOpenCraft() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.OpenCraft })
    }

    @Test
    fun execute_sendsRecipeSync() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.RecipeSync>().isNotEmpty())
    }
}
