package org.micoli.micraft.player.rpg

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class CombatantData(
    @EncodeDefault val characterClass: CharacterClass = CharacterClass.WARRIOR,
    @EncodeDefault val level: Int = 1,
    @EncodeDefault val xp: Int = 0,
    @EncodeDefault val baseStats: BaseStats = BaseStats(),
    @EncodeDefault val currentHp: Int = 0,
    @EncodeDefault val currentMana: Int = 0,
    @EncodeDefault val currentRage: Int = 0,
    @EncodeDefault val currentTokens: Int = 0,
)
