package org.micoli.micraft.game.npc.animal

import java.nio.file.Path
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.npc.NpcSubsystemHooks
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.npc.NpcTuning
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testI18n

private const val HALF = 48
private const val GROUND = 7

/**
 * Goal-seeking.
 *
 * Two things kept an animal from ever reaching what it wanted. Adults ran `random_movable`, which
 * ignores the prey/food/mate target entirely; and the chase was leashed to `aggroRange` around the
 * *spawn point* — 5 blocks for a goat — so even the babies that did read the target could not walk
 * to it. Predation and mating therefore happened only when two animals wandered into each other.
 */
private fun hunterDef(roamRadius: Float) =
    NpcDefinition(
        type = "hunter",
        behavior = AnimalNpcBehavior(),
        behaviorKey = "animal",
        bbmodelFile = "wolf",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 6f,
        wanderRadius = 20f,
        hp = 40,
        aggroMode = AggroMode.AGGRESSIVE,
        // deliberately short: the errand must not be limited by eyesight
        aggroRange = 6f,
        animalConfig =
            AnimalYamlEntry(
                diet = NpcDiet.CARNIVORE,
                lifespanDays = 100.0,
                preyTypes = listOf("prey"),
                canReproduce = false,
                hpRegenPerSec = 0f,
                hungerRatePerDay = 0.0,
                foodSearchRadius = 40f,
                roamRadius = roamRadius,
                hungerThresholdToHunt = 0.4,
            ),
    )

private fun preyDef(fleeRadius: Float = 0f, wanderSpeed: Float = 0f) =
    NpcDefinition(
        type = "prey",
        behavior = AnimalNpcBehavior(),
        behaviorKey = "animal",
        bbmodelFile = "goat",
        width = 0.5f,
        height = 0.9f,
        // stays put by default, so the test measures the hunter's travel and nothing else
        wanderSpeed = wanderSpeed,
        wanderRadius = 0f,
        hp = 10,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 2f,
        animalConfig =
            AnimalYamlEntry(
                diet = NpcDiet.HERBIVORE,
                lifespanDays = 100.0,
                canReproduce = false,
                hpRegenPerSec = 0f,
                hungerRatePerDay = 0.0,
                roamRadius = 60f,
                fleeRadius = fleeRadius,
            ),
    )

private class Arena(roamRadius: Float, preyFleeRadius: Float = 0f, preySpeed: Float = 0f) {
    val world =
        WorldState(
            FlatArenaChunkGenerator(halfSize = HALF, groundY = GROUND, vegetationDensity = 0.0),
            persistence = null)

    private val ctx = NpcTickContext(NpcTuning(), Random(7L))

    private val factory =
        NpcSubsystemFactory(
            hooks = NpcSubsystemHooks(ctxOf = { ctx }),
            world = world,
            vegetationManager =
                VegetationManager(world, VegetationConfig(), Path.of("/tmp/test_veg_move.yaml")),
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
        // BlockRegistry is a global singleton other tests reload; restore the defaults so this
        // arena's physics — what is solid, what an animal can walk through — never depends on
        // which test ran before it.
        BlockRegistry.load(emptyMap())
        npcManager.loadDefinitions(
            mapOf("hunter" to hunterDef(roamRadius), "prey" to preyDef(preyFleeRadius, preySpeed)))
        val radius = HALF / WorldConstants.CHUNK_SIZE + 1
        for (cx in -radius..radius) for (cz in -radius..radius) world.getOrGenerate(
            ChunkPos(cx, cz))
    }

    suspend fun run(ticks: Int) =
        repeat(ticks) {
            subsystem.gameTimeService.tick(org.micoli.micraft.game.TICK_SECONDS.toDouble())
            subsystem.pipeline.tick(world, emptyList(), combat)
        }
}

private fun NpcInstance.distanceTo(other: NpcInstance): Float {
    val dx = other.state.pos.x - state.pos.x
    val dz = other.state.pos.z - state.pos.z
    return sqrt(dx * dx + dz * dz)
}

private fun NpcInstance.starving() {
    animalData?.hunger = 1.0
}

class AnimalMovementTest {

    @Test
    fun aHungryPredator_walksBeyondItsEyesightToReachPrey() = runBlocking {
        val arena = Arena(roamRadius = 40f)
        val hunter = arena.npcManager.spawnNpc("Hunter", "hunter", Vec3(0.5f, GROUND + 1f, 0.5f))
        val prey = arena.npcManager.spawnNpc("Prey", "prey", Vec3(20.5f, GROUND + 1f, 0.5f))
        hunter.starving()
        val startDistance = hunter.distanceTo(prey)

        arena.run(600)

        val endDistance = hunter.distanceTo(prey)
        assertTrue(
            endDistance < startDistance / 2f,
            "the hunter must close the 20-block gap despite an aggroRange of 6 (was $startDistance, now $endDistance)")
    }

