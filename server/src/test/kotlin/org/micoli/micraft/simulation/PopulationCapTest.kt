package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.animal.AnimalYamlEntry
import org.micoli.micraft.game.npc.animal.NpcDiet
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.player.rpg.BaseStats

private const val HALF = 24

private val BREEDER_CONFIG =
    AnimalYamlEntry(
        diet = NpcDiet.HERBIVORE,
        lifespanDays = 1_000.0,
        canReproduce = true,
        gestationDays = 0.01,
        offspringType = "rabbit",
        offspringMinCount = 2,
        offspringMaxCount = 4,
        reproductionCooldownDays = 0.0,
        matingRange = 40f,
        baseStats = BaseStats(str = 5, dex = 5, con = 5),
        statsVariance = 0,
        hpRegenPerSec = 0f,
        hungerRatePerDay = 0.0,
        feedHungerReduction = 1.0,
        hungerThresholdToHunt = 0.99,
        hungerThresholdToMate = 0.99,
    )

private fun rabbitDef() =
    NpcDefinition(
        type = "rabbit",
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = "rabbit",
        width = 0.4f,
        height = 0.5f,
        wanderSpeed = 2f,
        wanderRadius = 8f,
        hp = 10,
        aggroMode = AggroMode.PASSIVE,
        // mates are chased, and the chase is leashed to aggroRange around the spawn point
        aggroRange = 30f,
        animalConfig = BREEDER_CONFIG,
    )

private fun deps() =
    SimulationDeps(
        definitions = mapOf("rabbit" to rabbitDef()),
    )

private fun config(cap: Int) =
    SimulationConfig(
        halfSize = HALF,
        ticksPerSecond = 0,
        seed = 3L,
        gameDayDurationSeconds = 0.5,
        populationCap = cap,
    )

/**
 * Place the herd in a cluster. Random placement across the arena leaves pairs further apart than
 * the chase leash allows, so they would never meet and nothing would breed.
 */
private suspend fun WorldSimulator.seedHerd(count: Int = 8) {
    repeat(count) { i ->
        val x = (i % 4).toFloat() - 1.5f
        val z = (i / 4).toFloat() - 1.5f
        spawnNamed("rabbit-$i", "rabbit", org.micoli.micraft.player.Vec3(x, 8f, z))
    }
}

class PopulationCapTest {

    @Test
    fun reproduction_stopsAtTheCap() = runBlocking {
        val sim = WorldSimulator(config(cap = 20), deps())
        try {
            sim.start()
            sim.seedHerd()
            sim.stepOnce(1_500)
            val population = sim.npcInstances().size
            assertTrue(population <= 20, "population overshot the cap: $population")
            assertTrue(population >= 8, "the initial batch should still be there: $population")
            assertFalse(sim.belowPopulationCap(), "the arena should report itself full")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun capReached_isReportedInTheLogAndStats() = runBlocking {
        val sim = WorldSimulator(config(cap = 12), deps())
        try {
            sim.start()
            sim.seedHerd()
            sim.stepOnce(1_500)
            assertTrue(
                sim.events.snapshot().any { it.message.contains("plafond de population") },
                "hitting the ceiling must be visible in the event log")
            assertEquals(12, sim.statsDto().populationCap)
        } finally {
            sim.stop()
        }
    }

    @Test
    fun zeroCap_neverRefusesASpawn() = runBlocking {
        // Deliberately not run long: unbounded reproduction is exponential and the animal slow tick
        // is quadratic in the population. That runaway is the reason the ceiling exists, so it is
        // asserted through the predicate instead of by letting it happen.
        val sim = WorldSimulator(config(cap = 0), deps())
        try {
            sim.start()
            sim.seedHerd(count = 30)
            assertTrue(sim.belowPopulationCap(), "no ceiling means no refusal, whatever the count")
            assertEquals(30, sim.npcInstances().size)
            assertEquals(0, sim.statsDto().populationCap)
        } finally {
            sim.stop()
        }
    }

    @Test
    fun highCap_stillLetsTheHerdGrow() = runBlocking {
        val sim = WorldSimulator(config(cap = 200), deps())
        try {
            sim.start()
            sim.seedHerd()
            sim.stepOnce(400)
            assertTrue(
                sim.npcInstances().size > 8,
                "a ceiling far above the herd must not block reproduction")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun manualSpawn_alsoRespectsTheCap() = runBlocking {
        val sim = WorldSimulator(config(cap = 10), deps())
        try {
            sim.start()
            sim.spawn("rabbit", 0f, 0f, count = 50)
            assertTrue(sim.npcInstances().size <= 10, "manual spawns must not bypass the ceiling")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun arena_carriesGrazingFoodSoHerbivoresCanEat() = runBlocking {
        val sim = WorldSimulator(config(cap = 20), deps())
        try {
            sim.start()
            // magnitude matters: a handful of plants across the arena starves the herd anyway
            val cells = (HALF * 2 - 1) * (HALF * 2 - 1)
            val expected = cells * sim.config.vegetationDensity
            val counted = sim.foodBlockCount()
            assertTrue(
                counted > expected * 0.5,
                "expected roughly $expected grazing plants over $cells cells, counted $counted")
            val ground = sim.config.groundY
            val food =
                (-HALF + 1 until HALF).flatMap { x ->
                    (-HALF + 1 until HALF).map { z -> sim.world.getBlockIfLoaded(x, ground + 1, z) }
                }
            assertTrue(food.any { it == BlockType.WEED || it == BlockType.FLOWER })
        } finally {
            sim.stop()
        }
    }
}
