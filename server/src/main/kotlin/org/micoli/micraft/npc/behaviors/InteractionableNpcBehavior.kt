package org.micoli.micraft.npc.behaviors

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.micoli.micraft.npc.NpcBehavior
import org.micoli.micraft.npc.NpcConstants
import org.micoli.micraft.npc.NpcInstance
import org.micoli.micraft.npc.NpcPhysics
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.WorldState

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

        val payload = buildJsonObject {
            put("type", instance.state.type)
            put("name", instance.state.name)
        }.toString()
        send(ServerMessage.NpcInteractResult(instance.state.id, payload))
    }
}
