package org.micoli.micraft.game.npc

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

interface NpcBehavior {
    fun tick(
        instance: NpcInstance,
        world: WorldState,
        ctx: NpcTickContext = NpcTickContext.live
    ): Boolean

    suspend fun onInteract(
        instance: NpcInstance,
        session: PlayerSession,
        ctx: NpcTickContext = NpcTickContext.live,
        send: suspend (ServerMessage) -> Unit,
    ) {}
}

suspend fun NpcTickContext.tooFarToInteract(
    instance: NpcInstance,
    session: PlayerSession,
    send: suspend (ServerMessage) -> Unit,
): Boolean {
    val distSq = session.state.pos.distanceSquaredTo(instance.state.pos)
    if (distSq <= tuning.interactionRange * tuning.interactionRange) return false
    i18n?.let {
        send(ServerMessage.Notification(it.t(session.state.language, "npc:server:too_far")))
    }
    return true
}
