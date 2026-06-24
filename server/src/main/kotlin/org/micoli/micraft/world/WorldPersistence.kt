package org.micoli.micraft.world

import java.io.IOException
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.PlayerState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("WorldPersistence")

class WorldPersistence(val worldDir: Path) {
    private val chunksDir = worldDir.resolve("chunks")
    private val playersDir = worldDir.resolve("players")
    private val metaFile = worldDir.resolve("world.json")

    init {
        worldDir.createDirectories()
        chunksDir.createDirectories()
        playersDir.createDirectories()
    }

    fun loadChunk(pos: ChunkPos): Chunk? {
        val file = chunksDir.resolve("${pos.cx}_${pos.cz}.mcc.gz")
        if (!file.exists()) return null
        return try {
            val bytes = GZIPInputStream(file.inputStream()).use { it.readBytes() }
            if (bytes.size != Chunk.TOTAL) {
                log.warn(
                    "Corrupt chunk file for {}: expected {} bytes, got {}",
                    pos,
                    Chunk.TOTAL,
                    bytes.size)
                return null
            }
            Chunk(pos, bytes)
        } catch (e: IOException) {
            log.warn("Failed to load chunk {}: {}", pos, e.message)
            null
        }
    }

    fun saveChunk(pos: ChunkPos, chunk: Chunk) {
        val file = chunksDir.resolve("${pos.cx}_${pos.cz}.mcc.gz")
        try {
            GZIPOutputStream(file.outputStream()).use { it.write(chunk.blocks) }
        } catch (e: IOException) {
            log.warn("Failed to save chunk {}: {}", pos, e.message)
        }
    }

    fun loadPlayerState(name: String): PlayerState? {
        val file = playersDir.resolve("${name.sanitize()}.json")
        if (!file.exists()) return null
        return try {
            Json.decodeFromString<PlayerState>(file.readText())
        } catch (e: Exception) {
            log.warn("Failed to load player {}: {}", name, e.message)
            null
        }
    }

    fun savePlayerState(name: String, state: PlayerState) {
        val file = playersDir.resolve("${name.sanitize()}.json")
        try {
            file.writeText(Json.encodeToString(state))
        } catch (e: IOException) {
            log.warn("Failed to save player {}: {}", name, e.message)
        }
    }

    fun loadMetadata(): WorldMetadata? {
        if (!metaFile.exists()) return null
        return try {
            Json.decodeFromString<WorldMetadata>(metaFile.readText())
        } catch (e: Exception) {
            log.warn("Failed to load world metadata: {}", e.message)
            null
        }
    }

    fun saveMetadata(meta: WorldMetadata) {
        try {
            metaFile.writeText(Json.encodeToString(meta))
        } catch (e: IOException) {
            log.warn("Failed to save world metadata: {}", e.message)
        }
    }

    private fun String.sanitize() = replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
