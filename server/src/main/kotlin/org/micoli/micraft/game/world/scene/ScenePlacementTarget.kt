package org.micoli.micraft.game.world.scene

import org.micoli.micraft.game.world.BlockEntity
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.block.BlockStore
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.BlockEntityProto
import org.micoli.micraft.protocol.EntityRemoveAt

/**
 * Adapts a [Scene]'s bounded `blocks`/`states`/`entities` buffers to [BlockStore], so
 * [org.micoli.micraft.game.world.block.BlockPlacer.placeAt] and
 * [org.micoli.micraft.game.world.block.BlockBreaker.removeAt] — otherwise written against the
 * persistent world ([org.micoli.micraft.game.world.WorldState]) — can place/remove fractional
 * (lego/plate/arch) block entities in the admin Scene editor with the exact same resolution logic.
 *
 * Unlike WorldState (chunk-indexed for O(1) lookups over a effectively unbounded world), a Scene's
 * volume is small and bounded, so every query here is a plain linear scan of [Scene.entities] — no
 * separate chunk map to maintain.
 */
class ScenePlacementTarget(private val scene: Scene) : BlockStore {
    override fun getBlock(wx: Int, wy: Int, wz: Int): BlockType {
        if (!scene.contains(wx, wy, wz)) return BlockType.AIR
        return BlockRegistry.byWireIndex(scene.blockAt(wx, wy, wz).toInt() and 0xFF)
    }

    override fun getState(wx: Int, wy: Int, wz: Int): Byte {
        if (!scene.contains(wx, wy, wz)) return 0
        return scene.stateAt(wx, wy, wz)
    }

    override fun hasEntityAt(wx: Int, wy: Int, wz: Int): Boolean {
        if (!scene.contains(wx, wy, wz)) return false
        val idx = scene.idx(wx, wy, wz)
        return scene.entities.any { entityCovers(it, idx) }
    }

    override fun applyChange(change: BlockChange) {
        if (!scene.contains(change.pos.x, change.pos.y, change.pos.z)) return
        scene.setBlock(
            change.pos.x,
            change.pos.y,
            change.pos.z,
            BlockRegistry.wireIndex(change.type).toByte(),
            change.state)
    }

    override fun applyEntityAdd(proto: BlockEntityProto) {
        val masterIdx = scene.idx(proto.worldX, proto.worldY, proto.worldZ)
        scene.entities.add(
            BlockEntity(
                masterIdx = masterIdx,
                type = BlockType(proto.type),
                sizeX = proto.sizeX,
                sizeY = proto.sizeY,
                sizeZ = proto.sizeZ,
                rotation = proto.rotation,
                yOffset = proto.yOffset,
                xOffset = proto.xOffset,
                zOffset = proto.zOffset,
                colorIndex = proto.colorIndex,
            ))
    }

    override fun applyEntityRemove(worldMasterPos: BlockPos) {
        val masterIdx = scene.idx(worldMasterPos.x, worldMasterPos.y, worldMasterPos.z)
        scene.entities.removeAll { it.masterIdx == masterIdx }
    }

    override fun applyEntityRemoveAt(spec: EntityRemoveAt) {
        val masterIdx = scene.idx(spec.pos.x, spec.pos.y, spec.pos.z)
        scene.entities.removeAll {
            it.masterIdx == masterIdx &&
                it.yOffset == spec.yOffset &&
                it.xOffset == spec.xOffset &&
                it.zOffset == spec.zOffset
        }
    }

    override fun getXZOffsetsAt(wx: Int, wy: Int, wz: Int): List<Pair<Int, Int>> {
        if (!scene.contains(wx, wy, wz)) return emptyList()
        val masterIdx = scene.idx(wx, wy, wz)
        return scene.entities
            .filter {
                it.masterIdx == masterIdx &&
                    (it.xOffset > 0 || it.zOffset > 0 || isXZFractional(it.type))
            }
            .map { it.xOffset to it.zOffset }
    }

    override fun hasMisalignedNeighbor(wx: Int, wy: Int, wz: Int): Boolean =
        listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dz) ->
            getXZOffsetsAt(wx + dx, wy, wz + dz).isNotEmpty()
        }

    override fun getLastXZFractionalEntityAt(wx: Int, wy: Int, wz: Int): BlockEntity? {
        if (!scene.contains(wx, wy, wz)) return null
        val masterIdx = scene.idx(wx, wy, wz)
        return scene.entities
            .filter { it.masterIdx == masterIdx && isXZFractional(it.type) }
            .maxWithOrNull(compareBy({ it.xOffset }, { it.zOffset }))
    }

    override fun getFractionalYOffsetsAt(
        wx: Int,
        wy: Int,
        wz: Int,
        xOffset: Int,
        zOffset: Int
    ): List<Int> {
        if (!scene.contains(wx, wy, wz)) return emptyList()
        val masterIdx = scene.idx(wx, wy, wz)
        return scene.entities
            .filter {
                it.masterIdx == masterIdx &&
                    BlockRegistry.get(it.type).brickSize[1] < 2.0f &&
                    it.xOffset == xOffset &&
                    it.zOffset == zOffset
            }
            .map { it.yOffset }
    }

    override fun getTopmostFractionalEntityAt(
        wx: Int,
        wy: Int,
        wz: Int,
        xOffset: Int?,
        zOffset: Int?
    ): BlockEntity? {
        if (!scene.contains(wx, wy, wz)) return null
        val masterIdx = scene.idx(wx, wy, wz)
        return scene.entities
            .filter {
                it.masterIdx == masterIdx &&
                    BlockRegistry.get(it.type).brickSize[1] < 2.0f &&
                    (xOffset == null || it.xOffset == xOffset) &&
                    (zOffset == null || it.zOffset == zOffset)
            }
            .maxByOrNull { it.yOffset }
    }

    override fun getEntityMasterWorldPos(wx: Int, wy: Int, wz: Int): BlockPos? {
        if (!scene.contains(wx, wy, wz)) return null
        val idx = scene.idx(wx, wy, wz)
        val entity = scene.entities.firstOrNull { entityCovers(it, idx) } ?: return null
        val (mx, my, mz) = scene.idxToXYZ(entity.masterIdx)
        return BlockPos(mx, my, mz)
    }

    private fun entityCovers(entity: BlockEntity, targetIdx: Int): Boolean {
        val (mx, my, mz) = scene.idxToXYZ(entity.masterIdx)
        for (dx in 0 until entity.sizeX) for (dy in 0 until entity.sizeY) for (dz in
            0 until entity.sizeZ) {
            val nx = mx + dx
            val ny = my + dy
            val nz = mz + dz
            if (!scene.contains(nx, ny, nz)) continue
            if (scene.idx(nx, ny, nz) == targetIdx) return true
        }
        return false
    }

    private fun isXZFractional(type: BlockType): Boolean {
        val def = BlockRegistry.get(type)
        return def.brickSize.getOrElse(0) { 2f } < 2.0f || def.brickSize.getOrElse(2) { 2f } < 2.0f
    }
}
