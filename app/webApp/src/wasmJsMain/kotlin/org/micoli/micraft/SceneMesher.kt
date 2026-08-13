package org.micoli.micraft

import org.micoli.micraft.babylon.*
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState

// Fork of ChunkManager.renderRow/computeFaceAO (see ChunkManager.kt) for the admin Scene editor
// (AdminScenePreview.kt): a bounded X/Y/Z raw block buffer that is NOT chunk-shaped (width,
// height, depth are independent parameters, not the fixed 16/16/1025 the live game uses) and has
// no chunk-grid neighbors — meshing is always fully local to this one buffer, so every boundary
// face (x==0, x==width-1, z==0, z==depth-1, y==0, y==height-1) is always emitted, exactly like
// ChunkManager's per-chunk boundary handling (chunks never see across into a neighbor chunk's
// blocks either — that's handled by the live game re-meshing both chunks on a cross-boundary
// edit, which the standalone Scene buffer has no equivalent of and doesn't need).
//
// Unlike Chunk, a Scene has no BlockEntity/fractional-plate system (LEGO_PIECE-style sub-voxel
// stacking) — the admin HTTP contract only supports whole-cell block+state writes — so this is a
// straight port of the base-array face-culling/greedy-merge path only, with the
// renderFractionalEntities second pass dropped entirely.
//
// Whole-volume mesh: keyed under a single fixed "chunk" slot (0,0) in the JS-side chunk map
// (mc.chunkBegin/chunkEnd), since a Scene is exactly one contiguous mesh, never streamed/paged.
class SceneMesher(
    var width: Int,
    var height: Int,
    var depth: Int,
    var blocks: ByteArray,
    var states: ByteArray,
) {
    private var blockMaterials: JsAny? = null

    private val solidByOrd = ByteArray(256)
    private val liquidByOrd = ByteArray(256)
    private val hasStudsByOrd = ByteArray(256)
    private val isMultiCellByOrd = ByteArray(256)
    private val cubicByOrd = ByteArray(256)
    private val occludesByOrd = ByteArray(256)
    private val mergeableByOrd = ByteArray(256)
    private var ordFlagsBuilt = false

    private val strideX: Int
        get() = height * depth

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
                    (def.brickSize[0] > 1 || def.brickSize[1] > 1 || def.brickSize[2] > 1))
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

    fun index(x: Int, y: Int, z: Int): Int = x * strideX + y * depth + z

    fun inBounds(x: Int, y: Int, z: Int): Boolean =
        x in 0 until width && y in 0 until height && z in 0 until depth

    fun getBlockOrdinal(x: Int, y: Int, z: Int): Int {
        if (!inBounds(x, y, z)) return 0
        return blocks[index(x, y, z)].toInt() and 0xFF
    }

    fun getState(x: Int, y: Int, z: Int): Int {
        if (!inBounds(x, y, z)) return 0
        return states[index(x, y, z)].toInt() and 0xFF
    }

    fun setBlock(x: Int, y: Int, z: Int, ordinal: Int, state: Int) {
        if (!inBounds(x, y, z)) return
        blocks[index(x, y, z)] = ordinal.toByte()
        states[index(x, y, z)] = state.toByte()
    }

    fun replaceBuffers(width: Int, height: Int, depth: Int, blocks: ByteArray, states: ByteArray) {
        this.width = width
        this.height = height
        this.depth = depth
        this.blocks = blocks
        this.states = states
    }

    private fun getBlockMaterials(scene: JsAny): JsAny? {
        if (blockMaterials == null && jsIsBlockDefsReady()) {
            blockMaterials = jsCreateBlockMaterials(scene)
        }
        return blockMaterials
    }

    // Synchronous full re-mesh of the whole volume — mirrors ChunkManager.renderChunk's
    // synchronous path (no async Phase1/2/3 drain budget; a Scene buffer is small enough — a few
    // hundred thousand cells at most — to mesh in one go without frame-budget slicing).
    fun render(scene: JsAny) {
        val mats = getBlockMaterials(scene) ?: return
        buildOrdFlags()
        jsChunkBegin(0, 0)
        for (y in 0 until height) renderRow(y)
        val faceCount = jsGetFaceCount()
        var cursor = 0
        while (cursor < faceCount) cursor += jsChunkProcessFaces(cursor, 20_000)
        jsChunkEnd(scene, mats)
    }

    fun dispose() {
        jsDisposeChunk("0,0")
        blockMaterials = null
        ordFlagsBuilt = false
    }

    // Plain color index rides in bits 18-23 of the ao int (bits 0-15 = AO, 16-17 = yOffset) —
    // mirrors ChunkManager's COLOR_SHIFT.
    private val colorShift = 18

    // Port of ChunkManager.renderRow, minus the BlockEntity/fractional-plate handling (Scene has
    // no entity system) and with the chunk-local `s` bound split into independent width/depth,
    // and WorldConstants.WORLD_MAX_Y replaced by the passed-in height. Loop-order/greedy-merge
    // logic (top/bottom/east/west merge runs along Z; south/north stay one quad per block) is
    // otherwise identical.
    private fun renderRow(y: Int): Int {
        val hasStates = states.isNotEmpty()
        var faceCount = 0
        val mergeActive = BooleanArray(4)
        val mergeStartZ = IntArray(4)
        val mergeLen = IntArray(4)
        val mergeFaceMat = IntArray(4)
        val mergeAo = IntArray(4)
        for (x in 0 until width) {
            val wx = x
            for (z in 0 until depth) {
                val idx = x * strideX + y * depth + z
                val ord = blocks[idx].toInt() and 0xFF
                if (ord == 0) continue // AIR = wire index 0

                val state = if (hasStates) states[idx] else 0
                val rotation = BlockState.rotation(state)
                val colorBits = BlockState.colorIndex(state) shl colorShift
                val t = (ord * 4 + rotation) * 6
                val wz2 = z

                val bypassCulling = cubicByOrd[ord].toInt() == 0
                val mergeEligible = mergeableByOrd[ord].toInt() != 0 && !bypassCulling

                // top (+Y)
                val aboveOrd = if (y >= height - 1) 0 else blocks[idx + depth].toInt() and 0xFF
                val liquid = liquidByOrd[ord].toInt() != 0
                val liquidAbove = liquidByOrd[aboveOrd].toInt() != 0
                val emitTop =
                    bypassCulling ||
                        (occludesByOrd[aboveOrd].toInt() == 0 && !(liquid && liquidAbove))
                if (emitTop) {
                    val faceMatV = t + 4
                    val aoV = computeFaceAO(x, y, z, 4) or colorBits
                    if (mergeEligible) {
                        if (mergeActive[0] &&
                            mergeFaceMat[0] == faceMatV &&
                            mergeAo[0] == aoV &&
                            mergeStartZ[0] + mergeLen[0] == z) {
                            mergeLen[0]++
                        } else {
                            flushMergeRun(
                                0, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                            mergeActive[0] = true
                            mergeStartZ[0] = z
                            mergeLen[0] = 1
                            mergeFaceMat[0] = faceMatV
                            mergeAo[0] = aoV
                        }
                    } else {
                        flushMergeRun(
                            0, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRun(
                        0, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                }
                // bottom (-Y)
                val emitBottom =
                    bypassCulling ||
                        y <= 0 ||
                        occludesByOrd[blocks[idx - depth].toInt() and 0xFF].toInt() == 0
                if (emitBottom) {
                    val faceMatV = t + 5
                    val aoV = computeFaceAO(x, y, z, 5) or colorBits
                    if (mergeEligible) {
                        if (mergeActive[1] &&
                            mergeFaceMat[1] == faceMatV &&
                            mergeAo[1] == aoV &&
                            mergeStartZ[1] + mergeLen[1] == z) {
                            mergeLen[1]++
                        } else {
                            flushMergeRun(
                                1, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                            mergeActive[1] = true
                            mergeStartZ[1] = z
                            mergeLen[1] = 1
                            mergeFaceMat[1] = faceMatV
                            mergeAo[1] = aoV
                        }
                    } else {
                        flushMergeRun(
                            1, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRun(
                        1, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                }
                // south (+Z) — not mergeable via Z-adjacency, one quad per block
                if (bypassCulling ||
                    z == depth - 1 ||
                    occludesByOrd[blocks[idx + 1].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFaceAppend(wx, y, wz2, t + 0, computeFaceAO(x, y, z, 0) or colorBits)
                    faceCount++
                }
                // north (-Z)
                if (bypassCulling ||
                    z == 0 ||
                    occludesByOrd[blocks[idx - 1].toInt() and 0xFF].toInt() == 0) {
                    jsChunkFaceAppend(wx, y, wz2, t + 1, computeFaceAO(x, y, z, 1) or colorBits)
                    faceCount++
                }
                // east (+X)
                val emitEast =
                    bypassCulling ||
                        x == width - 1 ||
                        occludesByOrd[blocks[idx + strideX].toInt() and 0xFF].toInt() == 0
                if (emitEast) {
                    val faceMatV = t + 2
                    val aoV = computeFaceAO(x, y, z, 2) or colorBits
                    if (mergeEligible) {
                        if (mergeActive[2] &&
                            mergeFaceMat[2] == faceMatV &&
                            mergeAo[2] == aoV &&
                            mergeStartZ[2] + mergeLen[2] == z) {
                            mergeLen[2]++
                        } else {
                            flushMergeRun(
                                2, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                            mergeActive[2] = true
                            mergeStartZ[2] = z
                            mergeLen[2] = 1
                            mergeFaceMat[2] = faceMatV
                            mergeAo[2] = aoV
                        }
                    } else {
                        flushMergeRun(
                            2, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRun(
                        2, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                }
                // west (-X)
                val emitWest =
                    bypassCulling ||
                        x == 0 ||
                        occludesByOrd[blocks[idx - strideX].toInt() and 0xFF].toInt() == 0
                if (emitWest) {
                    val faceMatV = t + 3
                    val aoV = computeFaceAO(x, y, z, 3) or colorBits
                    if (mergeEligible) {
                        if (mergeActive[3] &&
                            mergeFaceMat[3] == faceMatV &&
                            mergeAo[3] == aoV &&
                            mergeStartZ[3] + mergeLen[3] == z) {
                            mergeLen[3]++
                        } else {
                            flushMergeRun(
                                3, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                            mergeActive[3] = true
                            mergeStartZ[3] = z
                            mergeLen[3] = 1
                            mergeFaceMat[3] = faceMatV
                            mergeAo[3] = aoV
                        }
                    } else {
                        flushMergeRun(
                            3, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                        jsChunkFaceAppend(wx, y, wz2, faceMatV, aoV)
                    }
                    faceCount++
                } else {
                    flushMergeRun(
                        3, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
                }
            }
            flushMergeRun(0, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
            flushMergeRun(1, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
            flushMergeRun(2, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
            flushMergeRun(3, mergeActive, mergeStartZ, mergeLen, mergeFaceMat, mergeAo, wx, y)
        }
        return faceCount
    }

    private fun flushMergeRun(
        idx: Int,
        active: BooleanArray,
        startZ: IntArray,
        len: IntArray,
        faceMatArr: IntArray,
        aoArr: IntArray,
        wx: Int,
        y: Int,
    ) {
        if (!active[idx]) return
        val wz = startZ[idx]
        if (len[idx] <= 1) {
            jsChunkFaceAppend(wx, y, wz, faceMatArr[idx], aoArr[idx])
        } else {
            jsChunkFaceAppendRun(wx, y, wz, faceMatArr[idx], aoArr[idx], len[idx])
        }
        active[idx] = false
    }

    // Port of ChunkManager.computeFaceAO — x bound is width, z bound is depth (chunk version uses
    // one shared square bound `s` for both since chunks are always CHUNK_SIZE x CHUNK_SIZE).
    private fun computeFaceAO(lx: Int, ly: Int, lz: Int, fd: Int): Int {
        val nbrs = AO_NEIGHBORS[fd]
        var packed = 0
        for (v in 0..3) {
            var solid = 0
            for (n in 0..2) {
                val off = nbrs[v][n]
                val nx = lx + off[0]
                val ny = ly + off[1]
                val nz = lz + off[2]
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) continue
                if (ny < 0 || ny >= height) continue
                val nIdx = nx * strideX + ny * depth + nz
                if (solidByOrd[blocks[nIdx].toInt() and 0xFF].toInt() != 0) solid++
            }
            packed = packed or ((solid * 5).coerceAtMost(15) shl (v * 4))
        }
        return packed
    }

    companion object {
        // AO neighbor offsets: [face][vertex][neighbor(s1,s2,corner)][axis(dx,dy,dz)] — copied
        // verbatim from ChunkManager.kt's private AO_NEIGHBORS table (no shared module between
        // the two — the table is small and static, duplicating it is simpler than extracting a
        // shared file for one constant).
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
    }
}
