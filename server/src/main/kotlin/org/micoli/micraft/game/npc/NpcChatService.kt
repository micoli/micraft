package org.micoli.micraft.game.npc

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.addItems
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ItemOfferSummary
import org.micoli.micraft.protocol.NpcChatAction
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.quest.QuestOfferSummary
import org.slf4j.LoggerFactory

private val chatNpcLog = LoggerFactory.getLogger("NpcChatService")

/**
 * Stand-in "player level" for [NpcChatService.testChat] — high enough that no quest's level gate.
 */
private const val TEST_CHAT_PLAYER_LEVEL = 9_999

/**
 * Drives the [NpcChatCapability] dialogue — not an [NpcBehavior] itself, since chat is a capability
 * layered on top of whatever behavior the NPC already has ([NpcManager.handleInteract] decides
 * whether to route here instead of `behavior.onInteract`). The model only ever produces text plus a
 * *proposed* intent — it never mutates game state. A proposed quest/item id is revalidated against
 * the NPC's own whitelists ([NpcDefinition.offersQuests]/`chat.giftableItems`) before the player
 * ever sees it as an actionable offer, and accepting it still goes through the existing
 * `QuestManager.accept`/`turnIn` paths or [onAcceptGift] — never a direct call from here.
 */
object NpcChatService {
    suspend fun onInteract(
        instance: NpcInstance,
        session: PlayerSession,
        ctx: NpcTickContext,
        send: suspend (ServerMessage) -> Unit,
    ) {
        if (ctx.tooFarToInteract(instance, session, send)) return
        send(
            ServerMessage.NpcChatReply(
                npcId = instance.state.id,
                npcType = instance.state.type,
                text = ctx.i18n?.t(session.state.language, "npc:server:chat_greeting") ?: "...",
            ))
    }

    suspend fun onChatMessage(
        instance: NpcInstance,
        session: PlayerSession,
        ctx: NpcTickContext,
        text: String,
        send: suspend (ServerMessage) -> Unit,
    ) {
        if (ctx.tooFarToInteract(instance, session, send)) return
        val chat = instance.definition.chat ?: return
        val ollama = ctx.ollamaClient ?: return
        val history = ctx.chatHistoryStore ?: return

        if (!history.canCallNow(session.id, instance.state.id)) {
            ctx.i18n?.let {
                send(
                    ServerMessage.Notification(
                        it.t(session.state.language, "npc:server:chat_rate_limited")))
            }
            return
        }
        history.markCallNow(session.id, instance.state.id)

        val playerLevel = session.characterData?.level ?: 1
        val offerableQuests =
            ctx.questManager?.let {
                computeOfferableQuests(
                    it, instance.definition.offersQuests, playerLevel, session.state.quests)
            } ?: emptyList()
        val giftableItems = chat.giftableItems

        val languageInstruction =
            "Always reply in the language with ISO code '${session.state.language}', " +
                "regardless of what language this prompt or the player's message is written in."
        val systemPrompt =
            buildSystemPrompt(chat, offerableQuests, giftableItems, languageInstruction)

        val result = ollama.chat(systemPrompt, history.get(session.id, instance.state.id), text)
        if (result == null) {
            ctx.i18n?.let {
                send(
                    ServerMessage.Notification(
                        it.t(session.state.language, "npc:server:llm_unavailable")))
            }
            return
        }

        val (action, questOffer, itemOffer) =
            validateAction(result, instance.state.type, offerableQuests, giftableItems)

        history.append(session.id, instance.state.id, text, result.reply)

        send(
            ServerMessage.NpcChatReply(
                npcId = instance.state.id,
                npcType = instance.state.type,
                text = result.reply,
                action = action,
                questOffer = questOffer,
                itemOffer = itemOffer,
            ))
    }

    private fun buildSystemPrompt(
        chat: NpcChatCapability,
        offerableQuests: List<QuestOfferSummary>,
        giftableItems: List<String>,
        languageInstruction: String,
    ): String = buildString {
        append(chat.dialoguePrompt)
        append("\n\n")
        append(languageInstruction)
        if (offerableQuests.isNotEmpty()) {
            append("\n\nQuests you may offer if it fits the conversation: ")
            append(offerableQuests.joinToString(", ") { "${it.id} (${it.title})" })
        }
        if (giftableItems.isNotEmpty()) {
            append("\n\nItems you may gift if the player convinces you: ")
            append(giftableItems.joinToString(", "))
        }
    }

