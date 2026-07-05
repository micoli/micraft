package org.micoli.micraft.combat

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AttackRegistryLoader")

class AttackRegistryLoader(private val attacksPath: Path) {
    fun load(): Map<String, AttackDefinition> {
        if (!attacksPath.exists()) return emptyMap()
        val result =
            attacksPath
                .listDirectoryEntries("*.yaml")
                .mapNotNull { file ->
                    val name = file.nameWithoutExtension
                    runCatching {
                            name to
                                Yaml.default.decodeFromString(
                                    AttackDefinition.serializer(), file.readText())
                        }
                        .onFailure { log.warn("Failed to load attack '{}': {}", name, it.message) }
                        .getOrNull()
                }
                .toMap()
        log.info("Attack registry loaded: {} attacks", result.size)
        return result
    }
}
