package org.micoli.micraft.game.npc

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeZone
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testWorld

private fun wanderDef(
    type: String = "GOAT",
    autoSpawn: Boolean = true,
    maxPerChunk: Int = 2,
    spawnBiomes: List<String> = emptyList(),
    maxTotal: Int = 0,
    weight: Int = 1,
    movementMode: List<MovementMode> = listOf(MovementMode.WALKING),
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 8f,
        spawn =
            NpcSpawnConfig(
                autoSpawn = autoSpawn,
                maxPerChunk = maxPerChunk,
                spawnBiomes = spawnBiomes,
                maxTotal = maxTotal,
                weight = weight,
            ),
        movementMode = movementMode,
    )

private fun staticDef(
    type: String = "SELLER",
    autoSpawn: Boolean = false,
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = StaticNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
        spawn = NpcSpawnConfig(autoSpawn = autoSpawn),
    )

private fun biome(id: String = "plains", budget: Int = 25) =
    BiomeDefinition(
        id = id,
        zones = listOf(BiomeZone(moistureMin = 0.0, moistureMax = 1.0)),
        surface = BlockType.GRASS,
        subsurface = BlockType.DIRT,
        regionBudget = budget,
    )

/** [inner]'s blocks, as one Region of [biome] at Danger level 1. */
private class OneRegionGenerator(
    private val inner: ChunkGenerator,
    private val biome: BiomeDefinition
) : ChunkGenerator by inner {
    private val region = Region(0, 0, biome, "Testland", 1)

    override fun biomeDefinitionAt(wx: Int, wz: Int) = biome

    override fun zoneLevelAt(wx: Int, wz: Int) = 1

    override fun regionAt(wx: Int, wz: Int) = region

    override fun regionsNear(wx: Int, wz: Int, radiusBlocks: Int) = listOf(region)
}

private val floorBlocks = buildList {
    val size = WorldConstants.CHUNK_SIZE * 5
    for (x in 0 until size) for (z in 0 until size) add(Triple(x, 3, z))
}

private fun WorldState.discover(blocks: List<Triple<Int, Int, Int>>) = apply {
    blocks
        .map { (x, _, z) ->
            ChunkPos(
                Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                Math.floorDiv(z, WorldConstants.CHUNK_SIZE))
        }
        .toSet()
        .forEach { getOrGenerate(it) }
}

private fun regionWorld(biome: BiomeDefinition = biome(), top: BlockType? = null): WorldState {
    val inner =
        if (top == null) testWorld(*floorBlocks.toTypedArray()).generator
        else MapChunkGenerator(floorBlocks.associateWith { top })
    return WorldState(OneRegionGenerator(inner, biome)).discover(floorBlocks)
}

private fun testManager(defs: Map<String, NpcDefinition>): NpcManager {
    val m = NpcManager(broadcast = {})
    m.loadDefinitions(defs)
    return m
}

private suspend fun NpcSpawner.passes(world: WorldState, m: NpcManager, count: Int) =
    repeat(count) { trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }

class NpcSpawnerTest {
    private var savedBlocks: Map<BlockType, BlockDefinition> = emptyMap()

    @BeforeTest
    fun registerWater() {
        savedBlocks = BlockRegistry.all().associateWith { BlockRegistry.get(it) }
        BlockRegistry.load(
            savedBlocks +
                mapOf(
                    BlockType.WATER to
                        BlockDefinition(
                            hardness = -1f, solid = false, liquid = true, viscosity = 3)))
    }

    @AfterTest
    fun restoreBlocks() {
        BlockRegistry.load(savedBlocks)
    }

    @Test
    fun trySpawn_rosterType_spawns() = runBlocking {
        val world = regionWorld()
        val m = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().passes(world, m, 1)
        assertTrue(m.getAll().isNotEmpty())
    }

