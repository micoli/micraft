@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package org.micoli.micraft

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.math.atan2
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsDisposeChunk
import org.micoli.micraft.babylon.jsWarn
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.game.world.rail.RailConnectionPoint
import org.micoli.micraft.game.world.rail.RailTraversal
import org.micoli.micraft.game.world.rail.RailWorldView
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
    val infos = Json.decodeFromString(ListSerializer(BlockInfo.serializer()), json)
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
                    plainColorable = info.plainColorable,
                    isCubic = info.isCubic,
                    connections =
                        info.connections.map { group ->
                            group.map { RailConnectionPoint.parse(it) }
                        },
                )
        }
    BlockRegistry.load(defs)
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
            msg.wireExtraStates.takeIf { it.isNotEmpty() },
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

// Packs the first XZ-fractional slot already occupied at this cell as x*4+z, or -1 if none.
// Mirrors the in-game ghost/break-overlay fix (LocalPlayerController.kt): a lateral face's
// normal axis carries no positional info in the pick point, so that axis must snap to an
// existing neighbor's slot instead of the meaningless face-boundary value.
@JsExport
fun mcAdminGetUsedXZOffsetAt(scene: JsAny, wx: Int, wy: Int, wz: Int): Int {
    val manager = managerFor(scene)
    val slot = manager.getUsedXZOffsetsAt(wx, wy, wz).firstOrNull() ?: return -1
    return slot.first * 4 + slot.second
}

// Exposes the switch/junction extra-state byte (see BlockState.extra, RailConnection.active) of
// the block under the cursor — needed so the editor's rail circuit test (below) picks the same
// branch a placed switch was toggled to, instead of always assuming branch 0.
@JsExport
fun mcAdminGetExtraStateAt(scene: JsAny, wx: Int, wy: Int, wz: Int): Int {
    val manager = managerFor(scene)
    return manager.getExtraStateAtWorld(wx, wy, wz).toInt() and 0xFF
}

// ── Rail circuit test (editor-only, no server round-trip) ──────────────────────────────────
// A single in-memory "test cart" the admin instance editor can drop on a rail block and watch
// travel the network, to visually confirm switches/loops/dead-ends behave as intended before
// leaving the editor. Ports VehicleBehavior.kt's tick logic (server/game/vehicle) against
// ChunkManager instead of WorldState, since the editor has neither a WorldState nor a spawned
// VehicleInstance/VehicleRegistry entry to drive it — a fixed test speed stands in for a real
// vehicle's per-type speed. RailConnection itself (core, commonMain) is reused as-is.
private const val RAIL_TEST_SPEED = 3f // blocks/second — editor test only, no vehicle type context

// Half the cart mesh's height (railTestCart.ts's CART_SIZE / 2) — the reported pose sits the cart
// on top of the rail block's surface rather than centered inside it.
private const val CART_HALF_HEIGHT = 0.3f
private var railTestPos: BlockPos? = null
private var railTestDir: Direction = Direction.NORTH
private var railTestProgress: Float = 0f

// Starts (or restarts) the test cart at (wx,wy,wz), returning 0 (no-op) if that cell isn't a
// rail block or exposes no usable connection to travel along, 1 on success.
@JsExport
fun mcAdminRailTestStart(scene: JsAny, wx: Int, wy: Int, wz: Int): Int {
    val manager = managerFor(scene)
    val type = manager.getBlockAtWorld(wx, wy, wz)
    if (!RailConnection.isRail(type)) return 0
    val state = manager.getStateAtWorld(wx, wy, wz)
    val extra = manager.getExtraStateAtWorld(wx, wy, wz)
    val dir = RailConnection.active(type, state, extra).firstOrNull() ?: return 0
    railTestPos = BlockPos(wx, wy, wz)
    railTestDir = dir
    railTestProgress = 0f
    return 1
}

