package org.micoli.micraft.game.quest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcSpawnConfig
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior

class QuestTargetValidatorTest {
    private fun npc(
        type: String,
        minLevel: Int = 0,
        maxLevel: Int = Int.MAX_VALUE,
        autoSpawn: Boolean = true
    ) =
        NpcDefinition(
            type = type,
            behavior = StaticNpcBehavior(),
            bbmodelFile = "npc",
            width = 0.6f,
            height = 1.8f,
            wanderSpeed = 0f,
            wanderRadius = 0f,
            minLevel = minLevel,
            maxLevel = maxLevel,
            spawn = NpcSpawnConfig(autoSpawn = autoSpawn),
        )

    private fun killQuest(id: String, level: Int, vararg targets: String) =
        QuestDefinition(
            id = id,
            title = id,
            description = "",
            type = QuestType.KILL,
            level = level,
            objectives = targets.map { KillObjective(it, 1) },
        )

    @Test
    fun unknownTargetIsAnError() {
        val report =
            QuestTargetValidator.validate(
                listOf(killQuest("goat_patrol", 1, "goat")), mapOf("wolf" to npc("wolf")))

        assertEquals(1, report.errors.size)
        assertTrue("goat_patrol" in report.errors.single() && "goat" in report.errors.single())
    }

    @Test
    fun targetOutsideTheQuestTierIsAWarning() {
        val report =
            QuestTargetValidator.validate(
                listOf(killQuest("wolf_hunt", 3, "bear")), mapOf("bear" to npc("bear", 11, 15)))

        assertTrue(report.errors.isEmpty())
        assertEquals(1, report.warnings.size)
        assertTrue("wolf_hunt" in report.warnings.single() && "bear" in report.warnings.single())
    }

    @Test
    fun targetThatNeverAutoSpawnsIsAWarning() {
        val report =
            QuestTargetValidator.validate(
                listOf(killQuest("hunt", 1, "camel")),
                mapOf("camel" to npc("camel", autoSpawn = false)))

        assertEquals(1, report.warnings.size)
    }

    @Test
    fun targetOverlappingTheQuestTierIsValid() {
        val report =
            QuestTargetValidator.validate(
                listOf(killQuest("bandits", 12, "bandit_man")),
                mapOf("bandit_man" to npc("bandit_man", 11, 15)))

        assertTrue(report.errors.isEmpty())
        assertTrue(report.warnings.isEmpty())
    }

    @Test
    fun nonKillQuestsAreIgnored() {
        val fetch =
            QuestDefinition(
                id = "fetch",
                title = "",
                description = "",
                type = QuestType.FETCH,
                itemType = "oak_log")

        val report = QuestTargetValidator.validate(listOf(fetch), emptyMap())

        assertTrue(report.errors.isEmpty() && report.warnings.isEmpty())
    }

    @Test
    fun bossTargetsAreValidatedToo() {
        val boss = killQuest("yeti_boss", 22, "yeti").copy(type = QuestType.BOSS)

        val report = QuestTargetValidator.validate(listOf(boss), emptyMap())

        assertEquals(1, report.errors.size)
    }
}
