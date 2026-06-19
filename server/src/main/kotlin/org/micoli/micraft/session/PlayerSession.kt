package org.micoli.micraft.session

import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage

class PlayerSession(
    val id: String,
    val socket: DefaultWebSocketSession,
    @Volatile var state: PlayerState,
    @Volatile var vy: Float = 0f,
) {
    val intents = Channel<ClientMessage>(capacity = Channel.UNLIMITED)

    suspend fun send(msg: ServerMessage) {
        socket.send(Frame.Text(Json.encodeToString(msg)))
    }
}
