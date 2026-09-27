package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.auth.CorePermissions
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcSpawnConfig
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.quest.KillObjective
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator
import org.micoli.micraft.quest.QuestStatus
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class QuestCommandTest {
    private val cmd = QuestCommand()

    /** A level-3 plains Region whose Roster holds `deer`: only deer_hunt suits it. */
    private val world = WorldState(FlatArenaChunkGenerator(regionBudget = 10, zoneLevel = 3))

    private val deer =
        NpcDefinition(
            type = "deer",
            behavior = RandomMovableNpcBehavior(),
            bbmodelFile = "npc",
            width = 0.5f,
            height = 0.9f,
            wanderSpeed = 2f,
            wanderRadius = 8f,
            spawn = NpcSpawnConfig(autoSpawn = true))

    private fun quest(id: String, target: String) =
        QuestDefinition(
            id = id,
            title = id,
            description = "",
            type = QuestType.KILL,
            level = 2,
            objectives = listOf(KillObjective(target, 1)))

    private val questManager =
        QuestManager(getSessions = { emptyList() }, savePlayer = {}).also {
            it.reloadDefinitions(
                listOf(quest("deer_hunt", "deer"), quest("eel_hunt", "eel")).associateBy { q ->
                    q.id
                })
        }

    private fun context() =
        testContext(
            world = world,
            npcManager =
                NpcManager(broadcast = {}).also { it.loadDefinitions(mapOf("deer" to deer)) },
            questManager = questManager)

    @Test
    fun accept_questOfferedInTheRegion_isAccepted() = runBlocking {
        val session = testSession()
        cmd.execute(session, "accept deer_hunt", context())
        assertEquals(QuestStatus.IN_PROGRESS, session.state.quests["deer_hunt"]?.status)
    }

    @Test
    fun accept_questNotOfferedInTheRegion_isRefused() = runBlocking {
        val session = testSession()
        cmd.execute(session, "accept eel_hunt", context())
        assertNull(session.state.quests["eel_hunt"])
        assertTrue(session.sent.any { it.toString().contains("eel_hunt") })
    }

    @Test
    fun accept_adminBypassesTheRegion() = runBlocking {
        val session = testSession().also { it.permissions = setOf(CorePermissions.ADMIN) }
        cmd.execute(session, "accept eel_hunt", context())
        assertEquals(QuestStatus.IN_PROGRESS, session.state.quests["eel_hunt"]?.status)
    }

    @Test
    fun questIdCompletion_listsTheRegionOffers() = runBlocking {
        val names = cmd.completeArg(1, "", testSession(), context())
        assertEquals(listOf("deer_hunt"), names)
    }

    @Test
    fun accept_unknownQuest_isReportedAsNotFound() = runBlocking {
        val session = testSession()
        cmd.execute(session, "accept no_such_quest", context())
        assertTrue(session.sent.none { it.toString().contains("in this Region") })
    }
}
