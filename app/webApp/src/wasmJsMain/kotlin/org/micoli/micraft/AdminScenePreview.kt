@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package org.micoli.micraft

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.world.BlockEntity
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.protocol.BlockEntityProto

// Lets the admin Scene editor (a bounded, self-contained X/Y/Z raw block buffer, NOT tied to the
// live world/chunk grid — see SceneMesher.kt) render its whole volume with the exact same
// meshing/face-culling code the live game's chunks use, mirroring AdminChunkPreview.kt's role for
// the Instance editor. A new SceneMesher is created whenever the caller hands in a different JS
// Scene (switching scenes re-mounts SceneEditorViewport with a fresh Babylon engine/scene) —
// reusing a mesher tied to a disposed scene would silently mesh into nothing.
private var previewScene: JsAny? = null
private var previewMesher: SceneMesher? = null

// ByteArray isn't a supported type at the Kotlin/Wasm JS-interop boundary (only
// external/primitive/string/function types are) — the raw block/state bytes travel as JS
// Uint8Array (JsAny) instead, read back byte-by-byte, avoiding any string/base64 round-trip.
// Mirrors AdminChunkPreview.kt's jsTypedArrayLength/jsTypedArrayGet helpers.
private fun jsTypedArrayLength(arr: JsAny): Int = js("arr.length")

private fun jsTypedArrayGet(arr: JsAny, i: Int): Int = js("arr[i]")

private fun toByteArray(data: JsAny): ByteArray {
    val len = jsTypedArrayLength(data)
    return ByteArray(len) { i -> jsTypedArrayGet(data, i).toByte() }
}

// Builds (or replaces) the in-memory scene buffer and meshes the whole volume in one call.
// `blocks`/`states` are raw JS Uint8Array (see toByteArray above), length width*height*depth
// each, flat index = x*height*depth + y*depth + z (same convention as Chunk.index, generalized —
// see SceneEditorViewport.tsx's binary-layout parsing of GET /api/admin/scenes/{id}/blocks/raw).
@JsExport
fun mcSceneLoad(scene: JsAny, width: Int, height: Int, depth: Int, blocks: JsAny, states: JsAny) {
    val blocksArr = toByteArray(blocks)
    val statesArr = toByteArray(states)
    if (previewScene !== scene || previewMesher == null) {
        previewScene = scene
        previewMesher = SceneMesher(width, height, depth, blocksArr, statesArr)
    } else {
        previewMesher!!.replaceBuffers(width, height, depth, blocksArr, statesArr)
    }
    previewMesher!!.render(scene)
}

// Mutates the local buffer and re-meshes the whole volume (simplicity over incremental remesh —
// a Scene buffer is small enough that a full remesh per edit is cheap, mirroring how
// ChunkManager.renderChunk() does a synchronous full re-render on any WorldUpdate).
@JsExport
fun mcSceneSetBlock(scene: JsAny, x: Int, y: Int, z: Int, type: Int, state: Int) {
    val mesher = previewMesher ?: return
    mesher.setBlock(x, y, z, type, state)
    previewScene = scene
    mesher.render(scene)
}

@JsExport
fun mcSceneGetBlockOrdinalAt(x: Int, y: Int, z: Int): Int =
    previewMesher?.getBlockOrdinal(x, y, z) ?: 0

@JsExport
fun mcSceneGetBlockStateAt(x: Int, y: Int, z: Int): Int = previewMesher?.getState(x, y, z) ?: 0

// Replaces the whole fractional-entity list (LEGO_PIECE etc., see BlockEntity/Scene.entities) and
// re-meshes — called once on scene load (GET /api/admin/scenes/{id}/entities) and again after any
// fractional place/break edit, since the server (the only source of truth for slot/offset
// resolution — see BlockPlacer.placeAt/BlockBreaker.removeAt) is the one computing the updated
// list; simpler and less error-prone than diffing individual adds/removes on the client.
@JsExport
fun mcSceneLoadEntities(scene: JsAny, entitiesJson: String) {
    val mesher = previewMesher ?: return
    val protos = Json.decodeFromString(ListSerializer(BlockEntityProto.serializer()), entitiesJson)
    val entities =
        protos.map { proto ->
            BlockEntity(
                masterIdx = mesher.index(proto.worldX, proto.worldY, proto.worldZ),
                type = BlockType(proto.type),
                sizeX = proto.sizeX,
                sizeY = proto.sizeY,
                sizeZ = proto.sizeZ,
                rotation = proto.rotation,
                yOffset = proto.yOffset,
                xOffset = proto.xOffset,
                zOffset = proto.zOffset,
                colorIndex = proto.colorIndex,
            )
        }
    mesher.setEntities(entities)
    previewScene = scene
    mesher.render(scene)
}

// Mirrors mcAdminGetUsedXZOffsetAt (AdminChunkPreview.kt) for the Scene editor — packs the first
// XZ-fractional slot already occupied at this cell as x*4+z, or -1 if none.
@JsExport
fun mcSceneGetUsedXZOffsetAt(x: Int, y: Int, z: Int): Int {
    val mesher = previewMesher ?: return -1
    val slot = mesher.getUsedXZOffsetsAt(x, y, z).firstOrNull() ?: return -1
    return slot.first * 4 + slot.second
}

@JsExport
fun mcSceneDispose() {
    previewMesher?.dispose()
    previewMesher = null
    previewScene = null
}
