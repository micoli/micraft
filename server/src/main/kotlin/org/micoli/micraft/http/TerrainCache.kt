package org.micoli.micraft.http

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TerrainCache")

class TerrainCache {
    private val cache = ConcurrentHashMap<ChunkPos, Pair<List<String?>, Int?>>()
    @Volatile
    var cachedJson: String = "[]"
        private set

    /**
     * Load from [cacheFile] then recompute any chunk in [chunksDir] whose file is newer than the
     * cache file — so on startup the map is fully prewarmed.
     */
    fun prewarm(chunksDir: Path, cacheFile: Path) {
        val cacheTime =
            if (cacheFile.exists()) {
                try {
                    Json.decodeFromString<List<ChunkTerrainInfo>>(cacheFile.readText()).forEach {
                        info ->
                        cache[ChunkPos(info.cx, info.cz)] = Pair(info.colors, info.avgHeight)
                    }
                    cacheFile.getLastModifiedTime().toMillis()
                } catch (e: Exception) {
                    log.warn("Failed to load terrain cache: {}", e.message)
                    0L
                }
            } else 0L

        val chunkFiles = chunksDir.toFile().listFiles { f -> f.name.endsWith(".mcc.gz") } ?: return
        // Prune cache entries for chunks no longer on disk.
        val diskChunks =
            chunkFiles.mapNotNullTo(HashSet()) {
                parseChunkPos(it.nameWithoutExtension.removeSuffix(".mcc"))
            }
        val staleCount = cache.keys.count { it !in diskChunks }
        cache.keys.retainAll(diskChunks)
        if (staleCount > 0) log.info("Pruned {} stale terrain cache entries", staleCount)
        var recomputed = 0
        for (file in chunkFiles) {
            if (file.lastModified() <= cacheTime) continue
            val name = file.nameWithoutExtension.removeSuffix(".mcc")
            val pos = parseChunkPos(name) ?: continue
            try {
                val bytes = GZIPInputStream(file.inputStream()).use { it.readBytes() }
                if (bytes.size == Chunk.TOTAL) {
                    update(Chunk(pos, bytes))
                    recomputed++
                }
            } catch (e: Exception) {
                log.warn("Failed to read chunk {} for terrain prewarm: {}", pos, e.message)
            }
        }
        log.info(
            "Terrain cache prewarmed: {} from file, {} recomputed",
            cache.size - recomputed,
            recomputed)
        cachedJson = Json.encodeToString(getAll())
    }

    fun save(cacheFile: Path) {
        try {
            cacheFile.writeText(Json.encodeToString(getAll()))
        } catch (e: Exception) {
            log.warn("Failed to save terrain cache: {}", e.message)
        }
    }

    fun rebuild(chunks: Collection<Chunk>) {
        for (chunk in chunks) update(chunk)
        cachedJson = Json.encodeToString(getAll())
    }

    fun update(chunk: Chunk) {
        val colors = ArrayList<String?>(256)
        for (lx in 0 until 16) for (lz in 0 until 16) colors += topBlockColor(chunk, lx, lz)
        val centerHeight = topBlockY(chunk, 8, 8)
        cache[chunk.pos] = Pair(colors, centerHeight)
    }

    fun getAll(): List<ChunkTerrainInfo> =
        cache.entries.map { (pos, pair) ->
            ChunkTerrainInfo(pos.cx, pos.cz, pair.first, pair.second)
        }
}

/** Parses "{cx}_{cz}" (supports negative values). */
private fun parseChunkPos(name: String): ChunkPos? {
    // Handle negative coords: "-1_-2" → split on the last underscore that separates cx from cz.
    // Safe approach: find the '_' that is not preceded by a digit of the cx part.
    val idx = name.lastIndexOf('_')
    if (idx <= 0) return null
    val cx = name.substring(0, idx).toIntOrNull() ?: return null
    val cz = name.substring(idx + 1).toIntOrNull() ?: return null
    return ChunkPos(cx, cz)
}

internal fun topBlockColor(chunk: Chunk, lx: Int, lz: Int): String? {
    for (y in Chunk.SIZE_Y - 1 downTo 0) {
        val bt = chunk.getBlock(lx, y, lz)
        if (bt != BlockType.AIR) {
            val c = BlockRegistry.get(bt).minimapColor
            return "#%02x%02x%02x".format(c[0], c[1], c[2])
        }
    }
    return null
}

internal fun topBlockY(chunk: Chunk, lx: Int, lz: Int): Int? {
    for (y in Chunk.SIZE_Y - 1 downTo 0) if (chunk.getBlock(lx, y, lz) != BlockType.AIR) return y
    return null
}
