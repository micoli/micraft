package org.micoli.micraft

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

private const val SLICE_HEIGHT = 32

private data class ChunkRender(val chunk: Chunk, val topY: Int, var nextY: Int = 0)

class ChunkManager(private val scene: JsAny) {
    val loadedChunks = mutableSetOf<ChunkPos>()
    val chunkData = mutableMapOf<ChunkPos, Pair<Chunk, Int>>()
    private val pendingChunks = mutableListOf<Pair<Chunk, Int>>()
    private val pendingUnloads = mutableListOf<ChunkPos>()
    private var blockMaterials: JsAny? = null
    private var shadersEnabled = true
    private var activeRender: ChunkRender? = null

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

    fun enqueueChunk(chunk: Chunk, topY: Int) {
        val pos = chunk.pos
        if (activeRender?.chunk?.pos == pos) {
            // Abort in-progress render; jsChunkBegin on next drain will release the JS buffer
            activeRender = null
        }
        pendingChunks.removeAll { (c, _) -> c.pos == pos }
        pendingChunks.add(0, Pair(chunk, topY)) // front = higher priority
    }

    fun drainPendingChunks(budgetMs: Double = 4.0) {
        val mats = getBlockMaterials() ?: return
        if (pendingChunks.isEmpty() && activeRender == null) return
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
            val fromY = ar.nextY
            val toY = minOf(fromY + SLICE_HEIGHT - 1, ar.topY)
            renderSlice(ar.chunk, ar.topY, fromY, toY)
            ar.nextY = toY + 1

            if (ar.nextY > ar.topY) {
                jsChunkEnd(scene, mats)
                loadedChunks.add(ar.chunk.pos)
                pushMinimapChunk(ar.chunk, ar.topY)
                activeRender = null
            }
            // Budget check at top of loop — never blocks more than one slice duration
        }
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
        chunkData[chunk.pos] = Pair(chunk, topY)
        jsChunkBegin(chunk.pos.cx, chunk.pos.cz)
        renderSlice(chunk, topY, 0, topY)
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

    fun clear() {
        loadedChunks.forEach { cp -> jsDisposeChunk("${cp.cx},${cp.cz}") }
        loadedChunks.clear()
        chunkData.clear()
        pendingChunks.clear()
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

    private fun renderSlice(chunk: Chunk, topY: Int, fromY: Int, toY: Int) {
        val ox = chunk.pos.cx * WorldConstants.CHUNK_SIZE
        val oz = chunk.pos.cz * WorldConstants.CHUNK_SIZE
        val s = WorldConstants.CHUNK_SIZE
        for (y in fromY..toY) {
            for (x in 0 until s) {
                for (z in 0 until s) {
                    val block = chunk.getBlock(x, y, z)
                    if (block == BlockType.AIR) continue
                    val t = BlockRegistry.wireIndex(block) * 6
                    val wx = ox + x
                    val wz2 = oz + z
                    val blockAbove =
                        if (y >= WorldConstants.WORLD_MAX_Y) BlockType.AIR
                        else chunk.getBlock(x, y + 1, z)
                    if (!blockAbove.isSolid && !(block.isLiquid && blockAbove.isLiquid))
                        jsChunkFace(wx, y, wz2, t + 4, computeFaceAO(chunk, x, y, z, 4))
                    if (y <= 0 || !chunk.getBlock(x, y - 1, z).isSolid)
                        jsChunkFace(wx, y, wz2, t + 5, computeFaceAO(chunk, x, y, z, 5))
                    if (z == s - 1 || !chunk.getBlock(x, y, z + 1).isSolid)
                        jsChunkFace(wx, y, wz2, t + 0, computeFaceAO(chunk, x, y, z, 0))
                    if (z == 0 || !chunk.getBlock(x, y, z - 1).isSolid)
                        jsChunkFace(wx, y, wz2, t + 1, computeFaceAO(chunk, x, y, z, 1))
                    if (x == s - 1 || !chunk.getBlock(x + 1, y, z).isSolid)
                        jsChunkFace(wx, y, wz2, t + 2, computeFaceAO(chunk, x, y, z, 2))
                    if (x == 0 || !chunk.getBlock(x - 1, y, z).isSolid)
                        jsChunkFace(wx, y, wz2, t + 3, computeFaceAO(chunk, x, y, z, 3))
                }
            }
        }
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

    private fun computeFaceAO(chunk: Chunk, lx: Int, ly: Int, lz: Int, fd: Int): Int {
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
                if (chunk.getBlock(nx, ny, nz).isSolid) solid++
            }
            packed = packed or ((solid * 5).coerceAtMost(15) shl (v * 4))
        }
        return packed
    }
}
