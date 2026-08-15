package org.micoli.micraft.game.world.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType

class ScenePlacementTest {

    private fun registerLegoPiece() {
        BlockRegistry.load(
            mapOf(
                BlockType.LEGO_PIECE to
                    BlockDefinition(
                        hardness = 1f,
                        solid = true,
                        isCubic = false,
                        replaceable = false,
                        // Same fixture as BlockPlacerTest: 1/4-voxel XZ footprint, 3-high Y stack.
                        brickSize = listOf(0.5f, 0.666f, 0.5f),
                    )))
    }

    private fun createScene(
        registry: SceneRegistry,
        width: Int = 4,
        height: Int = 4,
        depth: Int = 4
    ): String = registry.create("test", width, height, depth, "admin").id

    @Test
    fun placeBlock_fractionalXZAndY_addsEntityAtDistinctSlots() {
        registerLegoPiece()
        val registry = SceneRegistry(null)
        val id = createScene(registry)

        val first =
            registry.placeBlock(
                id,
                1,
                1,
                1,
                BlockType.LEGO_PIECE,
                rotation = 0,
                colorIndex = 0,
                xOffset = 0,
                zOffset = 0)
        assertNotNull(first)
        assertNull(first.rejectedReason)
        assertEquals(1, first.entityAdds.size)

        val second =
            registry.placeBlock(
                id,
                1,
                1,
                1,
                BlockType.LEGO_PIECE,
                rotation = 0,
                colorIndex = 0,
                xOffset = 1,
                zOffset = 0)
        assertNotNull(second)
        assertNull(second.rejectedReason)
        assertEquals(1, second.entityAdds.size)

        val scene = registry.get(id)!!
        assertEquals(2, scene.entities.size)
    }

    @Test
    fun placeBlock_sameSlotStacksUntilFull() {
        registerLegoPiece()
        val registry = SceneRegistry(null)
        val id = createScene(registry)

        // Same XZ slot (0,0) stacks up to 3 high (maxYSlots for brickSize[1]=0.666).
        repeat(3) { i ->
            val result =
                registry.placeBlock(
                    id,
                    1,
                    1,
                    1,
                    BlockType.LEGO_PIECE,
                    rotation = 0,
                    colorIndex = 0,
                    xOffset = 0,
                    zOffset = 0)
            assertNotNull(result, "placement $i should succeed")
            assertNull(result.rejectedReason, "placement $i rejected: ${result.rejectedReason}")
        }

        val overflow =
            registry.placeBlock(
                id,
                1,
                1,
                1,
                BlockType.LEGO_PIECE,
                rotation = 0,
                colorIndex = 0,
                xOffset = 0,
                zOffset = 0)
        assertNotNull(overflow)
        assertNotNull(overflow.rejectedReason)

        assertEquals(3, registry.get(id)!!.entities.size)
    }

    @Test
    fun breakBlock_removesExactSlot() {
        registerLegoPiece()
        val registry = SceneRegistry(null)
        val id = createScene(registry)

        registry.placeBlock(
            id,
            1,
            1,
            1,
            BlockType.LEGO_PIECE,
            rotation = 0,
            colorIndex = 0,
            xOffset = 0,
            zOffset = 0)
        registry.placeBlock(
            id,
            1,
            1,
            1,
            BlockType.LEGO_PIECE,
            rotation = 0,
            colorIndex = 0,
            xOffset = 1,
            zOffset = 0)
        assertEquals(2, registry.get(id)!!.entities.size)

        val removed = registry.breakBlock(id, 1, 1, 1, xOffset = 1, zOffset = 0)
        assertNotNull(removed)
        assertEquals(1, removed.entityRemovesAt.size)
        assertEquals(1, registry.get(id)!!.entities.size)
        assertEquals(0, registry.get(id)!!.entities.first().xOffset)
    }

    @Test
    fun placeBlock_sceneNotFound_returnsNull() {
        registerLegoPiece()
        val registry = SceneRegistry(null)
        val result =
            registry.placeBlock(
                "missing",
                0,
                0,
                0,
                BlockType.LEGO_PIECE,
                rotation = 0,
                colorIndex = 0,
                xOffset = 0,
                zOffset = 0)
        assertNull(result)
    }

    @Test
    fun persistence_roundTrip_survivesEntities() {
        registerLegoPiece()
        val tmpDir = kotlin.io.path.createTempDirectory("scene-persist-test")
        try {
            val persistence = org.micoli.micraft.game.world.WorldPersistence(tmpDir)
            val registry = SceneRegistry(persistence)
            val id = createScene(registry)
            registry.placeBlock(
                id,
                1,
                1,
                1,
                BlockType.LEGO_PIECE,
                rotation = 0,
                colorIndex = 0,
                xOffset = 0,
                zOffset = 0)
            assertEquals(1, registry.get(id)!!.entities.size)

            val reloaded = SceneRegistry(persistence)
            val scene = reloaded.get(id)
            assertNotNull(scene)
            assertEquals(1, scene.entities.size)
            assertTrue(scene.entities.first().type == BlockType.LEGO_PIECE)
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }
}