    @Test
    fun aShortRoamRadius_stillPinsThePredatorNearHome() = runBlocking {
        // The leash is a real constraint, not decoration: this is the behaviour that used to apply
        // to every animal, and it is what made goal-seeking pointless.
        val arena = Arena(roamRadius = 4f)
        val hunter = arena.npcManager.spawnNpc("Hunter", "hunter", Vec3(0.5f, GROUND + 1f, 0.5f))
        val prey = arena.npcManager.spawnNpc("Prey", "prey", Vec3(20.5f, GROUND + 1f, 0.5f))
        hunter.starving()

        arena.run(600)

        val fromSpawn =
            sqrt(
                (hunter.state.pos.x - hunter.spawnPos.x).let { it * it } +
                    (hunter.state.pos.z - hunter.spawnPos.z).let { it * it })
        assertTrue(fromSpawn < 8f, "a 4-block leash must keep the hunter home, was $fromSpawn")
        assertTrue(hunter.distanceTo(prey) > 10f, "and therefore never reach the prey")
    }

    @Test
    fun aSatedPredator_staysHomeEvenWithPreyInSight() = runBlocking {
        val arena = Arena(roamRadius = 40f)
        val hunter = arena.npcManager.spawnNpc("Hunter", "hunter", Vec3(0.5f, GROUND + 1f, 0.5f))
        val prey = arena.npcManager.spawnNpc("Prey", "prey", Vec3(20.5f, GROUND + 1f, 0.5f))
        hunter.animalData?.hunger = 0.0

        arena.run(600)

        // Asserted on the errand rather than on the distance: a sated predator still wanders, and
        // wandering can close the gap by luck, which would make a distance check flaky either way.
        assertTrue(hunter.animalData?.preyTargetPos == null, "a sated predator must have no prey")
        assertTrue(hunter.chaseLeash == null, "and therefore no roaming licence")
        assertTrue(prey.currentHp == prey.maxHp, "the prey must be untouched")
    }

    /**
     * Asserted on the direction, not on who wins the chase: whether a prey outruns a predator is a
     * tuning question (relative speeds, arena walls), while "it runs the other way" is the
     * mechanic.
     */
    @Test
    fun preyWithAFleeRadius_runsAwayFromTheNearestPredator() = runBlocking {
        val arena = Arena(roamRadius = 60f, preyFleeRadius = 20f, preySpeed = 8f)
        val hunter = arena.npcManager.spawnNpc("Hunter", "hunter", Vec3(0.5f, GROUND + 1f, 0.5f))
        val prey = arena.npcManager.spawnNpc("Prey", "prey", Vec3(8.5f, GROUND + 1f, 0.5f))
        hunter.starving()
        val startX = prey.state.pos.x

        // Just past the first slow tick, while the threat is still inside `fleeRadius`. Running
        // longer is what the second assertion covers: once the prey is clear, the flight point is
        // cleared too, so a late check would fail *because* fleeing worked.
        arena.run(25)

        val flight = prey.animalData?.fleeTargetPos
        assertTrue(flight != null, "the prey must know where to run while the threat is near")
        // the hunter sits at lower x, so the flight point must lead to higher x
        assertTrue(
            flight.x > hunter.state.pos.x,
            "the flight point must lead away (flight ${flight.x}, hunter ${hunter.state.pos.x})")

        arena.run(200)

        assertTrue(
            prey.state.pos.x > startX + 1f,
            "the prey must actually have run (from $startX to ${prey.state.pos.x})")
    }

    @Test
    fun preyWithoutAFleeRadius_doesNotReact() = runBlocking {
        val arena = Arena(roamRadius = 60f, preyFleeRadius = 0f, preySpeed = 8f)
        val hunter = arena.npcManager.spawnNpc("Hunter", "hunter", Vec3(0.5f, GROUND + 1f, 0.5f))
        arena.npcManager.spawnNpc("Prey", "prey", Vec3(8.5f, GROUND + 1f, 0.5f))
        hunter.starving()
        val prey = arena.npcManager.getAll().first { it.state.type == "prey" }

        arena.run(100)

        // 0 keeps the previous behaviour, so no existing definition changes by accident
        assertTrue(prey.animalData?.fleeTargetPos == null)
    }
}
