package org.micoli.micraft.npc

import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.WorldState

interface NpcBehavior {
    fun tick(instance: NpcInstance, world: WorldState): Boolean

    suspend fun onInteract(
        instance: NpcInstance,
        session: PlayerSession,
        send: suspend (ServerMessage) -> Unit
    ) {}
}
