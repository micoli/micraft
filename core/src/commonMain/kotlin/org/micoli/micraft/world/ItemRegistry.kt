package org.micoli.micraft.world

object ItemRegistry {
    private val defaults: Map<ItemType, ItemDefinition> =
        mapOf(
            ItemType.COBBLESTONE to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.STONE, label = "COB", bg = "#7A7A7A"),
            ItemType.DIRT to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.DIRT, label = "DRT", bg = "#8B5A2B"),
            ItemType.SAND to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.SAND, label = "SND", bg = "#D5C89A"),
            ItemType.GRAVEL to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.GRAVEL,
                    label = "GRV",
                    bg = "#9A9A9A"),
            ItemType.SANDSTONE to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.SANDSTONE,
                    label = "SST",
                    bg = "#C8B46C"),
            ItemType.SNOWBALL to ItemDefinition(buildable = false, label = "SNW", bg = "#DCE8F5"),
            ItemType.FLINT to ItemDefinition(buildable = false, label = "FLT", bg = "#4A4A52"),
            ItemType.SEED to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.SEED, label = "SED", bg = "#C8A050"),
            ItemType.GRASS to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.GRASS, label = "GRS", bg = "#4A7A28"),
            ItemType.SNOW_BLOCK to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.SNOW, label = "SNB", bg = "#F0F0F0"),
            ItemType.OAK_LOG to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.OAK_LOG,
                    label = "OLG",
                    bg = "#654321"),
            ItemType.OAK_LEAVES to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.OAK_LEAVES,
                    label = "OLV",
                    bg = "#3C641E"),
            ItemType.PINE_LOG to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.PINE_LOG,
                    label = "PLG",
                    bg = "#503219"),
            ItemType.PINE_LEAVES to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.PINE_LEAVES,
                    label = "PLV",
                    bg = "#285A3C"),
            ItemType.PINE_LEAVES_SNOW to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.PINE_LEAVES_SNOW,
                    label = "PLS",
                    bg = "#C8D7DC"),
            ItemType.FLOWER to
                ItemDefinition(
                    buildable = true,
                    placesBlock = BlockType.FLOWER,
                    label = "FLW",
                    bg = "#E6C832"),
            ItemType.WEED to
                ItemDefinition(
                    buildable = true, placesBlock = BlockType.WEED, label = "WED", bg = "#468228"),
        )

    private val defs: MutableMap<ItemType, ItemDefinition> = defaults.toMutableMap()

    fun load(incoming: Map<ItemType, ItemDefinition>) {
        defs.clear()
        defs.putAll(defaults)
        incoming.forEach { (type, def) ->
            val base = defs[type]
            defs[type] =
                if (base != null)
                    base.copy(
                        buildable = def.buildable,
                        placesBlock = def.placesBlock ?: base.placesBlock,
                    )
                else def
        }
    }

    fun get(type: ItemType): ItemDefinition = defs[type] ?: ItemDefinition()
}
