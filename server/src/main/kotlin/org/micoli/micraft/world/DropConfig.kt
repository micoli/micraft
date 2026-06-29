package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlinx.serialization.Serializable
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

private val DEFAULT_DROPS: Map<String, List<DropEntry>> =
    mapOf(
        "STONE" to listOf(DropEntry(ItemType("COBBLESTONE"))),
        "DIRT" to listOf(DropEntry(ItemType("DIRT"))),
        "GRASS" to listOf(DropEntry(ItemType("DIRT"))),
        "SAND" to listOf(DropEntry(ItemType("SAND"))),
        "SANDSTONE" to listOf(DropEntry(ItemType("SANDSTONE"))),
        "GRAVEL" to
            listOf(
                DropEntry(ItemType("GRAVEL"), dropRate = 90),
                DropEntry(ItemType("FLINT"), dropRate = 10),
            ),
        "SNOW" to listOf(DropEntry(ItemType("SNOWBALL"), minCount = 1, maxCount = 4)),
    )

private val DROP_TABLE_SERIALIZER =
    MapSerializer(
        String.serializer(), kotlinx.serialization.builtins.ListSerializer(DropEntry.serializer()))

class DropConfig(private val path: Path) {
    @Volatile private var table: Map<BlockType, List<DropEntry>>

    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            val yaml = Yaml.default.encodeToString(DROP_TABLE_SERIALIZER, DEFAULT_DROPS)
            path.writeText(yaml)
            log.info("Generated default drop config at {}", path.toAbsolutePath())
        }
        table = parseTable()
        log.info("Drop table loaded: {} block types configured", table.size)
    }

    private fun parseTable(): Map<BlockType, List<DropEntry>> {
        val raw =
            runCatching { Yaml.default.decodeFromString(DROP_TABLE_SERIALIZER, path.readText()) }
                .getOrElse { e ->
                    log.warn("Failed to load drops.yaml ({}), using defaults", e.message)
                    DEFAULT_DROPS
                }
        return raw.entries
            .mapNotNull { (key, entries) -> runCatching { BlockType(key) to entries }.getOrNull() }
            .toMap()
    }

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
