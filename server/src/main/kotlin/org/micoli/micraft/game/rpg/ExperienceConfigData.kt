package org.micoli.micraft.game.rpg

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "experience.schema.json")
data class ExperienceConfigData(
    val progression: ProgressionConfig = ProgressionConfig(),
    val sources: SourcesConfig = SourcesConfig(),
    val group: XpGroupConfig = XpGroupConfig(),
)
