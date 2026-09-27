package org.micoli.micraft.game.quest

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.roster.QuestCoverage
import org.micoli.micraft.game.world.ZoneTier
import org.micoli.micraft.game.world.biome.loadBiomeRegistry

/** The Quests the game actually ships, validated against the shipped NPC types. */
class ShippedQuestConfigTest {
    private val entitiesPath = Path.of("resources/entities")
    private val questsPath = Path.of("resources/quests")

    @Test
    fun everyShippedKillTargetIsAnNpcType() {
        assertTrue(questsPath.exists(), "resources/quests must be reachable from the test cwd")
        val npcTypes =
            NpcRegistryLoader(
                    resourcesEntityPath = entitiesPath,
                    dataEntityPath = Path.of("data/resources/entities"),
                )
                .load()
        val quests = QuestRegistryLoader(questsPath).load()

        val report = QuestTargetValidator.validate(quests.values, npcTypes)
        report.warnings.forEach(::println)

        assertTrue(quests.isNotEmpty(), "expected the shipped quests")
        assertTrue(report.errors.isEmpty(), report.errors.joinToString("\n"))
    }

    /** Not a failure: the table is the backlog of Quests to write (issue 07). */
    @Test
    fun printsTheQuestCoverageOfEveryBiomeAndDangerTier() {
        val npcTypes =
            NpcRegistryLoader(
                    resourcesEntityPath = entitiesPath,
                    dataEntityPath = Path.of("data/resources/entities"))
                .load()
        val biomes =
            loadBiomeRegistry(
                    path = Path.of("data/config/biomes.yaml"),
                    resourcesPath = Path.of("resources/config/biomes.yaml"))
                .biomes
        val gaps =
            QuestCoverage.gaps(biomes, npcTypes, QuestRegistryLoader(questsPath).load().values)

        println("Quest coverage gaps (${gaps.size} of ${biomes.size * ZoneTier.entries.size}):")
        gaps.forEach {
            println(
                "  ${it.biome} T${it.tier.tier}: ${it.reason}; NPC types: ${it.npcTypes.joinToString(", ")}")
        }
        assertTrue(
            gaps.size < biomes.size * ZoneTier.entries.size,
            "no Biome x Danger tier pair has a Quest at all")
    }
}
