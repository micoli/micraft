package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class BiomeMapCommandTest {
    private val cmd = BiomeMapCommand()

    @Test
    fun execute_sendsToggleBiomeMap() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.ToggleBiomeMap })
    }
}