    @Test
    fun trySpawn_autoSpawnFalse_neverSpawns() = runBlocking {
        val world = regionWorld()
        val m = testManager(mapOf("SELLER" to staticDef(autoSpawn = false)))
        NpcSpawner().passes(world, m, 5)
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun trySpawn_respectsMaxPerChunk() = runBlocking {
        val world = regionWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(maxPerChunk = 1)))
        NpcSpawner().passes(world, m, 10)
        val chunk = ChunkPos(0, 0)
        assertTrue(
            m.countByTypeInChunk("GOAT", chunk) <= 1, "got ${m.countByTypeInChunk("GOAT", chunk)}")
    }

    @Test
    fun trySpawn_fillsTheRegionBudgetAndNoMore() = runBlocking {
        val world = regionWorld(biome(budget = 4))
        val m = testManager(mapOf("GOAT" to wanderDef(maxPerChunk = 10)))
        NpcSpawner().passes(world, m, 20)
        assertEquals(4, m.getAll().size)
    }

    @Test
    fun trySpawn_zeroBudget_neverSpawns() = runBlocking {
        val world = regionWorld(biome(budget = 0))
        val m = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().passes(world, m, 5)
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun trySpawn_worldWithoutRegions_neverSpawns() = runBlocking {
        val world = testWorld(*floorBlocks.toTypedArray())
        val m = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().passes(world, m, 5)
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun trySpawn_onlyRosterTypesSpawn() = runBlocking {
        val world = regionWorld()
        val defs = (1..8).associate { "BEAST$it" to wanderDef(type = "BEAST$it", maxPerChunk = 10) }
        val m = testManager(defs)
        val spawner = NpcSpawner()
        spawner.passes(world, m, 20)

        val spawned = m.getAll().map { it.state.type }.toSet()
        assertTrue(spawned.size in 1..4, "a Roster holds 2-4 passive types, spawned $spawned")
    }

    @Test
    fun trySpawn_splitsTheBudgetByWeight() = runBlocking {
        val world = regionWorld(biome(budget = 8))
        val m =
            testManager(
                mapOf(
                    "HEAVY" to wanderDef(type = "HEAVY", weight = 3, maxPerChunk = 10),
                    "LIGHT" to wanderDef(type = "LIGHT", weight = 1, maxPerChunk = 10)))
        NpcSpawner().passes(world, m, 30)

        assertEquals(6, m.countByType("HEAVY"))
        assertEquals(2, m.countByType("LIGHT"))
    }

    @Test
    fun trySpawn_restocksWhatDied() = runBlocking {
        val world = regionWorld(biome(budget = 3))
        val m = testManager(mapOf("GOAT" to wanderDef(maxPerChunk = 10)))
        val spawner = NpcSpawner()
        spawner.passes(world, m, 10)
        m.getAll().first().let { m.despawnNpc(it.state.id) }
        assertEquals(2, m.countByType("GOAT"))

        spawner.passes(world, m, 10)

        assertEquals(3, m.countByType("GOAT"))
    }

    @Test
    fun trySpawn_rareNeverExceedsItsWorldCeiling() = runBlocking {
        val world = regionWorld()
        val m =
            testManager(
                mapOf(
                    "GOAT" to wanderDef(maxPerChunk = 10),
                    "DRAGON" to wanderDef(type = "DRAGON", maxTotal = 1, maxPerChunk = 10)))
        NpcSpawner().passes(world, m, 20)
        assertTrue(m.countByType("DRAGON") <= 1, "was ${m.countByType("DRAGON")}")
    }

    @Test
    fun trySpawn_topSurfaceIsTreeCanopy_doesNotSpawnOnIt() = runBlocking {
        val world = regionWorld(biome("forest"), top = BlockType.OAK_LOG)
        val m = testManager(mapOf("GOAT" to wanderDef(maxPerChunk = 10)))
        NpcSpawner().passes(world, m, 20)
        assertTrue(m.getAll().isEmpty(), "a walker must not spawn on a tree canopy/trunk")
    }

    @Test
    fun trySpawn_biomeFilterNoMatch_doesNotSpawn() = runBlocking {
        val world = regionWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(spawnBiomes = listOf("nonexistent_biome"))))
        NpcSpawner().passes(world, m, 5)
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun trySpawn_noDiscoveredChunks_doesNothing() = runBlocking {
        val world = WorldState(OneRegionGenerator(testWorld().generator, biome()))
        val m = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().passes(world, m, 1)
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun trySpawn_aquaticNpc_spawnsInsideWaterColumn() = runBlocking {
        val sea =
            biome("sea", budget = 20)
                .copy(
                    surface = BlockType.SAND,
                    subsurface = BlockType.SANDSTONE,
                    liquid = true,
                    waterLevel = 10)
        val world = regionWorld(sea)
        val size = WorldConstants.CHUNK_SIZE * 5
        for (x in 0 until size) for (z in 0 until size) for (y in 4..9) {
            world.applyChange(BlockChange(BlockPos(x, y, z), BlockType.WATER, 0))
        }
        val m =
            testManager(
                mapOf(
                    "FISH" to
                        wanderDef(
                            type = "FISH",
                            spawnBiomes = listOf("sea"),
                            movementMode = listOf(MovementMode.SWIMMING))))
        NpcSpawner().passes(world, m, 10)

        assertTrue(m.getAll().isNotEmpty(), "aquatic NPC should spawn in a liquid biome")
        m.getAll().forEach {
            val y = it.state.pos.y.toInt()
            assertTrue(y in 4..9, "spawn Y $y should be inside the water column")
            assertTrue(
                world.getBlock(it.state.pos.x.toInt(), y, it.state.pos.z.toInt()).isLiquid,
                "aquatic NPC must stand in water")
        }
    }

    @Test
    fun trySpawn_aquaticNpc_notInLiquidBiome_doesNotSpawn() = runBlocking {
        val world = regionWorld()
        val m =
            testManager(
                mapOf(
                    "FISH" to
                        wanderDef(type = "FISH", movementMode = listOf(MovementMode.SWIMMING))))
        NpcSpawner().passes(world, m, 5)
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun countsByType_matchesCountByType_andIgnoresTheDead() = runBlocking {
        val world = regionWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(), "SELLER" to staticDef()))
        NpcSpawner().passes(world, m, 1)
        m.getAll().first().isDead = true

        val counts = m.countsByType()

        val living = m.getAll().count { it.state.type == "GOAT" && !it.isDead }
        assertTrue(counts["GOAT"] == living, "${counts["GOAT"]} vs $living")
        assertTrue(counts["GOAT"]!! < m.countByType("GOAT"), "the dead one must be excluded")
    }
}
