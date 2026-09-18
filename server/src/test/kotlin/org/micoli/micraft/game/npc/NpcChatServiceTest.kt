package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.npc.behaviors.QuestGiverNpcBehavior
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession

/**
 * Chat is a capability layered on top of whatever [org.micoli.micraft.game.npc.NpcBehavior] the NPC
 * already has — these tests use `quest_giver` (hermit_man's real config) to prove the two compose,
 * not a dedicated "chat" behavior.
 */
class NpcChatServiceTest {
    private val i18n = I18nConfig.fromClasspath()

    private class FakeOllamaClient(private val result: OllamaChatResult?) :
        OllamaClient(org.micoli.micraft.game.OllamaConfig()) {
        var calls = 0

        override suspend fun chat(
            systemPrompt: String,
            history: List<ChatTurn>,
            userMessage: String,
        ): OllamaChatResult? {
            calls++
            return result
        }
    }

    private fun questManagerWith(vararg defs: QuestDefinition): QuestManager {
        val qm = QuestManager(getSessions = { emptyList() }, savePlayer = {})
        qm.reloadDefinitions(defs.associateBy { it.id })
        return qm
    }

    private fun instance(
        chat: NpcChatCapability? = NpcChatCapability(dialoguePrompt = "You are a friendly hermit."),
        offersQuests: List<String> = emptyList(),
    ): NpcInstance {
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
                offersQuests = offersQuests,
                chat = chat,
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
    fun onInteract_tooFar_sendsNoGreeting() = runBlocking {
        val npc = instance()
        val session = testSession(pos = Vec3(999f, 4f, 999f))
        val sent = mutableListOf<ServerMessage>()
        NpcChatService.onInteract(npc, session, NpcTickContext.live.copy(i18n = i18n)) {
            sent.add(it)
        }
        assertTrue(sent.filterIsInstance<ServerMessage.NpcChatReply>().isEmpty())
    }

    @Test
    fun onInteract_sendsGreeting() = runBlocking {
        val npc = instance()
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        NpcChatService.onInteract(npc, session, NpcTickContext.live.copy(i18n = i18n)) {
            sent.add(it)
        }
        assertTrue(sent.filterIsInstance<ServerMessage.NpcChatReply>().isNotEmpty())
    }

    @Test
    fun onChatMessage_withoutChatCapability_doesNothing() = runBlocking {
        val npc = instance(chat = null)
        val ollama = FakeOllamaClient(OllamaChatResult("Hi", "none"))
        val store = NpcChatHistoryStore()
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        val ctx =
            NpcTickContext.live.copy(ollamaClient = ollama, chatHistoryStore = store, i18n = i18n)
        NpcChatService.onChatMessage(npc, session, ctx, "hello") { sent.add(it) }
        assertTrue(sent.isEmpty())
        assertEquals(0, ollama.calls)
    }

    @Test
    fun onChatMessage_hallucinatedQuestId_isDowngradedToNone() = runBlocking {
        val npc = instance(offersQuests = listOf("q1"))
        val quest =
            QuestDefinition(
                id = "q1", title = "Q1", description = "d", type = QuestType.EXPLORE, level = 1)
        val qm = questManagerWith(quest)
        val ollama =
            FakeOllamaClient(OllamaChatResult("Sure!", "offer_quest", questId = "not-real"))
        val store = NpcChatHistoryStore()
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        val ctx =
            NpcTickContext.live.copy(
                questManager = qm, ollamaClient = ollama, chatHistoryStore = store, i18n = i18n)
        NpcChatService.onChatMessage(npc, session, ctx, "any quest for me?") { sent.add(it) }
        val reply = sent.filterIsInstance<ServerMessage.NpcChatReply>().first()
        assertEquals("none", reply.action?.type)
        assertNull(reply.questOffer)
    }

    @Test
    fun onChatMessage_legitimateQuestOffer_isPassedThroughWithoutAccepting() = runBlocking {
        val npc = instance(offersQuests = listOf("q1"))
        val quest =
            QuestDefinition(
                id = "q1", title = "Q1", description = "d", type = QuestType.EXPLORE, level = 1)
        val qm = questManagerWith(quest)
        val ollama = FakeOllamaClient(OllamaChatResult("Sure!", "offer_quest", questId = "q1"))
        val store = NpcChatHistoryStore()
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        val ctx =
            NpcTickContext.live.copy(
                questManager = qm, ollamaClient = ollama, chatHistoryStore = store, i18n = i18n)
        NpcChatService.onChatMessage(npc, session, ctx, "any quest for me?") { sent.add(it) }
        val reply = sent.filterIsInstance<ServerMessage.NpcChatReply>().first()
        assertEquals("offer_quest", reply.action?.type)
        assertEquals("q1", reply.questOffer?.id)
        assertTrue(session.state.quests.isEmpty(), "the LLM must never accept the quest itself")
    }

    @Test
    fun onChatMessage_hallucinatedItemId_isDowngradedToNone() = runBlocking {
        val npc =
            instance(
                chat = NpcChatCapability(dialoguePrompt = "hi", giftableItems = listOf("FLINT")))
        val ollama =
            FakeOllamaClient(OllamaChatResult("Here!", "give_item", itemId = "COBBLESTONE"))
        val store = NpcChatHistoryStore()
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        val ctx =
            NpcTickContext.live.copy(ollamaClient = ollama, chatHistoryStore = store, i18n = i18n)
        NpcChatService.onChatMessage(npc, session, ctx, "give me something") { sent.add(it) }
        val reply = sent.filterIsInstance<ServerMessage.NpcChatReply>().first()
        assertEquals("none", reply.action?.type)
        assertNull(reply.itemOffer)
    }

    @Test
    fun onChatMessage_ollamaUnavailable_sendsNotificationAndKeepsHistoryUnchanged() = runBlocking {
        val npc = instance()
        val ollama = FakeOllamaClient(null)
        val store = NpcChatHistoryStore()
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        val ctx =
            NpcTickContext.live.copy(ollamaClient = ollama, chatHistoryStore = store, i18n = i18n)
        NpcChatService.onChatMessage(npc, session, ctx, "hello") { sent.add(it) }
        assertTrue(sent.filterIsInstance<ServerMessage.NpcChatReply>().isEmpty())
        assertTrue(sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
        assertTrue(store.get(session.id, "npc-1").isEmpty())
    }

    @Test
    fun onChatMessage_rateLimited_skipsOllamaCall() = runBlocking {
        val npc = instance()
        val ollama = FakeOllamaClient(OllamaChatResult("Hi", "none"))
        val store = NpcChatHistoryStore(minCallIntervalMs = 60_000)
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val ctx =
            NpcTickContext.live.copy(ollamaClient = ollama, chatHistoryStore = store, i18n = i18n)
        NpcChatService.onChatMessage(npc, session, ctx, "hi") {}
        NpcChatService.onChatMessage(npc, session, ctx, "hi again") {}
        assertEquals(1, ollama.calls)
    }

    @Test
    fun onAcceptGift_itemOutsideWhitelist_isRefused() = runBlocking {
        val npc =
            instance(
                chat = NpcChatCapability(dialoguePrompt = "hi", giftableItems = listOf("FLINT")))
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        NpcChatService.onAcceptGift(
            npc, session, NpcTickContext.live.copy(i18n = i18n), "COBBLESTONE") {}
        assertTrue(session.inventory.isEmpty())
    }

    @Test
    fun onAcceptGift_itemInWhitelist_grantsItem() = runBlocking {
        val npc =
            instance(
                chat = NpcChatCapability(dialoguePrompt = "hi", giftableItems = listOf("FLINT")))
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        NpcChatService.onAcceptGift(npc, session, NpcTickContext.live.copy(i18n = i18n), "FLINT") {}
        assertEquals(1, session.inventory[ItemType("FLINT")])
    }
}