// Lists every switch/junction rail block in currently loaded chunks, so the editor can overlay a
// clickable marker on each and let the user cycle its branch while a rail test is running. CSV
// rows "x,y,z,branchCount,currentBranch" separated by ';' — one wasm→JS string call per rail-test
// toggle instead of a typed-array round trip, since junction counts are small (editor scale).
@JsExport
fun mcAdminListJunctions(scene: JsAny): String {
    val manager = managerFor(scene)
    val sb = StringBuilder()
    for ((pos, chunkAndTopY) in manager.chunkData) {
        val (chunk, topY) = chunkAndTopY
        val baseX = pos.cx * WorldConstants.CHUNK_SIZE
        val baseZ = pos.cz * WorldConstants.CHUNK_SIZE
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                for (y in 0..topY) {
                    val type = chunk.getBlock(lx, y, lz)
                    if (!RailConnection.isJunction(type)) continue
                    val branches = RailConnection.branchCount(type)
                    val current =
                        BlockState.extra(chunk.getExtraState(lx, y, lz)).coerceIn(0, branches - 1)
                    if (sb.isNotEmpty()) sb.append(';')
                    sb.append(baseX + lx)
                        .append(',')
                        .append(y)
                        .append(',')
                        .append(baseZ + lz)
                        .append(',')
                        .append(branches)
                        .append(',')
                        .append(current)
                }
            }
        }
    }
    return sb.toString()
}

@JsExport
fun mcAdminRailTestStop() {
    railTestPos = null
}

// Advances the test cart by [deltaSeconds] and returns its new pose as "x,y,z,yaw" (empty string
// if no test is running) — a plain CSV string rather than JSON, parsed once per frame on the TS
// side, since only 4 floats travel the JS-interop boundary each call.
@JsExport
fun mcAdminRailTestTick(scene: JsAny, deltaSeconds: Float): String {
    val manager = managerFor(scene)
    if (railTestPos == null) return ""
    railTestProgress += RAIL_TEST_SPEED * deltaSeconds
    while (railTestProgress >= 1f) {
        railTestProgress -= 1f
        advanceRailTest(manager)
    }
    val current = railTestPos ?: return ""
    val dir = railTestDir
    val t = railTestProgress
    val height =
        RailTraversal.localHeight(ChunkManagerRailView(manager), current, dir.opposite, dir, t)
    val x = current.x + 0.5f + dir.dx * t
    val y = current.y + 1f + height + CART_HALF_HEIGHT
    val z = current.z + 0.5f + dir.dz * t
    val yaw = atan2(dir.dx.toDouble(), dir.dz.toDouble()).toFloat()
    return "$x,$y,$z,$yaw"
}

// Adapts ChunkManager to the world-agnostic RailTraversal shared with the server (VehicleBehavior,
// RailNetworkRegistry) and the scene editor's own rail test (AdminScenePreview.kt).
private class ChunkManagerRailView(private val manager: ChunkManager) : RailWorldView {
    override fun getBlock(wx: Int, wy: Int, wz: Int) = manager.getBlockAtWorld(wx, wy, wz)

    override fun getBlockState(wx: Int, wy: Int, wz: Int) = manager.getStateAtWorld(wx, wy, wz)

    override fun getExtraState(wx: Int, wy: Int, wz: Int) = manager.getExtraStateAtWorld(wx, wy, wz)
}

// Mirrors VehicleBehavior.advanceOneBlock — reversal at a dead end and indefinite traversal on a
// loop both fall out of this same local connectivity check, see that file's doc comment.
private fun advanceRailTest(manager: ChunkManager) {
    val current = railTestPos ?: return
    val exitDir = railTestDir
    val arrivalDir = exitDir.opposite
    val view = ChunkManagerRailView(manager)
    val nextPos = RailTraversal.connectingNeighbor(view, current, exitDir)
    if (nextPos == null) {
        // See RailConnection.preferredContinuation: a straight/crossing piece reverses back the way
        // it came, a curve turns onto its only other connection — never a direction the piece
        // doesn't actually connect through (which would bounce forever).
        val currentType = manager.getBlockAtWorld(current.x, current.y, current.z)
        val currentState = manager.getStateAtWorld(current.x, current.y, current.z)
        val currentExtra = manager.getExtraStateAtWorld(current.x, current.y, current.z)
        val currentActive = RailConnection.activeGroups(currentType, currentState, currentExtra)
        railTestDir = RailConnection.preferredContinuation(currentActive, exitDir) ?: arrivalDir
        return
    }
    val nextType = manager.getBlockAtWorld(nextPos.x, nextPos.y, nextPos.z)
    val nextState = manager.getStateAtWorld(nextPos.x, nextPos.y, nextPos.z)
    val nextExtra = manager.getExtraStateAtWorld(nextPos.x, nextPos.y, nextPos.z)
    val nextActive = RailConnection.activeGroups(nextType, nextState, nextExtra)
    val forward = RailConnection.preferredContinuation(nextActive, arrivalDir)
    railTestPos = nextPos
    railTestDir = forward ?: arrivalDir
}
