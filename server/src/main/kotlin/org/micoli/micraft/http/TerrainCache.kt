package org.micoli.micraft.http

import com.charleskorn.kaml.Yaml
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(TerrainCache::class.java)

@Serializable private data class ChunkHeightInfo(val cx: Int, val cz: Int, val avgHeight: Int?)

class TerrainCache {
    private val cache = ConcurrentHashMap<ChunkPos, Pair<List<String?>, Int?>>()
    @Volatile
    var cachedJson: String = "[]"
        private set

    /**
     * Load chunk images from [cacheDir] then recompute any chunk in [chunksDir] whose .mcc.gz is
     * newer than its cached PNG — so on startup the map is fully prewarmed.
     */
    fun prewarm(chunksDir: Path, cacheDir: Path) {
        if (cacheDir.exists()) {
            val heights = loadHeights(cacheDir)
            for (file in
                cacheDir.toFile().listFiles { f -> f.name.endsWith(".png") } ?: emptyArray()) {
                val pos = parseChunkPos(file.nameWithoutExtension) ?: continue
                // Deleted on any read failure — including ImageIO.read returning null for an
                // unparsable file, which throws nothing — so the recompute pass below (which
                // skips chunks whose PNG already exists and is up to date) doesn't mistake the
                // corrupted file for a valid, current cache entry and leave it stuck unrecovered.
                try {
                    val img = ImageIO.read(file)
                    if (img == null) {
                        log.warn(
                            "Failed to read terrain cache PNG {}: unreadable image — deleting for regeneration",
                            file.name)
                        file.delete()
                        continue
                    }
                    cache[pos] = Pair(imageToColors(img), heights[pos])
                } catch (e: Exception) {
                    log.warn(
                        "Failed to read terrain cache PNG {}: {} — deleting for regeneration",
                        file.name,
                        e.message)
                    file.delete()
                }
            }
        }

        val chunkFiles = chunksDir.toFile().listFiles { f -> f.name.endsWith(".mcc.gz") } ?: return
        val diskChunks =
            chunkFiles.mapNotNullTo(HashSet()) {
                parseChunkPos(it.nameWithoutExtension.removeSuffix(".mcc"))
            }
        val staleCount = cache.keys.count { it !in diskChunks }
        cache.keys.retainAll(diskChunks)
        if (staleCount > 0) log.info("Pruned {} stale terrain cache entries", staleCount)

        var recomputed = 0
        for (file in chunkFiles) {
            val name = file.nameWithoutExtension.removeSuffix(".mcc")
            val pos = parseChunkPos(name) ?: continue
            val png = cacheDir.resolve("${pos.cx}_${pos.cz}.png").toFile()
            if (png.exists() && file.lastModified() <= png.lastModified()) continue
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

    fun save(cacheDir: Path) {
        try {
            cacheDir.createDirectories()
            val heights = mutableListOf<ChunkHeightInfo>()
            for ((pos, pair) in cache) {
                val (colors, avgHeight) = pair
                val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                for (lx in 0..15) for (lz in 0..15) {
                    img.setRGB(lx, lz, colorToArgb(colors[lx * 16 + lz]))
                }
                ImageIO.write(img, "PNG", cacheDir.resolve("${pos.cx}_${pos.cz}.png").toFile())
                heights += ChunkHeightInfo(pos.cx, pos.cz, avgHeight)
            }
            cacheDir
                .resolve("heights.yaml")
                .toFile()
                .writeText(
                    Yaml.default.encodeToString(
                        ListSerializer(ChunkHeightInfo.serializer()), heights))
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

    fun getChunkImage(pos: ChunkPos): BufferedImage? {
        val (colors, _) = cache[pos] ?: return null
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (lx in 0..15) for (lz in 0..15) img.setRGB(lx, lz, colorToArgb(colors[lx * 16 + lz]))
        return img
    }

    private fun loadHeights(cacheDir: Path): Map<ChunkPos, Int?> {
        val file = cacheDir.resolve("heights.yaml").toFile()
        if (!file.exists()) return emptyMap()
        return try {
            Yaml.default
                .decodeFromString(ListSerializer(ChunkHeightInfo.serializer()), file.readText())
                .associate { ChunkPos(it.cx, it.cz) to it.avgHeight }
        } catch (e: Exception) {
            log.warn("Failed to load terrain heights: {}", e.message)
            emptyMap()
        }
    }
}

private fun imageToColors(img: BufferedImage): List<String?> {
    val colors = ArrayList<String?>(256)
    for (lx in 0..15) for (lz in 0..15) {
        val argb = img.getRGB(lx, lz)
        val a = (argb ushr 24) and 0xFF
        colors +=
            if (a == 0) null
            else
                "#%02x%02x%02x".format((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    }
    return colors
}

private fun colorToArgb(hex: String?): Int {
    if (hex == null) return 0
    val r = hex.substring(1, 3).toInt(16)
    val g = hex.substring(3, 5).toInt(16)
    val b = hex.substring(5, 7).toInt(16)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** Parses "{cx}_{cz}" (supports negative values). */
private fun parseChunkPos(name: String): ChunkPos? {
    val idx = name.lastIndexOf('_')
    if (idx <= 0) return null
    val cx = name.substring(0, idx).toIntOrNull() ?: return null
    val cz = name.substring(idx + 1).toIntOrNull() ?: return null
    return ChunkPos(cx, cz)
}

internal fun topBlockColor(chunk: Chunk, lx: Int, lz: Int): String? {
    for (y in Chunk.SIZE_Y - 1 downTo 0) {
        val bt = chunk.getBlock(lx, y, lz)
        if (bt == BlockType.AIR) continue
        val def = BlockRegistry.get(bt)
        if (!def.solid && !def.liquid && !def.minimapVisible) continue
        val c = def.minimapColor
        return "#%02x%02x%02x".format(c[0], c[1], c[2])
    }
    return null
}

internal fun topBlockY(chunk: Chunk, lx: Int, lz: Int): Int? {
    for (y in Chunk.SIZE_Y - 1 downTo 0) {
        val bt = chunk.getBlock(lx, y, lz)
        if (bt == BlockType.AIR) continue
        val def = BlockRegistry.get(bt)
        if (!def.solid && !def.liquid && !def.minimapVisible) continue
        return y
    }
    return null
}
