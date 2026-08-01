package org.micoli.micraft

import kotlin.math.*
import org.micoli.micraft.babylon.*
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
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
    val loadedChunks = mutableSetOf<ChunkPos>()
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
            isMultiCellByOrd[i] =
                if (def.brickSize.size == 3 &&
                    (def.brickSize[0] > 1 || def.brickSize[1] > 1 || def.brickSize[2] > 1))
                    1
                else 0
        }
        ordFlagsBuilt = true
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
        val entity = chunk.buildEntitiesMap()[idx] ?: return null
        if (BlockRegistry.get(entity.type).heightFraction >= 1.0f) return null
        val (mx, my, mz) = Chunk.indexToXYZ(entity.masterIdx)
        val masterWx = cx * WorldConstants.CHUNK_SIZE + mx
        val masterWz = cz * WorldConstants.CHUNK_SIZE + mz
        val usedCount =
            chunk.entityMasters.count {
                it.masterIdx == entity.masterIdx && BlockRegistry.get(it.type).heightFraction < 1.0f
            }
        return Pair(BlockPos(masterWx, my, masterWz), usedCount)
    }

    /**
     * Computes the effective placement position for a fractional plate, mirroring server redirect:
     * (a) pos covers a fractional satellite/master with free slots → master (b) pos is AIR and y-1
     * has a fractional plate with free slots → that plate's master
     */
    fun resolveFractionalPlacementPos(pos: BlockPos): BlockPos {
        val directInfo = getFractionalInfoAt(pos.x, pos.y, pos.z)
        if (directInfo != null && directInfo.second < 3) return directInfo.first
        if (getBlockAtWorld(pos.x, pos.y, pos.z) == BlockType.AIR && pos.y > 0) {
            val belowY = pos.y - 1
            val belowInfo = getFractionalInfoAt(pos.x, belowY, pos.z)
            if (belowInfo != null && belowInfo.second < 3) return belowInfo.first
        }
        return pos
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
        }
        pendingUnloads.addAll(toUnload)
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
        for (x in 0 until s) {
            for (z in 0 until s) {
                // Direct ByteArray access — no getBlock(), no registry lookup
                val idx = x * strideX + y * s + z
                val ord = blocks[idx].toInt() and 0xFF
                if (ord == 0) continue // AIR = wire index 0

                // Multi-cell entity satellite: skip (master emits geometry for whole extent)
                val entity = entityMap[idx]
                if (entity != null && entity.masterIdx != idx) continue

                // rotation: bits 0-1 of state byte; faceMat = (ord * 4 + rotation) * 6 + fd
                val rotation = if (hasStates) states[idx].toInt() and 0x03 else 0
                val t = (ord * 4 + rotation) * 6
                val wx = ox + x
                val wz2 = oz + z

                val isMaster = entity != null // entity master → bypass culling
                val bypassCulling = isMaster

                // top (+Y): use solidByOrd + liquidByOrd to skip redundant BlockType creation
                val aboveOrd =
                    if (y >= WorldConstants.WORLD_MAX_Y) 0 else blocks[idx + s].toInt() and 0xFF
                val liquid = liquidByOrd[ord].toInt() != 0
                val liquidAbove = liquidByOrd[aboveOrd].toInt() != 0
                // Blocks with studs or slopes: always emit top face
                val emitTop =
                    bypassCulling ||
                        hasStudsByOrd[ord].toInt() != 0 ||
                        (solidByOrd[aboveOrd].toInt() == 0 && !(liquid && liquidAbove))
                if (emitTop) {
                    jsChunkFaceAppend(wx, y, wz2, t + 4, computeFaceAO(blocks, x, y, z, 4))
                    faceCount++
                }
                // bottom (-Y)
                if (bypassCulling ||
                    y <= 0 ||
                    solidByOrd[blocks[idx - s].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFaceAppend(wx, y, wz2, t + 5, computeFaceAO(blocks, x, y, z, 5))
                    faceCount++
                }
                // south (+Z)
                if (bypassCulling ||
                    z == s - 1 ||
                    solidByOrd[blocks[idx + 1].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFaceAppend(wx, y, wz2, t + 0, computeFaceAO(blocks, x, y, z, 0))
                    faceCount++
                }
                // north (-Z)
                if (bypassCulling ||
                    z == 0 ||
                    solidByOrd[blocks[idx - 1].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFaceAppend(wx, y, wz2, t + 1, computeFaceAO(blocks, x, y, z, 1))
                    faceCount++
                }
                // east (+X)
                if (bypassCulling ||
                    x == s - 1 ||
                    solidByOrd[blocks[idx + strideX].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFaceAppend(wx, y, wz2, t + 2, computeFaceAO(blocks, x, y, z, 2))
                    faceCount++
                }
                // west (-X)
                if (bypassCulling ||
                    x == 0 ||
                    solidByOrd[blocks[idx - strideX].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFaceAppend(wx, y, wz2, t + 3, computeFaceAO(blocks, x, y, z, 3))
                    faceCount++
                }
            }
        }
        return faceCount
    }

    // Second-pass render: emit faces for fractional entities (yOffset > 0) that have no block type.
    private fun renderFractionalEntities(chunk: Chunk): Int {
        val s = WorldConstants.CHUNK_SIZE
        val ox = chunk.pos.cx * s
        val oz = chunk.pos.cz * s
        var faceCount = 0
        for (entity in chunk.entityMasters) {
            if (entity.yOffset == 0) continue
            val (mx, my, mz) = Chunk.indexToXYZ(entity.masterIdx)
            val ord = BlockRegistry.wireIndex(entity.type)
            if (ord == 0) continue
            val t = (ord * 4 + entity.rotation) * 6
            val wx = ox + mx
            val wz2 = oz + mz
            for (fd in 0..5) {
                jsChunkFaceAppendYOffset(wx, my, wz2, entity.yOffset, t + fd, 0)
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
