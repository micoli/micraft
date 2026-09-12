package org.micoli.micraft.quest

import kotlinx.serialization.Serializable

/** Lightweight, client-facing view of a quest a quest-giver NPC can hand out. */
@Serializable
data class QuestOfferSummary(
    val id: String,
    val title: String,
    val description: String,
    val level: Int,
)