    /**
     * Admin-only sandbox: drives the same [OllamaClient] call and whitelist validation as
     * [onChatMessage], but without a [PlayerSession] — no rate limiting, no shared chat history
     * (the caller supplies/keeps [history] itself), quest offers computed at max level with no
     * player quest progress. Used by the admin "test chat_npc" page to iterate on
     * [NpcChatCapability.dialoguePrompt] without a live player.
     */
    suspend fun testChat(
        instance: NpcInstance,
        ctx: NpcTickContext,
        history: List<ChatTurn>,
        text: String,
    ): ChatTestResult? {
        val chat = instance.definition.chat ?: return null
        val ollama = ctx.ollamaClient ?: return null

        val offerableQuests =
            ctx.questManager?.let {
                computeOfferableQuests(
                    it, instance.definition.offersQuests, TEST_CHAT_PLAYER_LEVEL, emptyMap())
            } ?: emptyList()
        val giftableItems = chat.giftableItems

        val systemPrompt =
            buildSystemPrompt(
                chat,
                offerableQuests,
                giftableItems,
                "This is an admin test conversation, not a real player — reply in English.")

        val result = ollama.chat(systemPrompt, history, text) ?: return null
        val (action, questOffer, itemOffer) =
            validateAction(result, instance.state.type, offerableQuests, giftableItems)
        return ChatTestResult(result.reply, action, questOffer, itemOffer)
    }

    data class ChatTestResult(
        val reply: String,
        val action: NpcChatAction,
        val questOffer: QuestOfferSummary?,
        val itemOffer: ItemOfferSummary?,
    )

    /**
     * Revalidates the model's proposed [OllamaChatResult] against the NPC's own whitelists — never
     * trusts `questId`/`itemId` as returned. Anything not found there is downgraded to `"none"` and
     * logged as a hallucination.
     */
    private fun validateAction(
        result: OllamaChatResult,
        npcType: String,
        offerableQuests: List<QuestOfferSummary>,
        giftableItems: List<String>,
    ): Triple<NpcChatAction, QuestOfferSummary?, ItemOfferSummary?> {
        val validQuestId = result.questId?.takeIf { id -> offerableQuests.any { it.id == id } }
        val validItemId = result.itemId?.takeIf { id -> id in giftableItems }
        return when {
            result.actionType == "offer_quest" && validQuestId != null ->
                Triple(
                    NpcChatAction("offer_quest", questId = validQuestId),
                    offerableQuests.first { it.id == validQuestId },
                    null)
            result.actionType == "give_item" && validItemId != null ->
                Triple(
                    NpcChatAction("give_item", itemId = validItemId),
                    null,
                    ItemOfferSummary(validItemId, validItemId))
            else -> {
                if (result.actionType != "none") {
                    chatNpcLog.warn(
                        "NPC '{}' proposed an unwhitelisted {} '{}' — downgraded to none",
                        npcType,
                        result.actionType,
                        result.questId ?: result.itemId,
                    )
                }
                Triple(NpcChatAction("none"), null, null)
            }
        }
    }

    /** Called by `NpcManager.handleChatAcceptGift`. Revalidates — never trusts the client. */
    suspend fun onAcceptGift(
        instance: NpcInstance,
        session: PlayerSession,
        ctx: NpcTickContext,
        itemId: String,
        send: suspend (ServerMessage) -> Unit,
    ) {
        if (ctx.tooFarToInteract(instance, session, send)) return
        if (itemId !in (instance.definition.chat?.giftableItems ?: emptyList())) return
        session.addItems(mapOf(ItemType(itemId) to 1))
        ctx.i18n?.let {
            send(
                ServerMessage.Notification(
                    it.t(session.state.language, "npc:server:chat_gift_received", itemId)))
        }
    }
}
