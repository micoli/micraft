package org.micoli.micraft.game.combat

import kotlin.random.Random
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.combat.isMagical
import org.micoli.micraft.player.rpg.DerivedStats

/**
 * True when [damageType] is avoided entirely by [defender]'s Dodge or Magic resistance (ADR-0013):
 * Dodge for physical/poison, Magic resistance for magic/fire/lightning/necrotic. Rolled only once a
 * hit is otherwise determined; an avoided hit deals no damage and applies no Status effect. Shared
 * by [CombatProcessor] (attacks) and [SpellProcessor] (AoE Spells).
 */
fun rollAvoided(rollSource: Random, damageType: DamageType, defender: DerivedStats): Boolean {
    val chancePct = if (damageType.isMagical) defender.magicResistPct else defender.dodgePct
    return rollSource.nextInt(1, 101) <= chancePct.toInt()
}
