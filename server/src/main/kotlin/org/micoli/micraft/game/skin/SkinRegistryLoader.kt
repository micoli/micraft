package org.micoli.micraft.game.skin

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SkinRegistryLoader")

private fun SkinDefinition.applyOverride(o: SkinYamlOverride) =
    copy(
        eyes = o.eyes ?: eyes,
        firstPersonHiddenBones = o.firstPersonHiddenBones ?: firstPersonHiddenBones,
    )

/**
 * Loads `resources/skins/<name>/<name>.yaml`, optionally overridden by
 * `data/resources/skins/<name>/<name>.yaml`. Skins without a yaml are simply absent from the
 * registry — the client then falls back to the stance eye offset.
 */
class SkinRegistryLoader(
    private val skinsPath: Path,
    private val dataSkinsPath: Path,
) {
    fun load(): Map<String, SkinDefinition> {
        if (!skinsPath.exists()) return emptyMap()
        val result =
            skinsPath
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val name = dir.fileName.toString()
                    load(name)?.let { name to it }
                }
                .toMap()
        log.info("Skin registry loaded: {} skins", result.size)
        return result
    }

    fun load(name: String): SkinDefinition? {
        val yaml = skinsPath.resolve("$name/$name.yaml")
        if (!yaml.exists()) return null
        val base =
            runCatching {
                    Yaml.default.decodeFromString(SkinDefinition.serializer(), yaml.readText())
                }
                .onFailure { log.warn("Failed to load skin '{}': {}", name, it.message) }
                .getOrNull() ?: return null

        val dataYaml = dataSkinsPath.resolve("$name/$name.yaml")
        if (!dataYaml.exists()) return base
        val content = dataYaml.readText()
        if (content.isBlank()) return base
        return runCatching {
                base.applyOverride(
                    Yaml.default.decodeFromString(SkinYamlOverride.serializer(), content))
            }
            .onFailure { log.warn("Failed to apply skin override for '{}': {}", name, it.message) }
            .getOrDefault(base)
    }
}
