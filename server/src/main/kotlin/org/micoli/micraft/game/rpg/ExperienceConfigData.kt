package org.micoli.micraft.game.rpg

import kotlinx.serialization.Serializable

@Serializable
data class ExperienceConfigData(
    val progression: ProgressionConfig = ProgressionConfig(),
    val sources: SourcesConfig = SourcesConfig(),
    val group: XpGroupConfig = XpGroupConfig(),
)
