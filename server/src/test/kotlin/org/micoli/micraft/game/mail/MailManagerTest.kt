package org.micoli.micraft.game.mail

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.MailMessage
import org.micoli.micraft.quest.QuestStatus
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class MailManagerTest {
    @Test
    fun claimAttachments_updatesFetchQuestProgress() = runBlocking {
        val persistence = MailPersistence(Files.createTempDirectory("mail-manager"))
        val registry = SessionRegistry()
        val alice = testSession(id = "alice-id", name = "Alice")
        registry[alice.id] = alice

        val qm = QuestManager(getSessions = { registry.all() }, savePlayer = {})
        qm.reloadDefinitions(
            mapOf(
                "flint_run" to
                    QuestDefinition(
                        id = "flint_run",
                        title = "Flint Run",
                        description = "Collect flint.",
                        type = QuestType.FETCH,
                        itemType = "FLINT",
                        requiredCount = 10,
                    )))
        qm.accept(alice, "flint_run")

        val mailManager =
            MailManager(persistence, registry, testI18n(), savePlayer = {}, questManager = qm)
        val mail =
            MailMessage(
                id = "mail-1",
                from = "system",
                to = "Alice",
                subject = "Reward",
                body = "",
                attachments = mapOf(ItemType("FLINT") to 10),
                sentAt = System.currentTimeMillis(),
            )
        persistence.addMail("Alice", mail)

        mailManager.handleClaimAttachments(alice, "mail-1")

        assertEquals(10, alice.inventory[ItemType("FLINT")])
        assertEquals(QuestStatus.COMPLETED, alice.state.quests["flint_run"]?.status)
    }
}
