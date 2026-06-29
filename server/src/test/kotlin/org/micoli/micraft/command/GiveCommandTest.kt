package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.ItemType

class GiveCommandTest {
    private val cmd = GiveCommand()

    @Test
    fun noArgs_sendsUsageNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("give", ignoreCase = true) ||
                    it.message.contains("Usage", ignoreCase = true)
            })
    }

    @Test
    fun unknownItem_sendsErrorNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "dragon_egg", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("dragon_egg", ignoreCase = true) })
    }

    @Test
    fun giveOne_defaultN_addsToInventory() = runBlocking {
        val session = testSession()
        cmd.execute(session, "cobblestone", testContext())
        assertEquals(1, session.inventory[ItemType("COBBLESTONE")])
    }

    @Test
    fun giveExplicitN_addsCorrectAmount() = runBlocking {
        val session = testSession()
        cmd.execute(session, "dirt 5", testContext())
        assertEquals(5, session.inventory[ItemType("DIRT")])
    }

    @Test
    fun give_stacksWithExistingInventory() = runBlocking {
        val session = testSession()
        session.inventory[ItemType("SAND")] = 3
        cmd.execute(session, "sand 4", testContext())
        assertEquals(7, session.inventory[ItemType("SAND")])
    }

    @Test
    fun give_sendsInventoryUpdate() = runBlocking {
        val session = testSession()
        cmd.execute(session, "gravel 2", testContext())
        assertTrue(session.sent.any { it is ServerMessage.InventoryUpdate })
    }

    @Test
    fun give_sendsDoneNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "snowball 3", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("3") && it.message.contains("snowball", ignoreCase = true)
            })
    }

    @Test
    fun give_callsSavePlayer() = runBlocking {
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        val session = testSession()
        cmd.execute(session, "flint 1", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun negativeN_treatedAsOne() = runBlocking {
        val session = testSession()
        cmd.execute(session, "cobblestone -5", testContext())
        assertEquals(1, session.inventory[ItemType("COBBLESTONE")])
    }

    @Test
    fun caseInsensitive_itemName() = runBlocking {
        val session = testSession()
        cmd.execute(session, "SANDSTONE 2", testContext())
        assertEquals(2, session.inventory[ItemType("SANDSTONE")])
    }
}
