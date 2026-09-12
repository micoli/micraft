package org.micoli.micraft.game.npc.behaviors

import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.quest.QuestOfferSummary
import org.micoli.micraft.quest.QuestStatus

/**
 * Offers the quests listed in [org.micoli.micraft.game.npc.NpcDefinition.offersQuests], filtered to
 * what the player can actually accept (level, `dependsOn`, cooldown, not already active). Does not
 * accept quests itself — the dialog's "accept" action replays through the existing `/quest accept`
 * path (`QuestManager.accept`) so there is one acceptance code path, not two.
 */
class QuestGiverNpcBehavior : NpcBehavior {
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

        val qm = ctx.questManager ?: return
        val definitions = qm.getDefinitions()
        val playerLevel = session.characterData?.level ?: 1
        val playerQuests = session.state.quests

        val offerable =
            instance.definition.offersQuests.mapNotNull { questId ->
                val def = definitions[questId] ?: return@mapNotNull null
                val current = playerQuests[questId]
                if (current?.status == QuestStatus.IN_PROGRESS) return@mapNotNull null
                if (current?.status == QuestStatus.COMPLETED && !def.repeatable) {
                    return@mapNotNull null
                }
                if (def.level > playerLevel + 2) return@mapNotNull null
                if (def.dependsOn.any { playerQuests[it]?.status != QuestStatus.COMPLETED }) {
                    return@mapNotNull null
                }
                val lastCompletedAt = current?.lastCompletedAt
                if (def.repeatable && def.cooldownSeconds > 0 && lastCompletedAt != null) {
                    val remainingMs =
                        lastCompletedAt + def.cooldownSeconds * 1000 - System.currentTimeMillis()
                    if (remainingMs > 0) return@mapNotNull null
                }
                QuestOfferSummary(def.id, def.title, def.description, def.level)
            }

        val turnInable =
            instance.definition.offersQuests.filter { questId ->
                playerQuests[questId]?.status == QuestStatus.IN_PROGRESS
            }

        send(
            ServerMessage.QuestGiverDialog(
                instance.state.id, instance.state.type, offerable, turnInable))
    }
}
