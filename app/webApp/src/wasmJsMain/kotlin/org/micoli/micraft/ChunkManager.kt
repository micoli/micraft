package org.micoli.micraft

import kotlin.math.*
import org.micoli.micraft.babylon.*
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants

// AO neighbor offsets: [face][vertex][neighbor(s1,s2,corner)][axis(dx,dy,dz)]
private val AO_NEIGHBORS: Array<Array<Array<IntArray>>> =
    arrayOf(
        // fd=0: +Z (south)
        arrayOf(
            arrayOf(intArrayOf(-1, 0, 1), intArrayOf(0, -1, 1), intArrayOf(-1, -1, 1)),
            arrayOf(intArrayOf(1, 0, 1), intArrayOf(0, -1, 1), intArrayOf(1, -1, 1)),
            arrayOf(intArrayOf(1, 0, 1), intArrayOf(0, 1, 1), intArrayOf(1, 1, 1)),
            arrayOf(intArrayOf(-1, 0, 1), intArrayOf(0, 1, 1), intArrayOf(-1, 1, 1)),
        ),
        // fd=1: -Z (north)
        arrayOf(
            arrayOf(intArrayOf(1, 0, -1), intArrayOf(0, -1, -1), intArrayOf(1, -1, -1)),
            arrayOf(intArrayOf(-1, 0, -1), intArrayOf(0, -1, -1), intArrayOf(-1, -1, -1)),
            arrayOf(intArrayOf(-1, 0, -1), intArrayOf(0, 1, -1), intArrayOf(-1, 1, -1)),
            arrayOf(intArrayOf(1, 0, -1), intArrayOf(0, 1, -1), intArrayOf(1, 1, -1)),
        ),
        // fd=2: +X (east)
        arrayOf(
            arrayOf(intArrayOf(1, 0, 1), intArrayOf(1, -1, 0), intArrayOf(1, -1, 1)),
            arrayOf(intArrayOf(1, 0, -1), intArrayOf(1, -1, 0), intArrayOf(1, -1, -1)),
            arrayOf(intArrayOf(1, 0, -1), intArrayOf(1, 1, 0), intArrayOf(1, 1, -1)),
            arrayOf(intArrayOf(1, 0, 1), intArrayOf(1, 1, 0), intArrayOf(1, 1, 1)),
        ),
        // fd=3: -X (west)
        arrayOf(
            arrayOf(intArrayOf(-1, 0, -1), intArrayOf(-1, -1, 0), intArrayOf(-1, -1, -1)),
            arrayOf(intArrayOf(-1, 0, 1), intArrayOf(-1, -1, 0), intArrayOf(-1, -1, 1)),
            arrayOf(intArrayOf(-1, 0, 1), intArrayOf(-1, 1, 0), intArrayOf(-1, 1, 1)),
            arrayOf(intArrayOf(-1, 0, -1), intArrayOf(-1, 1, 0), intArrayOf(-1, 1, -1)),
        ),
        // fd=4: +Y (top)
        arrayOf(
            arrayOf(intArrayOf(-1, 1, 0), intArrayOf(0, 1, 1), intArrayOf(-1, 1, 1)),
            arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, 1), intArrayOf(1, 1, 1)),
            arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, -1), intArrayOf(1, 1, -1)),
            arrayOf(intArrayOf(-1, 1, 0), intArrayOf(0, 1, -1), intArrayOf(-1, 1, -1)),
        ),
        // fd=5: -Y (bottom)
        arrayOf(
            arrayOf(intArrayOf(-1, -1, 0), intArrayOf(0, -1, -1), intArrayOf(-1, -1, -1)),
            arrayOf(intArrayOf(1, -1, 0), intArrayOf(0, -1, -1), intArrayOf(1, -1, -1)),
            arrayOf(intArrayOf(1, -1, 0), intArrayOf(0, -1, 1), intArrayOf(1, -1, 1)),
            arrayOf(intArrayOf(-1, -1, 0), intArrayOf(0, -1, 1), intArrayOf(-1, -1, 1)),
        ),
    )

// Faces processed per Phase-2 budget slice. At ~200–400ns/face this targets ~1–2ms/slice.
private const val FACE_SLICE_SIZE = 5_000

// Master switch for the far-chunk impostor system below. Default true — overridable per-player
// via graphics preferences (see ChunkManager.useImpostor).
private const val DEFAULT_USE_IMPOSTOR = true

// Chebyshev chunk distance from the viewer (see jsGetActiveCameraChunkX/Z) within which a chunk
// gets full block geometry; beyond it, a cheap flat impostor (buildChunkImpostorMesh) instead.
// FORWARD_VIEW_RADIUS is 7, so this still leaves a generous full-detail radius while cutting
// geometry cost on the outer rings, which is most of a 15×15 candidate area by cell count.
// Decided once when a chunk is first meshed — a chunk doesn't get upgraded/downgraded later if
// the viewer moves closer/farther without the chunk itself unloading and reloading.
// Default 3 — overridable per-player via graphics preferences (see
// ChunkManager.impostorRadiusChunks).
private const val DEFAULT_IMPOSTOR_RADIUS_CHUNKS = 3

// Plain color index rides in bits 18-23 of the ao int (bits 0-15 = AO, 16-17 = yOffset).
private const val COLOR_SHIFT = 18

private data class ChunkRender(
    val chunk: Chunk,
    val topY: Int,
    var nextY: Int = 0,
    val t0: Double = jsNow(),
    var faces: Int = 0,
    var faceCount: Int = -1, // set when rows finish; -1 = rows still in progress
    var faceProcessingCursor: Int = 0,
)

