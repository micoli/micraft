package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType

@Serializable
data class MailMessage(
    val id: String,
    val from: String,
    val to: String,
    val subject: String,
    val body: String,
    val attachments: Map<ItemType, Int> = emptyMap(),
    val sentAt: Long,
    val seen: Boolean = false,
    val attachmentsClaimed: Boolean = false,
    val copperAmount: Long = 0L,
)
