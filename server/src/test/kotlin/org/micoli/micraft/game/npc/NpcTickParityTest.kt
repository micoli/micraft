package org.micoli.micraft.game.npc

import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.animal.AnimalInteractionProcessor
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.simulation.SimulationConfig
import org.micoli.micraft.simulation.SimulationDeps
import org.micoli.micraft.simulation.WorldSimulator
import org.micoli.micraft.support.testI18n

private const val HALF = 32
private const val GROUND = 7
private const val SEED = 4242L

private fun walkerDef() =
    NpcDefinition(
        type = "walker",
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = "walker",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 4f,
        wanderRadius = 12f,
        hp = 30,
    )

private val DEFS = mapOf("walker" to walkerDef())

/**
 * Parity between the two NPC drivers.
 *
 * The world simulator must evolve NPCs exactly like a pipeline wired the way
 * [org.micoli.micraft.game.GameLoop] wires it — same definitions, same tunables, same seed.
 * Together with [NpcTickOwnershipTest] (which proves the game loop owns no NPC tick logic of its
 * own), this pins down the claim that the simulator shows the real rules.
 */
class NpcTickParityTest {

    /** The live-server wiring, reproduced explicitly over the same arena. */
    private class GameLoopStyleRig(seed: Long) {
        val world =
            WorldState(
                FlatArenaChunkGenerator(halfSize = HALF, groundY = GROUND), persistence = null)
        private val ctx = NpcTickContext(NpcTuning(), Random(seed))
        val npcManager = NpcManager(broadcast = {}, getSessions = { emptyList() }, ctxOf = { ctx })
        private val combat =
            CombatProcessor(
                config = CombatConfigData(),
                attackRegistry = emptyMap(),
                armorRegistry = emptyMap(),
                classRegistry = emptyMap(),
                npcManager = npcManager,
                getSessions = { emptyList() },
                broadcastCombatLog = {},
                subscribeToChannel = { _, _ -> },
                i18n = testI18n(),
                savePlayer = {},
            )
        private val animals =
            AnimalInteractionProcessor(
                npcManager = npcManager,
                combatProcessor = combat,
                world = world,
                vegetationManager =
                    VegetationManager(
                        world, VegetationConfig(), Path.of("/tmp/test_veg_parity.yaml")),
                gameTimeService = GameTimeService(1.0),
                broadcast = {},
                ctxOf = { ctx },
            )
        private val pipeline =
            NpcTickPipeline(
                npcManager = npcManager,
                npcSpawner = NpcSpawner(),
                animals = animals,
                ctxOf = { ctx },
            )

        init {
            npcManager.loadDefinitions(DEFS)
            val radius = HALF / WorldConstants.CHUNK_SIZE + 1
            for (cx in -radius..radius) for (cz in -radius..radius) world.getOrGenerate(
                ChunkPos(cx, cz))
        }

        suspend fun run(ticks: Int) = repeat(ticks) { pipeline.tick(world, emptyList(), combat) }

        fun trace() = npcManager.getAll().sortedBy { it.state.name }.map { it.traceLine() }
    }

    private fun simulatorRig(seed: Long) =
        WorldSimulator(
            SimulationConfig(
                halfSize = HALF,
                groundY = GROUND,
                ticksPerSecond = 0,
                seed = seed,
                gameDayDurationSeconds = 1.0,
                npcTuning = NpcTuning(),
                initialSpawns = emptyList(),
                players = emptyList(),
            ),
            SimulationDeps(
                definitions = DEFS,
                combatConfig = CombatConfigData(),
                attackRegistry = emptyMap(),
                armorRegistry = emptyMap(),
                classRegistry = emptyMap(),
                i18n = testI18n(),
                vegetationConfig = VegetationConfig(),
            ),
        )

    @Test
    fun simulatorAndGameLoopWiring_evolveNpcsIdentically() = runBlocking {
        val positions = listOf(Vec3(4.5f, GROUND + 1f, 4.5f), Vec3(-6.5f, GROUND + 1f, 8.5f))

        val rig = GameLoopStyleRig(SEED)
        positions.forEachIndexed { i, pos -> rig.npcManager.spawnNpc("walker-$i", "walker", pos) }
        rig.run(400)

        val sim = simulatorRig(SEED)
        val simTrace =
            try {
                sim.start()
                positions.forEachIndexed { i, pos ->
                    sim.spawnNamed("walker-$i", "walker", pos, level = 0)
                }
                sim.stepOnce(400)
                sim.npcInstances().sortedBy { it.state.name }.map { it.traceLine() }
            } finally {
                sim.stop()
            }

        assertEquals(
            rig.trace(),
            simTrace,
            "the simulator must reproduce the live NPC tick exactly; a difference means one of the two drifted")
    }

    @Test
    fun differentTuning_producesDifferentEvolution() = runBlocking {
        // sanity check that the parity assertion above is not vacuous
        val fast = GameLoopStyleRig(SEED)
        fast.npcManager.spawnNpc("walker-0", "walker", Vec3(4.5f, GROUND + 1f, 4.5f))
        fast.run(200)

        val other = GameLoopStyleRig(SEED + 1)
        other.npcManager.spawnNpc("walker-0", "walker", Vec3(4.5f, GROUND + 1f, 4.5f))
        other.run(200)

        assertTrue(
            fast.trace() != other.trace(),
            "two different seeds should not produce identical traces")
    }
}

private fun NpcInstance.traceLine(): String =
    "${state.name}|${state.type}|${"%.3f".format(state.pos.x)}|${"%.3f".format(state.pos.y)}|${"%.3f".format(state.pos.z)}|${"%.3f".format(state.yaw)}|$currentHp"
