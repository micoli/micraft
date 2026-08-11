@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package org.micoli.micraft

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsDisposeChunk
import org.micoli.micraft.babylon.jsWarn
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.protocol.BlockInfo
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

// The admin bundle never connects to the game WebSocket, so it never receives a
// ServerMessage.RegistrySync — GameClient.kt's BlockRegistry.load(...) call (which populates the
// WASM-side registry used by renderFractionalEntities' BlockRegistry.wireIndex(entity.type) lookup)
// never runs. Without it, wireIndex returns 0 for custom blocks like LEGO_PIECE and
// renderFractionalEntities silently drops every XZ/Y-fractional entity face (the block byte array
// itself still renders fine since that path reads the wire byte directly, no registry lookup
// needed — only the fractional overlay silently disappears). Mirrors the mapping in
// GameClient.kt's RegistrySync handler, fed from the same /api/admin/blocks JSON already fetched
// by InstanceEditorViewport.tsx.
@JsExport
fun mcAdminSetBlockRegistry(json: String) {
    // jsWarn("fracDebug mcAdminSetBlockRegistry called, json.length=${json.length}")
    val infos = Json.decodeFromString(ListSerializer(BlockInfo.serializer()), json)
    // jsWarn("fracDebug mcAdminSetBlockRegistry decoded ${infos.size} infos")
    val defs =
        infos.associate { info ->
            BlockType(info.name) to
                BlockDefinition(
                    hardness = info.hardness,
                    solid = info.solid,
                    transparent = info.transparent,
                    minimapColor = info.minimapColor,
                    modelElement = info.modelElement,
                    gltfModel = info.gltfModel,
                    liquid = info.liquid,
                    viscosity = info.viscosity,
                    minimapVisible = info.minimapVisible,
                    rotatable = info.rotatable,
                    hasStuds = info.hasStuds,
                    brickSize = info.brickSize,
                    heightFraction = info.heightFraction,
                    plainColorable = info.plainColorable,
                    isCubic = info.isCubic,
                )
        }
    BlockRegistry.load(defs)
    // jsWarn(
    // "fracDebug BlockRegistry.load done, LEGO_PIECE
    // wireIndex=${BlockRegistry.wireIndex(BlockType("LEGO_PIECE"))}")
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
            msg.pos,
            msg.topY,
            msg.wireBlocks,
            msg.wireStates.takeIf { it.isNotEmpty() },
            msg.entities)
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

// Lets the admin editor resolve the block type ordinal under the cursor when breaking, so it can
// look up its brickSize (via window.mc.getBlockDef) and compute the precise XZ sub-slot targeted
// — the same block-def lookup the ghost preview already uses for placement.
@JsExport
fun mcAdminGetBlockOrdinalAt(scene: JsAny, wx: Int, wy: Int, wz: Int): Int {
    val manager = managerFor(scene)
    return BlockRegistry.wireIndex(manager.getBlockAtWorld(wx, wy, wz))
}

// Exposes the raw state byte (rotation + colorIndex, see BlockState.kt) of the block under the
// cursor, so the break ghost can render with the same rotation as the actually placed block
// instead of assuming rotation 0.
@JsExport
fun mcAdminGetBlockStateAt(scene: JsAny, wx: Int, wy: Int, wz: Int): Int {
    val manager = managerFor(scene)
    return manager.getStateAtWorld(wx, wy, wz).toInt() and 0xFF
}
