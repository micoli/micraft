package org.micoli.micraft.game.mail

import java.util.UUID
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.MailMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(MailManager::class.java)

class MailManager(
    private val persistence: MailPersistence,
    private val sessionRegistry: SessionRegistry,
    private val i18n: I18nConfig,
    private val savePlayer: (PlayerSession) -> Unit,
) {
    fun loadForPlayer(name: String): List<MailMessage> = persistence.loadMails(name)

    fun knownPlayerNames(): List<String> {
        val onlineNames = sessionRegistry.all().map { it.state.name }
        val onlineSanitized = onlineNames.map { it.sanitize() }.toSet()
        val offlineNames = persistence.knownPlayerNames().filter { it !in onlineSanitized }
        return (onlineNames + offlineNames).distinct().sorted()
    }

    suspend fun handleSendMail(session: PlayerSession, msg: ClientMessage.SendMail) {
        val lang = session.state.language
        val recipientName = msg.to.trim()
        log.info("[mail] handleSendMail from={} to={}", session.state.name, recipientName)

        if (recipientName.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "mail:server:recipient_invalid")))
            return
        }
        if (!persistence.playerExists(recipientName)) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(lang, "mail:server:recipient_not_found", recipientName)))
            return
        }
        if (msg.copperAmount < 0) {
            session.send(ServerMessage.Notification(i18n.t(lang, "mail:server:recipient_invalid")))
            return
        }
        if (msg.copperAmount > 0 && session.state.wallet < msg.copperAmount) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "mail:server:insufficient_copper")))
            return
        }
        if (msg.attachments.isNotEmpty()) {
            for ((type, count) in msg.attachments) {
                val owned = session.inventory.getOrDefault(type, 0)
                if (owned < count) {
                    session.send(
                        ServerMessage.Notification(i18n.t(lang, "mail:server:insufficient_items")))
                    return
                }
            }
            for ((type, count) in msg.attachments) {
                session.inventory.merge(type, -count, Int::plus)
                if ((session.inventory[type] ?: 0) <= 0) session.inventory.remove(type)
            }
        }
        val needsSave = msg.attachments.isNotEmpty() || msg.copperAmount > 0
        if (msg.copperAmount > 0) {
            session.state = session.state.copy(wallet = session.state.wallet - msg.copperAmount)
            session.send(ServerMessage.WalletUpdate(session.state.wallet))
        }
        if (needsSave) {
            savePlayer(session)
            if (msg.attachments.isNotEmpty()) {
                session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
            }
        }

        val mail =
            MailMessage(
                id = UUID.randomUUID().toString(),
                from = session.state.name,
                to = recipientName,
                subject = msg.subject.take(120),
                body = msg.body.take(4000),
                attachments = msg.attachments,
                copperAmount = msg.copperAmount,
                sentAt = System.currentTimeMillis(),
            )
        persistence.addMail(recipientName, mail)
        log.info("[mail] mail persisted id={}", mail.id)

        val recipientSession =
            sessionRegistry.all().firstOrNull {
                it.state.name == recipientName ||
                    it.state.name.sanitize() == recipientName.sanitize()
            }
        log.info("[mail] recipientSession found={}", recipientSession != null)
        if (recipientSession != null) {
            log.info("[mail] sending MailReceived to {}", recipientName)
            recipientSession.send(ServerMessage.MailReceived(mail))
            log.info("[mail] MailReceived sent OK")
            recipientSession.send(
                ServerMessage.Notification(
                    i18n.t(
                        recipientSession.state.language,
                        "mail:server:new_mail",
                        session.state.name,
                        mail.subject)))
        }
        session.send(
            ServerMessage.Notification(i18n.t(lang, "mail:server:send_success", recipientName)))
    }

    // No sender to debit — for system-driven credits (e.g. auction settlement) to a maybe-offline
    // player.
    suspend fun deliverSystemMail(
        to: String,
        subject: String,
        body: String,
        attachments: Map<ItemType, Int> = emptyMap(),
        copperAmount: Long = 0L,
    ) {
        val mail =
            MailMessage(
                id = UUID.randomUUID().toString(),
                from = "system",
                to = to,
                subject = subject.take(120),
                body = body.take(4000),
                attachments = attachments,
                copperAmount = copperAmount,
                sentAt = System.currentTimeMillis(),
            )
        persistence.addMail(to, mail)
        val recipientSession =
            sessionRegistry.all().firstOrNull {
                it.state.name == to || it.state.name.sanitize() == to.sanitize()
            }
        if (recipientSession != null) {
            recipientSession.send(ServerMessage.MailReceived(mail))
            recipientSession.send(
                ServerMessage.Notification(
                    i18n.t(
                        recipientSession.state.language,
                        "mail:server:new_mail",
                        "system",
                        mail.subject)))
        }
    }

    suspend fun handleMarkSeen(session: PlayerSession, mailId: String) {
        val mails = persistence.loadMails(session.state.name)
        val mail = mails.firstOrNull { it.id == mailId } ?: return
        if (mail.seen) return
        val updated = mail.copy(seen = true)
        persistence.updateMail(session.state.name, updated)
        session.send(ServerMessage.MailUpdate(updated))
    }

    suspend fun handleDelete(session: PlayerSession, mailId: String) {
        val mails = persistence.loadMails(session.state.name)
        val mail = mails.firstOrNull { it.id == mailId } ?: return
        if (!mail.attachmentsClaimed) {
            if (mail.attachments.isNotEmpty() || mail.copperAmount > 0) {
                returnAttachments(session, mail.attachments, mail.copperAmount)
            }
        }
        persistence.deleteMail(session.state.name, mailId)
        session.send(ServerMessage.MailDeleted(mailId))
    }

    suspend fun handleClaimAttachments(session: PlayerSession, mailId: String) {
        val mails = persistence.loadMails(session.state.name)
        val mail = mails.firstOrNull { it.id == mailId } ?: return
        if (mail.attachmentsClaimed || (mail.attachments.isEmpty() && mail.copperAmount <= 0))
            return
        returnAttachments(session, mail.attachments, mail.copperAmount)
        val updated = mail.copy(attachmentsClaimed = true)
        persistence.updateMail(session.state.name, updated)
        session.send(ServerMessage.MailUpdate(updated))
    }

    private suspend fun returnAttachments(
        session: PlayerSession,
        attachments: Map<ItemType, Int>,
        copperAmount: Long = 0L,
    ) {
        for ((type, count) in attachments) {
            session.inventory.merge(type, count, Int::plus)
        }
        if (copperAmount > 0) {
            session.state = session.state.copy(wallet = session.state.wallet + copperAmount)
            session.send(ServerMessage.WalletUpdate(session.state.wallet))
        }
        savePlayer(session)
        if (attachments.isNotEmpty()) {
            session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        }
    }
}
