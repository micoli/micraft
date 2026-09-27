package org.micoli.micraft.game.npc.roster

import org.micoli.micraft.game.quest.QuestDefinition

/** The Quests a Region's Quest giver may offer (ADR-0010). */
object RegionQuests {
    /**
     * Quest ids, sorted, whose level fits the Region's Danger tier and whose targets are all in
     * [roster].
     */
    fun suitedTo(roster: Roster, quests: Collection<QuestDefinition>): List<String> {
        val levels = roster.region.dangerTier.npcLevelRange
        val types = roster.types
        return quests
            .filter { quest ->
                quest.level in levels && quest.objectives.all { it.npcType in types }
            }
            .map { it.id }
            .sorted()
    }
}
