package org.micoli.micraft.input

import org.micoli.micraft.protocol.ClientMessage

/** Mailbox payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface MailEvent

data class MailSend(val json: String) : ClientInputEvent(), MailEvent

data class MailSeen(val mailId: String) : ClientInputEvent(), MailEvent

data class MailDelete(val mailId: String) : ClientInputEvent(), MailEvent

data class MailClaim(val mailId: String) : ClientInputEvent(), MailEvent

class MailEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: MailEvent) {
        when (event) {
            is MailSend -> ctx.decodeSilently<ClientMessage.SendMail>(event.json)
            is MailSeen -> ctx.outMessages.trySend(ClientMessage.MarkMailSeen(event.mailId))
            is MailDelete -> ctx.outMessages.trySend(ClientMessage.DeleteMail(event.mailId))
            is MailClaim ->
                ctx.outMessages.trySend(ClientMessage.ClaimMailAttachments(event.mailId))
        }
    }
}
