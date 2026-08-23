package org.micoli.micraft.game.world.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockEntity
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.support.testWorld

class ScenePlacerTest {
    private fun sceneWithBlock(x: Int, y: Int, z: Int, type: BlockType, rotation: Int = 0): Scene {
        val scene =
            SceneRegistry(null)
                .create(name = "Test", width = 2, height = 1, depth = 3, ownerName = "Alice")
        scene.setBlock(
            x, y, z, BlockRegistry.wireIndex(type).toByte(), BlockState.pack(rotation, 0))
        return scene
    }

    @Test
    fun stamp_rotation0_copiesBlockAtOrigin() {
        val scene = sceneWithBlock(1, 0, 2, BlockType.STONE)
        val world = testWorld()
        ScenePlacer.stamp(scene, BlockPos(10, 5, 10), rotationSteps = 0, target = world)
        assertEquals(BlockType.STONE, world.getBlock(11, 5, 12))
    }

    @Test
    fun stamp_rotation1_rotatesCellIntoSwappedBoundingBox() {
        // scene is width=2, depth=3; local (1,0,2) rotated 90 deg CW should land at (dimB-1-2, 1) =
        // (0, 1) within a 3x2 (depth x width) box.
        val scene = sceneWithBlock(1, 0, 2, BlockType.STONE)
        val world = testWorld()
        ScenePlacer.stamp(scene, BlockPos(0, 0, 0), rotationSteps = 1, target = world)
        assertEquals(BlockType.STONE, world.getBlock(0, 0, 1))
        assertEquals(BlockType.AIR, world.getBlock(1, 0, 2))
    }

    @Test
    fun stamp_rotation2_isPointReflection() {
        val scene = sceneWithBlock(0, 0, 0, BlockType.STONE)
        val world = testWorld()
        ScenePlacer.stamp(scene, BlockPos(0, 0, 0), rotationSteps = 2, target = world)
        // width=2, depth=3 -> (width-1-0, depth-1-0) = (1, 2)
        assertEquals(BlockType.STONE, world.getBlock(1, 0, 2))
    }

    @Test
    fun stamp_appliesRotationToBlockStateRotation() {
        val scene = sceneWithBlock(0, 0, 0, BlockType.STONE, rotation = 1)
        val world = testWorld()
        ScenePlacer.stamp(scene, BlockPos(0, 0, 0), rotationSteps = 1, target = world)
        val worldPos = BlockPos(2, 0, 0) // rotate1: (dimB-1-0, 0) = (2, 0)
        assertEquals(
            (1 + 1) % 4,
            BlockState.rotation(world.getBlockState(worldPos.x, worldPos.y, worldPos.z)))
    }

    @Test
    fun stamp_airCells_areSkipped() {
        val scene =
            SceneRegistry(null)
                .create(name = "Empty", width = 1, height = 1, depth = 1, ownerName = "Alice")
        val world = testWorld()
        ScenePlacer.stamp(scene, BlockPos(5, 5, 5), rotationSteps = 0, target = world)
        assertEquals(BlockType.AIR, world.getBlock(5, 5, 5))
    }

    @Test
    fun stamp_entity_rotatesPositionSizeAndRotationField() {
        // BlockRegistry.load() replaces defs.putAll(defaults) + this map — STONE etc. used by the
        // other tests stay registered via `defaults`, only LEGO_ARCH_4X1 is newly added here.
        BlockRegistry.load(
            mapOf(
                BlockType("LEGO_ARCH_4X1") to
                    BlockDefinition(
                        hardness = 4f,
                        solid = true,
                        isCubic = false,
                        replaceable = false,
                        // Real fixture from resources/blocks/LEGO_ARCH_4X1/LEGO_ARCH_4X1.yaml —
                        // half-voxel units, Z=1 (0.5 voxel) makes it XZ-fractional.
                        brickSize = listOf(4f, 2f, 1f),
                    )))
        val scene =
            SceneRegistry(null)
                .create(name = "Test", width = 2, height = 1, depth = 3, ownerName = "Alice")
        // masterIdx for local (1, 0, 0) in a 2x1x3 scene: idx = 1*1*3 + 0*3 + 0 = 3
        // LEGO_ARCH_4X1 (brickSize [4,2,1], half-voxel units) is XZ-fractional in Z — a directional
        // piece, unlike LEGO_BRICK's full 1x1x1 footprint — needed to exercise
        // getLastXZFractionalEntityAt below and to make the rotation actually observable.
        scene.entities.add(
            BlockEntity(
                masterIdx = scene.idx(1, 0, 0),
                type = BlockType("LEGO_ARCH_4X1"),
                sizeX = 2,
                sizeY = 1,
                sizeZ = 1,
                rotation = 0,
                yOffset = 0,
                xOffset = 0,
                zOffset = 0,
                colorIndex = 3,
            ))
        val world = testWorld()
        ScenePlacer.stamp(scene, BlockPos(0, 0, 0), rotationSteps = 1, target = world)
        // rotate1: local (1,0) -> (dimB-1-0, 1) = (2, 1); sizeX/sizeZ swap since steps is odd.
        val master = world.getEntityMasterWorldPos(2, 0, 1)
        assertEquals(BlockPos(2, 0, 1), master)
        val entity = world.getLastXZFractionalEntityAt(2, 0, 1)
        assertEquals(1, entity?.rotation)
        assertEquals(1, entity?.sizeX)
        assertEquals(2, entity?.sizeZ)
    }

    @Test
    fun previewOccupancy_marksEntityFootprintNonAir_withoutMutatingRawBlocks() {
        val scene =
            SceneRegistry(null)
                .create(name = "Test", width = 3, height = 1, depth = 1, ownerName = "Alice")
        // Entity spans a 2-cell-wide footprint (sizeX=2) starting at local (0,0,0); its raw
        // `blocks` grid stays AIR at both cells since it's only ever recorded as an entity.
        scene.entities.add(
            BlockEntity(
                masterIdx = scene.idx(0, 0, 0),
                type = BlockType("LEGO_ARCH_4X1"),
                sizeX = 2,
                sizeY = 1,
                sizeZ = 1,
            ))
        val occupancy = ScenePlacer.previewOccupancy(scene)
        assertTrue(occupancy[scene.idx(0, 0, 0)].toInt() != 0)
        assertTrue(occupancy[scene.idx(1, 0, 0)].toInt() != 0)
        assertEquals(0, occupancy[scene.idx(2, 0, 0)].toInt())
        // Real buffer untouched.
        assertEquals(0, scene.blockAt(0, 0, 0).toInt())
    }
}
