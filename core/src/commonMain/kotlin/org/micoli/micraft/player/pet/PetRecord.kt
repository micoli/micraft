package org.micoli.micraft.player.pet

import kotlinx.serialization.Serializable

/**
 * A tamed pet a player owns. Persisted on [org.micoli.micraft.player.PlayerState.pets].
 *
 * [currentHp] holds the pet's hp at its last dismiss or death: a re-summon restores it, so
 * dismiss/spawn is not a free heal. `0` (or [dead]) means "start the next summon at full hp".
 */
@Serializable
data class PetRecord(
    val id: String,
    val npcType: String,
    val name: String,
    val level: Int = 1,
    val xp: Int = 0,
    val currentHp: Int = 0,
    val tamedAtLevel: Int = 1,
    val dead: Boolean = false,
    val resurrectReadyAtMs: Long = 0L,
)
