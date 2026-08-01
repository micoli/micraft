package org.micoli.micraft.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcSpawnConfig
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.npc.NpcTier
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

private val stubBehavior =
    object : NpcBehavior {
        override fun tick(instance: NpcInstance, world: WorldState, ctx: NpcTickContext) = false

        override suspend fun onInteract(
            instance: NpcInstance,
            session: PlayerSession,
            ctx: NpcTickContext,
            send: suspend (ServerMessage) -> Unit
        ) {}
    }

private fun defWith(hp: Int, minLevel: Int = 1) =
    NpcDefinition(
        type = "test",
        behavior = stubBehavior,
        bbmodelFile = "test",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
        spawn = NpcSpawnConfig(),
        hp = hp,
        minLevel = minLevel,
        maxLevel = Int.MAX_VALUE,
        tier = NpcTier.COMMON,
    )

class NpcHpCalculatorTest {

    @Test
    fun defaultFormula_atMinLevel_returnsBaseHp() {
        val def = defWith(hp = 18, minLevel = 1)
        assertEquals(18, def.computeMaxHp(level = 1))
    }

    @Test
    fun defaultFormula_aboveMinLevel_scalesLinearly() {
        val def = defWith(hp = 20, minLevel = 1)
        // level 5: 20 + (5-1) * 20/10 = 20 + 8 = 28
        assertEquals(28, def.computeMaxHp(level = 5))
    }

    @Test
    fun defaultFormula_nonOneMinLevel_anchorsCorrectly() {
        val def = defWith(hp = 30, minLevel = 5)
        assertEquals(30, def.computeMaxHp(level = 5))
        // level 10: 30 + (10-5) * 30/10 = 30 + 15 = 45
        assertEquals(45, def.computeMaxHp(level = 10))
    }

    @Test
    fun levelBelowMinLevel_clampedToBase() {
        val def = defWith(hp = 20, minLevel = 5)
        assertEquals(20, def.computeMaxHp(level = 1))
    }

    @Test
    fun result_coercedToAtLeastOne() {
        val def = defWith(hp = 0, minLevel = 1)
        assertTrue(def.computeMaxHp(level = 1) >= 1)
    }
}
