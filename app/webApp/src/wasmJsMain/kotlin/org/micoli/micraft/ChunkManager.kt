package org.micoli.micraft

import kotlin.math.*
import org.micoli.micraft.babylon.*
import org.micoli.micraft.world.*

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

private data class ChunkRender(
    val chunk: Chunk,
    val topY: Int,
    var nextY: Int = 0,
    val t0: Double = jsNow(),
    var faces: Int = 0
)

class ChunkManager(private val scene: JsAny) {
    val loadedChunks = mutableSetOf<ChunkPos>()
    val chunkData = mutableMapOf<ChunkPos, Pair<Chunk, Int>>()
    private val pendingChunks = mutableListOf<Pair<Chunk, Int>>()
    private val pendingUnloads = mutableListOf<ChunkPos>()
    private var blockMaterials: JsAny? = null
    private var shadersEnabled = true
    private var activeRender: ChunkRender? = null

    // Precomputed per-wireIndex flags to avoid HashMap lookups in the hot meshing path
    private val solidByOrd = ByteArray(256)
    private val liquidByOrd = ByteArray(256)
    private var ordFlagsBuilt = false
    private val strideX = (WorldConstants.WORLD_MAX_Y + 1) * WorldConstants.CHUNK_SIZE

    private fun buildOrdFlags() {
        if (ordFlagsBuilt) return
        for (i in 0 until 256) {
            val bt = BlockRegistry.byWireIndex(i)
            solidByOrd[i] = if (bt.isSolid) 1 else 0
            liquidByOrd[i] = if (bt.isLiquid) 1 else 0
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
        if (pendingChunks.isNotEmpty()) {
            pendingChunks.sortBy { (chunk, _) ->
                meshScore(chunk.pos.cx - playerCx, chunk.pos.cz - playerCz, yaw)
            }
        }
        val deadline = jsNow() + budgetMs

        while (jsNow() < deadline) {
            if (activeRender == null) {
                if (pendingChunks.isEmpty()) break
                val (chunk, topY) = pendingChunks.removeAt(0)
                chunkData[chunk.pos] = Pair(chunk, topY)
                // mcChunkBegin resets __mcBuf and releases any orphaned pool groups
                jsChunkBegin(chunk.pos.cx, chunk.pos.cz)
                activeRender = ChunkRender(chunk, topY)
            }

            val ar = activeRender!!
            ar.faces += renderRow(ar.chunk, ar.topY, ar.nextY)
            ar.nextY++

            if (ar.nextY > ar.topY) {
                jsChunkEnd(scene, mats)
                val elapsed = jsNow() - ar.t0
                jsLog(
                    "[mesh] ${ar.chunk.pos.cx},${ar.chunk.pos.cz} rows=${ar.topY + 1} ms=${elapsed.toInt()} faces=${ar.faces} ffiCalls=${ar.faces}")
                loadedChunks.add(ar.chunk.pos)
                pendingMinimapPushes.addLast(Pair(ar.chunk, ar.topY))
                activeRender = null
            }
            // Budget check at top of loop — never blocks more than one slice duration
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
        jsChunkEnd(scene, mats)
        loadedChunks.add(chunk.pos)
        pushMinimapChunk(chunk, topY)
    }

    // Update chunkData immediately (for physics/raycast) and defer mesh rebuild
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

    // Returns number of faces emitted (= FFI calls to jsChunkFace)
    private fun renderRow(chunk: Chunk, topY: Int, y: Int): Int {
        val blocks = chunk.blocks
        val s = WorldConstants.CHUNK_SIZE
        val ox = chunk.pos.cx * s
        val oz = chunk.pos.cz * s
        var faceCount = 0
        for (x in 0 until s) {
            for (z in 0 until s) {
                // Direct ByteArray access — no getBlock(), no registry lookup
                val idx = x * strideX + y * s + z
                val ord = blocks[idx].toInt() and 0xFF
                if (ord == 0) continue // AIR = wire index 0
                val t = ord * 6
                val wx = ox + x
                val wz2 = oz + z

                // top (+Y): use solidByOrd + liquidByOrd to skip redundant BlockType creation
                val aboveOrd =
                    if (y >= WorldConstants.WORLD_MAX_Y) 0 else blocks[idx + s].toInt() and 0xFF
                if (solidByOrd[aboveOrd].toInt() == 0 &&
                    !(liquidByOrd[ord].toInt() != 0 && liquidByOrd[aboveOrd].toInt() != 0)) {
                    jsChunkFace(wx, y, wz2, t + 4, computeFaceAO(blocks, x, y, z, 4))
                    faceCount++
                }
                // bottom (-Y)
                if (y <= 0 || solidByOrd[blocks[idx - s].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFace(wx, y, wz2, t + 5, computeFaceAO(blocks, x, y, z, 5))
                    faceCount++
                }
                // south (+Z)
                if (z == s - 1 || solidByOrd[blocks[idx + 1].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFace(wx, y, wz2, t + 0, computeFaceAO(blocks, x, y, z, 0))
                    faceCount++
                }
                // north (-Z)
                if (z == 0 || solidByOrd[blocks[idx - 1].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFace(wx, y, wz2, t + 1, computeFaceAO(blocks, x, y, z, 1))
                    faceCount++
                }
                // east (+X)
                if (x == s - 1 || solidByOrd[blocks[idx + strideX].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFace(wx, y, wz2, t + 2, computeFaceAO(blocks, x, y, z, 2))
                    faceCount++
                }
                // west (-X)
                if (x == 0 || solidByOrd[blocks[idx - strideX].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFace(wx, y, wz2, t + 3, computeFaceAO(blocks, x, y, z, 3))
                    faceCount++
                }
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
                    if (block != BlockType.AIR) {
                        topYParts[idx] = y
                        topBlockParts[idx] = BlockRegistry.wireIndex(block)
                        break
                    }
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
