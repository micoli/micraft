package org.micoli.micraft.http.terrain

import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val dir = Files.createTempDirectory("terrain-cache")
        cache.save(dir)
        assertTrue(
            dir.toFile().listFiles()?.isNotEmpty() == true,
            "terrain_cache dir should contain files")
        assertTrue(dir.resolve("0_0.png").toFile().exists(), "should write 0_0.png")
    }

    @Test
    fun prewarm_emptyChunksDir_doesNotThrow() {
        val cache = TerrainCache()
        val chunksDir = Files.createTempDirectory("terrain-chunks")
        val cacheDir = Files.createTempDirectory("terrain-cache")
        cache.prewarm(chunksDir, cacheDir)
    }

    @Test
    fun prewarm_corruptedCachePng_deletesItAndRecomputesFromChunkFile() {
        val cache = TerrainCache()
        val world = testWorld()
        val chunk = world.getOrGenerate(ChunkPos(0, 0))
        val chunksDir = Files.createTempDirectory("terrain-chunks")
        val cacheDir = Files.createTempDirectory("terrain-cache")

        val chunkFile = chunksDir.resolve("0_0.mcc.gz").toFile()
        GZIPOutputStream(chunkFile.outputStream()).use { it.write(chunk.blocks) }

        val corruptedPng = cacheDir.resolve("0_0.png").toFile()
        corruptedPng.writeBytes(byteArrayOf(1, 2, 3)) // not a valid PNG
        // Newer than the chunk file, so without deletion the recompute pass below would treat it
        // as an up-to-date cache entry and skip regenerating it forever.
        corruptedPng.setLastModified(chunkFile.lastModified() + 10_000)

        cache.prewarm(chunksDir, cacheDir)

        assertFalse(corruptedPng.exists(), "corrupted PNG should be deleted")
        assertTrue(cache.cachedJson.contains("\"cx\":0"), "chunk should be recomputed into cache")
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
