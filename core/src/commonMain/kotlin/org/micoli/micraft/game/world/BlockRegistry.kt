package org.micoli.micraft.game.world

object BlockRegistry {
    private val wireIds = mutableMapOf<BlockType, Int>()
    private val byWireId = mutableListOf<BlockType>()

    fun wireIndex(type: BlockType): Int = wireIds[type] ?: 0

    fun byWireIndex(idx: Int): BlockType = byWireId.getOrElse(idx) { BlockType.AIR }

    fun all(): List<BlockType> = byWireId.toList()

    private val defaults: Map<BlockType, BlockDefinition> =
        mapOf(
            BlockType.AIR to
                BlockDefinition(
                    hardness = 0f,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(10, 10, 30),
                    replaceable = true,
                    minimapVisible = false),
            BlockType.BEDROCK to
                BlockDefinition(hardness = -1f, solid = true, minimapColor = listOf(58, 58, 58)),
            BlockType.STONE to
                BlockDefinition(hardness = 5f, solid = true, minimapColor = listOf(136, 136, 136)),
            BlockType.DIRT to
                BlockDefinition(hardness = 3f, solid = true, minimapColor = listOf(122, 92, 46)),
            BlockType.GRASS to
                BlockDefinition(
                    hardness = 3f,
                    solid = true,
                    minimapColor = listOf(74, 122, 40),
                    vegetationHost = true),
            BlockType.SAND to
                BlockDefinition(
                    hardness = 2f,
                    solid = true,
                    minimapColor = listOf(212, 200, 122),
                    treeAllowed = false),
            BlockType.SANDSTONE to
                BlockDefinition(
                    hardness = 4f,
                    solid = true,
                    minimapColor = listOf(200, 160, 87),
                    treeAllowed = false),
            BlockType.GRAVEL to
                BlockDefinition(hardness = 3f, solid = true, minimapColor = listOf(128, 128, 128)),
            BlockType.SNOW to
                BlockDefinition(hardness = 1f, solid = true, minimapColor = listOf(240, 240, 240)),
            BlockType.OAK_LOG to
                BlockDefinition(hardness = 3f, solid = true, minimapColor = listOf(101, 67, 33)),
            BlockType.OAK_LEAVES to
                BlockDefinition(
                    hardness = 1f,
                    solid = true,
                    transparent = true,
                    minimapColor = listOf(60, 100, 30)),
            BlockType.PINE_LOG to
                BlockDefinition(hardness = 3f, solid = true, minimapColor = listOf(80, 50, 25)),
            BlockType.PINE_LEAVES to
                BlockDefinition(
                    hardness = 1f,
                    solid = true,
                    transparent = true,
                    minimapColor = listOf(40, 90, 60)),
            BlockType.PINE_LEAVES_SNOW to
                BlockDefinition(
                    hardness = 1f,
                    solid = true,
                    transparent = true,
                    minimapColor = listOf(200, 215, 220)),
            BlockType.FLOWER to
                BlockDefinition(
                    hardness = 1f,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(230, 200, 50),
                    replaceable = true),
            BlockType.WEED to
                BlockDefinition(
                    hardness = 1f,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(70, 130, 40),
                    replaceable = true),
            BlockType.WATER to
                BlockDefinition(
                    hardness = -1f,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(50, 120, 200),
                    liquid = true,
                    viscosity = 3,
                    replaceable = true),
            BlockType.SEED to
                BlockDefinition(
                    hardness = 0f,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(180, 140, 60),
                    replaceable = true,
                    minimapVisible = false),
            BlockType.SPROUT to
                BlockDefinition(
                    hardness = 0f,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(100, 160, 50),
                    replaceable = true,
                    minimapVisible = false),
            BlockType.SAPLING to
                BlockDefinition(
                    hardness = 1f,
                    solid = false,
                    transparent = true,
                    minimapColor = listOf(60, 120, 40),
                    replaceable = true,
                    minimapVisible = false),
        )

    private val defs: MutableMap<BlockType, BlockDefinition> = defaults.toMutableMap()

    init {
        rebuildWireIds(defs.keys)
    }

    fun load(incoming: Map<BlockType, BlockDefinition>) {
        defs.clear()
        defs.putAll(defaults)
        defs.putAll(incoming)
        rebuildWireIds(defs.keys)
    }

    private fun rebuildWireIds(keys: Set<BlockType>) {
        wireIds.clear()
        byWireId.clear()
        val ordered = listOf(BlockType.AIR) + (keys - BlockType.AIR)
        ordered.forEachIndexed { idx, type ->
            wireIds[type] = idx
            byWireId.add(type)
        }
    }

    fun get(type: BlockType): BlockDefinition = defs[type] ?: BlockDefinition()

    fun orderedList(): List<BlockDefinition> = byWireId.map { get(it) }
}
