package org.micoli.micraft.game.npc.behaviors

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.npc.tooFarToInteract
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

class InteractionableNpcBehavior : NpcBehavior {
    override fun tick(instance: NpcInstance, world: WorldState, ctx: NpcTickContext): Boolean =
        NpcPhysics.applyGravity(instance, world)

    override suspend fun onInteract(
        instance: NpcInstance,
        session: PlayerSession,
        ctx: NpcTickContext,
        send: suspend (ServerMessage) -> Unit,
    ) {
        if (ctx.tooFarToInteract(instance, session, send)) return

        val payload =
            buildJsonObject {
                    put("type", instance.state.type)
                    put("name", instance.state.name)
                }
                .toString()
        send(ServerMessage.NpcInteractResult(instance.state.id, payload))
    }
}
