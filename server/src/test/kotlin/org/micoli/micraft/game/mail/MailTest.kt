package org.micoli.micraft.game.mail

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class MailTest {

    private fun setup(): Triple<MailManager, MailPersistence, java.nio.file.Path> {
        val dir = Files.createTempDirectory("mail-test")
        dir.toFile().also { it.mkdirs() }
        // create a fake player YAML so playerExists() works
        dir.resolve("alice.yaml").toFile().writeText("state:\n  id: alice-id\n  name: alice\n")
        dir.resolve("bob.yaml").toFile().writeText("state:\n  id: bob-id\n  name: bob\n")
        val persistence = MailPersistence(dir)
        val registry = SessionRegistry()
        val manager = MailManager(persistence, registry, testI18n(), savePlayer = {})
        return Triple(manager, persistence, dir)
    }

    @Test
    fun sendMail_persistsToRecipientMailbox() = runBlocking {
        val (manager, persistence) = setup()
        val alice = testSession(id = "alice-id", name = "alice")
        val msg = ClientMessage.SendMail(to = "bob", subject = "Hello", body = "World")
        manager.handleSendMail(alice, msg)

        val mails = persistence.loadMails("bob")
        assertEquals(1, mails.size)
        assertEquals("Hello", mails[0].subject)
        assertEquals("World", mails[0].body)
        assertEquals("alice", mails[0].from)
        assertFalse(mails[0].seen)
    }

    @Test
    fun sendMail_deductsAttachmentsFromSenderInventory() = runBlocking {
        val (manager, _, _) = setup()
        val cobble = ItemType("COBBLESTONE")
        val alice = testSession(id = "alice-id", name = "alice")
        alice.inventory[cobble] = 10

        val msg =
            ClientMessage.SendMail(
                to = "bob", subject = "Gift", body = "", attachments = mapOf(cobble to 3))
        manager.handleSendMail(alice, msg)

        assertEquals(7, alice.inventory[cobble])
        val inventoryUpdate =
            alice.sent.filterIsInstance<ServerMessage.InventoryUpdate>().lastOrNull()
        assertNotNull(inventoryUpdate)
        Unit
    }

    @Test
    fun sendMail_insufficientItems_sendsNotificationAndAborts() = runBlocking {
        val (manager, persistence) = setup()
        val cobble = ItemType("COBBLESTONE")
        val alice = testSession(id = "alice-id", name = "alice")
        alice.inventory[cobble] = 1

        val msg =
            ClientMessage.SendMail(
                to = "bob", subject = "Too much", body = "", attachments = mapOf(cobble to 5))
        manager.handleSendMail(alice, msg)

        assertEquals(1, alice.inventory[cobble])
        assertEquals(0, persistence.loadMails("bob").size)
        val notif = alice.sent.filterIsInstance<ServerMessage.Notification>().lastOrNull()
        assertNotNull(notif)
        Unit
    }

    @Test
    fun sendMail_recipientNotFound_sendsNotification() = runBlocking {
        val (manager, persistence) = setup()
        val alice = testSession(id = "alice-id", name = "alice")
        manager.handleSendMail(alice, ClientMessage.SendMail("unknown_player", "Hi", ""))
        assertEquals(0, persistence.loadMails("unknown_player").size)
        assertTrue(alice.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun markSeen_updatesSeen() = runBlocking {
        val (manager, persistence) = setup()
        val bob = testSession(id = "bob-id", name = "bob")
        manager.handleSendMail(bob, ClientMessage.SendMail("alice", "Test", "Body"))
        val mailId = persistence.loadMails("alice")[0].id

        val alice = testSession(id = "alice-id", name = "alice")
        manager.handleMarkSeen(alice, mailId)

        assertTrue(persistence.loadMails("alice")[0].seen)
        val update = alice.sent.filterIsInstance<ServerMessage.MailUpdate>().lastOrNull()
        assertNotNull(update)
        assertTrue(update.mail.seen)
    }

    @Test
    fun claimAttachments_addsItemsToInventory() = runBlocking {
        val (manager, persistence) = setup()
        val cobble = ItemType("COBBLESTONE")
        val alice = testSession(id = "alice-id", name = "alice")
        alice.inventory[cobble] = 5

        manager.handleSendMail(alice, ClientMessage.SendMail("bob", "Gift", "", mapOf(cobble to 5)))
        assertEquals(0, alice.inventory.getOrDefault(cobble, 0))

        val mailId = persistence.loadMails("bob")[0].id
        val bob = testSession(id = "bob-id", name = "bob")
        manager.handleClaimAttachments(bob, mailId)

        assertEquals(5, bob.inventory.getOrDefault(cobble, 0))
        assertTrue(persistence.loadMails("bob")[0].attachmentsClaimed)
    }

    @Test
    fun deleteMail_withUnclaimedAttachments_returnsItemsToInventory() = runBlocking {
        val (manager, persistence) = setup()
        val cobble = ItemType("COBBLESTONE")
        val alice = testSession(id = "alice-id", name = "alice")
        alice.inventory[cobble] = 5

        manager.handleSendMail(alice, ClientMessage.SendMail("bob", "Gift", "", mapOf(cobble to 5)))
        val mailId = persistence.loadMails("bob")[0].id

        val bob = testSession(id = "bob-id", name = "bob")
        manager.handleDelete(bob, mailId)

        assertEquals(5, bob.inventory.getOrDefault(cobble, 0))
        assertEquals(0, persistence.loadMails("bob").size)
        assertNotNull(bob.sent.filterIsInstance<ServerMessage.MailDeleted>().lastOrNull())
        Unit
    }

    @Test
    fun deleteMail_withClaimedAttachments_doesNotReturnItems() = runBlocking {
        val (manager, persistence) = setup()
        val cobble = ItemType("COBBLESTONE")
        val alice = testSession(id = "alice-id", name = "alice")
        alice.inventory[cobble] = 5

        manager.handleSendMail(alice, ClientMessage.SendMail("bob", "Gift", "", mapOf(cobble to 5)))
        val mailId = persistence.loadMails("bob")[0].id

        val bob = testSession(id = "bob-id", name = "bob")
        manager.handleClaimAttachments(bob, mailId)
        bob.inventory.clear()
        manager.handleDelete(bob, mailId)

        assertEquals(0, bob.inventory.size)
        assertEquals(0, persistence.loadMails("bob").size)
    }

    @Test
    fun knownPlayerNames_returnsExpectedNames() {
        val (manager) = setup()
        val names = manager.knownPlayerNames()
        assertTrue("alice" in names)
        assertTrue("bob" in names)
        assertFalse(names.any { it.contains("mailbox") })
    }
}
