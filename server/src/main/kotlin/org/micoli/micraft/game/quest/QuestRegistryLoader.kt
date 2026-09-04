package org.micoli.micraft.game.quest

import com.charleskorn.kaml.Yaml
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.slf4j.LoggerFactory

fun findRecursive(path: Path, mask: String): List<Path> {
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$mask")
    return Files.walk(path).filter { matcher.matches(it.fileName) }.toList()
}

private val log = LoggerFactory.getLogger(QuestRegistryLoader::class.java)

class QuestRegistryLoader(private val questsPath: Path) {
    fun load(): Map<String, QuestDefinition> {
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
        log.info("Quest registry loaded: {} quests", result.size)
        return result
    }
}
