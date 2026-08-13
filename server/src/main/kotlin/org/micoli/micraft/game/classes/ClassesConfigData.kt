package org.micoli.micraft.game.classes

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "classes.schema.json")
data class ClassesConfigData(
    val regen: RegenSettings = RegenSettings(),
    val classes: Map<String, ClassDefinitionEntry> = emptyMap(),
)