class ChunkManager(private val scene: JsAny) {
    var useImpostor: Boolean = DEFAULT_USE_IMPOSTOR
    var impostorRadiusChunks: Int = DEFAULT_IMPOSTOR_RADIUS_CHUNKS

    val loadedChunks = mutableSetOf<ChunkPos>()

    // Chunks currently meshed as a flat impostor rather than full geometry — see
    // IMPOSTOR_RADIUS_CHUNKS. Tracked so upgradeNearImpostors() can promote them to full
    // detail once the viewer gets close enough, since the impostor/full choice is otherwise
    // only made once, at first mesh (see USE_IMPOSTOR comment above).
    private val impostorChunks = mutableSetOf<ChunkPos>()
    val chunkData = mutableMapOf<ChunkPos, Pair<Chunk, Int>>()
    private val pendingChunks = mutableListOf<Pair<Chunk, Int>>()
    private var pendingChunksDirty = false
    private val pendingUnloads = mutableListOf<ChunkPos>()
    private var blockMaterials: JsAny? = null
    private var shadersEnabled = true
    private var activeRender: ChunkRender? = null

    // Precomputed per-wireIndex flags to avoid HashMap lookups in the hot meshing path
    private val solidByOrd = ByteArray(256)
    private val liquidByOrd = ByteArray(256)
    private val hasStudsByOrd = ByteArray(256)
    private val isMultiCellByOrd = ByteArray(256)
    // Full-unit-cube shape flag (BlockDefinition.isCubic) — drives neighbor face culling and
    // greedy-merge eligibility. Distinct from solidByOrd (physics-only): a block can be solid
    // (not walkable) but non-cubic (arches, slopes, corners, steps), in which case neighbors
    // must NOT cull faces against it, and it must always emit its own faces.
    private val cubicByOrd = ByteArray(256)
    // Whether a neighbor block fully hides the face touching it: a full cube AND opaque.
    // Transparent cubes (GLASS, WATER) and AIR must not occlude — AIR is cubic by default
    // (no shape), so without the transparent check every face touching air would be culled.
    private val occludesByOrd = ByteArray(256)
    // Eligible for greedy-meshing merge along Z (top/bottom/east/west only — see renderRow):
    // solid, cubic, simple-cube blocks only. Excludes studs/non-cube shapes (cubicByOrd) and
    // brick/sub-voxel blocks (isMultiCellByOrd); non-solid blocks (cross-sprite decorations like
    // FLOWER/WEED) are excluded too since their real geometry isn't a full-cube face.
    private val mergeableByOrd = ByteArray(256)
    private var ordFlagsBuilt = false
    private val strideX = (WorldConstants.WORLD_MAX_Y + 1) * WorldConstants.CHUNK_SIZE

    private fun buildOrdFlags() {
        if (ordFlagsBuilt) return
        for (i in 0 until 256) {
            val bt = BlockRegistry.byWireIndex(i)
            val def = BlockRegistry.get(bt)
            solidByOrd[i] = if (bt.isSolid) 1 else 0
            liquidByOrd[i] = if (bt.isLiquid) 1 else 0
            hasStudsByOrd[i] = if (def.hasStuds) 1 else 0
            cubicByOrd[i] = if (def.isCubic) 1 else 0
            occludesByOrd[i] = if (def.isCubic && !def.transparent) 1 else 0
            isMultiCellByOrd[i] =
                if (def.brickSize.size == 3 &&
                    (def.brickSize[0] > 2 || def.brickSize[1] > 2 || def.brickSize[2] > 2))
                    1
                else 0
            mergeableByOrd[i] =
                if (solidByOrd[i].toInt() != 0 &&
                    cubicByOrd[i].toInt() != 0 &&
                    hasStudsByOrd[i].toInt() == 0 &&
                    isMultiCellByOrd[i].toInt() == 0)
                    1
                else 0
        }
        ordFlagsBuilt = true
    }

    // Flushes an in-progress greedy-merge run for direction slot [idx] (0=east,1=west),
    // emitting a single wide face if len>1 or a normal face if len==1.
    private fun flushMergeRun(
        idx: Int,
        active: BooleanArray,
        startZ: IntArray,
        len: IntArray,
        faceMatArr: IntArray,
        aoArr: IntArray,
        wx: Int,
        y: Int,
        oz: Int,
    ) {
        if (!active[idx]) return
        val wz = oz + startZ[idx]
        if (len[idx] <= 1) {
            jsChunkFaceAppend(wx, y, wz, faceMatArr[idx], aoArr[idx])
        } else {
            jsChunkFaceAppendRunZ(wx, y, wz, faceMatArr[idx], aoArr[idx], len[idx])
        }
        active[idx] = false
    }

    // Flushes an in-progress greedy-merge run along X for south/north faces (see renderRow).
    // One run per Z value (not a single scalar like flushMergeRun) since X is the outer loop —
    // each z's run can only be continued the next time that same z is revisited, one x later.
    private fun flushMergeRunX(
        z: Int,
        active: BooleanArray,
        startX: IntArray,
        len: IntArray,
        faceMatArr: IntArray,
        aoArr: IntArray,
        y: Int,
        ox: Int,
        oz: Int,
    ) {
        if (!active[z]) return
        val wx = ox + startX[z]
        val wz = oz + z
        if (len[z] <= 1) {
            jsChunkFaceAppend(wx, y, wz, faceMatArr[z], aoArr[z])
        } else {
            jsChunkFaceAppendRunX(wx, y, wz, faceMatArr[z], aoArr[z], len[z])
        }
        active[z] = false
    }

