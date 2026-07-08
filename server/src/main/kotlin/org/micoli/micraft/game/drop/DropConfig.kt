package org.micoli.micraft.game.drop

import kotlin.random.Random
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.block.DropEntry
import org.slf4j.LoggerFactory

private val dropConfigLog = LoggerFactory.getLogger("DropConfig")

class DropConfig(private val blockRegistryLoader: BlockRegistryLoader) {
    @Volatile
    private var table: Map<BlockType, List<DropEntry>> = blockRegistryLoader.loadDropTable()

    init {
        dropConfigLog.info("Drop table loaded: {} block types configured", table.size)
    }

    // Re-parses the same resources/blocks + data/resources/blocks yaml files BlockRegistryLoader
    // already reads for block properties — an accepted double-parse cost on the infrequent
    // admin-only /reload path (not a hot path).
    fun reload(): Int {
        table = blockRegistryLoader.loadDropTable()
        dropConfigLog.info("Drop table reloaded: {} block types configured", table.size)
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
