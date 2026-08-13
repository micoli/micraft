package org.micoli.micraft.game.world.scene

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A bounded X/Y/Z raw block-structure editor buffer — deliberately NOT tied to the live
 * world/chunks (unlike [org.micoli.micraft.game.world.instance.InstanceZone], which carves a region
 * out of the persistent world). [blocks]/[states] are excluded from the YAML-serialized metadata
 * (they're persisted separately as gzip binaries, mirroring chunk persistence) — a `@Transient`
 * field with an empty-array default keeps this a single `@Serializable data class` instead of
 * splitting metadata/buffers into separate types.
 */
@Serializable
data class Scene(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val depth: Int,
    val ownerName: String,
    val createdAt: Long,
    // Admin editor shortcut bar, persisted so it survives a page reload/navigation — same
    // rationale as InstanceZone.shortcutBarPages.
    @EncodeDefault(ALWAYS) val shortcutBarPages: List<List<String?>> = emptyList(),
    @Transient val blocks: ByteArray = ByteArray(0),
    @Transient val states: ByteArray = ByteArray(0),
) {
    // Same convention as Chunk.index(x,y,z): X-major, Y-mid, Z-minor.
    fun idx(x: Int, y: Int, z: Int): Int = x * height * depth + y * depth + z

    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in 0 until width && y in 0 until height && z in 0 until depth

    fun blockAt(x: Int, y: Int, z: Int): Byte = blocks[idx(x, y, z)]

    fun stateAt(x: Int, y: Int, z: Int): Byte = if (states.isNotEmpty()) states[idx(x, y, z)] else 0

    /** Mutates the backing buffers in place — callers are responsible for persisting after. */
    fun setBlock(x: Int, y: Int, z: Int, type: Byte, state: Byte) {
        blocks[idx(x, y, z)] = type
        states[idx(x, y, z)] = state
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Scene) return false
        return id == other.id &&
            name == other.name &&
            width == other.width &&
            height == other.height &&
            depth == other.depth &&
            ownerName == other.ownerName &&
            createdAt == other.createdAt &&
            shortcutBarPages == other.shortcutBarPages &&
            blocks.contentEquals(other.blocks) &&
            states.contentEquals(other.states)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + depth
        result = 31 * result + ownerName.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + shortcutBarPages.hashCode()
        result = 31 * result + blocks.contentHashCode()
        result = 31 * result + states.contentHashCode()
        return result
    }
}
