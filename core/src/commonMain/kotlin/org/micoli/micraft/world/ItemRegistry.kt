package org.micoli.micraft.world

object ItemRegistry {
    private val defaults: Map<ItemType, ItemDefinition> =
        mapOf(
            ItemType.COBBLESTONE to ItemDefinition(buildable = true, placesBlock = BlockType.STONE),
            ItemType.DIRT to ItemDefinition(buildable = true, placesBlock = BlockType.DIRT),
            ItemType.SAND to ItemDefinition(buildable = true, placesBlock = BlockType.SAND),
            ItemType.GRAVEL to ItemDefinition(buildable = true, placesBlock = BlockType.GRAVEL),
            ItemType.SANDSTONE to
                ItemDefinition(buildable = true, placesBlock = BlockType.SANDSTONE),
            ItemType.SNOWBALL to ItemDefinition(buildable = false),
            ItemType.FLINT to ItemDefinition(buildable = false),
        )

    private val defs: MutableMap<ItemType, ItemDefinition> = defaults.toMutableMap()

    fun load(incoming: Map<ItemType, ItemDefinition>) {
        defs.clear()
        defs.putAll(defaults)
        defs.putAll(incoming)
    }

    fun get(type: ItemType): ItemDefinition = defs[type] ?: ItemDefinition()
}
