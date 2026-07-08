package org.micoli.micraft.game.npc

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

interface NpcBehavior {
    fun tick(instance: NpcInstance, world: WorldState): Boolean

    suspend fun onInteract(
        instance: NpcInstance,
        session: PlayerSession,
        send: suspend (ServerMessage) -> Unit
    ) {}
}
