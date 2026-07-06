package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("DropConfig")

@Serializable
data class DropEntry(
    val item: ItemType,
    val dropRate: Int = 100,
    val minCount: Int = 1,
    val maxCount: Int = 1,
)

private val DROP_LIST_SERIALIZER = ListSerializer(DropEntry.serializer())
private val DROP_TABLE_SERIALIZER = MapSerializer(String.serializer(), DROP_LIST_SERIALIZER)

/**
 * Each map value is a `List<DropEntry>`, not a record — [mergeConfig]/[mergeMapConfig] can't
 * reflect into it, so merging/backfilling happens at whole-key granularity only.
 */
private fun mergeDropTable(
    decoded: Map<String, List<DropEntry>>,
    default: Map<String, List<DropEntry>>,
): Map<String, List<DropEntry>> =
    (default.keys + decoded.keys).associateWith { key -> decoded[key] ?: default.getValue(key) }

private fun dropTableSection(entries: Map<String, List<DropEntry>>, node: YamlNode?): YamlSection =
    YamlSection(
        key = "",
        fields =
            entries.map { (key, value) ->
                YamlField(key, value, DROP_LIST_SERIALIZER, presentInYaml(node, key))
            },
    )

class DropConfig(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/drops.yaml"),
) {
    private val default: Map<String, List<DropEntry>> =
        Yaml.default.decodeFromString(DROP_TABLE_SERIALIZER, resourcesPath.readText())

    @Volatile private var table: Map<BlockType, List<DropEntry>>

    init {
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(spliceMissingAsComments("", dropTableSection(default, null)))
            log.info("Generated default drop config at {}", path.toAbsolutePath())
        } else {
            runCatching { Yaml.default.parseToYamlNode(originalText) }
                .onSuccess { node ->
                    path.writeText(
                        spliceMissingAsComments(
                            originalText, dropTableSection(mergedTable(), node)))
                }
                .onFailure {
                    log.warn(
                        "drops.yaml has unparseable structure, leaving file untouched: {}",
                        it.message)
                }
        }
        validateYamlConfig(path, "drops.schema.json")
        table = parseTable()
        log.info("Drop table loaded: {} block types configured", table.size)
    }

    private fun mergedTable(): Map<String, List<DropEntry>> {
        val originalText = if (path.exists()) path.readText() else ""
        val decoded =
            if (originalText.isBlank()) emptyMap()
            else
                runCatching { Yaml.default.decodeFromString(DROP_TABLE_SERIALIZER, originalText) }
                    .getOrElse { emptyMap() }
        return mergeDropTable(decoded, default)
    }

    private fun parseTable(): Map<BlockType, List<DropEntry>> =
        mergedTable()
            .entries
            .mapNotNull { (key, entries) -> runCatching { BlockType(key) to entries }.getOrNull() }
            .toMap()

    fun reload(): Int {
        table = parseTable()
        log.info("Drop table reloaded: {} block types configured", table.size)
        return table.size
    }

    fun rollDrops(blockType: BlockType): List<Pair<ItemType, Int>> {
        val entries = table[blockType] ?: return emptyList()
        return entries.mapNotNull { entry ->
            if (Random.nextInt(100) < entry.dropRate) {
                val count =
                    if (entry.minCount == entry.maxCount) entry.minCount
                    else entry.minCount + Random.nextInt(entry.maxCount - entry.minCount + 1)
                entry.item to count
            } else null
        }
    }
}
