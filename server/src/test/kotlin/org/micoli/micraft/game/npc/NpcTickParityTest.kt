package org.micoli.micraft.game.npc

import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.animal.AnimalNpcBehavior
import org.micoli.micraft.game.npc.animal.AnimalYamlEntry
import org.micoli.micraft.game.npc.animal.NpcDiet
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.pack.PackConfig
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

/**
 * The compared trace, with NPC ids resolved to names: an id is a fresh UUID per spawn, so comparing
 * them across two independent worlds could only ever fail.
 */
private fun traceOf(instances: Collection<NpcInstance>): List<String> {
    val nameById = instances.associate { it.state.id to it.state.name }
    return instances.sortedBy { it.state.name }.map { it.traceLine { id -> nameById[id] ?: "?" } }
}

private const val HALF = 32
private const val GROUND = 7
private const val SEED = 4242L
private const val VEGETATION_DENSITY = 0.08

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

/**
 * A grazer that ages, hungers, mates and hibernates — the parts of the tick a bare `walker` never
 * reaches. Without one of these in the scenario, animal lifecycle, pack and hibernation parity are
 * simply untested.
 */
private fun grazerDef() =
    NpcDefinition(
        type = "grazer",
        behavior = AnimalNpcBehavior(),
        behaviorKey = "animal",
        bbmodelFile = "grazer",
        width = 0.6f,
        height = 1.2f,
        wanderSpeed = 3f,
        wanderRadius = 10f,
        hp = 20,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 8f,
        animalConfig =
            AnimalYamlEntry(
                diet = NpcDiet.HERBIVORE,
                lifespanDays = 40.0,
                canReproduce = true,
                gestationDays = 1.0,
                offspringType = "grazer",
                offspringMinCount = 1,
                offspringMaxCount = 1,
                reproductionCooldownDays = 1.0,
                matingRange = 8f,
                hpRegenPerSec = 1f,
                hungerRatePerDay = 0.5,
                hungerThresholdToHunt = 0.4,
                hungerThresholdToMate = 0.6,
            ),
        packConfig = PackConfig(minSizeToEngage = 2, hostileTypes = listOf("walker")),
        hibernation = HibernationConfig(hoursPerCycle = 4.0, cycleDays = 1.0),
    )

private val DEFS = mapOf("walker" to walkerDef(), "grazer" to grazerDef())

/**
 * Parity between the two NPC drivers.
 *
 * Both sides are built by [NpcSubsystemFactory] — the same call the live server and the simulator
 * make. That is the point: this test used to hand-wire a *third* rig, and the hooks it forgot were
 * exactly the ones that diverged, so the simulator could run for 60 game days without kill XP and
 * the test stayed green. Comparing two wirings means little; comparing two hosts of one wiring is
 * what pins down the claim that the simulator shows the real rules.
 *
 * Together with [NpcTickOwnershipTest] (which proves the game loop owns no NPC tick logic of its
 * own), this covers both the tick *order* and the tick *wiring*.
 */
class NpcTickParityTest {

    /** The live-server host: the shared factory, driven the way `GameLoop` drives it. */
    private class GameLoopStyleRig(seed: Long) {
        val world =
            WorldState(
                FlatArenaChunkGenerator(
                    halfSize = HALF,
                    groundY = GROUND,
                    vegetationDensity = VEGETATION_DENSITY,
                    vegetationSeed = SEED,
                ),
                persistence = null)
        private val ctx = NpcTickContext(NpcTuning(), Random(seed))

        private val factory =
            NpcSubsystemFactory(
                hooks = NpcSubsystemHooks(ctxOf = { ctx }),
                world = world,
                vegetationManager =
                    VegetationManager(
                        world, VegetationConfig(), Path.of("/tmp/test_veg_parity.yaml")),
                gameDayDurationSecondsOf = { 1.0 },
            )

        val npcManager: NpcManager = factory.npcManager

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

        private val subsystem = factory.build(combat)

        init {
            npcManager.loadDefinitions(DEFS)
            val radius = HALF / WorldConstants.CHUNK_SIZE + 1
            for (cx in -radius..radius) for (cz in -radius..radius) world.getOrGenerate(
                ChunkPos(cx, cz))
        }

