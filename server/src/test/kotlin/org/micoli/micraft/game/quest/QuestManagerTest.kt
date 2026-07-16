package org.micoli.micraft.game.quest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.quest.QuestStatus
import org.micoli.micraft.support.testSession

private fun killDef(
    id: String = "q1",
    objectives: List<KillObjective> = listOf(KillObjective("wolf", 3)),
    repeatable: Boolean = false,
    cooldownSeconds: Long = 0,
    dependsOn: List<String> = emptyList(),
) =
    QuestDefinition(
        id = id,
        title = "Test Quest",
        description = "Kill some wolves.",
        type = QuestType.KILL,
        level = 1,
        objectives = objectives,
        rewards = QuestReward(xp = 100),
        dependsOn = dependsOn,
        repeatable = repeatable,
        cooldownSeconds = cooldownSeconds,
    )

private fun npcDef(type: String = "wolf"): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = StaticNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
    )

private fun npcInstance(type: String = "wolf", contributorId: String): NpcInstance {
    val def = npcDef(type)
    val state = NpcState(id = "npc-1", name = type, type = type, pos = Vec3(0f, 0f, 0f), yaw = 0f)
    return NpcInstance(state = state, definition = def, spawnPos = Vec3(0f, 0f, 0f)).also {
        it.damageContributors[contributorId] = 10
    }
}

private fun testQuestManager(
    session: PlayerSession,
    xpGranted: MutableList<Int> = mutableListOf(),
): QuestManager {
    val sessions = listOf(session)
    val saved = mutableListOf<PlayerSession>()
    return QuestManager(
        getSessions = { sessions },
        savePlayer = { saved.add(it) },
        grantXp = { _, xp -> xpGranted.add(xp) },
    )
}

class QuestManagerTest {
    @Test
    fun accept_setsInProgress() = runBlocking {
        val session = testSession()
        val qm = testQuestManager(session)
        qm.reloadDefinitions(mapOf("q1" to killDef()))
        qm.accept(session, "q1")
        assertEquals(QuestStatus.IN_PROGRESS, session.state.quests["q1"]?.status)
    }

    @Test
    fun accept_alreadyActive_sendsNotification() = runBlocking {
        val session = testSession()
        val qm = testQuestManager(session)
        qm.reloadDefinitions(mapOf("q1" to killDef()))
        qm.accept(session, "q1")
        session.sent.clear()
        qm.accept(session, "q1")
        // Second accept should notify "already active", not change state
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertEquals(QuestStatus.IN_PROGRESS, session.state.quests["q1"]?.status)
        assertEquals(1, notifs.size)
    }

    @Test
    fun accept_prereqMissing_rejectsWithNotification() = runBlocking {
        val session = testSession()
        val qm = testQuestManager(session)
        qm.reloadDefinitions(
            mapOf(
                "q0" to killDef(id = "q0"),
                "q1" to killDef(id = "q1", dependsOn = listOf("q0")),
            ))
        qm.accept(session, "q1")
        assertNull(session.state.quests["q1"])
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertEquals(1, notifs.size)
    }

    @Test
    fun abandon_setsAbandoned() = runBlocking {
        val session = testSession()
        val qm = testQuestManager(session)
        qm.reloadDefinitions(mapOf("q1" to killDef()))
        qm.accept(session, "q1")
        qm.abandon(session, "q1")
        assertEquals(QuestStatus.ABANDONED, session.state.quests["q1"]?.status)
    }

    @Test
    fun onNpcKilled_incrementsProgress() = runBlocking {
        val session = testSession(id = "player-1")
        val qm = testQuestManager(session)
        qm.reloadDefinitions(mapOf("q1" to killDef()))
        qm.accept(session, "q1")
        val npc = npcInstance(type = "wolf", contributorId = "player-1")
        qm.onNpcKilled(npc)
        assertEquals(1, session.state.quests["q1"]?.progress?.get("wolf"))
    }

    @Test
    fun onNpcKilled_completesQuestAndGrantsXp() = runBlocking {
        val session = testSession(id = "player-1")
        val xpGranted = mutableListOf<Int>()
        val qm = testQuestManager(session, xpGranted)
        qm.reloadDefinitions(mapOf("q1" to killDef(objectives = listOf(KillObjective("wolf", 1)))))
        qm.accept(session, "q1")
        val npc = npcInstance(type = "wolf", contributorId = "player-1")
        qm.onNpcKilled(npc)
        assertEquals(QuestStatus.COMPLETED, session.state.quests["q1"]?.status)
        assertEquals(listOf(100), xpGranted)
    }

    @Test
    fun repeatableQuest_resetsToTodoAfterCompletion() = runBlocking {
        val session = testSession(id = "player-1")
        val qm = testQuestManager(session)
        qm.reloadDefinitions(
            mapOf(
                "q1" to killDef(objectives = listOf(KillObjective("wolf", 1)), repeatable = true)))
        qm.accept(session, "q1")
        val npc = npcInstance(type = "wolf", contributorId = "player-1")
        qm.onNpcKilled(npc)
        // After completion of repeatable quest, status resets to TODO
        assertEquals(QuestStatus.TODO, session.state.quests["q1"]?.status)
        assertNotNull(session.state.quests["q1"]?.lastCompletedAt)
        Unit
    }

    @Test
    fun repeatableQuest_cooldownPreventsReaccept() = runBlocking {
        val session = testSession(id = "player-1")
        val qm = testQuestManager(session)
        qm.reloadDefinitions(
            mapOf(
                "q1" to
                    killDef(
                        objectives = listOf(KillObjective("wolf", 1)),
                        repeatable = true,
                        cooldownSeconds = 3600,
                    )))
        // Accept and complete
        qm.accept(session, "q1")
        val npc = npcInstance(type = "wolf", contributorId = "player-1")
        qm.onNpcKilled(npc)
        assertEquals(QuestStatus.TODO, session.state.quests["q1"]?.status)
        // Try to accept again — should be blocked by cooldown
        session.sent.clear()
        qm.accept(session, "q1")
        assertEquals(QuestStatus.TODO, session.state.quests["q1"]?.status)
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertEquals(1, notifs.size)
    }

    @Test
    fun onNpcKilled_unknownType_doesNotUpdateProgress() = runBlocking {
        val session = testSession(id = "player-1")
        val qm = testQuestManager(session)
        qm.reloadDefinitions(mapOf("q1" to killDef(objectives = listOf(KillObjective("wolf", 3)))))
        qm.accept(session, "q1")
        val npc = npcInstance(type = "bear", contributorId = "player-1")
        qm.onNpcKilled(npc)
        assertEquals(null, session.state.quests["q1"]?.progress?.get("bear"))
        assertEquals(null, session.state.quests["q1"]?.progress?.get("wolf"))
    }
}
