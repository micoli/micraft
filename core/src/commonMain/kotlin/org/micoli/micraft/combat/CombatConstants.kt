package org.micoli.micraft.combat

/**
 * Dodge (physical, poison) and Magic resistance (magic, fire, lightning, necrotic) — every
 * Character's chance to avoid a hit entirely (ADR-0013). Shared between client and server (`core`)
 * so combat and the admin simulator classify damage the same way.
 */
object CombatConstants {
    var DODGE_CAP_PCT = 60f
    var MAGIC_RESIST_CAP_PCT = 60f
    val MAGICAL_DAMAGE_TYPES: Set<DamageType> =
        setOf(DamageType.MAGIC, DamageType.FIRE, DamageType.LIGHTNING, DamageType.NECROTIC)
}

/** Magic resistance covers this damage type; Dodge covers everything else (ADR-0013). */
val DamageType.isMagical: Boolean
    get() = this in CombatConstants.MAGICAL_DAMAGE_TYPES
