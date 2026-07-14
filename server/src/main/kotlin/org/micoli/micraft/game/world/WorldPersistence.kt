package org.micoli.micraft.game.world

import com.charleskorn.kaml.Yaml
import java.io.IOException
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.keybinding.defaultKeyBindings
import org.micoli.micraft.player.PlayerState
import org.slf4j.LoggerFactory

private val worldPersistenceLog = LoggerFactory.getLogger("WorldPersistence")

private val playerJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val keybindingsJson = Json { ignoreUnknownKeys = true }

class WorldPersistence(val worldDir: Path) {
    private val chunksDir = worldDir.resolve("chunks")
    private val playersDir = worldDir.resolve("players")
    private val metaFile = worldDir.resolve("world.yaml")

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
                worldPersistenceLog.warn(
                    "Corrupt chunk file for {}: expected {} bytes, got {}",
                    pos,
                    Chunk.TOTAL,
                    bytes.size)
                return null
            }
            Chunk(pos, bytes)
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to load chunk {}: {}", pos, e.message)
            null
        }
    }

    fun saveChunk(pos: ChunkPos, chunk: Chunk) {
        val file = chunksDir.resolve("${pos.cx}_${pos.cz}.mcc.gz")
        try {
            GZIPOutputStream(file.outputStream()).use { it.write(chunk.blocks) }
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save chunk {}: {}", pos, e.message)
        }
    }

    fun loadPlayerState(name: String): PlayerState? {
        val jsonFile = playersDir.resolve("${name.sanitize()}.json")
        val yamlFile = playersDir.resolve("${name.sanitize()}.yaml")
        return when {
            jsonFile.exists() ->
                try {
                    playerJson.decodeFromString(PlayerState.serializer(), jsonFile.readText())
                } catch (e: Exception) {
                    worldPersistenceLog.warn("Failed to load player {}: {}", name, e.message)
                    null
                }
            yamlFile.exists() ->
                try {
                    Yaml.default.decodeFromString(PlayerState.serializer(), yamlFile.readText())
                } catch (e: Exception) {
                    worldPersistenceLog.warn("Failed to load player {}: {}", name, e.message)
                    null
                }
            else -> null
        }
    }

    fun savePlayerState(name: String, state: PlayerState) {
        val file = playersDir.resolve("${name.sanitize()}.json")
        try {
            file.writeText(playerJson.encodeToString(PlayerState.serializer(), state))
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save player {}: {}", name, e.message)
        }
    }

    private val keybindingsSerializer =
        MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    private val macrosSerializer = MapSerializer(String.serializer(), String.serializer())

    fun loadPlayerKeyBindings(name: String): Map<String, List<String>> {
        val jsonFile = playersDir.resolve("${name.sanitize()}-keybindings.json")
        val yamlFile = playersDir.resolve("${name.sanitize()}-keybindings.yaml")
        if (!jsonFile.exists() && !yamlFile.exists()) {
            val defaults = defaultKeyBindings()
            savePlayerKeyBindings(name, defaults)
            return defaults
        }
        return try {
            if (jsonFile.exists()) {
                val saved =
                    keybindingsJson.decodeFromString(keybindingsSerializer, jsonFile.readText())
                defaultKeyBindings() + saved
            } else {
                val saved =
                    Yaml.default.decodeFromString(keybindingsSerializer, yamlFile.readText())
                defaultKeyBindings() + saved
            }
        } catch (e: Exception) {
            worldPersistenceLog.warn("Failed to load keybindings for {}: {}", name, e.message)
            defaultKeyBindings()
        }
    }

    fun savePlayerKeyBindings(name: String, bindings: Map<String, List<String>>) {
        val file = playersDir.resolve("${name.sanitize()}-keybindings.json")
        try {
            file.writeText(keybindingsJson.encodeToString(keybindingsSerializer, bindings))
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save keybindings for {}: {}", name, e.message)
        }
    }

    fun loadPlayerCustomCommands(name: String): Map<String, List<String>> {
        val file = playersDir.resolve("${name.sanitize()}-custom-commands.yaml")
        if (!file.exists()) return emptyMap()
        return try {
            Yaml.default.decodeFromString(keybindingsSerializer, file.readText())
        } catch (e: Exception) {
            worldPersistenceLog.warn("Failed to load custom commands for {}: {}", name, e.message)
            emptyMap()
        }
    }

    fun savePlayerCustomCommands(name: String, commands: Map<String, List<String>>) {
        val file = playersDir.resolve("${name.sanitize()}-custom-commands.yaml")
        try {
            file.writeText(Yaml.default.encodeToString(keybindingsSerializer, commands))
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save custom commands for {}: {}", name, e.message)
        }
    }

    fun loadPlayerMacros(name: String): Map<String, String> {
        val file = playersDir.resolve("${name.sanitize()}-macros.yaml")
        if (!file.exists()) return emptyMap()
        return try {
            Yaml.default.decodeFromString(macrosSerializer, file.readText())
        } catch (e: Exception) {
            worldPersistenceLog.warn("Failed to load macros for {}: {}", name, e.message)
            emptyMap()
        }
    }

    fun savePlayerMacros(name: String, macros: Map<String, String>) {
        val file = playersDir.resolve("${name.sanitize()}-macros.yaml")
        try {
            file.writeText(Yaml.default.encodeToString(macrosSerializer, macros))
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save macros for {}: {}", name, e.message)
        }
    }

    fun loadMetadata(): WorldMetadata? {
        if (!metaFile.exists()) return null
        return try {
            Yaml.default.decodeFromString(WorldMetadata.serializer(), metaFile.readText())
        } catch (e: Exception) {
            worldPersistenceLog.warn("Failed to load world metadata: {}", e.message)
            null
        }
    }

    fun saveMetadata(meta: WorldMetadata) {
        try {
            metaFile.writeText(Yaml.default.encodeToString(WorldMetadata.serializer(), meta))
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save world metadata: {}", e.message)
        }
    }

    private fun String.sanitize() = replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
