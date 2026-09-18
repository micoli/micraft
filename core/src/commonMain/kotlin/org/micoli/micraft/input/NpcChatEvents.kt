package org.micoli.micraft.input

import org.micoli.micraft.protocol.ClientMessage

/** NPC dialogue payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface NpcChatEvent

data class NpcChatSend(val json: String) : ClientInputEvent(), NpcChatEvent

data class NpcChatAcceptGift(val json: String) : ClientInputEvent(), NpcChatEvent

class NpcChatEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: NpcChatEvent) {
        when (event) {
            is NpcChatSend -> ctx.decodeSilently<ClientMessage.NpcChatSend>(event.json)
            is NpcChatAcceptGift -> ctx.decodeSilently<ClientMessage.NpcChatAcceptGift>(event.json)
        }
    }
}
