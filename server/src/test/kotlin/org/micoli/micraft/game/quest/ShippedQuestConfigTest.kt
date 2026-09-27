package org.micoli.micraft.game.quest

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.NpcRegistryLoader

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
}
