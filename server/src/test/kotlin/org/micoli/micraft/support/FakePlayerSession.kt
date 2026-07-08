package org.micoli.micraft.support

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.protocol.ServerMessage

class FakePlayerSession(id: String, userName: String, state: PlayerState) :
    PlayerSession(id, userName, FakeWebSocketSession(), state) {

    val sent = mutableListOf<ServerMessage>()

    override suspend fun send(msg: ServerMessage) {
        sent.add(msg)
    }
}
