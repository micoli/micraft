package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsPetRosterUpdate
import org.micoli.micraft.protocol.ServerMessage

/** Tamed/summoned pet roster sync. */
class PetRosterHandler : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.PetRosterSync -> jsPetRosterUpdate(Json.encodeToString(msg))
            else -> Unit
        }
}
