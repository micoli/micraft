package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class CodexCommandTest {
    private val cmd = CodexCommand()

    @Test
    fun execute_sendsOpenCodex() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.OpenCodex })
    }
}
