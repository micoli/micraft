package org.micoli.micraft.game.world

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testWorld

class WorldStateTest {
    @Test
    fun getOrGenerate_returnsChunk() {
        val world = testWorld()
        val chunk = world.getOrGenerate(ChunkPos(0, 0))
        assertNotNull(chunk)
        assertEquals(ChunkPos(0, 0), chunk.pos)
    }

    @Test
    fun getOrGenerate_samePos_returnsSameChunk() {
        val world = testWorld()
        val a = world.getOrGenerate(ChunkPos(1, 2))
        val b = world.getOrGenerate(ChunkPos(1, 2))
        assertEquals(a.pos, b.pos)
        assertEquals(a.blocks.size, b.blocks.size)
    }

    @Test
    fun getBlockIfLoaded_unloadedChunk_returnsAir() {
        val world = testWorld()
        assertEquals(BlockType.AIR, world.getBlockIfLoaded(100, 10, 100))
    }

    @Test
    fun getBlockIfLoaded_belowMinY_returnsAir() {
        val world = testWorld()
        assertEquals(BlockType.AIR, world.getBlockIfLoaded(0, WorldConstants.WORLD_MIN_Y - 1, 0))
    }

    @Test
    fun getBlockIfLoaded_aboveMaxY_returnsAir() {
        val world = testWorld()
        assertEquals(BlockType.AIR, world.getBlockIfLoaded(0, WorldConstants.WORLD_MAX_Y + 1, 0))
    }

    @Test
    fun applyChange_updatesBlock() {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(5, 10, 5), BlockType.STONE))
        assertEquals(BlockType.STONE, world.getBlock(5, 10, 5))
    }

    @Test
    fun applyChange_overwritesExistingBlock() {
        val world = testWorld(Triple(5, 10, 5))
        assertEquals(BlockType.STONE, world.getBlock(5, 10, 5))
        world.applyChange(BlockChange(BlockPos(5, 10, 5), BlockType.AIR))
        assertEquals(BlockType.AIR, world.getBlock(5, 10, 5))
    }

    @Test
    fun loadedChunkCount_tracksGeneratedChunks() {
        val world = testWorld()
        assertEquals(0, world.loadedChunkCount())
        world.getOrGenerate(ChunkPos(0, 0))
        assertEquals(1, world.loadedChunkCount())
        world.getOrGenerate(ChunkPos(1, 0))
        assertEquals(2, world.loadedChunkCount())
    }

    @Test
    fun getChunkIfDiscovered_notGenerated_returnsNull() {
        val world = testWorld()
        assertEquals(null, world.getChunkIfDiscovered(ChunkPos(99, 99)))
    }

    @Test
    fun getChunkIfDiscovered_afterGenerate_returnsChunk() {
        val world = testWorld()
        world.getOrGenerate(ChunkPos(2, 3))
        assertNotNull(world.getChunkIfDiscovered(ChunkPos(2, 3)))
    }

    @Test
    fun flushDirty_noPersistence_noError() {
        val world = WorldState(MapChunkGenerator())
        world.getOrGenerate(ChunkPos(0, 0))
        world.flushDirty()
    }

    @Test
    fun flushDirty_withPersistence_savesChunks() {
        val dir = Files.createTempDirectory("world-state-flush")
        val persistence = WorldPersistence(dir)
        val world = WorldState(MapChunkGenerator(), persistence)
        world.getOrGenerate(ChunkPos(0, 0))
        world.flushDirty()
        assertNotNull(persistence.loadChunk(ChunkPos(0, 0)))
    }
}
