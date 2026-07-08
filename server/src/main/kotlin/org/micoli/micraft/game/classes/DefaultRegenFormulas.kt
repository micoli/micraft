package org.micoli.micraft.game.classes

import kotlinx.serialization.Serializable

@Serializable
data class DefaultRegenFormulas(
    val hpFormula: String = "hpRegenPerSec * dt",
    val manaFormula: String = "manaRegenPerSec * dt",
)
