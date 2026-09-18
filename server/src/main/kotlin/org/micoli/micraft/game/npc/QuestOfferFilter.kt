package org.micoli.micraft.game.npc

import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.quest.QuestOfferSummary
import org.micoli.micraft.quest.QuestProgress
import org.micoli.micraft.quest.QuestStatus

/**
 * Quests from [offersQuests] the player can actually accept right now (level, `dependsOn`,
 * cooldown, not already active/completed-non-repeatable). Single source of truth for "what can this
 * NPC legitimately offer" — used both by
 * [org.micoli.micraft.game.npc.behaviors.QuestGiverNpcBehavior] and by `ChatNpcBehavior` to
 * validate a quest id an LLM proposes before ever trusting it.
 */
fun computeOfferableQuests(
    qm: QuestManager,
    offersQuests: List<String>,
    playerLevel: Int,
    playerQuests: Map<String, QuestProgress>,
): List<QuestOfferSummary> {
    val definitions = qm.getDefinitions()
    return offersQuests.mapNotNull { questId ->
        val def = definitions[questId] ?: return@mapNotNull null
        val current = playerQuests[questId]
        if (current?.status == QuestStatus.IN_PROGRESS ||
            current?.status == QuestStatus.READY_TO_TURN_IN) {
            return@mapNotNull null
        }
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
}
