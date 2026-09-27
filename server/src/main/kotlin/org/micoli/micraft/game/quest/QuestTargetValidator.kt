package org.micoli.micraft.game.quest

import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.world.ZoneTier

data class QuestTargetReport(val errors: List<String>, val warnings: List<String>)

/**
 * Checks that every kill objective target is a known NPC type (error) and that it can spawn
 * somewhere within the Quest's Danger tier (warning).
 */
object QuestTargetValidator {
    fun validate(
        quests: Collection<QuestDefinition>,
        npcTypes: Map<String, NpcDefinition>,
    ): QuestTargetReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        for (quest in quests) {
            val tierLevels = ZoneTier.fromZoneLevel(quest.level).npcLevelRange
            for (objective in quest.objectives) {
                val target = npcTypes[objective.npcType]
                if (target == null) {
                    errors += "Quest '${quest.id}' targets unknown NPC type '${objective.npcType}'"
                    continue
                }
                if (!canSpawnWithin(target, tierLevels)) {
                    warnings +=
                        "Quest '${quest.id}' (level ${quest.level}) targets '${objective.npcType}', " +
                            "which never spawns at Danger levels $tierLevels"
                }
            }
        }
        return QuestTargetReport(errors, warnings)
    }

    private fun canSpawnWithin(target: NpcDefinition, levels: IntRange): Boolean =
        target.spawn.autoSpawn && target.minLevel <= levels.last && target.maxLevel >= levels.first
}
