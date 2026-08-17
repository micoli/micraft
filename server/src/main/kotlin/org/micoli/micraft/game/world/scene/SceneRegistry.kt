package org.micoli.micraft.game.world.scene

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.rail.RailConnection

class SceneRegistry(private val persistence: WorldPersistence?) {
    private val scenes = ConcurrentHashMap<String, Scene>()

    init {
        persistence?.loadScenes()?.forEach { scenes[it.id] = it }
    }

    fun all(): List<Scene> = scenes.values.sortedBy { it.createdAt }

    fun get(id: String): Scene? = scenes[id]

    fun create(name: String, width: Int, height: Int, depth: Int, ownerName: String): Scene {
        val scene =
            Scene(
                id = UUID.randomUUID().toString(),
                name = name,
                width = width,
                height = height,
                depth = depth,
                ownerName = ownerName,
                createdAt = System.currentTimeMillis(),
                blocks = ByteArray(width * height * depth),
                states = ByteArray(width * height * depth),
                extraStates = ByteArray(width * height * depth),
            )
        scenes[scene.id] = scene
        persistMetadata()
        persistBlocks(scene)
        return scene
    }

    fun duplicate(id: String): Scene? {
        val existing = scenes[id] ?: return null
        val copy =
            existing.copy(
                id = UUID.randomUUID().toString(),
                name = "${existing.name} copy",
                createdAt = System.currentTimeMillis(),
                blocks = existing.blocks.copyOf(),
                states = existing.states.copyOf(),
                extraStates = existing.extraStates.copyOf(),
                // Scene.copy() shallow-copies entities (same MutableList reference by default) —
                // must deep-copy so mutating the duplicate's entities never touches the original.
                entities = existing.entities.toMutableList(),
            )
        scenes[copy.id] = copy
        persistMetadata()
        persistBlocks(copy)
        persistEntities(copy)
        return copy
    }

    fun rename(id: String, name: String): Scene? {
        val existing = scenes[id] ?: return null
        val updated = existing.copy(name = name)
        scenes[id] = updated
        persistMetadata()
        return updated
    }

    /** Reallocates buffers at the new dimensions, copying the overlapping region at origin. */
    fun resize(id: String, width: Int, height: Int, depth: Int): Scene? {
        val existing = scenes[id] ?: return null
        val newBlocks = ByteArray(width * height * depth)
        val newStates = ByteArray(width * height * depth)
        val newExtraStates = ByteArray(width * height * depth)
        val cw = min(existing.width, width)
        val ch = min(existing.height, height)
        val cd = min(existing.depth, depth)
        for (x in 0 until cw) {
            for (y in 0 until ch) {
                for (z in 0 until cd) {
                    val newIdx = x * height * depth + y * depth + z
                    val oldIdx = existing.idx(x, y, z)
                    newBlocks[newIdx] = existing.blocks[oldIdx]
                    newStates[newIdx] = existing.states[oldIdx]
                    newExtraStates[newIdx] = existing.extraStateAt(x, y, z)
                }
            }
        }
        // Scene.idx depends on height/depth, so masterIdx must be remapped to the new dimensions —
        // entities whose master cell falls outside the new bounds are dropped (their footprint is
        // no longer meaningful).
        val newEntities =
            existing.entities.mapNotNull { entity ->
                val (mx, my, mz) = existing.idxToXYZ(entity.masterIdx)
                if (mx >= cw || my >= ch || mz >= cd) null
                else entity.copy(masterIdx = mx * height * depth + my * depth + mz)
            }
        val updated =
            existing.copy(
                width = width,
                height = height,
                depth = depth,
                blocks = newBlocks,
                states = newStates,
                extraStates = newExtraStates,
                entities = newEntities.toMutableList())
        scenes[id] = updated
        persistMetadata()
        persistBlocks(updated)
        persistEntities(updated)
        return updated
    }

    fun updateLayout(id: String, shortcutBarPages: List<List<String?>>): Scene? {
        val existing = scenes[id] ?: return null
        val updated = existing.copy(shortcutBarPages = shortcutBarPages)
        scenes[id] = updated
        persistMetadata()
        return updated
    }

