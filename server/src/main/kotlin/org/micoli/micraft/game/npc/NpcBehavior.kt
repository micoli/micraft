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
