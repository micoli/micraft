package org.micoli.micraft.game.npc

import org.micoli.micraft.game.npc.animal.AnimalYamlEntry
import org.micoli.micraft.game.npc.pack.PackConfig
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass

data class NpcDefinition(
    val type: String,
    val behavior: NpcBehavior,
    val behaviorKey: String = "static",
    val bbmodelFile: String,
    val width: Float,
    val height: Float,
    val wanderSpeed: Float,
    val wanderRadius: Float,
    val spawn: NpcSpawnConfig = NpcSpawnConfig(),
    val hp: Int = 20,
    val aggroMode: AggroMode = AggroMode.PASSIVE,
    val aggroRange: Float = 12.0f,
    val deaggroTimeSec: Float = 10.0f,
    val attacks: List<NpcAttackSlot> = emptyList(),
    val spells: List<String> = emptyList(),
    val minLevel: Int = 0,
    val maxLevel: Int = Int.MAX_VALUE,
    val tier: NpcTier = NpcTier.COMMON,
    val characterClass: CharacterClass = CharacterClass.WARRIOR,
    val baseStats: BaseStats = BaseStats(),
    val xpReward: Int = 0,
    val walkBoneAliases: Map<String, String> = emptyMap(),
    val animalConfig: AnimalYamlEntry? = null,
    val packConfig: PackConfig? = null,
    val hibernation: HibernationConfig? = null,
) {
    fun computeMaxHp(level: Int): Int =
        (hp + (level - minLevel).coerceAtLeast(0) * hp / 10).coerceAtLeast(1)
}
