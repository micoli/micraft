package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsMailDeleted
import org.micoli.micraft.babylon.jsMailReceived
import org.micoli.micraft.babylon.jsMailSync
import org.micoli.micraft.babylon.jsMailUpdate
import org.micoli.micraft.babylon.jsOpenMailbox
import org.micoli.micraft.protocol.ServerMessage

/** In-game mailbox sync and notifications. */
class MailHandler : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.MailSync -> jsMailSync(Json.encodeToString(msg))
            is ServerMessage.MailReceived -> jsMailReceived(Json.encodeToString(msg))
            is ServerMessage.MailUpdate -> jsMailUpdate(Json.encodeToString(msg))
            is ServerMessage.MailDeleted -> jsMailDeleted(msg.mailId)
            is ServerMessage.OpenMailbox -> jsOpenMailbox()
            else -> Unit
        }
}
