package org.micoli.micraft.tick

import org.micoli.micraft.combat.CombatProcessor
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.session.hasPermission

class IntentCollector(
    private val blockBreaker: BlockBreaker,
    private val blockPlacer: BlockPlacer,
    private val onCommand: suspend (PlayerSession, String) -> Unit,
    private val onChatSend: suspend (PlayerSession, String, String) -> Unit = { _, _, _ -> },
    private val combatProcessor: CombatProcessor? = null,
) {
    suspend fun collect(session: PlayerSession): TickInput {
        var dx = 0f
        var dz = 0f
        var dy = 0f
        var yaw = session.state.orientation.yaw
        var pitch = session.state.orientation.pitch
        var stance = session.state.stance
        var jumpRequested = false
        var flyToggleRequested = false
        var speedUpRequested = false
        var speedDownRequested = false
        var receivedMove = false

        while (true) {
            val intent = session.intents.tryReceive().getOrNull() ?: break
            when (intent) {
                is ClientMessage.MoveIntent -> {
                    receivedMove = true
                    dx = intent.dx
                    dz = intent.dz
                    dy = intent.dy
                    yaw = intent.yaw
                    pitch = intent.pitch
                    stance = intent.stance
                    if (intent.jump) jumpRequested = true
                    if (intent.flyToggle && session.hasPermission("action.fly"))
                        flyToggleRequested = true
                    if (intent.speedUp) speedUpRequested = true
                    if (intent.speedDown) speedDownRequested = true
                }
                is ClientMessage.BlockBreakStart ->
                    if (session.hasPermission("action.break"))
                        blockBreaker.handleStart(session, intent)
                is ClientMessage.BlockBreakStop -> blockBreaker.handleStop(session)
                is ClientMessage.BlockPlace ->
                    if (session.hasPermission("action.place"))
                        blockPlacer.handlePlace(session, intent)
                is ClientMessage.ShortcutBarSet -> blockPlacer.handleShortcutBarSet(session, intent)
                is ClientMessage.Command -> onCommand(session, intent.text)
                is ClientMessage.ChatSend -> onChatSend(session, intent.channel, intent.text)
                is ClientMessage.SetCombatTarget ->
                    combatProcessor?.handleSetTarget(session, intent)
                is ClientMessage.AttackTarget -> combatProcessor?.handleAttack(session, intent)
                else -> {}
            }
        }

        if (receivedMove) {
            session.lastMoveDx = dx
            session.lastMoveDz = dz
            session.lastMoveDy = dy
        } else {
            dx = session.lastMoveDx
            dz = session.lastMoveDz
            dy = session.lastMoveDy
        }

        return TickInput(
            dx,
            dz,
            dy,
            yaw,
            pitch,
            stance,
            jumpRequested,
            flyToggleRequested,
            speedUpRequested,
            speedDownRequested)
    }
}
