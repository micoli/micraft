package org.micoli.micraft.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.CharacterClass

@Serializable
enum class DamageType {
    PHYSICAL,
    FIRE,
    POISON,
    MAGIC,
    LIGHTNING,
    NECROTIC
}

@Serializable
data class AttackDefinition(
    val damageType: DamageType = DamageType.PHYSICAL,
    val power: Int = 1,
    val eligibleClasses: List<CharacterClass> = CharacterClass.entries,
    val cooldownMs: Long = 1000,
    val manaCost: Int = 0,
    val rageCost: Int = 0,
    val rangeOverride: Float? = null,
    val weaponDice: String = "1d4",
    val statusEffect: StatusEffect? = null,
)
