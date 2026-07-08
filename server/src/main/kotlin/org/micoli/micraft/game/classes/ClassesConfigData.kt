package org.micoli.micraft.game.classes

import kotlinx.serialization.Serializable

@Serializable
data class ClassesConfigData(
    val regen: RegenSettings = RegenSettings(),
    val classes: Map<String, ClassDefinitionEntry> = emptyMap(),
)
