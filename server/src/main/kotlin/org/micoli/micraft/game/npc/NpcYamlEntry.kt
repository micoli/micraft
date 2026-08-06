package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.npc.animal.AnimalYamlEntry
import org.micoli.micraft.game.npc.pack.PackConfig
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass

@Serializable
data class NpcYamlEntry(
    val behavior: String = "static",
    val width: Float = 0.6f,
    val height: Float = 1.8f,
    val wanderSpeed: Float = 0f,
    val wanderRadius: Float = 0f,
    val spawn: NpcSpawnConfigRaw = NpcSpawnConfigRaw(),
    val hp: Int = 20,
    val aggroMode: AggroMode = AggroMode.PASSIVE,
    val aggroRange: Float = 12.0f,
    val deaggroTimeSec: Float = 10.0f,
    val attacks: List<NpcAttackSlot> = emptyList(),
    val spells: List<String> = emptyList(),
    val minLevel: Int = 0,
    val maxLevel: Int = Int.MAX_VALUE,
    val characterClass: CharacterClass = CharacterClass.WARRIOR,
    val baseStats: BaseStats = BaseStats(),
    val xpReward: Int = 0,
    val walkBoneAliases: Map<String, String> = emptyMap(),
    val bbmodelFile: String? = null,
    val animal: AnimalYamlEntry? = null,
    val pack: PackConfig? = null,
    val hibernation: HibernationConfig? = null,
    val shopItems: List<ShopItemEntry> = emptyList(),
)
