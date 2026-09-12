package org.micoli.micraft.game.npc.behaviors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.quest.QuestProgress
import org.micoli.micraft.quest.QuestStatus
import org.micoli.micraft.support.testSession

class QuestGiverNpcBehaviorTest {

    private fun questManagerWith(vararg defs: QuestDefinition): QuestManager {
        val qm = QuestManager(getSessions = { emptyList() }, savePlayer = {})
        qm.reloadDefinitions(defs.associateBy { it.id })
        return qm
    }

    private fun instanceOffering(vararg questIds: String): NpcInstance {
        val def =
            NpcDefinition(
                type = "hermit_man",
                behavior = QuestGiverNpcBehavior(),
                behaviorKey = "quest_giver",
                bbmodelFile = "npc",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
                offersQuests = questIds.toList(),
            )
        val pos = Vec3(8f, 4f, 8f)
        return NpcInstance(
            state =
                NpcState(id = "npc-1", name = "Hermit", type = "hermit_man", pos = pos, yaw = 0f),
            definition = def,
            spawnPos = pos,
        )
    }

    @Test
    fun offersEligibleQuest() = runBlocking {
        val quest =
            QuestDefinition(
                id = "q1", title = "Q1", description = "d", type = QuestType.EXPLORE, level = 1)
        val qm = questManagerWith(quest)
        val instance = instanceOffering("q1")
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        session.characterData =
            CharacterData(
                id = "c1",
                name = "Alice",
                characterClass = CharacterClass.WARRIOR,
                level = 1,
                baseStats = BaseStats(),
                currentHp = 10,
                currentMana = 10,
            )
        val sent = mutableListOf<ServerMessage>()
        instance.definition.behavior.onInteract(
            instance, session, NpcTickContext.live.copy(questManager = qm)) {
                sent.add(it)
            }
        val dialog = sent.filterIsInstance<ServerMessage.QuestGiverDialog>().first()
        assertEquals(1, dialog.offerable.size)
        assertEquals("q1", dialog.offerable.first().id)
    }

    @Test
    fun hidesQuestAlreadyInProgress() = runBlocking {
        val quest =
            QuestDefinition(
                id = "q1", title = "Q1", description = "d", type = QuestType.EXPLORE, level = 1)
        val qm = questManagerWith(quest)
        val instance = instanceOffering("q1")
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        session.state =
            session.state.copy(
                quests = mapOf("q1" to QuestProgress(status = QuestStatus.IN_PROGRESS)))
        val sent = mutableListOf<ServerMessage>()
        instance.definition.behavior.onInteract(
            instance, session, NpcTickContext.live.copy(questManager = qm)) {
                sent.add(it)
            }
        val dialog = sent.filterIsInstance<ServerMessage.QuestGiverDialog>().first()
        assertTrue(dialog.offerable.isEmpty())
        assertEquals(listOf("q1"), dialog.turnInable)
    }

    @Test
    fun hidesQuestAboveLevelBudget() = runBlocking {
        val quest =
            QuestDefinition(
                id = "q1", title = "Q1", description = "d", type = QuestType.EXPLORE, level = 10)
        val qm = questManagerWith(quest)
        val instance = instanceOffering("q1")
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        session.characterData =
            CharacterData(
                id = "c1",
                name = "Alice",
                characterClass = CharacterClass.WARRIOR,
                level = 1,
                baseStats = BaseStats(),
                currentHp = 10,
                currentMana = 10,
            )
        val sent = mutableListOf<ServerMessage>()
        instance.definition.behavior.onInteract(
            instance, session, NpcTickContext.live.copy(questManager = qm)) {
                sent.add(it)
            }
        val dialog = sent.filterIsInstance<ServerMessage.QuestGiverDialog>().first()
        assertTrue(dialog.offerable.isEmpty())
    }

    @Test
    fun withoutQuestManager_sendsNothing() = runBlocking {
        val instance = instanceOffering("q1")
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        instance.definition.behavior.onInteract(instance, session, NpcTickContext.live) {
            sent.add(it)
        }
        assertTrue(sent.isEmpty())
    }
}
