package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable

@Serializable data class ToolCategoryDefinition(val mainHandOnly: Boolean = false)
