package org.micoli.micraft.game.classes

import kotlinx.serialization.Serializable

@Serializable data class ClassSpellAccess(val spell: String, val rank: Int)
