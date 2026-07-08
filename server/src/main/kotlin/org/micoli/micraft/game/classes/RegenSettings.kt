package org.micoli.micraft.game.classes

import kotlinx.serialization.Serializable

@Serializable
data class RegenSettings(
    val regenIntervalMs: Long = 1000L,
    val default: DefaultRegenFormulas = DefaultRegenFormulas(),
)
