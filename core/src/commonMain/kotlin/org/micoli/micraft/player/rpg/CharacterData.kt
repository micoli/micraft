package org.micoli.micraft.player.rpg

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.micoli.micraft.player.Vec3

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class CharacterData(
    @EncodeDefault val id: String,
    @EncodeDefault val name: String,
    @EncodeDefault val characterClass: CharacterClass,
    @EncodeDefault val level: Int = 1,
    @EncodeDefault val xp: Int = 0,
    @EncodeDefault val baseStats: BaseStats,
    @EncodeDefault val currentHp: Int,
    @EncodeDefault val currentMana: Int,
    @EncodeDefault val currentRage: Int = 0,
    @EncodeDefault val restPoint: List<Vec3> = emptyList(),
)
