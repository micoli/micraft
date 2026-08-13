package org.micoli.micraft.game.world.scene

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import org.micoli.micraft.game.world.WorldPersistence

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
            )
        scenes[scene.id] = scene
        persistMetadata()
        persistBlocks(scene)
        return scene
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
                }
            }
        }
        val updated =
            existing.copy(
                width = width,
                height = height,
                depth = depth,
                blocks = newBlocks,
                states = newStates)
        scenes[id] = updated
        persistMetadata()
        persistBlocks(updated)
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
        persistence?.saveSceneBlocks(scene.id, scene.blocks, scene.states)
    }
}