        suspend fun run(ticks: Int) =
            repeat(ticks) {
                subsystem.gameTimeService.tick(org.micoli.micraft.game.TICK_SECONDS.toDouble())
                subsystem.pipeline.tick(world, emptyList(), combat)
            }

        fun trace() = traceOf(npcManager.getAll())
    }

    private fun simulatorRig(seed: Long) =
        WorldSimulator(
            SimulationConfig(
                halfSize = HALF,
                groundY = GROUND,
                ticksPerSecond = 0,
                seed = seed,
                gameDayDurationSeconds = 1.0,
                vegetationDensity = VEGETATION_DENSITY,
                npcTuning = NpcTuning(),
                initialSpawns = emptyList(),
                players = emptyList(),
            ),
            SimulationDeps(
                definitions = DEFS,
            ),
        )

    @Test
    fun simulatorAndGameLoopWiring_evolveNpcsIdentically() = runBlocking {
        val spawns =
            listOf(
                Triple("walker-0", "walker", Vec3(4.5f, GROUND + 1f, 4.5f)),
                Triple("walker-1", "walker", Vec3(-6.5f, GROUND + 1f, 8.5f)),
                Triple("grazer-0", "grazer", Vec3(2.5f, GROUND + 1f, -3.5f)),
                Triple("grazer-1", "grazer", Vec3(-2.5f, GROUND + 1f, -5.5f)),
            )

        val rig = GameLoopStyleRig(SEED)
        spawns.forEach { (name, type, pos) -> rig.npcManager.spawnNpc(name, type, pos) }
        rig.run(400)

        val sim = simulatorRig(SEED)
        val simTrace =
            try {
                sim.start()
                spawns.forEach { (name, type, pos) -> sim.spawnNamed(name, type, pos, level = 0) }
                sim.stepOnce(400)
                traceOf(sim.npcInstances())
            } finally {
                sim.stop()
            }

        assertEquals(
            rig.trace().joinToString("\n"),
            simTrace.joinToString("\n"),
            "the simulator must reproduce the live NPC tick exactly; a difference means one of the two drifted")
    }

    @Test
    fun theParityScenarioActuallyExercisesTheAnimalLifecycle() = runBlocking {
        // Guards against the trap this test fell into before: a scenario that compares two hosts
        // over a code path neither of them enters proves nothing.
        val rig = GameLoopStyleRig(SEED)
        rig.npcManager.spawnNpc("grazer-0", "grazer", Vec3(2.5f, GROUND + 1f, -3.5f))
        rig.run(400)

        val animal = rig.npcManager.getAll().first { it.state.type == "grazer" }.animalData
        assertTrue(animal != null, "the grazer must carry animal data")
        assertTrue(animal.ageGameDays > 0.0, "the animal lifecycle must have run")
    }

    @Test
    fun differentSeeds_produceDifferentEvolution() = runBlocking {
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

/**
 * The compared state.
 *
 * Deliberately wider than position and HP: level, death, pack membership, hibernation, aggro and
 * the whole animal record are exactly where a wiring difference shows up first. A trace that only
 * carries coordinates cannot tell a world where babies grow up from one where they never do.
 */
private fun NpcInstance.traceLine(nameOf: (String) -> String): String {
    val npcTargetName = npcAggroTarget?.let(nameOf) ?: "-"
    val animal = animalData
    val animalPart =
        if (animal == null) "-"
        else
            "${animal.gender}/${"%.4f".format(animal.ageGameDays)}/${"%.4f".format(animal.hunger)}/" +
                "${animal.gestationRemainingDays?.let { "%.4f".format(it) } ?: "-"}/${animal.motherLevel}"
    return listOf(
            state.name,
            state.type,
            "%.3f".format(state.pos.x),
            "%.3f".format(state.pos.y),
            "%.3f".format(state.pos.z),
            "%.3f".format(state.yaw),
            currentHp.toString(),
            instanceLevel.toString(),
            xp.toString(),
            isDead.toString(),
            hibernating.toString(),
            packId ?: "-",
            aggroTarget ?: "-",
            npcTargetName,
            animalPart,
        )
        .joinToString("|")
}
