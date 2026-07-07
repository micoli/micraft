package org.micoli.micraft.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.ClassResource

@Serializable
data class ClassDefinitionEntry(
    val strBonus: Int = 0,
    val dexBonus: Int = 0,
    val intelBonus: Int = 0,
    val wisBonus: Int = 0,
    val conBonus: Int = 0,
    val chaBonus: Int = 0,
    val classResource: ClassResource = ClassResource.MANA,
    val hpFormula: String = "hpRegenPerSec * dt",
    val manaFormula: String = "manaRegenPerSec * dt",
)
