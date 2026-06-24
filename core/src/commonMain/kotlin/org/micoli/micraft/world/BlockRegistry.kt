package org.micoli.micraft.world

object BlockRegistry {
    private val defaults: Map<BlockType, BlockDefinition> =
        mapOf(
            BlockType.AIR to
                BlockDefinition(
                    hardness = 0,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(10, 10, 30)),
            BlockType.BEDROCK to
                BlockDefinition(hardness = -1, solid = true, minimapColor = listOf(58, 58, 58)),
            BlockType.STONE to
                BlockDefinition(hardness = 5, solid = true, minimapColor = listOf(136, 136, 136)),
            BlockType.DIRT to
                BlockDefinition(hardness = 3, solid = true, minimapColor = listOf(122, 92, 46)),
            BlockType.GRASS to
                BlockDefinition(hardness = 3, solid = true, minimapColor = listOf(74, 122, 40)),
            BlockType.SAND to
                BlockDefinition(hardness = 2, solid = true, minimapColor = listOf(212, 200, 122)),
            BlockType.SANDSTONE to
                BlockDefinition(hardness = 4, solid = true, minimapColor = listOf(200, 160, 87)),
            BlockType.GRAVEL to
                BlockDefinition(hardness = 3, solid = true, minimapColor = listOf(128, 128, 128)),
            BlockType.SNOW to
                BlockDefinition(hardness = 1, solid = true, minimapColor = listOf(240, 240, 240)),
            BlockType.OAK_LOG to
                BlockDefinition(hardness = 3, solid = true, minimapColor = listOf(101, 67, 33)),
            BlockType.OAK_LEAVES to
                BlockDefinition(
                    hardness = 1,
                    solid = true,
                    transparent = true,
                    minimapColor = listOf(60, 100, 30)),
            BlockType.PINE_LOG to
                BlockDefinition(hardness = 3, solid = true, minimapColor = listOf(80, 50, 25)),
            BlockType.PINE_LEAVES to
                BlockDefinition(
                    hardness = 1,
                    solid = true,
                    transparent = true,
                    minimapColor = listOf(40, 90, 60)),
            BlockType.PINE_LEAVES_SNOW to
                BlockDefinition(
                    hardness = 1,
                    solid = true,
                    transparent = true,
                    minimapColor = listOf(200, 215, 220)),
            BlockType.FLOWER to
                BlockDefinition(
                    hardness = 1,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(230, 200, 50)),
            BlockType.WEED to
                BlockDefinition(
                    hardness = 1,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(70, 130, 40)),
        )

    private val defs: MutableMap<BlockType, BlockDefinition> = defaults.toMutableMap()

    fun load(incoming: Map<BlockType, BlockDefinition>) {
        defs.clear()
        defs.putAll(defaults)
        defs.putAll(incoming)
    }

    fun get(type: BlockType): BlockDefinition = defs[type] ?: BlockDefinition()

    fun orderedList(): List<BlockDefinition> = BlockType.entries.map { get(it) }
}
