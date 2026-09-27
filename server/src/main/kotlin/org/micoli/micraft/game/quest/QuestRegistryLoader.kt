package org.micoli.micraft.game.quest

import com.charleskorn.kaml.Yaml
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.roster.QuestCoverage
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.slf4j.LoggerFactory

fun findRecursive(path: Path, mask: String): List<Path> {
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$mask")
    return Files.walk(path).filter { matcher.matches(it.fileName) }.toList()
}

private val log = LoggerFactory.getLogger(QuestRegistryLoader::class.java)

class QuestRegistryLoader(
    private val questsPath: Path = ConfigPaths.resourcesDir("quests"),
    private val npcTypes: (() -> Map<String, NpcDefinition>)? = null,
    private val biomes: (() -> Collection<BiomeDefinition>)? = null,
) {
    @Volatile private var cached: Map<String, QuestDefinition>? = null

    fun load(): Map<String, QuestDefinition> =
        cached ?: synchronized(this) { cached ?: computeLoad().also { cached = it } }

    /** Bypasses the cache and re-reads disk — used by `/reload`. */
    fun reload(): Map<String, QuestDefinition> = computeLoad().also { cached = it }

    private fun computeLoad(): Map<String, QuestDefinition> {
        if (!questsPath.exists()) return emptyMap()
        val result =
            findRecursive(questsPath, "*.yaml")
                .filter { it.isRegularFile() }
                .mapNotNull { file ->
                    val name = file.fileName.toString()
                    runCatching {
                            Yaml.default.decodeFromString(
                                QuestYamlEntry.serializer(), file.readText())
                        }
                        .onFailure { log.warn("Failed to load quest '{}': {}", name, it.message) }
                        .getOrNull()
                        ?.let { entry -> name to entry.toDefinition(name) }
                }
                .toMap()
        validateTargets(result.values)
        log.info("Quest registry loaded: {} quests", result.size)
        return result
    }

    private fun validateTargets(quests: Collection<QuestDefinition>) {
        val types = npcTypes?.invoke() ?: return
        val report = QuestTargetValidator.validate(quests, types)
        report.warnings.forEach { log.warn(it) }
        check(report.errors.isEmpty()) { report.errors.joinToString("\n") }
        val gaps = biomes?.let { QuestCoverage.gaps(it(), types, quests) }.orEmpty()
        if (gaps.isEmpty()) return
        log.warn(
            "Quest coverage: no Quest giver in {} Biome x Danger tier pair(s): {}",
            gaps.size,
            gaps.joinToString("; ") { "${it.biome} T${it.tier} (${it.reason})" })
    }
}
