package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.armor.ArmorDropEntry
import org.micoli.micraft.game.npc.animal.AnimalYamlEntry
import org.micoli.micraft.game.npc.pack.PackConfig
import org.micoli.micraft.game.world.block.DropEntry
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "npcs.schema.json")
data class NpcYamlEntry(
    @JsonSchemaConstraint(
        enum = ["static", "random_movable", "interactionable", "animal", "seller", "quest_giver"])
    val behavior: String = "static",
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val width: Float = 0.6f,
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val height: Float = 1.8f,
    @JsonSchemaConstraint(minimum = 0.0) val wanderSpeed: Float = 0f,
    @JsonSchemaConstraint(minimum = 0.0) val wanderRadius: Float = 0f,
    val spawn: NpcSpawnConfigRaw = NpcSpawnConfigRaw(),
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val hp: Int = 20,
    val aggroMode: AggroMode = AggroMode.PASSIVE,
    @JsonSchemaConstraint(minimum = 0.0) val aggroRange: Float = 12.0f,
    @JsonSchemaConstraint(minimum = 0.0) val deaggroTimeSec: Float = 10.0f,
    val attacks: List<NpcAttackSlot> = emptyList(),
    val spells: List<String> = emptyList(),
    @JsonSchemaConstraint(minimum = 0.0) val minLevel: Int = 0,
    val maxLevel: Int = Int.MAX_VALUE,
    val tier: NpcTier = NpcTier.COMMON,
    val characterClass: CharacterClass = CharacterClass.WARRIOR,
    val baseStats: BaseStats = BaseStats(),
    @JsonSchemaConstraint(minimum = 0.0) val xpReward: Int = 0,
    val walkBoneAliases: Map<String, String> = emptyMap(),
    val bbmodelFile: String? = null,
    val animal: AnimalYamlEntry? = null,
    val pack: PackConfig? = null,
    val hibernation: HibernationConfig? = null,
    val shopItems: List<ShopItemEntry> = emptyList(),
    val loot: List<DropEntry> = emptyList(),
    val tameable: Boolean = false,
    @JsonSchemaConstraint(minimum = 0.0, maximum = 1.0) val tameBaseChance: Float = 0.5f,
    val offersQuests: List<String> = emptyList(),
    val armorLoot: List<ArmorDropEntry> = emptyList(),
    /** How this NPC moves — see [MovementMode]. Defaults to `[WALKING]`. */
    val movementMode: List<MovementMode> = listOf(MovementMode.WALKING),
)
