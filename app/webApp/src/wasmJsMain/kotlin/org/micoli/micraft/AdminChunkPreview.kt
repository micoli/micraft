@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package org.micoli.micraft

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import org.micoli.micraft.babylon.jsDisposeChunk
import org.micoli.micraft.babylon.jsWarn
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec

// Lets the admin instance-editor bundle (a separate esbuild TS bundle, driven by classic
// <script> loads rather than the real game's login/GameClient flow) render zone chunks with the
// exact same meshing code the live game uses, instead of a hand-rolled per-block renderer.
// A new ChunkManager is created whenever the caller hands in a different Scene (e.g. switching
// to a different instance zone re-mounts InstanceEditorViewport with a fresh Babylon engine/
// scene) — reusing a manager tied to a disposed scene would silently mesh into nothing.
private var previewScene: JsAny? = null
private var previewChunkManager: ChunkManager? = null

// ByteArray isn't a supported type at the Kotlin/Wasm JS-interop boundary (only
// external/primitive/string/function types are) — the wire bytes travel as a raw JS Uint8Array
// (JsAny) instead, read back byte-by-byte, avoiding any string/base64 round-trip.
private fun jsTypedArrayLength(arr: JsAny): Int = js("arr.length")

private fun jsTypedArrayGet(arr: JsAny, i: Int): Int = js("arr[i]")

private fun managerFor(scene: JsAny): ChunkManager {
    if (previewScene !== scene) {
        previewScene = scene
        previewChunkManager = ChunkManager(scene)
    }
    return previewChunkManager!!
}

@JsExport
fun mcAdminLoadChunk(scene: JsAny, data: JsAny, yMin: Int, yMax: Int) {
    val manager = managerFor(scene)
    val len = jsTypedArrayLength(data)
    val bytes = ByteArray(len) { i -> jsTypedArrayGet(data, i).toByte() }
    val msg =
        try {
            ServerMessageCodec.decode(bytes)
        } catch (e: Throwable) {
            jsWarn("mcAdminLoadChunk: decode failed: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    if (msg !is ServerMessage.ChunkData) {
        jsWarn("mcAdminLoadChunk: decoded non-ChunkData message: ${msg::class.simpleName}")
        return
    }
    val chunk =
        Chunk.decodeWire(
            msg.pos, msg.topY, msg.wireBlocks, msg.wireStates.takeIf { it.isNotEmpty() })
    // /api/chunks/{cx}/{cz} returns the whole world-height column — clip to the zone's
    // [yMin, yMax] bounds so the preview only shows what's actually editable/visible in-zone.
    for (x in 0 until Chunk.SIZE_X) for (z in 0 until Chunk.SIZE_Z) {
        for (y in 0 until yMin) chunk.blocks[Chunk.index(x, y, z)] = 0
        for (y in (yMax + 1)..msg.topY) chunk.blocks[Chunk.index(x, y, z)] = 0
    }
    manager.renderChunk(chunk, msg.topY)
}

@JsExport
fun mcAdminDisposeChunk(cx: Int, cz: Int) {
    jsDisposeChunk("$cx,$cz")
}
