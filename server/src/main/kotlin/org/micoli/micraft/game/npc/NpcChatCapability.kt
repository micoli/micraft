package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

/**
 * Optional LLM-dialogue capability, orthogonal to [NpcDefinition.behavior] — any NPC (quest_giver,
 * seller, static, ...) can carry this on top of its normal behavior. Presence (non-null) is what
 * turns on chat: [org.micoli.micraft.game.npc.NpcManager.handleInteract] routes to [NpcChatService]
 * instead of the underlying behavior's `onInteract` whenever this is set.
 */
@Serializable
data class NpcChatCapability(
    val dialoguePrompt: String = "",
    val giftableItems: List<String> = emptyList(),
)
