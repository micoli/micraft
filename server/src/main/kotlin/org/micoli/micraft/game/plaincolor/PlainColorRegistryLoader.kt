package org.micoli.micraft.game.plaincolor

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.serializer
import org.micoli.micraft.config.YamlField
import org.micoli.micraft.config.YamlSection
import org.micoli.micraft.config.isYamlEffectivelyEmpty
import org.micoli.micraft.config.presentInYaml
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.PlainColor
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("PlainColorRegistryLoader")

private const val ROOT_KEY = "plainColors"

/**
 * Loads the plain-color palette from `resources/config/plain_colors.yaml`, merged with the optional
 * `data/config/plain_colors.yaml` override (same write-back-as-comments convention as
 * [org.micoli.micraft.game.item.ItemRegistryLoader]).
 *
 * Declaration order is the palette index order, so resources entries always come first and
 * data-only entries are appended — adding a color at the end never recolors placed blocks.
 */
class PlainColorRegistryLoader(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/plain_colors.yaml"),
) {
    private val default: Map<String, String> = decode(resourcesPath.readText())

    init {
        path.parent.createDirectories()
        if (!path.exists() || path.readText().isBlank()) {
            // Seed the root key uncommented so uncommenting a single color is enough to override it
            path.writeText("$ROOT_KEY:\n")
            log.info("Generated default plain color palette at {}", path.toAbsolutePath())
        }
        val originalText = path.readText()
        runCatching { Yaml.default.parseToYamlNode(originalText) }
            .onSuccess { node ->
                path.writeText(
                    spliceMissingAsComments(
                        originalText, section(mergedEntries(originalText, node), node)))
            }
            .onFailure {
                if (!originalText.isYamlEffectivelyEmpty())
                    log.warn(
                        "plain_colors.yaml has unparseable structure, leaving file untouched: {}",
                        it.message)
            }
        validateYamlConfig(path, "plain_colors.schema.json")
    }

    fun load(): List<PlainColor> {
        val originalText = if (path.exists()) path.readText() else ""
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        val merged = mergedEntries(originalText, node)
        val parsed = merged.mapNotNull { (name, hex) -> parse(name, hex) }
        if (parsed.size > BlockState.MAX_COLOR_INDEX) {
            log.error(
                "Plain color palette has {} entries, only the first {} are usable (color index is 6 bits) — dropping {}",
                parsed.size,
                BlockState.MAX_COLOR_INDEX,
                parsed.drop(BlockState.MAX_COLOR_INDEX).joinToString(", ") { it.name })
        }
        val result = parsed.take(BlockState.MAX_COLOR_INDEX)
        log.info("Plain color palette loaded: {} colors", result.size)
        return result
    }

    fun reload(): List<PlainColor> = load()

    /** Resources order first (override values applied), then data-only colors appended. */
    private fun mergedEntries(originalText: String, node: YamlNode?): Map<String, String> {
        val decoded =
            if (originalText.isBlank()) emptyMap()
            else runCatching { decode(originalText) }.getOrElse { emptyMap() }
        val merged = LinkedHashMap<String, String>()
        for ((name, hex) in default) merged[name] = decoded[name] ?: hex
        for ((name, hex) in decoded) merged.putIfAbsent(name, hex)
        return merged
    }

    private fun section(entries: Map<String, String>, node: YamlNode?) =
        YamlSection(
            key = "",
            subsections =
                listOf(
                    YamlSection(
                        key = ROOT_KEY,
                        present = presentInYaml(node, ROOT_KEY),
                        fields =
                            entries.map { (name, hex) ->
                                YamlField(
                                    name,
                                    hex,
                                    String.serializer(),
                                    presentInYaml(node, ROOT_KEY, name),
                                )
                            },
                    )),
        )

    private fun decode(text: String): Map<String, String> =
        Yaml.default.decodeFromString(PlainColorsYaml.serializer(), text).plainColors

    private fun parse(name: String, hex: String): PlainColor? =
        PlainColor.fromHex(name, hex).also {
            if (it == null) log.warn("Plain color '{}' has invalid hex '{}', skipped", name, hex)
        }
}
