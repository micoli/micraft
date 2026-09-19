package org.micoli.micraft.input

import org.micoli.micraft.protocol.ClientMessage

/** Mini-game room payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface MiniGameEvent

data class MiniGameCreateEvent(val json: String) : ClientInputEvent(), MiniGameEvent

data class MiniGameInviteEvent(val json: String) : ClientInputEvent(), MiniGameEvent

data class MiniGameRespondEvent(val json: String) : ClientInputEvent(), MiniGameEvent

data class MiniGameLeaveEvent(val json: String) : ClientInputEvent(), MiniGameEvent

/** Opaque game payload — decoded to a [ClientMessage] envelope only, never inspected further. */
data class MiniGameActionEvent(val json: String) : ClientInputEvent(), MiniGameEvent

class MiniGameEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: MiniGameEvent) {
        when (event) {
            is MiniGameCreateEvent -> ctx.decodeSilently<ClientMessage.MiniGameCreate>(event.json)
            is MiniGameInviteEvent -> ctx.decodeSilently<ClientMessage.MiniGameInvite>(event.json)
            is MiniGameRespondEvent ->
                ctx.decodeSilently<ClientMessage.MiniGameRespondInvite>(event.json)
            is MiniGameLeaveEvent -> ctx.decodeSilently<ClientMessage.MiniGameLeave>(event.json)
            is MiniGameActionEvent -> ctx.decodeSilently<ClientMessage.MiniGameAction>(event.json)
        }
    }
}
