package org.micoli.micraft.game.npc

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.yamlOverrideSection
import org.slf4j.LoggerFactory

private val npcLog = LoggerFactory.getLogger("NpcRegistryLoader")

private fun NpcSpawnConfigRaw.applyOverride(o: NpcSpawnConfigRawOverride) =
    copy(
        autoSpawn = o.autoSpawn ?: autoSpawn,
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
        attacks = o.attacks ?: attacks,
        spells = o.spells ?: spells,
        minLevel = o.minLevel ?: minLevel,
        maxLevel = o.maxLevel ?: maxLevel,
        characterClass = o.characterClass ?: characterClass,
        baseStats = o.baseStats ?: baseStats,
        xpReward = o.xpReward ?: xpReward,
        bbmodelFile = o.bbmodelFile ?: bbmodelFile,
        animal = o.animal ?: animal,
        pack = o.pack ?: pack,
    )

/**
 * Apply a data override on an already-built definition. Used by the admin world simulator, which
 * receives the live registry and layers instance-scoped rules on top without touching any file.
 */
fun NpcDefinition.applyOverride(o: NpcYamlOverride): NpcDefinition =
    copy(
        behavior = o.behavior?.let { NpcBehaviorRegistry.get(it) } ?: behavior,
        behaviorKey = o.behavior ?: behaviorKey,
        bbmodelFile = o.bbmodelFile ?: bbmodelFile,
        width = o.width ?: width,
        height = o.height ?: height,
        wanderSpeed = o.wanderSpeed ?: wanderSpeed,
        wanderRadius = o.wanderRadius ?: wanderRadius,
        spawn =
            o.spawn?.let {
                spawn.copy(
                    autoSpawn = it.autoSpawn ?: spawn.autoSpawn,
                    maxPerChunk = it.maxPerChunk ?: spawn.maxPerChunk,
                    spawnBiomes = it.spawnBiomes ?: spawn.spawnBiomes,
                )
            } ?: spawn,
        hp = o.hp ?: hp,
        aggroMode = o.aggroMode ?: aggroMode,
        aggroRange = o.aggroRange ?: aggroRange,
        deaggroTimeSec = o.deaggroTimeSec ?: deaggroTimeSec,
        attacks = o.attacks ?: attacks,
        spells = o.spells ?: spells,
        minLevel = o.minLevel ?: minLevel,
        maxLevel = o.maxLevel ?: maxLevel,
        characterClass = o.characterClass ?: characterClass,
        baseStats = o.baseStats ?: baseStats,
        xpReward = o.xpReward ?: xpReward,
        animalConfig = o.animal ?: animalConfig,
        packConfig = o.pack ?: packConfig,
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
                    .onFailure { npcLog.warn("Failed to load NPC {}: {}", name, it.message) }
                    .getOrNull()
                    ?.let { entry ->
                        val dataYaml = dataEntityPath.resolve("$name/$name.yaml")
                        val merged =
                            if (dataYaml.exists()) {
                                val content = dataYaml.readText()
                                val overrideResult =
                                    if (content.isNotBlank()) {
                                        runCatching {
                                            Yaml.default.decodeFromString(
                                                NpcYamlOverride.serializer(), content)
                                        }
                                    } else Result.success(NpcYamlOverride())
                                val override = overrideResult.getOrNull()
                                val overridden = override?.let { entry.applyOverride(it) } ?: entry
                                overrideResult.fold(
                                    onSuccess = {
                                        dataYaml.writeText(
                                            spliceMissingAsComments(
                                                content, yamlOverrideSection(overridden, it)))
                                        npcLog.debug("Wrote back merged data override for {}", name)
                                    },
                                    onFailure = {
                                        npcLog.warn(
                                            "Failed to apply override for {}, leaving file untouched: {}",
                                            name,
                                            it.message)
                                    })
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
                                    bbmodelFile = entry.bbmodelFile ?: key,
                                    width = entry.width,
                                    height = entry.height,
                                    wanderSpeed = entry.wanderSpeed,
                                    wanderRadius = entry.wanderRadius,
                                    spawn =
                                        NpcSpawnConfig(
                                            autoSpawn = entry.spawn.autoSpawn,
                                            maxPerChunk = entry.spawn.maxPerChunk,
                                            spawnBiomes = entry.spawn.spawnBiomes,
                                        ),
                                    hp = entry.hp,
                                    aggroMode = entry.aggroMode,
                                    aggroRange = entry.aggroRange,
                                    deaggroTimeSec = entry.deaggroTimeSec,
                                    attacks = entry.attacks,
                                    spells = entry.spells,
                                    minLevel = entry.minLevel,
                                    maxLevel = entry.maxLevel,
                                    characterClass = entry.characterClass,
                                    baseStats = entry.baseStats,
                                    xpReward = entry.xpReward,
                                    walkBoneAliases = entry.walkBoneAliases,
                                    animalConfig = entry.animal,
                                    packConfig = entry.pack,
                                )
                        }
                        .onFailure { e -> npcLog.warn("Skipping entity '{}': {}", key, e.message) }
                        .getOrNull()
                }
                .toMap()
        npcLog.info("NPC registry loaded: {} NPC types", result.size)
        npcLog.debug(
            "NPC entries:\n{}",
            entries.entries.joinToString("\n") { (key, entry) ->
                Yaml.default
                    .encodeToString(NpcYamlEntry.serializer(), entry)
                    .lines()
                    .joinToString("\n") { "  $it" }
                    .let { "$key:\n$it" }
            })
        return result
    }

    fun reload(): Map<String, NpcDefinition> = load()
}