    fun setShadersEnabled(enabled: Boolean) {
        shadersEnabled = enabled
        jsSetShadersEnabled(scene, enabled)
    }

    fun getBlockMaterials(): JsAny? {
        if (blockMaterials == null && jsIsBlockDefsReady()) {
            blockMaterials = jsCreateBlockMaterials(scene)
            jsSetShadersEnabled(scene, shadersEnabled)
        }
        return blockMaterials
    }

    fun applyBiomeGrassTint(biome: String) {
        if (blockMaterials != null) jsApplyBiomeGrassTint(biome)
    }

    val pendingRenderCount: Int
        get() = pendingChunks.size + if (activeRender != null) 1 else 0

    private val pendingMinimapPushes = ArrayDeque<Pair<Chunk, Int>>()

    fun enqueueChunk(chunk: Chunk, topY: Int) {
        val pos = chunk.pos
        if (activeRender?.chunk?.pos == pos) {
            // Abort in-progress render; jsChunkBegin on next drain will release the JS buffer
            activeRender = null
        }
        pendingChunks.removeAll { (c, _) -> c.pos == pos }
        pendingChunks.add(0, Pair(chunk, topY)) // front = higher priority
        pendingChunksDirty = true
    }

    fun drainPendingChunks(
        playerCx: Int = 0,
        playerCz: Int = 0,
        yaw: Double = 0.0,
        budgetMs: Double = 4.0,
    ) {
        val mats = getBlockMaterials() ?: return
        buildOrdFlags()
        if (pendingChunks.isEmpty() && activeRender == null) return
        val deadline = jsNow() + budgetMs

        while (true) {
            val ar = activeRender

            if (ar == null) {
                if (jsNow() >= deadline) break
                if (pendingChunks.isEmpty()) break
                if (pendingChunksDirty) {
                    pendingChunks.sortBy { (chunk, _) ->
                        meshScore(chunk.pos.cx - playerCx, chunk.pos.cz - playerCz, yaw)
                    }
                    pendingChunksDirty = false
                }
                val (chunk, topY) = pendingChunks.removeAt(0)
                chunkData[chunk.pos] = Pair(chunk, topY)
                if (useImpostor) {
                    val viewerCx = jsGetActiveCameraChunkX(scene)
                    val viewerCz = jsGetActiveCameraChunkZ(scene)
                    val viewerDist =
                        maxOf(abs(chunk.pos.cx - viewerCx), abs(chunk.pos.cz - viewerCz))
                    if (viewerDist > impostorRadiusChunks) {
                        pushMinimapChunk(chunk, topY)
                        jsBuildChunkImpostor(scene, chunk.pos.cx, chunk.pos.cz)
                        loadedChunks.add(chunk.pos)
                        impostorChunks.add(chunk.pos)
                        continue
                    }
                }
                impostorChunks.remove(chunk.pos)
                jsChunkBegin(chunk.pos.cx, chunk.pos.cz)
                activeRender = ChunkRender(chunk, topY)
                continue
            }

            when {
                ar.faceCount < 0 -> {
                    // Phase 1: scan rows — fill __mcFB via jsChunkFaceAppend
                    if (jsNow() >= deadline) break
                    ar.faces += renderRow(ar.chunk, ar.topY, ar.nextY)
                    ar.nextY++
                    if (ar.nextY > ar.topY) {
                        ar.faces += renderFractionalEntities(ar.chunk)
                        ar.faceCount = jsGetFaceCount()
                    }
                }
                ar.faceProcessingCursor < ar.faceCount -> {
                    // Phase 2: process face buffer in budget slices (geometry only, no GPU)
                    if (jsNow() >= deadline) break
                    val processed = jsChunkProcessFaces(ar.faceProcessingCursor, FACE_SLICE_SIZE)
                    ar.faceProcessingCursor += processed
                }
                else -> {
                    // Phase 3: GPU upload — always execute when face processing is done
                    jsChunkEnd(scene, mats)
                    loadedChunks.add(ar.chunk.pos)
                    pendingMinimapPushes.addLast(Pair(ar.chunk, ar.topY))
                    activeRender = null
                    break // one GPU upload per drain call
                }
            }
        }
    }

    private fun meshScore(dx: Int, dz: Int, yaw: Double): Double {
        val dist = sqrt((dx * dx + dz * dz).toDouble())
        if (dist == 0.0) return 0.0
        if (dist <= sqrt(2.0) + 0.01) return 1000.0 + dist
        val fwdX = sin(yaw)
        val fwdZ = cos(yaw)
        val dot = (dx * fwdX + dz * fwdZ) / dist
        val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * (180.0 / PI)
        val halfR = WorldConstants.CLIENT_VIEW_RADIUS / 2.0
        return when {
            angleDeg < 60.0 -> 1500.0 + dist
            dist <= halfR -> 2000.0 + dist
            dist > halfR -> 3000.0 + dist
            else -> 4000.0 + dist
        }
    }

    fun drainOneMinimapPush() {
        val (chunk, topY) = pendingMinimapPushes.removeFirstOrNull() ?: return
        pushMinimapChunk(chunk, topY)
    }

    fun collectAndClearUnloads(): List<ChunkPos> {
        val result = pendingUnloads.toList()
        pendingUnloads.clear()
        return result
    }

