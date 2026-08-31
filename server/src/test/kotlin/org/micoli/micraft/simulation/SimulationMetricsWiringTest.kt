package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.animal.AnimalNpcBehavior
import org.micoli.micraft.game.npc.animal.AnimalYamlEntry
import org.micoli.micraft.game.npc.animal.NpcDiet
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.player.Vec3

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

/** Lives less than one tick's worth of game time, so a single step ages it to death. */
private fun mayflyDef() =
    NpcDefinition(
        type = "mayfly",
        behavior = AnimalNpcBehavior(),
        behaviorKey = "animal",
        bbmodelFile = "mayfly",
        width = 0.3f,
        height = 0.5f,
        wanderSpeed = 1f,
        wanderRadius = 4f,
        hp = 5,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 2f,
        animalConfig =
            AnimalYamlEntry(
                diet = NpcDiet.HERBIVORE,
                lifespanDays = 0.001,
                canReproduce = false,
                hpRegenPerSec = 0f,
                hungerRatePerDay = 0.0,
            ),
    )

private fun deps() =
    SimulationDeps(
        definitions = mapOf("grazer" to grazerDef(), "mayfly" to mayflyDef()),
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

    /**
     * Regression: the animal processor used to raise its own AGE_DEATH *and* let the kill hook
     * raise a DEATH, so every natural death appeared twice in `deathsByType`. One death, one row.
     */
    @Test
    fun anOldAgeDeathIsCountedOnce_andAsOldAge(): Unit = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.spawnNamed("Ephemera", "mayfly", Vec3(0f, 8f, 0f))
            repeat(5) { sim.stepOnce(1) }

            val buckets = sim.metrics.snapshot()
            assertEquals(1, buckets.sumOf { it.deathsByType.values.sum() })
            assertEquals(1, buckets.sumOf { it.ageDeathsByType.values.sum() })
            assertEquals(0, buckets.sumOf { it.killDeathsByType.values.sum() })
            assertEquals(0, buckets.sumOf { it.starvationsByType.values.sum() })
        } finally {
            sim.stop()
        }
    }

    /**
     * The panel that would have named the cause on day one. A population chart alone said "cats
     * fell from 300 to 1"; hunger pegged at 1.0 with an adult share of zero says why.
     */
    @Test
    fun theSampleReportsTheConditionOfThePopulation(): Unit = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.spawnNamed("Ephemera", "mayfly", Vec3(0f, 8f, 0f))
            val fly = sim.npcInstances().first { it.state.type == "mayfly" }
            fly.animalData?.hunger = 1.0
            fly.animalData?.ageGameDays = 0.0005

            val sample = sim.populationSample()

            assertEquals(mapOf("mayfly" to 1), sample.aliveByType, "alive")
            assertEquals(1.0, sample.meanHungerByType["mayfly"], "hunger")
            assertEquals(1.0, sample.starvingShareByType["mayfly"], "starving share")
            // absent rather than 0.0: nothing pregnant means no row for it
            assertEquals(null, sample.pregnantShareByType["mayfly"], "pregnant share")
            // no adultType, so it is already an adult form
            assertEquals(1.0, sample.adultShareByType["mayfly"], "adult share")
            val ageRatio = sample.meanAgeRatioByType["mayfly"]
            assertTrue(ageRatio != null && ageRatio > 0.0, "age must be reported as a ratio")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun theSampleIgnoresTheDead(): Unit = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.spawnNamed("Gertrude", "grazer", Vec3(0f, 8f, 0f))
            sim.npcInstances().first().isDead = true

            // a corpse lingers five seconds before despawning; counting it would overstate the
            // world
            assertTrue(sim.populationSample().aliveByType.isEmpty())
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
