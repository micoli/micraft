package org.micoli.micraft.http.terrain

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.http.topBlockColor
import org.micoli.micraft.http.topBlockY
import org.micoli.micraft.support.testWorld

class TerrainCacheTest {
    @Test
    fun cachedJson_initiallyEmptyArray() {
        assertEquals("[]", TerrainCache().cachedJson)
    }

    @Test
    fun rebuild_allAirChunk_updatesCachedJson() {
        val cache = TerrainCache()
        val world = testWorld()
        val chunk = world.getOrGenerate(ChunkPos(0, 0))
        cache.rebuild(listOf(chunk))
        assertTrue(cache.cachedJson != "[]", "cachedJson should be updated after rebuild")
    }

    @Test
    fun topBlockY_allAirChunk_returnsNull() {
        val world = testWorld()
        val chunk = world.getOrGenerate(ChunkPos(0, 0))
        assertNull(topBlockY(chunk, 0, 0))
        assertNull(topBlockY(chunk, 8, 8))
    }

    @Test
    fun topBlockColor_allAirChunk_returnsNull() {
        val world = testWorld()
        val chunk = world.getOrGenerate(ChunkPos(0, 0))
        assertNull(topBlockColor(chunk, 0, 0))
    }

    @Test
    fun rebuild_populatesCacheFromChunks() {
        val cache = TerrainCache()
        val world = testWorld()
        val chunks =
            listOf(
                world.getOrGenerate(ChunkPos(0, 0)),
                world.getOrGenerate(ChunkPos(1, 0)),
            )
        cache.rebuild(chunks)
        assertTrue(cache.cachedJson != "[]")
    }

    @Test
    fun save_writesFile() {
        val cache = TerrainCache()
        val world = testWorld()
        cache.update(world.getOrGenerate(ChunkPos(0, 0)))
        val file = Files.createTempFile("terrain-cache", ".json")
        cache.save(file)
        assertTrue(file.toFile().length() > 0)
    }

    @Test
    fun prewarm_emptyChunksDir_doesNotThrow() {
        val cache = TerrainCache()
        val chunksDir = Files.createTempDirectory("terrain-chunks")
        val cacheFile = Files.createTempFile("terrain-cache", ".json")
        cache.prewarm(chunksDir, cacheFile)
    }

    @Test
    fun rebuild_multipleChunks_cachedJsonContainsAll() {
        val cache = TerrainCache()
        val world = testWorld()
        cache.rebuild(
            listOf(
                world.getOrGenerate(ChunkPos(0, 0)),
                world.getOrGenerate(ChunkPos(1, 0)),
            ))
        assertTrue(cache.cachedJson.contains("\"cx\":1"), "cachedJson should contain chunk at cx=1")
    }
}
