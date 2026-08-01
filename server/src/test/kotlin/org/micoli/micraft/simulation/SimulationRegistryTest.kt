package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.support.testI18n

private fun deps() =
    SimulationDeps(
        definitions =
            mapOf(
                "walker" to
                    NpcDefinition(
                        type = "walker",
                        behavior = RandomMovableNpcBehavior(),
                        behaviorKey = "random_movable",
                        bbmodelFile = "walker",
                        width = 0.6f,
                        height = 1.8f,
                        wanderSpeed = 4f,
                        wanderRadius = 12f,
                    )),
        combatConfig = CombatConfigData(),
        attackRegistry = emptyMap(),
        armorRegistry = emptyMap(),
        classRegistry = emptyMap(),
        i18n = testI18n(),
        vegetationConfig = VegetationConfig(),
    )

private val CONFIG =
    SimulationConfig(
        halfSize = 20,
        ticksPerSecond = 0,
        seed = 5L,
        gameDayDurationSeconds = 1.0,
        initialSpawns = listOf(SimSpawn("walker", count = 2)),
    )

class SimulationRegistryTest {

    @Test
    fun start_registersOneSimulationPerKey(): Unit = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            registry.start("a", CONFIG)
            registry.start("b", CONFIG)
            assertEquals(2, registry.count)
            assertNotNull(registry["a"])
            assertNotNull(registry["b"])
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun startTwiceOnTheSameKey_replacesTheSimulation() = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val first = registry.start("a", CONFIG)
            first.stepOnce(10)
            val second = registry.start("a", CONFIG)
            assertEquals(1, registry.count)
            assertTrue(first !== second)
            assertEquals(0L, second.tick, "the replacement starts from scratch")
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun restart_reusesTheConfigAndResetsTheTick() = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val sim = registry.start("a", CONFIG)
            sim.stepOnce(42)
            assertEquals(42L, sim.tick)

            val restarted = registry.restart("a")
            assertNotNull(restarted)
            assertEquals(0L, restarted.tick)
            assertEquals(CONFIG, restarted.config)
            assertEquals(2, restarted.npcDtos().size, "initial spawns are replayed")
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun restart_withoutSimulation_returnsNull() = runBlocking {
        val registry = SimulationRegistry { deps() }
        assertNull(registry.restart("missing"))
    }

    @Test
    fun stop_removesTheSimulation() = runBlocking {
        val registry = SimulationRegistry { deps() }
        registry.start("a", CONFIG)
        registry.stop("a")
        assertNull(registry["a"])
        assertEquals(0, registry.count)
    }
}
