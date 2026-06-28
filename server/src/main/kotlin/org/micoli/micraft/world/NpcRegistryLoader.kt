package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.micoli.micraft.npc.NpcBehaviorRegistry
import org.micoli.micraft.npc.NpcDefinition
import org.micoli.micraft.npc.NpcSpawnConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("NpcRegistryLoader")

@Serializable
private data class NpcSpawnConfigRaw(
    val autoSpawn: Boolean = false,
    val maxTotal: Int = 0,
    val maxPerChunk: Int = 1,
    val spawnBiomes: List<String> = emptyList(),
)

@Serializable
private data class NpcYamlEntry(
    val bbmodelFile: String = "npc_unknown",
    val behavior: String = "static",
    val width: Float = 0.6f,
    val height: Float = 1.8f,
    val wanderSpeed: Float = 0f,
    val wanderRadius: Float = 0f,
    val spawn: NpcSpawnConfigRaw = NpcSpawnConfigRaw(),
)

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), NpcYamlEntry.serializer())

private val DEFAULT_YAML: Map<String, NpcYamlEntry> =
    mapOf(
        "SELLER" to
            NpcYamlEntry(
                bbmodelFile = "npc_seller",
                behavior = "interactionable",
                width = 0.6f,
                height = 1.8f,
            ),
        "BLACK_SMITH" to
            NpcYamlEntry(
                bbmodelFile = "npc_blacksmith",
                behavior = "interactionable",
                width = 0.6f,
                height = 1.8f,
            ),
        "GOAT" to
            NpcYamlEntry(
                bbmodelFile = "npc_goat",
                behavior = "random_movable",
                width = 0.5f,
                height = 0.9f,
                wanderSpeed = 2.0f,
                wanderRadius = 12.0f,
                spawn = NpcSpawnConfigRaw(autoSpawn = true, maxTotal = 30, maxPerChunk = 3),
            ),
        "DUCK" to
            NpcYamlEntry(
                bbmodelFile = "npc_duck",
                behavior = "random_movable",
                width = 0.3f,
                height = 0.5f,
                wanderSpeed = 1.2f,
                wanderRadius = 6.0f,
                spawn = NpcSpawnConfigRaw(autoSpawn = true, maxTotal = 20, maxPerChunk = 2),
            ),
    )

class NpcRegistryLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(ENTRY_MAP_SERIALIZER, DEFAULT_YAML))
            log.info("Generated default NPC registry at {}", path.toAbsolutePath())
        }
    }

    fun load(): Map<String, NpcDefinition> {
        val raw =
            runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, path.readText()) }
                .getOrElse { e ->
                    log.warn("Failed to load npcs.yaml ({}), using defaults", e.message)
                    DEFAULT_YAML
                }
        val result =
            raw.entries
                .mapNotNull { (key, entry) ->
                    runCatching {
                            val behavior = NpcBehaviorRegistry.get(entry.behavior)
                            key to
                                NpcDefinition(
                                    type = key,
                                    behavior = behavior,
                                    behaviorKey = entry.behavior,
                                    bbmodelFile = entry.bbmodelFile,
                                    width = entry.width,
                                    height = entry.height,
                                    wanderSpeed = entry.wanderSpeed,
                                    wanderRadius = entry.wanderRadius,
                                    spawn =
                                        NpcSpawnConfig(
                                            autoSpawn = entry.spawn.autoSpawn,
                                            maxTotal = entry.spawn.maxTotal,
                                            maxPerChunk = entry.spawn.maxPerChunk,
                                            spawnBiomes = entry.spawn.spawnBiomes,
                                        ),
                                )
                        }
                        .onFailure { e ->
                            log.warn("Skipping NPC type '{}' in npcs.yaml: {}", key, e.message)
                        }
                        .getOrNull()
                }
                .toMap()
        log.info("NPC registry loaded: {} NPC types", result.size)
        return result
    }

    fun reload(): Map<String, NpcDefinition> = load()
}
