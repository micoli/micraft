package org.micoli.micraft.game.npc.behaviors

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

class SellerNpcBehavior : NpcBehavior {
    override fun tick(instance: NpcInstance, world: WorldState, ctx: NpcTickContext): Boolean =
        NpcPhysics.applyGravity(instance, world)

    override suspend fun onInteract(
        instance: NpcInstance,
        session: PlayerSession,
        ctx: NpcTickContext,
        send: suspend (ServerMessage) -> Unit,
    ) {
        val playerPos = session.state.pos
        val npcPos = instance.state.pos
        val dx = playerPos.x - npcPos.x
        val dy = playerPos.y - npcPos.y
        val dz = playerPos.z - npcPos.z
        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq > ctx.tuning.interactionRange * ctx.tuning.interactionRange) return

        val shopItemsJson = buildJsonArray {
            instance.definition.shopItems.forEach { item ->
                add(
                    buildJsonObject {
                        put("itemType", item.itemType)
                        put("buyPrice", item.buyPrice)
                        put("sellPrice", item.sellPrice)
                    })
            }
        }

        val payload =
            buildJsonObject {
                    put("type", "seller")
                    put("name", instance.state.name)
                    put("npcId", instance.state.id)
                    put("shopItems", shopItemsJson)
                }
                .toString()
        send(ServerMessage.NpcInteractResult(instance.state.id, payload))
    }
}
