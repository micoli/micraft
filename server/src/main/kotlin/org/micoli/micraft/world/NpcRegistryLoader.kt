package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.micoli.micraft.npc.AggroMode
import org.micoli.micraft.npc.NpcBehaviorRegistry
import org.micoli.micraft.npc.NpcDefinition
import org.micoli.micraft.npc.NpcSpawnConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("NpcRegistryLoader")

@Serializable
internal data class NpcSpawnConfigRaw(
    val autoSpawn: Boolean = false,
    val maxTotal: Int = 0,
    val maxPerChunk: Int = 1,
    val spawnBiomes: List<String> = emptyList(),
)

@Serializable
internal data class NpcYamlEntry(
    val behavior: String = "static",
    val width: Float = 0.6f,
    val height: Float = 1.8f,
    val wanderSpeed: Float = 0f,
    val wanderRadius: Float = 0f,
    val spawn: NpcSpawnConfigRaw = NpcSpawnConfigRaw(),
    val hp: Int = 20,
    val aggroMode: AggroMode = AggroMode.PASSIVE,
    val aggroRange: Float = 12.0f,
    val deaggroTimeSec: Float = 10.0f,
    val attackId: String? = null,
)

@Serializable
private data class NpcSpawnConfigRawOverride(
    val autoSpawn: Boolean? = null,
    val maxTotal: Int? = null,
    val maxPerChunk: Int? = null,
    val spawnBiomes: List<String>? = null,
)

@Serializable
private data class NpcYamlOverride(
    val behavior: String? = null,
    val width: Float? = null,
    val height: Float? = null,
    val wanderSpeed: Float? = null,
    val wanderRadius: Float? = null,
    val spawn: NpcSpawnConfigRawOverride? = null,
    val hp: Int? = null,
    val aggroMode: AggroMode? = null,
    val aggroRange: Float? = null,
    val deaggroTimeSec: Float? = null,
    val attackId: String? = null,
)

private fun NpcSpawnConfigRaw.applyOverride(o: NpcSpawnConfigRawOverride) =
    copy(
        autoSpawn = o.autoSpawn ?: autoSpawn,
        maxTotal = o.maxTotal ?: maxTotal,
        maxPerChunk = o.maxPerChunk ?: maxPerChunk,
        spawnBiomes = o.spawnBiomes ?: spawnBiomes,
    )

private fun NpcYamlEntry.applyOverride(o: NpcYamlOverride) =
    copy(
        behavior = o.behavior ?: behavior,
        width = o.width ?: width,
        height = o.height ?: height,
        wanderSpeed = o.wanderSpeed ?: wanderSpeed,
        wanderRadius = o.wanderRadius ?: wanderRadius,
        spawn = o.spawn?.let { spawn.applyOverride(it) } ?: spawn,
        hp = o.hp ?: hp,
        aggroMode = o.aggroMode ?: aggroMode,
        aggroRange = o.aggroRange ?: aggroRange,
        deaggroTimeSec = o.deaggroTimeSec ?: deaggroTimeSec,
        attackId = o.attackId ?: attackId,
    )

class NpcRegistryLoader(
    private val resourcesEntityPath: Path,
    private val dataEntityPath: Path,
) {
    fun load(): Map<String, NpcDefinition> {
        val entries = mutableMapOf<String, NpcYamlEntry>()
        resourcesEntityPath
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .forEach { entityDir ->
                val name = entityDir.fileName.toString()
                val resourceYaml = entityDir.resolve("$name.yaml")
                if (!resourceYaml.exists()) return@forEach
                runCatching {
                        Yaml.default.decodeFromString(
                            NpcYamlEntry.serializer(), resourceYaml.readText())
                    }
                    .onFailure { log.warn("Failed to load NPC {}: {}", name, it.message) }
                    .getOrNull()
                    ?.let { entry ->
                        val dataYaml = dataEntityPath.resolve("$name/$name.yaml")
                        val merged =
                            if (dataYaml.exists()) {
                                val content = dataYaml.readText()
                                val overridden =
                                    if (content.isNotBlank()) {
                                        runCatching {
                                                val override =
                                                    Yaml.default.decodeFromString(
                                                        NpcYamlOverride.serializer(), content)
                                                entry.applyOverride(override)
                                            }
                                            .onFailure {
                                                log.warn(
                                                    "Failed to apply override for {}: {}",
                                                    name,
                                                    it.message)
                                            }
                                            .getOrDefault(entry)
                                    } else entry
                                dataYaml.writeText(
                                    Yaml.default.encodeToString(
                                        NpcYamlEntry.serializer(), overridden))
                                log.debug("Wrote back merged data override for {}", name)
                                overridden
                            } else entry
                        entries[name] = merged
                    }
            }
        val result =
            entries.entries
                .mapNotNull { (key, entry) ->
                    runCatching {
                            val behavior = NpcBehaviorRegistry.get(entry.behavior)
                            key to
                                NpcDefinition(
                                    type = key,
                                    behavior = behavior,
                                    behaviorKey = entry.behavior,
                                    bbmodelFile = key,
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
                                    hp = entry.hp,
                                    aggroMode = entry.aggroMode,
                                    aggroRange = entry.aggroRange,
                                    deaggroTimeSec = entry.deaggroTimeSec,
                                    attackId = entry.attackId,
                                )
                        }
                        .onFailure { e -> log.warn("Skipping entity '{}': {}", key, e.message) }
                        .getOrNull()
                }
                .toMap()
        log.info("NPC registry loaded: {} NPC types", result.size)
        log.info(
            "NPC entries:\n{}",
            entries.entries.joinToString("\n") { (key, entry) ->
                Yaml.default.encodeToString(NpcYamlEntry.serializer(), entry)
                    .lines()
                    .joinToString("\n") { "  $it" }
                    .let { "$key:\n$it" }
            })
        return result
    }

    fun reload(): Map<String, NpcDefinition> = load()
}
