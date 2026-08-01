package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testI18n

private fun grazerDef() =
    NpcDefinition(
        type = "grazer",
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = "grazer",
        width = 0.6f,
        height = 1.2f,
        wanderSpeed = 3f,
        wanderRadius = 8f,
        hp = 20,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 10f,
    )

private fun deps() =
    SimulationDeps(
        definitions = mapOf("grazer" to grazerDef()),
        combatConfig = CombatConfigData(),
        attackRegistry = emptyMap(),
        armorRegistry = emptyMap(),
        classRegistry = emptyMap(),
        i18n = testI18n(),
        vegetationConfig = VegetationConfig(),
    )

private fun simulator() =
    WorldSimulator(
        SimulationConfig(
            halfSize = 24,
            // paused: the test drives step() by hand
            ticksPerSecond = 0,
            seed = 7L,
            gameDayDurationSeconds = 1.0,
        ),
        deps(),
    )

/**
 * The charts are only as good as their feed. These check the wiring between the arena and
 * [SimMetrics] — that logging an event counts it, and that the population gauge gets sampled by the
 * tick loop itself rather than by whoever happens to look.
 */
class SimulationMetricsWiringTest {

    @Test
    fun spawningAnNpc_showsUpInTheSpawnSeries(): Unit = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.spawnNamed("Gertrude", "grazer", Vec3(0f, 8f, 0f))
            assertEquals(1, sim.metrics.snapshot().sumOf { it.spawns })
        } finally {
            sim.stop()
        }
    }

    @Test
    fun ticking_samplesThePopulationPerType(): Unit = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            repeat(3) { sim.spawnNamed("Grazer $it", "grazer", Vec3(it.toFloat(), 8f, 0f)) }
            sim.stepOnce(1)

            val alive = sim.metrics.snapshot().last().aliveByType
            assertEquals(mapOf("grazer" to 3), alive)
        } finally {
            sim.stop()
        }
    }

    @Test
    fun aFreshArenaHasAnEmptySeries(): Unit = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            // the "arène prête" system line is logged, but it belongs to no chart
            assertTrue(sim.metrics.snapshot().all { it.attacks == 0 && it.deathsByType.isEmpty() })
        } finally {
            sim.stop()
        }
    }

    @Test
    fun metricsDto_carriesTheBucketWidthSoTheClientCanLabelItsAxis(): Unit = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            assertEquals(SimMetrics.DEFAULT_BUCKET_GAME_DAYS, sim.metricsDto().bucketGameDays)
        } finally {
            sim.stop()
        }
    }
}
