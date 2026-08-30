package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.npc.animal.AnimalYamlOverride
import org.micoli.micraft.game.npc.pack.PackConfigOverride
import org.micoli.micraft.game.world.block.DropEntry
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass

@Serializable
data class NpcYamlOverride(
    val behavior: String? = null,
    val width: Float? = null,
    val height: Float? = null,
    val wanderSpeed: Float? = null,
    val wanderRadius: Float? = null,
    val spawn: NpcSpawnConfigRawOverride? = null,
    val hp: Int? = null,
    val aggroMode: AggroMode? = null,
    val aggroRange: Float? = null,
    val deaggroTimeSec: Float? = null,
    val attacks: List<NpcAttackSlot>? = null,
    val spells: List<String>? = null,
    val minLevel: Int? = null,
    val maxLevel: Int? = null,
    val characterClass: CharacterClass? = null,
    val baseStats: BaseStats? = null,
    val xpReward: Int? = null,
    val walkBoneAliases: Map<String, String>? = null,
    val bbmodelFile: String? = null,
    /**
     * Merged field by field, not swapped wholesale: an override that only shortens a lifespan must
     * leave the diet and the prey list alone.
     */
    val animal: AnimalYamlOverride? = null,
    val pack: PackConfigOverride? = null,
    val hibernation: HibernationConfig? = null,
    val shopItems: List<ShopItemEntry>? = null,
    val loot: List<DropEntry>? = null,
    val tameable: Boolean? = null,
    val tameBaseChance: Float? = null,
)
