package org.micoli.micraft.game.world

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
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

private val entityJson = Json { encodeDefaults = true }

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
    private val playerLoadedMtime = ConcurrentHashMap<String, FileTime>()
    private val playerMacrosLoadedMtime = ConcurrentHashMap<String, FileTime>()

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
            val statesFile = chunksDir.resolve("${pos.cx}_${pos.cz}.mcs.gz")
            val states =
                if (statesFile.exists())
                    try {
                        GZIPInputStream(statesFile.inputStream()).use { it.readBytes() }
                    } catch (_: IOException) {
                        ByteArray(Chunk.TOTAL)
                    }
                else ByteArray(Chunk.TOTAL)
            val entityFile = chunksDir.resolve("${pos.cx}_${pos.cz}.mce.gz")
            val entityMasters =
                if (entityFile.exists())
                    try {
                        val text = GZIPInputStream(entityFile.inputStream()).use { it.readBytes() }
                        entityJson.decodeFromString(
                            ListSerializer(BlockEntity.serializer()), text.toString(Charsets.UTF_8))
                    } catch (_: Exception) {
                        emptyList()
                    }
                else emptyList()
            Chunk(pos, bytes, states, entityMasters)
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
        if (chunk.states.isNotEmpty() && chunk.states.any { it != 0.toByte() }) {
            val statesFile = chunksDir.resolve("${pos.cx}_${pos.cz}.mcs.gz")
            try {
                GZIPOutputStream(statesFile.outputStream()).use { it.write(chunk.states) }
            } catch (e: IOException) {
                worldPersistenceLog.warn("Failed to save chunk states {}: {}", pos, e.message)
            }
        }
        if (chunk.entityMasters.isNotEmpty()) {
            val entityFile = chunksDir.resolve("${pos.cx}_${pos.cz}.mce.gz")
            try {
                val json =
                    entityJson.encodeToString(
                        ListSerializer(BlockEntity.serializer()), chunk.entityMasters)
                GZIPOutputStream(entityFile.outputStream()).use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                }
            } catch (e: IOException) {
                worldPersistenceLog.warn("Failed to save chunk entities {}: {}", pos, e.message)
            }
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
        val loadedAt = playerLoadedMtime[name]
        if (loadedAt != null && yamlFile.exists()) {
            val currentMtime = Files.getLastModifiedTime(yamlFile)
            if (currentMtime > loadedAt) {
                worldPersistenceLog.info(
                    "Player file {} was externally modified since load — skipping overwrite", name)
                return
            }
        }
        try {
            yamlFile.writeText(Yaml.default.encodeToString(PlayerFile.serializer(), file))
            playerLoadedMtime[name] = Files.getLastModifiedTime(yamlFile)
        } catch (e: IOException) {
            worldPersistenceLog.warn("Failed to save player {}: {}", name, e.message)
        }
    }

    fun listPlayers(): List<String> =
        playersDir
            .toFile()
            .listFiles { f -> f.extension == "yaml" && !f.name.contains("-") }
            ?.map { it.nameWithoutExtension } ?: emptyList()

    fun listPlayersByEmail(email: String): List<PlayerState> =
        playersDir
            .toFile()
            .listFiles { f -> f.extension == "yaml" && !f.name.contains("-") }
            ?.mapNotNull { f ->
                try {
                    loadPlayerFileFromYaml(f.toPath())?.state
                } catch (_: Exception) {
                    null
                }
            }
            ?.filter { it.email.equals(email, ignoreCase = true) } ?: emptyList()

    fun loadPlayerFile(name: String): PlayerFile? = loadOrCreatePlayerFile(name)

    fun loadPlayerState(name: String): PlayerState? {
        val yamlFile = playersDir.resolve("${name.sanitize()}.yaml")
        if (yamlFile.exists()) {
            playerLoadedMtime[name] = Files.getLastModifiedTime(yamlFile)
        }
        return loadOrCreatePlayerFile(name)?.state
    }

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
        playerMacrosLoadedMtime[name] = Files.getLastModifiedTime(file)
        return try {
            Yaml.default.decodeFromString(macrosSerializer, file.readText())
        } catch (e: Exception) {
            worldPersistenceLog.warn("Failed to load macros for {}: {}", name, e.message)
            emptyMap()
        }
    }

    fun savePlayerMacros(name: String, macros: Map<String, String>) {
        val file = playersDir.resolve("${name.sanitize()}-macros.yaml")
        val loadedAt = playerMacrosLoadedMtime[name]
        if (loadedAt != null && file.exists()) {
            val currentMtime = Files.getLastModifiedTime(file)
            if (currentMtime > loadedAt) {
                worldPersistenceLog.info(
                    "Macros file for {} was externally modified since load — skipping overwrite",
                    name)
                return
            }
        }
        try {
            file.writeText(Yaml.default.encodeToString(macrosSerializer, macros))
            playerMacrosLoadedMtime[name] = Files.getLastModifiedTime(file)
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
