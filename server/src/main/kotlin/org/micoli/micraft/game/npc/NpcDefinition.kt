package org.micoli.micraft.game.npc

import org.micoli.micraft.player.rpg.ClassResource

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
    val level: Int = 1,
    val tier: NpcTier = NpcTier.COMMON,
    val classResource: ClassResource = ClassResource.MANA,
    val maxMana: Int = 0,
    val maxRage: Int = 0,
)
