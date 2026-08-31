package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.player.Vec3

private fun capDef() =
    NpcDefinition(
        type = "capybara",
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = "capybara",
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
        definitions = mapOf("capybara" to capDef()),
    )

/**
 * The two caps have to agree. A per-frame cap below the population cap means a zoomed-out view of a
 * full arena is permanently flagged "affichage partiel", which reads as a defect rather than as the
 * payload protection it is.
 */
class SimulationConfigDefaultsTest {

    @Test
    fun theFrameCapIsNotBelowThePopulationCap() {
        val config = SimulationConfig()
        assertTrue(
            config.maxNpcsPerFrame >= config.populationCap,
            "frame cap ${config.maxNpcsPerFrame} would always truncate a full arena of ${config.populationCap}")
    }

    @Test
    fun defaultPopulationCapIsAThousand() {
        assertEquals(1_000, SimulationConfig().populationCap)
    }

    @Test
    fun anArenaFilledToItsCap_isSentWhole(): Unit = runBlocking {
        val cap = 6
        val sim =
            WorldSimulator(
                SimulationConfig(
                    halfSize = 20,
                    ticksPerSecond = 0,
                    seed = 3L,
                    populationCap = cap,
                    maxNpcsPerFrame = cap,
                    gameDayDurationSeconds = 1.0,
                ),
                deps(),
            )
        try {
            sim.start()
            repeat(cap) { sim.spawnNamed("Capy $it", "capybara", Vec3(it.toFloat(), 8f, 0f)) }

            // no viewport = looking at the whole arena, the case that was reporting 800/1498
            assertEquals(cap, sim.npcDtos(null).size)
            assertFalse(
                sim.isTruncated(null), "a full arena at the cap must not report a partial view")
        } finally {
            sim.stop()
        }
    }
}
