package org.micoli.micraft.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcHpCalculator
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcSpawnConfig
import org.micoli.micraft.game.npc.NpcTier
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.protocol.ServerMessage

private val stubBehavior =
    object : NpcBehavior {
        override fun tick(instance: NpcInstance, world: WorldState) = false

        override suspend fun onInteract(
            instance: NpcInstance,
            session: PlayerSession,
            send: suspend (ServerMessage) -> Unit
        ) {}
    }

private fun defWith(
    hp: Int,
    minLevel: Int = 1,
    hpFormula: String = "hp + (level - minLevel) * hp * 0.1",
) =
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
        hpFormula = hpFormula,
        minLevel = minLevel,
        maxLevel = Int.MAX_VALUE,
        tier = NpcTier.COMMON,
        classResource = ClassResource.MANA,
        maxMana = 0,
        maxRage = 0,
    )

class NpcHpCalculatorTest {

    @Test
    fun defaultFormula_atMinLevel_returnsBaseHp() {
        val def = defWith(hp = 18, minLevel = 1)
        assertEquals(18, NpcHpCalculator.computeMaxHp(def, level = 1))
    }

    @Test
    fun defaultFormula_aboveMinLevel_scalesLinearly() {
        val def = defWith(hp = 20, minLevel = 1)
        // level 5: 20 + (5-1) * 20 * 0.1 = 20 + 8 = 28
        assertEquals(28, NpcHpCalculator.computeMaxHp(def, level = 5))
    }

    @Test
    fun defaultFormula_nonOneMinLevel_anchorsCorrectly() {
        val def = defWith(hp = 30, minLevel = 5)
        // at minLevel=5: 30 + 0 = 30
        assertEquals(30, NpcHpCalculator.computeMaxHp(def, level = 5))
        // at level 10: 30 + (10-5) * 30 * 0.1 = 30 + 15 = 45
        assertEquals(45, NpcHpCalculator.computeMaxHp(def, level = 10))
    }

    @Test
    fun customFormula_evaluated() {
        val def = defWith(hp = 10, hpFormula = "hp * level * 2")
        assertEquals(60, NpcHpCalculator.computeMaxHp(def, level = 3))
    }

    @Test
    fun invalidFormula_fallsBackToBaseHp() {
        val def = defWith(hp = 15, hpFormula = "unknownVar + 1")
        assertEquals(15, NpcHpCalculator.computeMaxHp(def, level = 1))
    }

    @Test
    fun result_coercedToAtLeastOne() {
        val def = defWith(hp = 10, hpFormula = "0")
        assertTrue(NpcHpCalculator.computeMaxHp(def, level = 1) >= 1)
    }
}
