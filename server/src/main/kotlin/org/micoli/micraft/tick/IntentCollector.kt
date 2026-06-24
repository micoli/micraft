package org.micoli.micraft.tick

import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.session.PlayerSession

class IntentCollector(
    private val blockBreaker: BlockBreaker,
    private val blockPlacer: BlockPlacer,
    private val onCommand: suspend (PlayerSession, String) -> Unit,
    private val onChatSend: suspend (PlayerSession, String, String) -> Unit = { _, _, _ -> },
) {
    suspend fun collect(session: PlayerSession): TickInput {
        var dx = 0f; var dz = 0f; var dy = 0f
        var yaw   = session.state.orientation.yaw
        var pitch = session.state.orientation.pitch
        var stance = session.state.stance
        var jumpRequested      = false
        var flyToggleRequested = false
        var speedUpRequested   = false
        var speedDownRequested = false

        while (true) {
            val intent = session.intents.tryReceive().getOrNull() ?: break
            when (intent) {
                is ClientMessage.MoveIntent -> {
                    dx += intent.dx; dz += intent.dz; dy += intent.dy
                    yaw = intent.yaw; pitch = intent.pitch; stance = intent.stance
                    if (intent.jump)      jumpRequested      = true
                    if (intent.flyToggle) flyToggleRequested = true
                    if (intent.speedUp)   speedUpRequested   = true
                    if (intent.speedDown) speedDownRequested = true
                }
                is ClientMessage.BlockBreakStart -> blockBreaker.handleStart(session, intent)
                is ClientMessage.BlockBreakStop  -> blockBreaker.handleStop(session)
                is ClientMessage.BlockPlace      -> blockPlacer.handlePlace(session, intent)
                is ClientMessage.ShortcutBarSet  -> blockPlacer.handleShortcutBarSet(session, intent)
                is ClientMessage.Command         -> onCommand(session, intent.text)
                is ClientMessage.ChatSend        -> onChatSend(session, intent.channel, intent.text)
                else -> {}
            }
        }

        return TickInput(dx, dz, dy, yaw, pitch, stance, jumpRequested, flyToggleRequested, speedUpRequested, speedDownRequested)
    }
}
