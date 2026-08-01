package org.micoli.micraft.simulation

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.tick.TickInput
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.protocol.ServerMessage

/** Neutral input: standing still, no jump, no fly toggle. */
val IDLE_TICK_INPUT =
    TickInput(
        dx = 0f,
        dz = 0f,
        dy = 0f,
        yaw = 0f,
        pitch = 0f,
        stance = PlayerStance.STANDING,
        jumpRequested = false,
        flyToggleRequested = false,
        speedUpRequested = false,
        speedDownRequested = false,
    )

/**
 * Headless player driven by the admin UI instead of a game client. Messages the server would send
 * it are dropped; [pendingInput] is what the movement processor consumes on the next tick.
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

    @Volatile var pendingInput: TickInput = IDLE_TICK_INPUT.copy(yaw = state.orientation.yaw)

    override suspend fun send(msg: ServerMessage) {}

    override suspend fun sendChunk(msg: ServerMessage.ChunkData) {}

    /** Consume the queued input; movement intents last a single tick, like a real key press. */
    fun takeInput(): TickInput {
        val input = pendingInput
        pendingInput = input.copy(dx = 0f, dz = 0f, dy = 0f, jumpRequested = false)
        return input
    }
}
