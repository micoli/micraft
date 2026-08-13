@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package org.micoli.micraft

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop

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

@JsExport
fun mcSceneDispose() {
    previewMesher?.dispose()
    previewMesher = null
    previewScene = null
}
