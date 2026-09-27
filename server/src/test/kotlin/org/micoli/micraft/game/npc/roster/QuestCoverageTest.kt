package org.micoli.micraft.game.npc.roster

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcSpawnConfig
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.quest.KillObjective
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.biome.BiomeDefinition

class QuestCoverageTest {
    private fun biome(id: String, liquid: Boolean = false) =
        BiomeDefinition(
            id = id,
            zones = emptyList(),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            liquid = liquid)

    private val wolf =
        NpcDefinition(
            type = "wolf",
            behavior = StaticNpcBehavior(),
            bbmodelFile = "npc",
            width = 0.6f,
            height = 1.8f,
            wanderSpeed = 0f,
            wanderRadius = 0f,
            maxLevel = 5,
            spawn = NpcSpawnConfig(autoSpawn = true, spawnBiomes = listOf("forest")))

    private val wolfHunt =
        QuestDefinition(
            id = "wolf_hunt",
            title = "",
            description = "",
            type = QuestType.KILL,
            level = 3,
            objectives = listOf(KillObjective("wolf", 1)))

    @Test
    fun reportsEveryPairNoQuestCanServe() {
        val gaps =
            QuestCoverage.gaps(
                listOf(biome("forest"), biome("desert")), mapOf("wolf" to wolf), listOf(wolfHunt))

        val forest = gaps.filter { it.biome == "forest" }.map { it.tier }
        val desert = gaps.filter { it.biome == "desert" }.map { it.tier }
        assertEquals(listOf(2, 3, 4, 5), forest)
        assertEquals(listOf(1, 2, 3, 4, 5), desert)
        assertEquals(emptySet(), gaps.first { it.biome == "forest" && it.tier == 2 }.npcTypes)
    }

    @Test
    fun liquidBiomesAlwaysReportAMissingGiver() {
        val gaps =
            QuestCoverage.gaps(listOf(biome("sea", liquid = true)), emptyMap(), listOf(wolfHunt))

        assertEquals(5, gaps.size)
        assertEquals("no dry ground for a Quest giver", gaps.first().reason)
    }
}
