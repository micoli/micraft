package org.micoli.micraft.game.npc.behaviors

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

class InteractionableNpcBehavior : NpcBehavior {
    override fun tick(instance: NpcInstance, world: WorldState): Boolean =
        NpcPhysics.applyGravity(instance, world)

    override suspend fun onInteract(
        instance: NpcInstance,
        session: PlayerSession,
        send: suspend (ServerMessage) -> Unit,
    ) {
        val playerPos = session.state.pos
        val npcPos = instance.state.pos
        val dx = playerPos.x - npcPos.x
        val dy = playerPos.y - npcPos.y
        val dz = playerPos.z - npcPos.z
        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq > NpcConstants.INTERACTION_RANGE * NpcConstants.INTERACTION_RANGE) return

        val payload =
            buildJsonObject {
                    put("type", instance.state.type)
                    put("name", instance.state.name)
                }
                .toString()
        send(ServerMessage.NpcInteractResult(instance.state.id, payload))
    }
}
