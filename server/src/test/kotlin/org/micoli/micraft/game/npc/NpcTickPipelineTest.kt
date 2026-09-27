package org.micoli.micraft.game.npc

import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.animal.AnimalInteractionProcessor
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.roster.RegionPopulation
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.SeededRegionsGenerator
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

private const val HALF = 32
private const val GROUND = 7

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
    )

private class Harness(
    tuning: NpcTuning = NpcConstants.live,
    seed: Long = 7L,
    generator: ChunkGenerator = FlatArenaChunkGenerator(halfSize = HALF, groundY = GROUND),
) {
    val world = WorldState(generator, persistence = null)
    val ctx = NpcTickContext(tuning, Random(seed))
    val npcManager = NpcManager(broadcast = {}, getSessions = { emptyList() }, ctxOf = { ctx })
    val combat =
        CombatProcessor(
            config = CombatConfigData(),
            attackRegistry = emptyMap(),
            classRegistry = emptyMap(),
            npcManager = npcManager,
            getSessions = { emptyList() },
            broadcastCombatLog = {},
            subscribeToChannel = { _, _ -> },
            i18n = testI18n(),
            savePlayer = {},
        )
    val animals =
        AnimalInteractionProcessor(
            npcManager = npcManager,
            combatProcessor = combat,
            world = world,
            vegetationManager =
                VegetationManager(
                    world, VegetationConfig(), Path.of("/tmp/test_veg_pipeline.yaml")),
            gameTimeService = GameTimeService(60.0),
            broadcast = {},
            ctxOf = { ctx },
        )
    val population = RegionPopulation(world) { npcManager.getDefinitions() }
    val pipeline =
        NpcTickPipeline(
            npcManager = npcManager,
            npcSpawner = NpcSpawner(population),
            animals = animals,
            ctxOf = { ctx },
        )

    init {
        npcManager.loadDefinitions(mapOf("walker" to walkerDef()))
        val radius = HALF / WorldConstants.CHUNK_SIZE + 1
        for (cx in -radius..radius) for (cz in -radius..radius) world.getOrGenerate(
            ChunkPos(cx, cz))
    }
}

class NpcTickPipelineTest {

    @Test
    fun tick_movesNpcsThroughTheirBehavior() = runBlocking {
        val h = Harness()
        val npc = h.npcManager.spawnNpc("Walker", "walker", Vec3(0.5f, GROUND + 1f, 0.5f))
        val start = npc.state.pos
        repeat(200) { h.pipeline.tick(h.world, emptyList(), h.combat) }
        assertTrue(
            npc.state.pos.x != start.x || npc.state.pos.z != start.z,
            "a random_movable NPC should have wandered after 200 ticks")
    }

    @Test
    fun tick_keepsNpcsInsideTheWalledArena() = runBlocking {
        val h = Harness()
        val npc = h.npcManager.spawnNpc("Walker", "walker", Vec3(0.5f, GROUND + 1f, 0.5f))
        // chase a target far outside the arena so the NPC pushes against the wall
        repeat(600) {
            npc.chaseTargetPos = Vec3(HALF + 500f, GROUND + 1f, 0.5f)
            h.pipeline.tick(h.world, emptyList(), h.combat)
        }
        assertTrue(
            npc.state.pos.x < HALF,
            "NPC escaped the arena at x=${npc.state.pos.x} (wall is at $HALF)")
    }

    @Test
    fun visibilityCadence_followsInjectedTuning() = runBlocking {
        // one session-less run: with no players nothing is sent, but the counter must not throw
        val h = Harness(tuning = NpcConstants.live.copy(npcVisibilityCheckIntervalTicks = 2))
        h.npcManager.spawnNpc("Walker", "walker", Vec3(0.5f, GROUND + 1f, 0.5f))
        repeat(6) { h.pipeline.tick(h.world, emptyList(), h.combat) }
        assertEquals(1, h.npcManager.getAll().size)
    }

    @Test
    fun lifecycle_withoutPlayers_doesNotSpawnAnything() = runBlocking {
        val h = Harness()
        h.pipeline.lifecycle(h.world, emptyList())
        assertEquals(0, h.npcManager.getAll().size)
    }

    // ── Region activation and parking ────────────────────────────────────────

    private fun regionsHarness() =
        Harness(generator = SeededRegionsGenerator(listOf(0 to 0, 5_000 to 0))).also { h ->
            h.npcManager.loadDefinitions(
                mapOf(
                    "walker" to walkerDef(),
                    "deer" to wildDef("deer", biomes = emptyList()),
                    "camel" to wildDef("camel", biomes = listOf("desert"))))
        }

    private val farAway = Vec3(5_000.5f, GROUND + 1f, 0.5f)

    @Test
    fun lifecycle_parksNpcsOfRegionsNobodyIsIn() = runBlocking {
        val h = regionsHarness()
        val near = h.npcManager.spawnNpc("Near", "deer", Vec3(0.5f, GROUND + 1f, 0.5f))
        val far = h.npcManager.spawnNpc("Far", "deer", farAway)

        h.pipeline.lifecycle(h.world, listOf(testSession(pos = Vec3(0.5f, GROUND + 1f, 0.5f))))

        assertTrue(h.npcManager.getInstance(near.state.id) != null)
        assertNull(h.npcManager.getInstance(far.state.id))
    }

    @Test
    fun onRegionEntered_bringsParkedNpcsBack() = runBlocking {
        val h = regionsHarness()
        h.npcManager.spawnNpc("Far", "deer", farAway)
        h.pipeline.lifecycle(h.world, listOf(testSession(pos = Vec3(0.5f, GROUND + 1f, 0.5f))))

        h.pipeline.onRegionEntered(h.world, testSession(pos = farAway))

        assertTrue(h.npcManager.getAll().any { it.state.name == "Far" })
    }

    @Test
    fun parking_dropsWildNpcsOutsideTheirRoster() = runBlocking {
        val h = regionsHarness()
        h.npcManager.spawnNpc("Stray", "camel", farAway)
        h.pipeline.lifecycle(h.world, emptyList())

        h.pipeline.onRegionEntered(h.world, testSession(pos = farAway))

        assertTrue(h.npcManager.getAll().none { it.state.name == "Stray" })
    }

    @Test
    fun parking_neverTakesAPet() = runBlocking {
        val h = regionsHarness()
        val pet = h.npcManager.spawnNpc("Rex", "deer", farAway).also { it.ownerId = "owner" }

        h.pipeline.lifecycle(h.world, emptyList())

        assertTrue(h.npcManager.getInstance(pet.state.id) != null)
    }
}

private fun wildDef(type: String, biomes: List<String>) =
    walkerDef().copy(type = type, spawn = NpcSpawnConfig(autoSpawn = true, spawnBiomes = biomes))
