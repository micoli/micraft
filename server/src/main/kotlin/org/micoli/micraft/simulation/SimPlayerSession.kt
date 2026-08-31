package org.micoli.micraft.simulation

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.protocol.ServerMessage

/**
 * Headless player driven by the admin UI instead of a game client. Movement arrives as
 * [org.micoli.micraft.protocol.ClientMessage.MoveIntent] on [intents] (see
 * `WorldSimulator.applyPlayerInput`) and is consumed by the movement pass in `GameWorld.tick`, the
 * same path a real client takes. Frames the server would send back are dropped.
 */
class SimPlayerSession(
    id: String,
    userName: String,
    state: PlayerState,
) :
    PlayerSession(
        id = id,
        userName = userName,
        socket = NullWebSocketSession(),
        state = state,
    ) {

    override suspend fun send(msg: ServerMessage) {}

    override suspend fun sendChunk(msg: ServerMessage.ChunkData) {}
}
