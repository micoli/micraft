package org.micoli.micraft.game.item

import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColor
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("PlainColorItemExpander")

/**
 * Derives one item per (block declaring `plainColorable`) × (palette color), e.g.
 * `LEGO_BRICK_BLUE`.
 *
 * Pure function, called on the map already produced by [ItemRegistryLoader.load] — the generated
 * variants therefore never reach `data/config/items.yaml`. An item explicitly declared in yaml with
 * the same id always wins, so a label or bg can still be overridden by hand.
 */
fun expandPlainColorItems(
    base: Map<ItemType, ItemDefinition>,
    blocks: Map<BlockType, BlockDefinition>,
    palette: List<PlainColor>,
): Map<ItemType, ItemDefinition> {
    val colorable = blocks.filterValues { it.plainColorable }.keys
    if (colorable.isEmpty() || palette.isEmpty()) return base

    // Label of the plain (textured) item placing this block, when there is one.
    val labelByBlock =
        base.values
            .filter { it.placesBlock != null && it.plainColor == null }
            .associate { it.placesBlock!! to it.label }

    val generated = LinkedHashMap<ItemType, ItemDefinition>()
    for (block in colorable.sortedBy { it.id }) {
        val label = labelByBlock[block]?.takeIf { it.isNotBlank() } ?: shortLabel(block.id)
        for (color in palette) {
            val itemType = ItemType("${block.id}_${color.name.uppercase()}")
            if (base.containsKey(itemType)) continue
            generated[itemType] =
                ItemDefinition(
                    buildable = true,
                    placesBlock = block,
                    label = label,
                    bg = "#${color.hex()}",
                    plainColor = color.name,
                )
        }
    }
    log.info(
        "Plain color items generated: {} ({} colorable blocks × {} colors)",
        generated.size,
        colorable.size,
        palette.size)
    return base + generated
}

/** Fallback 3-letter label for a block with no textured item of its own. */
private fun shortLabel(blockId: String): String =
    blockId.filter { it.isLetterOrDigit() }.take(3).uppercase()