    fun setBlock(id: String, x: Int, y: Int, z: Int, type: Byte, state: Byte): Boolean {
        val scene = scenes[id] ?: return false
        if (!scene.contains(x, y, z)) return false
        scene.setBlock(x, y, z, type, state)
        persistBlocks(scene)
        return true
    }

    /**
     * Places a (possibly fractional/lego) block via [BlockPlacer.placeAt], targeting this scene's
     * bounded buffer through [ScenePlacementTarget]. Returns null if the scene doesn't exist.
     */
    fun placeBlock(
        id: String,
        x: Int,
        y: Int,
        z: Int,
        blockType: BlockType,
        rotation: Int,
        colorIndex: Int,
        xOffset: Int,
        zOffset: Int,
    ): BlockPlacer.Companion.PlaceResult? {
        val scene = scenes[id] ?: return null
        val target = ScenePlacementTarget(scene)
        val result =
            BlockPlacer.placeAt(
                BlockPos(x, y, z), blockType, rotation, colorIndex, xOffset, zOffset, target)
        if (result.rejectedReason == null &&
            (result.changes.isNotEmpty() || result.entityAdds.isNotEmpty())) {
            persistBlocks(scene)
            persistEntities(scene)
        }
        return result
    }

    /**
     * Removes the block/entity slot at (x,y,z) (restricted to the given XZ sub-slot for
     * XZ+Y-fractional blocks) via [BlockBreaker.removeAt]. Returns null if the scene doesn't exist.
     */
    fun breakBlock(
        id: String,
        x: Int,
        y: Int,
        z: Int,
        xOffset: Int,
        zOffset: Int,
    ): BlockBreaker.Companion.RemoveResult? {
        val scene = scenes[id] ?: return null
        val target = ScenePlacementTarget(scene)
        val result = BlockBreaker.removeAt(BlockPos(x, y, z), xOffset, zOffset, target)
        persistBlocks(scene)
        persistEntities(scene)
        return result
    }

    sealed class SwitchToggleResult {
        data class Applied(val extraState: Byte) : SwitchToggleResult()

        data class Rejected(val reason: String) : SwitchToggleResult()
    }

    // Cycles a switch/junction rail block's active branch — mirrors BlockInteractor.handleInteract
    // (Instance/live-world path) for a Scene's bounded buffer, called by the Scene editor's
    // rail-test
    // switch overlay.
    fun toggleSwitch(id: String, x: Int, y: Int, z: Int): SwitchToggleResult {
        val scene = scenes[id] ?: return SwitchToggleResult.Rejected("Scene not found")
        if (!scene.contains(x, y, z)) {
            return SwitchToggleResult.Rejected("Coordinate outside scene bounds")
        }
        val type = BlockRegistry.byWireIndex(scene.blockAt(x, y, z).toInt() and 0xFF)
        if (!RailConnection.isJunction(type)) {
            return SwitchToggleResult.Rejected("Not a switch block")
        }
        val branches = RailConnection.branchCount(type)
        val nextBranch = (BlockState.extra(scene.extraStateAt(x, y, z)) + 1) % branches
        val nextExtraState = BlockState.packExtra(nextBranch)
        scene.setExtraState(x, y, z, nextExtraState)
        persistBlocks(scene)
        return SwitchToggleResult.Applied(nextExtraState)
    }

    fun delete(id: String): Boolean {
        val removed = scenes.remove(id) ?: return false
        persistMetadata()
        persistence?.deleteSceneFiles(removed.id)
        return true
    }

    private fun persistMetadata() {
        persistence?.saveScenesMetadata(all())
    }

    private fun persistBlocks(scene: Scene) {
        persistence?.saveSceneBlocks(scene.id, scene.blocks, scene.states, scene.extraStates)
    }

    private fun persistEntities(scene: Scene) {
        persistence?.saveSceneEntities(scene.id, scene.entities)
    }
}
