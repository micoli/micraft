package org.micoli.micraft.input

import org.micoli.micraft.protocol.ClientMessage

/** Party/group payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface GroupEvent

data class GroupInvite(val playerId: String) : ClientInputEvent(), GroupEvent

data class GroupRespond(val groupId: String, val accept: Boolean) : ClientInputEvent(), GroupEvent

data class GroupKick(val playerId: String) : ClientInputEvent(), GroupEvent

data class GroupTransfer(val playerId: String) : ClientInputEvent(), GroupEvent

/** `"<groupId>\t<0|1>"` — the two-field wire shape this event's constructor can't parse itself. */
internal fun parseGroupRespond(payload: String): ClientInputEvent? {
    val parts = payload.split("\t")
    if (parts.size != 2) return null
    return GroupRespond(parts[0], parts[1] == "1")
}

class GroupEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: GroupEvent) {
        when (event) {
            is GroupInvite -> ctx.outMessages.trySend(ClientMessage.GroupInvite(event.playerId))
            is GroupRespond ->
                ctx.outMessages.trySend(
                    ClientMessage.GroupInviteRespond(event.groupId, event.accept))
            is GroupKick -> ctx.outMessages.trySend(ClientMessage.GroupKick(event.playerId))
            is GroupTransfer -> ctx.outMessages.trySend(ClientMessage.GroupTransfer(event.playerId))
        }
    }
}
