package org.micoli.micraft.command

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import kotlin.test.Test
import kotlin.test.assertEquals

class DisconnectCommandTest {
    private val cmd = DisconnectCommand()

    @Test
    fun callsKickWithOwnSessionName() = runBlocking {
        val kicked = mutableListOf<String>()
        val session = testSession(name = "Alice")
        cmd.execute(session, "", testContext(kickSession = { kicked.add(it) }))
        assertEquals(listOf("Alice"), kicked)
    }
}
