package org.micoli.micraft.game.world.instance

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.InstanceZoneProto

@Serializable
data class ClipPlaneAxisState(
    val enabled: Boolean = false,
    val flipped: Boolean = false,
    val pos: Double = 0.0
)

@Serializable
data class InstanceClipPlanes(
    val x: ClipPlaneAxisState = ClipPlaneAxisState(),
    val y: ClipPlaneAxisState = ClipPlaneAxisState(),
    val z: ClipPlaneAxisState = ClipPlaneAxisState(),
)

@Serializable
data class InstanceZone(
    val id: String,
    val name: String,
    val yMin: Int,
    val yMax: Int,
    val chunks: Set<ChunkPos>,
    val ownerName: String,
    val createdAt: Long,
    // Default Json (encodeDefaults=false) drops fields equal to their default — without this,
    // enabled=true (the default) would be omitted from the admin instances list JSON, and the
    // frontend would see `undefined` (falsy) forever, unable to distinguish "true" from "missing".
    @EncodeDefault(ALWAYS) val enabled: Boolean = true,
    // Admin editor viewport layout, persisted so it survives a page reload/navigation instead of
    // living in localStorage (which was keyed by zone.id but not shareable across browsers/admins).
    @EncodeDefault(ALWAYS) val clipPlanes: InstanceClipPlanes = InstanceClipPlanes(),
    @EncodeDefault(ALWAYS) val shortcutBarPages: List<List<String?>> = emptyList(),
) {
    fun contains(x: Int, y: Int, z: Int): Boolean =
        enabled &&
            y in yMin..yMax &&
            ChunkPos(
                Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                Math.floorDiv(z, WorldConstants.CHUNK_SIZE)) in chunks

    // Y-layer-major order (every chunk's bottom layer before any chunk's next layer up), rather
    // than chunk-major. A capped block stream (see AdminController's blocks endpoint) then
    // truncates to a clean horizontal slice of the whole footprint instead of leaving random
    // whole chunk-columns entirely unstreamed, which reads as gaping holes in the editor.
    fun blockColumnsByLayer(chunkSize: Int): Sequence<Triple<Int, Int, Int>> {
        val orderedChunks = chunks.sortedWith(compareBy({ it.cx }, { it.cz }))
        return sequence {
            for (y in yMin..yMax) {
                for (chunkPos in orderedChunks) {
                    val xBase = chunkPos.cx * chunkSize
                    val zBase = chunkPos.cz * chunkSize
                    for (x in xBase until xBase + chunkSize) {
                        for (z in zBase until zBase + chunkSize) {
                            yield(Triple(x, y, z))
                        }
                    }
                }
            }
        }
    }

    // Single chunk column, full Y range — used by the admin instance editor to stream one
    // chunk's worth of blocks at a time instead of the whole zone, so it can load/unload
    // geometry as the camera moves rather than truncating at a fixed block cap.
    fun blockColumnsForChunk(chunkSize: Int, cx: Int, cz: Int): Sequence<Triple<Int, Int, Int>> {
        if (ChunkPos(cx, cz) !in chunks) return emptySequence()
        val xBase = cx * chunkSize
        val zBase = cz * chunkSize
        return sequence {
            for (y in yMin..yMax) {
                for (x in xBase until xBase + chunkSize) {
                    for (z in zBase until zBase + chunkSize) {
                        yield(Triple(x, y, z))
                    }
                }
            }
        }
    }
}

fun InstanceZone.toProto(): InstanceZoneProto =
    InstanceZoneProto(id = id, name = name, yMin = yMin, yMax = yMax, chunks = chunks.toList())