    fun getBlockAtWorld(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy < 0 || wy > WorldConstants.WORLD_MAX_Y) return BlockType.AIR
        val cx = wx.floorDiv(WorldConstants.CHUNK_SIZE)
        val cz = wz.floorDiv(WorldConstants.CHUNK_SIZE)
        val (chunk, _) = chunkData[ChunkPos(cx, cz)] ?: return BlockType.AIR
        val lx = wx - cx * WorldConstants.CHUNK_SIZE
        val lz = wz - cz * WorldConstants.CHUNK_SIZE
        return chunk.getBlock(lx, wy, lz)
    }

    fun getStateAtWorld(wx: Int, wy: Int, wz: Int): Byte {
        if (wy < 0 || wy > WorldConstants.WORLD_MAX_Y) return 0
        val cx = wx.floorDiv(WorldConstants.CHUNK_SIZE)
        val cz = wz.floorDiv(WorldConstants.CHUNK_SIZE)
        val (chunk, _) = chunkData[ChunkPos(cx, cz)] ?: return 0
        val lx = wx - cx * WorldConstants.CHUNK_SIZE
        val lz = wz - cz * WorldConstants.CHUNK_SIZE
        return chunk.getState(lx, wy, lz)
    }

    /**
     * Returns (masterWorldPos, usedSlotCount) if a fractional plate entity covers (wx,wy,wz)
     * (either as master or satellite). Returns null if no fractional entity is at this position.
     */
    fun getFractionalInfoAt(wx: Int, wy: Int, wz: Int): Pair<BlockPos, Int>? {
        if (wy < 0 || wy > WorldConstants.WORLD_MAX_Y) return null
        val cx = wx.floorDiv(WorldConstants.CHUNK_SIZE)
        val cz = wz.floorDiv(WorldConstants.CHUNK_SIZE)
        val (chunk, _) = chunkData[ChunkPos(cx, cz)] ?: return null
        val lx = wx - cx * WorldConstants.CHUNK_SIZE
        val lz = wz - cz * WorldConstants.CHUNK_SIZE
        val idx = Chunk.index(lx, wy, lz)
        val entity = chunk.buildEntitiesMap()[idx]?.firstOrNull() ?: return null
        if (BlockRegistry.get(entity.type).brickSize[1] >= 2.0f) return null
        val (mx, my, mz) = Chunk.indexToXYZ(entity.masterIdx)
        val masterWx = cx * WorldConstants.CHUNK_SIZE + mx
        val masterWz = cz * WorldConstants.CHUNK_SIZE + mz
        val usedCount =
            chunk.entityMasters.count {
                it.masterIdx == entity.masterIdx && BlockRegistry.get(it.type).brickSize[1] < 2.0f
            }
        return Pair(BlockPos(masterWx, my, masterWz), usedCount)
    }

    /**
     * True if this cell or one of its 4 orthogonal XZ neighbors (same Y) already hosts an
     * XZ-fractional entity — mirrors
     * [org.micoli.micraft.game.world.WorldState.hasMisalignedNeighbor] server-side so the placement
     * ghost forces the same fine-grid snap the server will validate.
     */
    fun hasMisalignedNeighborAt(wx: Int, wy: Int, wz: Int): Boolean =
        listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dz) ->
            cellHasXZFractionalEntity(wx + dx, wy, wz + dz)
        }

    private fun cellHasXZFractionalEntity(wx: Int, wy: Int, wz: Int): Boolean {
        if (wy < 0 || wy > WorldConstants.WORLD_MAX_Y) return false
        val cx = wx.floorDiv(WorldConstants.CHUNK_SIZE)
        val cz = wz.floorDiv(WorldConstants.CHUNK_SIZE)
        val (chunk, _) = chunkData[ChunkPos(cx, cz)] ?: return false
        val lx = wx - cx * WorldConstants.CHUNK_SIZE
        val lz = wz - cz * WorldConstants.CHUNK_SIZE
        val masterIdx = Chunk.index(lx, wy, lz)
        return chunk.entityMasters.any {
            it.masterIdx == masterIdx &&
                (it.xOffset > 0 ||
                    it.zOffset > 0 ||
                    BlockRegistry.get(it.type).brickSize[0] < 2.0f ||
                    BlockRegistry.get(it.type).brickSize[2] < 2.0f)
        }
    }

    // Synchronous full re-render (WorldUpdate block changes) — old mesh stays until done
    fun renderChunk(chunk: Chunk, topY: Int) {
        val mats =
            getBlockMaterials()
                ?: run {
                    pendingChunks.add(Pair(chunk, topY))
                    return
                }
        buildOrdFlags()
        chunkData[chunk.pos] = Pair(chunk, topY)
        jsChunkBegin(chunk.pos.cx, chunk.pos.cz)
        for (y in 0..topY) renderRow(chunk, topY, y)
        renderFractionalEntities(chunk)
        // Drain face buffer before GPU upload (same as async Phase 2)
        val faceCount = jsGetFaceCount()
        var cursor = 0
        while (cursor < faceCount) cursor += jsChunkProcessFaces(cursor, FACE_SLICE_SIZE)
        jsChunkEnd(scene, mats)
        loadedChunks.add(chunk.pos)
        pushMinimapChunk(chunk, topY)
    }

    // Update chunkData immediately (for physics/raycast) and defer mesh rebuild
    fun repushAllToMinimap() {
        for ((_, pair) in chunkData) {
            val (chunk, topY) = pair
            pushMinimapChunk(chunk, topY)
        }
    }

    fun updateAndEnqueue(chunk: Chunk, topY: Int) {
        chunkData[chunk.pos] = Pair(chunk, topY)
        enqueueChunk(chunk, topY)
    }

    fun unloadDistantChunks(playerCx: Int, playerCz: Int) {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        val toUnload =
            loadedChunks.filter { cp ->
                kotlin.math.abs(cp.cx - playerCx) > r || kotlin.math.abs(cp.cz - playerCz) > r
            }
        if (toUnload.isEmpty()) return
        toUnload.forEach { cp ->
            jsDisposeChunk("${cp.cx},${cp.cz}")
            jsClearMinimapChunk(cp.cx, cp.cz)
            loadedChunks.remove(cp)
            chunkData.remove(cp)
            impostorChunks.remove(cp)
        }
        pendingUnloads.addAll(toUnload)
    }

    // Re-enqueues impostor-meshed chunks that are now within IMPOSTOR_RADIUS_CHUNKS of the
    // viewer for a full-detail re-render — the impostor/full choice is otherwise frozen at
    // first mesh (see USE_IMPOSTOR comment), so without this, chunks meshed as impostors while
    // far away stay impostors even after the player walks back into their radius.
    fun upgradeNearImpostors(playerCx: Int, playerCz: Int) {
        if (!useImpostor || impostorChunks.isEmpty()) return
        val toUpgrade =
            impostorChunks.filter { cp ->
                maxOf(abs(cp.cx - playerCx), abs(cp.cz - playerCz)) <= impostorRadiusChunks
            }
        toUpgrade.forEach { cp ->
            val (chunk, topY) = chunkData[cp] ?: return@forEach
            enqueueChunk(chunk, topY)
        }
    }

    fun isDownloaded(pos: ChunkPos): Boolean =
        loadedChunks.contains(pos) ||
            pendingChunks.any { (c, _) -> c.pos == pos } ||
            activeRender?.chunk?.pos == pos

    fun allFovChunksMeshed(playerCx: Int, playerCz: Int, yaw: Double): Boolean {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        val fwdX = sin(yaw)
        val fwdZ = cos(yaw)
        for (dx in -r..r) {
            for (dz in -r..r) {
                if (dx == 0 && dz == 0) continue
                val dist = sqrt((dx * dx + dz * dz).toDouble())
                val dot = (dx * fwdX + dz * fwdZ) / dist
                val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * (180.0 / PI)
                if (angleDeg >= 60.0) continue
                if (!loadedChunks.contains(ChunkPos(playerCx + dx, playerCz + dz))) return false
            }
        }
        return true
    }

    fun allNearFovChunksMeshed(playerCx: Int, playerCz: Int, yaw: Double): Boolean {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        val halfR = r / 2.0
        val fwdX = sin(yaw)
        val fwdZ = cos(yaw)
        for (dx in -r..r) {
            for (dz in -r..r) {
                if (dx == 0 && dz == 0) continue
                val dist = sqrt((dx * dx + dz * dz).toDouble())
                if (dist > halfR) continue
                val dot = (dx * fwdX + dz * fwdZ) / dist
                val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * (180.0 / PI)
                if (angleDeg >= 60.0) continue
                if (!loadedChunks.contains(ChunkPos(playerCx + dx, playerCz + dz))) return false
            }
        }
        return true
    }

    fun clear() {
        loadedChunks.forEach { cp -> jsDisposeChunk("${cp.cx},${cp.cz}") }
        loadedChunks.clear()
        impostorChunks.clear()
        chunkData.clear()
        pendingChunks.clear()
        pendingMinimapPushes.clear()
        activeRender = null
    }

    fun getChunkDebugJson(playerCx: Int, playerCz: Int, radius: Int, playerYaw: Double): String {
        val pendingSet = pendingChunks.map { (c, _) -> c.pos }.toSet()
        val activePos = activeRender?.chunk?.pos
        val sb = StringBuilder()
        sb.append(
            "{\"playerCx\":$playerCx,\"playerCz\":$playerCz,\"radius\":$radius,\"playerYaw\":$playerYaw,\"chunks\":[")
        var first = true
        for (dz in -radius..radius) {
            for (dx in -radius..radius) {
                val cx = playerCx + dx
                val cz = playerCz + dz
                val pos = ChunkPos(cx, cz)
                val state =
                    when {
                        loadedChunks.contains(pos) -> "loaded"
                        pos == activePos || pendingSet.contains(pos) -> "loading"
                        else -> "missing"
                    }
                if (!first) sb.append(",")
                sb.append("{\"cx\":$cx,\"cz\":$cz,\"state\":\"$state\"}")
                first = false
            }
        }
        sb.append("]}")
        return sb.toString()
    }

    // Returns number of faces emitted (appended to window.__mcFB via jsChunkFaceAppend)
    private fun renderRow(chunk: Chunk, topY: Int, y: Int): Int {
        val blocks = chunk.blocks
        val states = chunk.states
        val hasStates = states.isNotEmpty()
        val s = WorldConstants.CHUNK_SIZE
        val ox = chunk.pos.cx * s
        val oz = chunk.pos.cz * s
        var faceCount = 0
        val entityMap = chunk.buildEntitiesMap()
        // Greedy-merge state for east/west faces (0=east,1=west), run along Z. Top/bottom get
        // a full 2D rectangle merge instead (see topActive/botActive below) since both their
        // plane axes (X and Z) are available within this single row; south/north merge along X
        // only (see mergeActiveS/N) since their other axis (Y) crosses rows.
        val mergeActive = BooleanArray(2)
        val mergeStartZ = IntArray(2)
        val mergeLen = IntArray(2)
        val mergeFaceMat = IntArray(2)
        val mergeAo = IntArray(2)
        // Greedy-merge state for south/north faces, run along X — one slot per Z (see
        // flushMergeRunX) since X is the outer loop, unlike the two arrays above.
        val mergeActiveS = BooleanArray(s)
        val mergeStartXS = IntArray(s)
        val mergeLenS = IntArray(s)
        val mergeFaceMatS = IntArray(s)
        val mergeAoS = IntArray(s)
        val mergeActiveN = BooleanArray(s)
        val mergeStartXN = IntArray(s)
        val mergeLenN = IntArray(s)
        val mergeFaceMatN = IntArray(s)
        val mergeAoN = IntArray(s)
        // Top/bottom masks (x*s+z) for a proper 2D greedy rectangle merge — filled while
        // scanning below, consumed by emitGreedyRects() after the whole row has been visited.
        val topActive = BooleanArray(s * s)
        val topFaceMat = IntArray(s * s)
        val topAo = IntArray(s * s)
        val botActive = BooleanArray(s * s)
        val botFaceMat = IntArray(s * s)
        val botAo = IntArray(s * s)
        for (x in 0 until s) {
            val wx = ox + x
            for (z in 0 until s) {
                // Direct ByteArray access — no getBlock(), no registry lookup
                val idx = x * strideX + y * s + z
                val ord = blocks[idx].toInt() and 0xFF
                if (ord == 0) continue // AIR = wire index 0

                // Several fractional entities (XZ sub-slots / Y stacks) can share one voxel, so
                // this is a list, not a single entity — e.g. multiple LEGO_PIECE in one cell.
                val entitiesHere = entityMap[idx]
                // Multi-cell entity satellite: skip (master emits geometry for whole extent)
                val entity = entitiesHere?.firstOrNull { it.masterIdx == idx }
                if (entitiesHere != null && entity == null) continue
                // Fractional-offset master: skip here, emitted by renderFractionalEntities —
                // unless another entity sharing this voxel has zero offset and still needs the
                // base-array (this) render path.
                if (entity != null && (entity.xOffset > 0 || entity.zOffset > 0)) {
                    val zeroOffsetEntity =
                        entitiesHere.firstOrNull {
                            it.masterIdx == idx && it.xOffset == 0 && it.zOffset == 0
                        }
                    if (zeroOffsetEntity == null) continue
                }

                // state byte: bits 0-1 rotation, bits 2-7 plain color index
                // faceMat = (ord * 4 + rotation) * 6 + fd; color rides in ao bits 18-23
                val state = if (hasStates) states[idx] else 0
                val rotation = BlockState.rotation(state)
                val colorBits = BlockState.colorIndex(state) shl COLOR_SHIFT
                val t = (ord * 4 + rotation) * 6
                val wz2 = oz + z

                val isMaster = entity != null // entity master → bypass culling
                val bypassCulling = isMaster || cubicByOrd[ord].toInt() == 0
                val mergeEligible = mergeableByOrd[ord].toInt() != 0 && !bypassCulling

                // top (+Y): use solidByOrd + liquidByOrd to skip redundant BlockType creation
                val aboveOrd =
                    if (y >= WorldConstants.WORLD_MAX_Y) 0 else blocks[idx + s].toInt() and 0xFF
                val liquid = liquidByOrd[ord].toInt() != 0
                val liquidAbove = liquidByOrd[aboveOrd].toInt() != 0
                val emitTop =
                    bypassCulling ||
                        (occludesByOrd[aboveOrd].toInt() == 0 && !(liquid && liquidAbove))
                if (emitTop) {
                    val faceMatV = t + 4
                    val aoV = computeFaceAO(blocks, x, y, z, 4) or colorBits
                    if (mergeEligible) {
                        val mi = x * s + z
                        topActive[mi] = true
                        topFaceMat[mi] = faceMatV
                        topAo[mi] = aoV
                    } else {
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                }
                // bottom (-Y)
                val emitBottom =
                    bypassCulling ||
                        y <= 0 ||
                        occludesByOrd[blocks[idx - s].toInt() and 0xFF].toInt() == 0
                if (emitBottom) {
                    val faceMatV = t + 5
                    val aoV = computeFaceAO(blocks, x, y, z, 5) or colorBits
                    if (mergeEligible) {
                        val mi = x * s + z
                        botActive[mi] = true
                        botFaceMat[mi] = faceMatV
                        botAo[mi] = aoV
                    } else {
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                }
                // south (+Z) — normal is ±Z, so not mergeable along Z (that's this face's own
                // fixed depth), but mergeable along X: consecutive x at the same z/y form one
                // contiguous wall segment. Run tracked per-z since x is the outer loop.
                val emitSouth =
                    bypassCulling ||
                        z == s - 1 ||
                        occludesByOrd[blocks[idx + 1].toInt() and 0xFF].toInt() == 0
                if (emitSouth) {
                    val faceMatV = t + 0
                    val aoV = computeFaceAO(blocks, x, y, z, 0) or colorBits
                    if (mergeEligible) {
                        if (mergeActiveS[z] &&
                            mergeFaceMatS[z] == faceMatV &&
                            mergeAoS[z] == aoV &&
                            mergeStartXS[z] + mergeLenS[z] == x) {
                            mergeLenS[z]++
                        } else {
                            flushMergeRunX(
                                z,
                                mergeActiveS,
                                mergeStartXS,
                                mergeLenS,
                                mergeFaceMatS,
                                mergeAoS,
                                y,
                                ox,
                                oz)
                            mergeActiveS[z] = true
                            mergeStartXS[z] = x
                            mergeLenS[z] = 1
                            mergeFaceMatS[z] = faceMatV
                            mergeAoS[z] = aoV
                        }
                    } else {
                        flushMergeRunX(
                            z,
                            mergeActiveS,
                            mergeStartXS,
                            mergeLenS,
                            mergeFaceMatS,
                            mergeAoS,
                            y,
                            ox,
                            oz)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRunX(
                        z,
                        mergeActiveS,
                        mergeStartXS,
                        mergeLenS,
                        mergeFaceMatS,
                        mergeAoS,
                        y,
                        ox,
                        oz)
                }
                // north (-Z)
                val emitNorth =
                    bypassCulling ||
                        z == 0 ||
                        occludesByOrd[blocks[idx - 1].toInt() and 0xFF].toInt() == 0
                if (emitNorth) {
                    val faceMatV = t + 1
                    val aoV = computeFaceAO(blocks, x, y, z, 1) or colorBits
                    if (mergeEligible) {
                        if (mergeActiveN[z] &&
                            mergeFaceMatN[z] == faceMatV &&
                            mergeAoN[z] == aoV &&
                            mergeStartXN[z] + mergeLenN[z] == x) {
                            mergeLenN[z]++
                        } else {
                            flushMergeRunX(
                                z,
                                mergeActiveN,
                                mergeStartXN,
                                mergeLenN,
                                mergeFaceMatN,
                                mergeAoN,
                                y,
                                ox,
                                oz)
                            mergeActiveN[z] = true
                            mergeStartXN[z] = x
                            mergeLenN[z] = 1
                            mergeFaceMatN[z] = faceMatV
                            mergeAoN[z] = aoV
                        }
                    } else {
                        flushMergeRunX(
                            z,
                            mergeActiveN,
                            mergeStartXN,
                            mergeLenN,
                            mergeFaceMatN,
                            mergeAoN,
                            y,
                            ox,
                            oz)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRunX(
                        z,
                        mergeActiveN,
                        mergeStartXN,
                        mergeLenN,
                        mergeFaceMatN,
                        mergeAoN,
                        y,
                        ox,
                        oz)
                }
                // east (+X)
                val emitEast =
                    bypassCulling ||
                        x == s - 1 ||
                        occludesByOrd[blocks[idx + strideX].toInt() and 0xFF].toInt() == 0
                if (emitEast) {
                    val faceMatV = t + 2
                    val aoV = computeFaceAO(blocks, x, y, z, 2) or colorBits
                    if (mergeEligible) {
                        if (mergeActive[0] &&
                            mergeFaceMat[0] == faceMatV &&
                            mergeAo[0] == aoV &&
                            mergeStartZ[0] + mergeLen[0] == z) {
                            mergeLen[0]++
                        } else {
                            flushMergeRun(
                                0,
                                mergeActive,
                                mergeStartZ,
                                mergeLen,
                                mergeFaceMat,
                                mergeAo,
                                wx,
                                y,
                                oz)
                            mergeActive[0] = true
                            mergeStartZ[0] = z
                            mergeLen[0] = 1
                            mergeFaceMat[0] = faceMatV
                            mergeAo[0] = aoV
                        }
                    } else {
                        flushMergeRun(
                            0, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y, oz)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRun(
                        0, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y, oz)
                }
                // west (-X)
                val emitWest =
                    bypassCulling ||
                        x == 0 ||
                        occludesByOrd[blocks[idx - strideX].toInt() and 0xFF].toInt() == 0
                if (emitWest) {
                    val faceMatV = t + 3
                    val aoV = computeFaceAO(blocks, x, y, z, 3) or colorBits
                    if (mergeEligible) {
                        if (mergeActive[1] &&
                            mergeFaceMat[1] == faceMatV &&
                            mergeAo[1] == aoV &&
                            mergeStartZ[1] + mergeLen[1] == z) {
                            mergeLen[1]++
                        } else {
                            flushMergeRun(
                                1,
                                mergeActive,
                                mergeStartZ,
                                mergeLen,
                                mergeFaceMat,
                                mergeAo,
                                wx,
                                y,
                                oz)
                            mergeActive[1] = true
                            mergeStartZ[1] = z
                            mergeLen[1] = 1
                            mergeFaceMat[1] = faceMatV
                            mergeAo[1] = aoV
                        }
                    } else {
                        flushMergeRun(
                            1, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y, oz)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRun(
                        1, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y, oz)
                }
            }
            // Flush any east/west runs still open at the end of this X column (z reached s-1).
            flushMergeRun(0, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y, oz)
            flushMergeRun(1, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y, oz)
        }
        // Flush any south/north X-runs still open once the whole row (all X columns) is done.
        for (z in 0 until s) {
            flushMergeRunX(
                z, mergeActiveS, mergeStartXS, mergeLenS, mergeFaceMatS, mergeAoS, y, ox, oz)
            flushMergeRunX(
                z, mergeActiveN, mergeStartXN, mergeLenN, mergeFaceMatN, mergeAoN, y, ox, oz)
        }
        // 2D greedy-rectangle merge for top/bottom, now that the whole row's mask is filled.
        emitGreedyRects(topActive, topFaceMat, topAo, s, y, ox, oz)
        emitGreedyRects(botActive, botFaceMat, botAo, s, y, ox, oz)
        return faceCount
    }

    // Standard greedy-mesher sweep over a per-row (x,z) mask: for each unvisited active cell,
    // grow a rectangle as wide as possible along X, then as tall as possible along Z (only where
    // every cell in the new row matches the same faceMat/ao), and emit one quad per rectangle.
    // Used for top/bottom faces only — the one face pair whose whole plane (X and Z) is available
    // within a single renderRow(y) call, unlike east/west/south/north whose second axis is Y.
    private fun emitGreedyRects(
        active: BooleanArray,
        faceMatArr: IntArray,
        aoArr: IntArray,
        s: Int,
        y: Int,
        ox: Int,
        oz: Int,
    ) {
        val visited = BooleanArray(s * s)
        for (z in 0 until s) {
            for (x in 0 until s) {
                val i = x * s + z
                if (!active[i] || visited[i]) continue
                val fm = faceMatArr[i]
                val ao = aoArr[i]
                var w = 1
                while (x + w < s) {
                    val j = (x + w) * s + z
                    if (!active[j] || visited[j] || faceMatArr[j] != fm || aoArr[j] != ao) break
                    w++
                }
                var h = 1
                rows@ while (z + h < s) {
                    for (dx in 0 until w) {
                        val j = (x + dx) * s + (z + h)
                        if (!active[j] || visited[j] || faceMatArr[j] != fm || aoArr[j] != ao) {
                            break@rows
                        }
                    }
                    h++
                }
                for (dz in 0 until h) {
                    for (dx in 0 until w) {
                        visited[(x + dx) * s + (z + dz)] = true
                    }
                }
                val wx = ox + x
                val wz = oz + z
                if (w == 1 && h == 1) {
                    jsChunkFaceAppend(wx, y, wz, fm, ao)
                } else {
                    jsChunkFaceAppendRun2D(wx, y, wz, fm, ao, w, h)
                }
            }
        }
    }

    // Second-pass render: emit faces for fractional entities that need sub-voxel offsets.
    // Handles Y-fractional plates (yOffset > 0) and XZ-fractional arches (xOffset/zOffset > 0).
    private fun renderFractionalEntities(chunk: Chunk): Int {
        val s = WorldConstants.CHUNK_SIZE
        val ox = chunk.pos.cx * s
        val oz = chunk.pos.cz * s
        var faceCount = 0
        for (entity in chunk.entityMasters) {
            val hasYOffset = entity.yOffset > 0
            val hasXZOffset = entity.xOffset > 0 || entity.zOffset > 0
            if (!hasYOffset && !hasXZOffset) continue
            val (mx, my, mz) = Chunk.indexToXYZ(entity.masterIdx)
            val ord = BlockRegistry.wireIndex(entity.type)
            if (ord == 0) continue
            val t = (ord * 4 + entity.rotation) * 6
            val colorBits = entity.colorIndex shl COLOR_SHIFT
            val wx = ox + mx
            val wz2 = oz + mz
            for (fd in 0..5) {
                if (hasXZOffset || hasYOffset) {
                    jsChunkFaceAppendXZOffset(
                        wx,
                        my,
                        wz2,
                        entity.yOffset,
                        entity.xOffset,
                        entity.zOffset,
                        t + fd,
                        colorBits)
                } else {
                    jsChunkFaceAppendYOffset(wx, my, wz2, entity.yOffset, t + fd, colorBits)
                }
                faceCount++
            }
        }
        return faceCount
    }

    private fun pushMinimapChunk(chunk: Chunk, topY: Int) {
        val topYParts = IntArray(WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE)
        val topBlockParts = IntArray(WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE)
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                val idx = lx * WorldConstants.CHUNK_SIZE + lz
                for (y in topY downTo 0) {
                    val block = chunk.getBlock(lx, y, lz)
                    if (block == BlockType.AIR) continue
                    val def = BlockRegistry.get(block)
                    if (!def.solid && !def.liquid && !def.minimapVisible) continue
                    topYParts[idx] = y
                    topBlockParts[idx] = BlockRegistry.wireIndex(block)
                    break
                }
            }
        }
        jsSetMinimapChunk(
            chunk.pos.cx,
            chunk.pos.cz,
            "[${topYParts.joinToString(",")}]",
            "[${topBlockParts.joinToString(",")}]",
        )
    }

    // Takes raw blocks ByteArray — avoids getBlock() + registry HashMap lookups per neighbor
    private fun computeFaceAO(blocks: ByteArray, lx: Int, ly: Int, lz: Int, fd: Int): Int {
        val nbrs = AO_NEIGHBORS[fd]
        val s = WorldConstants.CHUNK_SIZE
        var packed = 0
        for (v in 0..3) {
            var solid = 0
            for (n in 0..2) {
                val off = nbrs[v][n]
                val nx = lx + off[0]
                val ny = ly + off[1]
                val nz = lz + off[2]
                if (nx < 0 || nx >= s || nz < 0 || nz >= s) continue
                if (ny < 0 || ny > WorldConstants.WORLD_MAX_Y) continue
                val nIdx = nx * strideX + ny * s + nz
                if (solidByOrd[blocks[nIdx].toInt() and 0xFF].toInt() != 0) solid++
            }
            packed = packed or ((solid * 5).coerceAtMost(15) shl (v * 4))
        }
        return packed
    }
}
