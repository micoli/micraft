package org.micoli.micraft.game.world

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
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

private val playerYaml = Yaml(configuration = YamlConfiguration(strictMode = false))

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

    fun loadPlayerStateById(id: String): PlayerState? =
        playersDir
            .toFile()
            .listFiles { f -> f.extension == "json" || f.extension == "yaml" }
            ?.firstNotNullOfOrNull { file ->
                try {
                    val state =
                        if (file.extension == "yaml")
                            loadPlayerFileFromYaml(file.toPath())?.state
                                ?: playerYaml.decodeFromString(
                                    PlayerState.serializer(), file.readText())
                        else playerJson.decodeFromString(PlayerState.serializer(), file.readText())
                    state.takeIf { it.id == id }
                } catch (_: Exception) {
                    null
                }
            }

    private fun loadPlayerFileFromYaml(yamlFile: Path): PlayerFile? =
        try {
            playerYaml.decodeFromString(PlayerFile.serializer(), yamlFile.readText())
        } catch (_: Exception) {
            null
        }

    private fun loadOrCreatePlayerFile(name: String): PlayerFile? {
        val yamlFile = playersDir.resolve("${name.sanitize()}.yaml")
        val jsonFile = playersDir.resolve("${name.sanitize()}.json")
        return when {
            yamlFile.exists() -> {
                loadPlayerFileFromYaml(yamlFile)
                    ?: run {
                        // Legacy bare PlayerState yaml — migrate
                        try {
                            val state =
                                playerYaml.decodeFromString(
                                    PlayerState.serializer(), yamlFile.readText())
                            PlayerFile(
                                state,
                                migrateLegacyKeybindings(name),
                                migrateLegacyCustomCommands(name))
                        } catch (e: Exception) {
                            worldPersistenceLog.warn(
                                "Failed to load player {}: {}", name, e.message)
                            null
                        }
                    }
            }
            jsonFile.exists() -> {
                try {
                    val state =
                        playerJson.decodeFromString(PlayerState.serializer(), jsonFile.readText())
                    PlayerFile(
                        state, migrateLegacyKeybindings(name), migrateLegacyCustomCommands(name))
                } catch (e: Exception) {
                    worldPersistenceLog.warn("Failed to load player {}: {}", name, e.message)
                    null
                }
            }
            else -> null
        }
    }

    private fun migrateLegacyKeybindings(name: String): Map<String, List<String>> {
        val jsonFile = playersDir.resolve("${name.sanitize()}-keybindings.json")
        val yamlFile = playersDir.resolve("${name.sanitize()}-keybindings.yaml")
        return when {
            jsonFile.exists() ->
                try {
                    keybindingsJson.decodeFromString(keybindingsSerializer, jsonFile.readText())
                } catch (_: Exception) {
                    emptyMap()
                }
            yamlFile.exists() ->
                try {
                    Yaml.default.decodeFromString(keybindingsSerializer, yamlFile.readText())
                } catch (_: Exception) {
                    emptyMap()
                }
            else -> emptyMap()
        }
    }

    private fun migrateLegacyCustomCommands(name: String): Map<String, List<String>> {
        val file = playersDir.resolve("${name.sanitize()}-custom-commands.yaml")
        return if (file.exists())
            try {
                Yaml.default.decodeFromString(keybindingsSerializer, file.readText())
            } catch (_: Exception) {
                emptyMap()
            }
        else emptyMap()
    }

    private fun writePlayerFile(name: String, file: PlayerFile) {
        val yamlFile = playersDir.resolve("${name.sanitize()}.yaml")
        try {
            yamlFile.writeText(Yaml.default.encodeToString(PlayerFile.serializer(), file))
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save player {}: {}", name, e.message)
        }
    }

    fun listPlayers(): List<String> =
        playersDir
            .toFile()
            .listFiles { f -> f.extension == "yaml" && !f.name.contains("-") }
            ?.map { it.nameWithoutExtension } ?: emptyList()

    fun loadPlayerFile(name: String): PlayerFile? = loadOrCreatePlayerFile(name)

    fun loadPlayerState(name: String): PlayerState? = loadOrCreatePlayerFile(name)?.state

    fun savePlayerState(name: String, state: PlayerState) {
        val existing = loadOrCreatePlayerFile(name)
        writePlayerFile(name, existing?.copy(state = state) ?: PlayerFile(state))
    }

    fun renamePlayer(oldName: String, newName: String) {
        val oldSanitized = oldName.sanitize()
        val newSanitized = newName.sanitize()
        playersDir
            .listDirectoryEntries()
            .filter { it.name.startsWith(oldSanitized) }
            .forEach { file ->
                val newFileName = newSanitized + file.name.removePrefix(oldSanitized)
                file.moveTo(playersDir.resolve(newFileName), overwrite = true)
            }
        val state = loadPlayerState(newName)
        if (state != null) savePlayerState(newName, state.copy(name = newName))
    }

    private val keybindingsSerializer =
        MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    private val macrosSerializer = MapSerializer(String.serializer(), String.serializer())

    fun loadPlayerKeyBindings(name: String): Map<String, List<String>> {
        val file = loadOrCreatePlayerFile(name)
        val saved = file?.keybindings ?: emptyMap()
        if (saved.isEmpty()) {
            val defaults = defaultKeyBindings()
            savePlayerKeyBindings(name, defaults)
            return defaults
        }
        return defaultKeyBindings() + saved
    }

    fun savePlayerKeyBindings(name: String, bindings: Map<String, List<String>>) {
        val existing = loadOrCreatePlayerFile(name) ?: return
        writePlayerFile(name, existing.copy(keybindings = bindings))
    }

    fun loadPlayerCustomCommands(name: String): Map<String, List<String>> =
        loadOrCreatePlayerFile(name)?.customCommands ?: emptyMap()

    fun savePlayerCustomCommands(name: String, commands: Map<String, List<String>>) {
        val existing = loadOrCreatePlayerFile(name) ?: return
        writePlayerFile(name, existing.copy(customCommands = commands))
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
