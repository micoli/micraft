package org.micoli.micraft.game.classes

import kotlinx.serialization.Serializable

@Serializable
data class ClassLevelEntry(
    val attacks: List<ClassAttackAccess> = emptyList(),
    val spells: List<String> = emptyList(),
)
