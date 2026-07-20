package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.ClassResource

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
    val minLevel: Int? = null,
    val maxLevel: Int? = null,
    val classResource: ClassResource? = null,
    val maxMana: Int? = null,
    val maxRage: Int? = null,
    val walkBoneAliases: Map<String, String>? = null,
    val bbmodelFile: String? = null,
)
