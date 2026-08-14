package org.micoli.micraft.game.drop

import kotlin.random.Random
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColorRegistry
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

    /**
     * [colorIndex] > 0 redirects each drop to its plain-color variant item (e.g. `LEGO_BRICK` →
     * `LEGO_BRICK_BLUE`) so breaking a colored block returns the same color. Falls back to the base
     * item when no such variant exists.
     */
    fun rollDrops(blockType: BlockType, colorIndex: Int = 0): List<Pair<ItemType, Int>> {
        val entries = table[blockType] ?: return emptyList()
        val colorSuffix =
            if (colorIndex > 0 && BlockRegistry.get(blockType).plainColorable)
                PlainColorRegistry.byIndex(colorIndex)?.name?.uppercase()
            else null
        return rollDropEntries(entries).map { (item, count) ->
            colorVariant(item, colorSuffix) to count
        }
    }

    private fun colorVariant(item: ItemType, colorSuffix: String?): ItemType {
        if (colorSuffix == null) return item
        val variant = ItemType("${item.id}_$colorSuffix")
        return if (ItemRegistry.keys().contains(variant)) variant else item
    }
}

/** Percentage-chance roll shared by block drops and NPC loot tables. */
fun rollDropEntries(entries: List<DropEntry>): List<Pair<ItemType, Int>> =
    entries.mapNotNull { entry ->
        if (Random.nextInt(100) < entry.dropRate) {
            val count =
                if (entry.minCount == entry.maxCount) entry.minCount
                else entry.minCount + Random.nextInt(entry.maxCount - entry.minCount + 1)
            entry.item to count
        } else null
    }
