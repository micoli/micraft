package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun start_registersEachSimulationUnderItsOwnId(): Unit = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val first = registry.start(CONFIG)
            val second = registry.start(CONFIG)
            assertEquals(2, registry.count)
            assertTrue(first != second, "each arena gets its own id")
            assertNotNull(registry[first])
            assertNotNull(registry[second])
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun list_describesWhatIsRunning() = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val id = registry.start(CONFIG, name = "pâturage")
            registry[id]!!.stepOnce(10)
            val entry = registry.list().single()
            assertEquals(id, entry.id)
            assertEquals("pâturage", entry.name)
            assertEquals(2, entry.npcCount, "the initial batch is reported")
            assertEquals(10L, entry.tick)
            assertEquals(0, entry.viewers)
            assertEquals(CONFIG.halfSize, entry.halfSize)
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun unnamedSimulation_getsALabelFromItsConfig() = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            registry.start(CONFIG)
            val name = registry.list().single().name
            assertTrue(name.contains("40×40"), "arena size should be in the label: $name")
            assertTrue(name.contains("walker"), "initial spawns should be in the label: $name")
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun viewers_areCountedPerSimulation() = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val id = registry.start(CONFIG)
            assertTrue(registry.addViewer(id))
            assertTrue(registry.addViewer(id))
            assertEquals(2, registry.list().single().viewers, "two admins watch the same arena")
            registry.removeViewer(id)
            assertEquals(1, registry.list().single().viewers)
            assertFalse(registry.addViewer("nope"), "attaching to nothing must fail")
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun restart_keepsTheIdAndItsViewers() = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val id = registry.start(CONFIG)
            registry.addViewer(id)
            registry[id]!!.stepOnce(42)

            val restarted = registry.restart(id)
            assertNotNull(restarted)
            assertEquals(0L, restarted.tick, "a restart starts from scratch")
            assertEquals(2, restarted.npcDtos().size, "initial spawns are replayed")
            assertEquals(id, registry.list().single().id, "watchers stay attached to the same id")
            assertEquals(1, registry.list().single().viewers)
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
    fun stop_removesOnlyThatSimulation(): Unit = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val kept = registry.start(CONFIG)
            val dropped = registry.start(CONFIG)
            registry.stop(dropped)
            assertNull(registry[dropped])
            assertNotNull(registry[kept], "stopping one arena must not touch the others")
            assertEquals(1, registry.count)
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun tooManySimulations_isRefusedRatherThanStarvingThemAll() = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            repeat(SimulationRegistry.MAX_SIMULATIONS) { registry.start(CONFIG) }
            val failure = runCatching { registry.start(CONFIG) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(
                failure.message!!.contains("trop de simulations"),
                "the refusal must say why: ${failure.message}")
            assertEquals(SimulationRegistry.MAX_SIMULATIONS, registry.count)
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun unwatchedSimulation_isReapedAfterTheIdleTimeout(): Unit = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val id = registry.start(CONFIG)
            registry.reapIdle()
            assertNotNull(registry[id], "a fresh arena is not idle yet")

            registry.reapIdle(
                now = System.currentTimeMillis() + SimulationRegistry.IDLE_TIMEOUT_MS + 1)
            assertNull(registry[id], "nobody watching for too long: the arena is dropped")
        } finally {
            registry.stopAll()
        }
    }

    @Test
    fun watchedSimulation_survivesTheIdleSweep(): Unit = runBlocking {
        val registry = SimulationRegistry { deps() }
        try {
            val id = registry.start(CONFIG)
            registry.addViewer(id)
            registry.reapIdle(
                now = System.currentTimeMillis() + SimulationRegistry.IDLE_TIMEOUT_MS * 10)
            assertNotNull(registry[id], "an arena with a viewer must never be reaped")
        } finally {
            registry.stopAll()
        }
    }
}
