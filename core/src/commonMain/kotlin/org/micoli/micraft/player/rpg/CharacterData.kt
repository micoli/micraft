package org.micoli.micraft.player.rpg

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.schema.JsonSchemaOpen

@Serializable
@JsonSchemaOpen
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
    @EncodeDefault val currentTokens: Int = 0,
    @EncodeDefault val restPoint: List<Vec3> = emptyList(),
) {
    val combatant: CombatantData
        get() =
            CombatantData(
                characterClass = characterClass,
                level = level,
                xp = xp,
                baseStats = baseStats,
                currentHp = currentHp,
                currentMana = currentMana,
                currentRage = currentRage,
                currentTokens = currentTokens,
            )

    fun withCombatant(c: CombatantData): CharacterData =
        copy(
            characterClass = c.characterClass,
            level = c.level,
            xp = c.xp,
            baseStats = c.baseStats,
            currentHp = c.currentHp,
            currentMana = c.currentMana,
            currentRage = c.currentRage,
            currentTokens = c.currentTokens,
        )
}
