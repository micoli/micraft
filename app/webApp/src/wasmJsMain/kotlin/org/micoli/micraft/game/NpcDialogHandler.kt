package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsOpenNpcChatDialog
import org.micoli.micraft.babylon.jsOpenQuestGiverDialog
import org.micoli.micraft.protocol.ServerMessage

/** NPC dialog popups pushed by the server — quest-giver offers and freeform NPC chat replies. */
class NpcDialogHandler : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.QuestGiverDialog -> jsOpenQuestGiverDialog(Json.encodeToString(msg))
            is ServerMessage.NpcChatReply -> jsOpenNpcChatDialog(Json.encodeToString(msg))
            else -> Unit
        }
}
