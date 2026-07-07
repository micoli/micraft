package org.micoli.micraft.combat

import kotlinx.serialization.Serializable

@Serializable
data class DefaultRegenFormulas(
    val hpFormula: String = "hpRegenPerSec * dt",
    val manaFormula: String = "manaRegenPerSec * dt",
)

@Serializable
data class RegenSettings(
    val regenIntervalMs: Long = 1000L,
    val default: DefaultRegenFormulas = DefaultRegenFormulas(),
)

@Serializable
data class ClassesConfigData(
    val regen: RegenSettings = RegenSettings(),
    val classes: Map<String, ClassDefinitionEntry> = emptyMap(),
)
