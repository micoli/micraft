package org.micoli.micraft.world

import kotlinx.serialization.Serializable

@Serializable
data class WorldMetadata(
    val seed: Long,
    val generator: String,
    val createdAt: String,
    val gameTicks: Long = 18_000L, // dawn default (06:00)
)
