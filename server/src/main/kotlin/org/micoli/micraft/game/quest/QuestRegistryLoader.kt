package org.micoli.micraft.game.quest

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("QuestRegistryLoader")

class QuestRegistryLoader(private val questsPath: Path) {
    fun load(): Map<String, QuestDefinition> {
        if (!questsPath.exists()) return emptyMap()
        val result =
            questsPath
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val name = dir.fileName.toString()
                    val yaml = dir.resolve("$name.yaml")
                    if (!yaml.exists()) return@mapNotNull null
                    runCatching {
                            Yaml.default.decodeFromString(
                                QuestYamlEntry.serializer(), yaml.readText())
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
