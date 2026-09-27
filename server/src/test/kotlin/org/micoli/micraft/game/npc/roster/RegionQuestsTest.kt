package org.micoli.micraft.game.npc.roster

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.quest.KillObjective
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.biome.BiomeDefinition

class RegionQuestsTest {
    private val forest =
        BiomeDefinition(
            id = "forest",
            zones = emptyList(),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT)

    private fun roster(level: Int, vararg types: String) =
        Roster(
            Region(0, 0, forest, "Woodholm", level), 25, types.map { RosterEntry(it, 1, 5, false) })

    private fun quest(
        id: String,
        level: Int,
        type: QuestType = QuestType.KILL,
        vararg targets: String
    ) =
        QuestDefinition(
            id = id,
            title = id,
            description = "",
            type = type,
            level = level,
            objectives = targets.map { KillObjective(it, 1) })

    @Test
    fun keepsQuestsWhoseTargetsAllLiveInTheRoster() {
        val quests =
            listOf(
                quest("wolves", 3, QuestType.KILL, "wolf"),
                quest("wolves_and_bears", 3, QuestType.KILL, "wolf", "bear"),
                quest("eels", 3, QuestType.KILL, "eel"),
            )

        assertEquals(listOf("wolves"), RegionQuests.suitedTo(roster(3, "wolf", "deer"), quests))
    }

    @Test
    fun keepsQuestsOfTheRegionDangerTierOnly() {
        val quests =
            listOf(
                quest("easy", 2, QuestType.KILL, "wolf"), quest("hard", 12, QuestType.KILL, "wolf"))

        assertEquals(listOf("easy"), RegionQuests.suitedTo(roster(4, "wolf"), quests))
        assertEquals(listOf("hard"), RegionQuests.suitedTo(roster(13, "wolf"), quests))
    }

    @Test
    fun bossAndFetchQuestsFollowTheSameRules() {
        val quests =
            listOf(
                quest("yeti_boss", 22, QuestType.BOSS, "yeti"),
                quest("gather_wood", 22, QuestType.FETCH),
            )

        assertEquals(listOf("gather_wood"), RegionQuests.suitedTo(roster(30, "wolf"), quests))
        assertEquals(
            listOf("gather_wood", "yeti_boss"), RegionQuests.suitedTo(roster(30, "yeti"), quests))
    }
}
