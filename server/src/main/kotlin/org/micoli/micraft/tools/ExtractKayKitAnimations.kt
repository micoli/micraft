package org.micoli.micraft.tools

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ── GLTF data classes ──────────────────────────────────────────────────────────

@Serializable
private data class GltfRoot(
    val scene: Int = 0,
    val scenes: List<GltfScene> = emptyList(),
    val nodes: List<GltfNode> = emptyList(),
    val animations: List<GltfAnimation> = emptyList(),
    val accessors: List<GltfAccessor> = emptyList(),
    val bufferViews: List<GltfBufferView> = emptyList(),
    val buffers: List<GltfBuffer> = emptyList(),
)

@Serializable private data class GltfScene(val nodes: List<Int> = emptyList())

@Serializable
private data class GltfNode(
    val name: String = "",
    val children: List<Int> = emptyList(),
    val rotation: List<Float>? = null,
    val translation: List<Float>? = null,
)

@Serializable
private data class GltfAnimation(
    val name: String = "",
    val channels: List<GltfChannel> = emptyList(),
    val samplers: List<GltfSampler> = emptyList(),
)

@Serializable private data class GltfChannel(val sampler: Int, val target: GltfTarget)

@Serializable private data class GltfTarget(val node: Int? = null, val path: String = "")

@Serializable
private data class GltfSampler(
    val input: Int,
    val output: Int,
    val interpolation: String = "LINEAR",
)

@Serializable
private data class GltfAccessor(
    val bufferView: Int = 0,
    val byteOffset: Int = 0,
    val componentType: Int = 5126,
    val count: Int = 0,
    val type: String = "SCALAR",
)

@Serializable
private data class GltfBufferView(
    val buffer: Int = 0,
    val byteOffset: Int = 0,
    val byteLength: Int = 0,
    val byteStride: Int = 0,
)

@Serializable private data class GltfBuffer(val byteLength: Int = 0, val uri: String? = null)

// ── Target skeleton description ────────────────────────────────────────────────

/** A bbmodel bone, the KayKit joint it is driven by, and its parent in the bbmodel outliner. */
private data class BbBone(val name: String, val gltfNode: String, val parent: String?)

/**
 * Sides are crossed on purpose: `.r` joints drive the `left*` bones and vice versa.
 *
 * Under the Z mirror that [basisQuat] applies to compensate Babylon's left-handed scenes, the
 * bbmodel bone named `rightArm` (authored at x = +5) is the one that ends up rendered on the
 * character's *left*. Driving it from `upperarm.r` would put the source's right-arm motion on the
 * screen-left arm. The joint positions line up too: `upperarm.r` sits at x < 0 in the source, same
 * side as `leftArm` at x = −5.
 */
private val BB_BONES =
    listOf(
        BbBone("root", "root", null),
        BbBone("waist", "hips", "root"),
        BbBone("pelvis", "spine", "waist"),
        BbBone("body", "chest", "pelvis"),
        BbBone("head", "head", "body"),
        BbBone("rightArm", "upperarm.l", "body"),
        BbBone("rightElbow", "lowerarm.l", "rightArm"),
        BbBone("rightWrist", "hand.l", "rightElbow"),
        BbBone("leftArm", "upperarm.r", "body"),
        BbBone("leftElbow", "lowerarm.r", "leftArm"),
        BbBone("leftWrist", "hand.r", "leftElbow"),
        BbBone("rightLeg", "upperleg.l", "waist"),
        BbBone("rightKnee", "lowerleg.l", "rightLeg"),
        BbBone("rightAnkle", "foot.l", "rightKnee"),
        BbBone("leftLeg", "upperleg.r", "waist"),
        BbBone("leftKnee", "lowerleg.r", "leftLeg"),
        BbBone("leftAnkle", "foot.r", "leftKnee"),
    )

private val BB_BONE_BY_NAME = BB_BONES.associateBy { it.name }

/**
 * KayKit rests in a T-pose (arms horizontal along ±X) while the bbmodel rests with the arms hanging
 * down (−Y). That is the only rest-pose divergence between the two rigs — spine/head point +Y and
 * legs point −Y on both sides — so a single constant correction per arm chain is enough to retarget
 * absolutely (target bone points wherever the source bone points).
 */
private val ARM_CHAIN_NODES =
    setOf(
        "upperarm.r",
        "lowerarm.r",
        "wrist.r",
        "hand.r",
        "handslot.r",
        "upperarm.l",
        "lowerarm.l",
        "wrist.l",
        "hand.l",
        "handslot.l",
    )

private const val ARM_CHAIN_ROOT_R = "upperarm.r"
private const val ARM_CHAIN_ROOT_L = "upperarm.l"

/** Direction the bbmodel arm bones point at rest, in bbmodel space. */
private val ARM_REST_DIR = floatArrayOf(0f, -1f, 0f)

/** The glTF joint whose translation drives the bbmodel `waist` position channel. */
private const val ROOT_MOTION_NODE = "hips"

private const val ROOT_MOTION_BB_BONE = "waist"

/**
 * Metres → bbmodel units. Ratio of hip heights: `waist.origin.y = 12` against `hips.y = 0.405663`.
 * Chosen so that "hips on the floor" in the source maps to "waist on the floor" in the target,
 * which is the invariant that matters for Lie_* / Sit_* / Push_Ups.
 */
private const val TRANSLATION_SCALE = 12f / 0.405663f

/** Tolerance driving adaptive keyframe insertion, see [refineTimeline]. */
private const val MAX_INTERP_ERROR_DEG = 2f

private const val MAX_REFINEMENT_PASSES = 6

private const val MIN_KEY_SPACING_SECONDS = 1f / 480f

private const val ROTATION_EPSILON_DEG = 0.01f
private const val POSITION_EPSILON_UNITS = 0.001f

// ── Constants ──────────────────────────────────────────────────────────────────

private val GLTF_JSON = Json { ignoreUnknownKeys = true }
private val BBMODEL_JSON = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

// bbmodel bone name → UUID for player.bbmodel and askin.bbmodel (8 bones)
private val PLAYER_BONE_UUIDS =
    mapOf(
        "root" to "da541ef9-8630-9ad8-3679-0e7f2a04101f",
        "waist" to "8e59278d-df96-6d6f-5f8f-4100bc3e7ef1",
        "body" to "0a03e00f-a6a7-c8cc-e737-8a19d592bd19",
        "head" to "6f65a673-2926-5dbf-58ad-7c77725517fb",
        "rightArm" to "cbb20693-2002-c20a-8d34-2162b3f9016f",
        "leftArm" to "d0e5de27-ecbe-b7e8-43fb-fed1649fc8b8",
        "rightLeg" to "f1f7deeb-2099-b24d-c6f4-9ad96043a688",
        "leftLeg" to "a2757385-c447-1fef-ecb9-2314877eb19f",
    )

// articulated.bbmodel adds 9 articulation pivot groups
private val ARTICULATED_BONE_UUIDS =
    PLAYER_BONE_UUIDS +
        mapOf(
            "pelvis" to "cc000002-0000-0000-0000-000000000002",
            "rightElbow" to "bb000001-0000-0000-0000-000000000001",
            "rightWrist" to "bb000002-0000-0000-0000-000000000002",
            "leftElbow" to "bb000003-0000-0000-0000-000000000003",
            "leftWrist" to "bb000004-0000-0000-0000-000000000004",
            "rightKnee" to "bb000005-0000-0000-0000-000000000005",
            "rightAnkle" to "bb000006-0000-0000-0000-000000000006",
            "leftKnee" to "bb000007-0000-0000-0000-000000000007",
            "leftAnkle" to "bb000008-0000-0000-0000-000000000008",
        )

private val GLB_DIR =
    "resources/game-assets/KayKit_Character_Animations_1.1/Animations/gltf/Rig_Medium"
private val BBMODEL_FILES =
    listOf(
        "resources/skins/articulated/articulated.bbmodel" to ARTICULATED_BONE_UUIDS,
        "resources/skins/player/player.bbmodel" to PLAYER_BONE_UUIDS,
        "resources/skins/askin/askin.bbmodel" to PLAYER_BONE_UUIDS,
    )

// ── GLB binary parsing ─────────────────────────────────────────────────────────

private data class GlbData(val gltf: GltfRoot, val bin: ByteArray)

private fun parseGlb(file: File): GlbData {
    val bytes = file.readBytes()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val magic = buf.getInt()
    require(magic == 0x46546C67) { "Not a GLB file: ${file.name}" }
    buf.getInt() // version
    buf.getInt() // totalLength

    val jsonLen = buf.getInt()
    val jsonType = buf.getInt()
    require(jsonType == 0x4E4F534A) { "Expected JSON chunk in ${file.name}" }
    val jsonBytes = ByteArray(jsonLen)
    buf.get(jsonBytes)

    val gltf = GLTF_JSON.decodeFromString<GltfRoot>(String(jsonBytes, Charsets.UTF_8))

    val bin =
        if (buf.remaining() >= 8) {
            val binLen = buf.getInt()
            val binType = buf.getInt()
            if (binType == 0x004E4942) ByteArray(binLen).also { buf.get(it) } else ByteArray(0)
        } else ByteArray(0)

    return GlbData(gltf, bin)
}

// ── Accessor reading ───────────────────────────────────────────────────────────

private fun componentCount(type: String) =
    when (type) {
        "SCALAR" -> 1
        "VEC2" -> 2
        "VEC3" -> 3
        "VEC4" -> 4
        else -> throw IllegalArgumentException("Unknown GLTF type: $type")
    }

private fun readFloats(
    bin: ByteArray,
    gltf: GltfRoot,
    accessorIdx: Int,
    cubicSpline: Boolean
): FloatArray {
    val acc = gltf.accessors[accessorIdx]
    require(acc.componentType == 5126) {
        "Only FLOAT accessors are supported, got ${acc.componentType}"
    }
    val bv = gltf.bufferViews[acc.bufferView]
    val k = componentCount(acc.type)
    // CUBICSPLINE stores [inTangent(k), value(k), outTangent(k)] per sample; keep the value only.
    val perSample = if (cubicSpline) 3 * k else k
    val stride = if (bv.byteStride > 0) bv.byteStride else perSample * 4
    val valueOffset = if (cubicSpline) k * 4 else 0
    val base = bv.byteOffset + acc.byteOffset
    val bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)

    val out = FloatArray(acc.count * k)
    for (i in 0 until acc.count) {
        val off = base + i * stride + valueOffset
        for (c in 0 until k) out[i * k + c] = bb.getFloat(off + c * 4)
    }
    return out
}

// ── Quaternion / vector math ───────────────────────────────────────────────────

private val IDENTITY_QUAT = floatArrayOf(0f, 0f, 0f, 1f)

private fun quatMul(a: FloatArray, b: FloatArray): FloatArray {
    val ax = a[0]
    val ay = a[1]
    val az = a[2]
    val aw = a[3]
    val bx = b[0]
    val by = b[1]
    val bz = b[2]
    val bw = b[3]
    return floatArrayOf(
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz,
    )
}

private fun quatConj(q: FloatArray) = floatArrayOf(-q[0], -q[1], -q[2], q[3])

private fun quatNormalize(q: FloatArray): FloatArray {
    val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
    if (n < 1e-12f) return IDENTITY_QUAT.copyOf()
    return floatArrayOf(q[0] / n, q[1] / n, q[2] / n, q[3] / n)
}

private fun quatDot(a: FloatArray, b: FloatArray) =
    a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

private fun quatNegate(q: FloatArray) = floatArrayOf(-q[0], -q[1], -q[2], -q[3])

/** Shortest-path spherical interpolation, as required by GLTF LINEAR rotation samplers. */
private fun quatSlerp(a: FloatArray, bIn: FloatArray, t: Float): FloatArray {
    var dot = quatDot(a, bIn)
    val b = if (dot < 0f) quatNegate(bIn).also { dot = -dot } else bIn
    if (dot > 0.9995f) {
        return quatNormalize(
            floatArrayOf(
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t,
                a[3] + (b[3] - a[3]) * t,
            ))
    }
    val theta = acos(dot.coerceIn(-1f, 1f))
    val sinTheta = sin(theta)
    val wa = sin((1f - t) * theta) / sinTheta
    val wb = sin(t * theta) / sinTheta
    return quatNormalize(
        floatArrayOf(
            a[0] * wa + b[0] * wb,
            a[1] * wa + b[1] * wb,
            a[2] * wa + b[2] * wb,
            a[3] * wa + b[3] * wb,
        ))
}

private fun rotateVec(q: FloatArray, v: FloatArray): FloatArray {
    val x = q[0]
    val y = q[1]
    val z = q[2]
    val w = q[3]
    // t = 2 * (q.xyz × v); v' = v + w*t + q.xyz × t
    val tx = 2f * (y * v[2] - z * v[1])
    val ty = 2f * (z * v[0] - x * v[2])
    val tz = 2f * (x * v[1] - y * v[0])
    return floatArrayOf(
        v[0] + w * tx + (y * tz - z * ty),
        v[1] + w * ty + (z * tx - x * tz),
        v[2] + w * tz + (x * ty - y * tx),
    )
}

/** Minimal rotation taking unit vector [from] onto unit vector [to]. */
private fun shortestArc(from: FloatArray, to: FloatArray): FloatArray {
    val d = from[0] * to[0] + from[1] * to[1] + from[2] * to[2]
    if (d > 0.999999f) return IDENTITY_QUAT.copyOf()
    if (d < -0.999999f) {
        // Antipodal: any orthogonal axis works, pick the most stable one.
        val axis = if (abs(from[0]) < 0.9f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
        val ox = axis[1] * from[2] - axis[2] * from[1]
        val oy = axis[2] * from[0] - axis[0] * from[2]
        val oz = axis[0] * from[1] - axis[1] * from[0]
        return quatNormalize(floatArrayOf(ox, oy, oz, 0f))
    }
    val cx = from[1] * to[2] - from[2] * to[1]
    val cy = from[2] * to[0] - from[0] * to[2]
    val cz = from[0] * to[1] - from[1] * to[0]
    return quatNormalize(floatArrayOf(cx, cy, cz, 1f + d))
}

/**
 * glTF → bbmodel basis change: a **mirror through the XY plane**, `diag(1, 1, −1)`.
 *
 * Geometrically the two rigs are only 180° apart around Y — both are anatomically named (glTF faces
 * +Z, its toes point that way once the leg chain's rest rotations are composed; the bbmodel faces
 * −Z, its head UVs put the face on `north` = −Z and the character's right on `east` = +X). A pure
 * yaw would therefore be the correct *data* transform.
 *
 * But every consumer here is a Babylon scene, and none of them sets `useRightHandedSystem`, so they
 * are all **left-handed** while the bbmodel coordinates are right-handed. Babylon consequently
 * renders the whole model mirrored along Z. That is invisible on a symmetric Minecraft body, which
 * is why nobody noticed, but it swaps left and right on any asymmetric animation. Folding the
 * mirror into the export is what makes the result match the KayKit reference on screen.
 *
 * A mirror is improper, so a rotation axis transforms as a pseudo-vector — `axis' = det(M)·M·axis`
 * — which negates x and y and leaves the angle alone. Plain vectors just take `M`.
 *
 * If the target ever becomes a right-handed viewer (Blockbench itself, or a Babylon scene with
 * `useRightHandedSystem = true`), switch both functions back to the yaw: `(-x, y, -z, w)` and `(-x,
 * y, -z)`.
 */
private fun basisQuat(q: FloatArray) = floatArrayOf(-q[0], -q[1], q[2], q[3])

private fun basisVec(v: FloatArray) = floatArrayOf(v[0], v[1], -v[2])

/**
 * Quaternion → Euler in **ZYX** order (the composition `Rz · Ry · Rx`), in degrees. This is the
 * order Blockbench and the in-repo Babylon viewer both use for bone rotations. No axis conversion
 * happens here: the basis change is already applied upstream by [basisQuat].
 */
private fun quatToEulerZYX(qIn: FloatArray): FloatArray {
    val q = quatNormalize(qIn)
    val x = q[0].toDouble()
    val y = q[1].toDouble()
    val z = q[2].toDouble()
    val w = q[3].toDouble()
    val rx = atan2(2.0 * (w * x + y * z), 1.0 - 2.0 * (x * x + y * y))
    val ry = asin((2.0 * (w * y - z * x)).coerceIn(-1.0, 1.0))
    val rz = atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z))
    return floatArrayOf(
        Math.toDegrees(rx).toFloat(),
        Math.toDegrees(ry).toFloat(),
        Math.toDegrees(rz).toFloat(),
    )
}

/** Inverse of [quatToEulerZYX], used by the round-trip self-check. */
private fun eulerZYXToQuat(deg: FloatArray): FloatArray {
    val hx = Math.toRadians(deg[0].toDouble()) / 2.0
    val hy = Math.toRadians(deg[1].toDouble()) / 2.0
    val hz = Math.toRadians(deg[2].toDouble()) / 2.0
    val cx = cos(hx)
    val sx = sin(hx)
    val cy = cos(hy)
    val sy = sin(hy)
    val cz = cos(hz)
    val sz = sin(hz)
    return floatArrayOf(
        (sx * cy * cz - cx * sy * sz).toFloat(),
        (cx * sy * cz + sx * cy * sz).toFloat(),
        (cx * cy * sz - sx * sy * cz).toFloat(),
        (cx * cy * cz + sx * sy * sz).toFloat(),
    )
}

// ── Skeleton (hierarchy + bind pose) ───────────────────────────────────────────

private class Skeleton(gltf: GltfRoot) {
    val size = gltf.nodes.size
    val parentOf = IntArray(size) { -1 }
    /** Node indices in a parent-before-child order. */
    val topoOrder = ArrayList<Int>(size)
    val restLocalRot = Array(size) { IDENTITY_QUAT.copyOf() }
    val restTranslation = Array(size) { floatArrayOf(0f, 0f, 0f) }
    val restWorldRot = Array(size) { IDENTITY_QUAT.copyOf() }
    val indexByName = HashMap<String, Int>()

    init {
        for (i in 0 until size) {
            val node = gltf.nodes[i]
            node.rotation?.let {
                if (it.size == 4) restLocalRot[i] = floatArrayOf(it[0], it[1], it[2], it[3])
            }
            node.translation?.let {
                if (it.size == 3) restTranslation[i] = floatArrayOf(it[0], it[1], it[2])
            }
            indexByName[node.name.lowercase()] = i
        }

        val childSet = gltf.nodes.flatMap { it.children }.toSet()
        val roots =
            gltf.scenes.getOrNull(gltf.scene)?.nodes?.takeIf { it.isNotEmpty() }
                ?: (0 until size).filter { it !in childSet }

        val seen = BooleanArray(size)
        val queue = ArrayDeque<Int>()
        for (r in roots) {
            if (!seen[r]) {
                seen[r] = true
                queue.addLast(r)
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            topoOrder.add(i)
            for (c in gltf.nodes[i].children) {
                if (!seen[c]) {
                    seen[c] = true
                    parentOf[c] = i
                    queue.addLast(c)
                }
            }
        }

        for (i in topoOrder) {
            val p = parentOf[i]
            restWorldRot[i] =
                if (p < 0) restLocalRot[i].copyOf() else quatMul(restWorldRot[p], restLocalRot[i])
        }
    }

    fun requireIndex(name: String): Int =
        indexByName[name] ?: throw IllegalStateException("Joint '$name' missing from rig")
}

// ── Sampler tracks ─────────────────────────────────────────────────────────────

private class QuatTrack(val times: FloatArray, private val values: FloatArray) {
    fun at(t: Float): FloatArray {
        if (times.isEmpty()) return IDENTITY_QUAT.copyOf()
        if (t <= times.first() || times.size == 1) return quatAt(0)
        if (t >= times.last()) return quatAt(times.size - 1)
        val hi = upperBound(times, t)
        val lo = hi - 1
        val span = times[hi] - times[lo]
        val f = if (span <= 0f) 0f else (t - times[lo]) / span
        return quatSlerp(quatAt(lo), quatAt(hi), f)
    }

    private fun quatAt(i: Int) =
        floatArrayOf(values[i * 4], values[i * 4 + 1], values[i * 4 + 2], values[i * 4 + 3])
}

private class Vec3Track(val times: FloatArray, private val values: FloatArray) {
    fun at(t: Float): FloatArray {
        if (times.isEmpty()) return floatArrayOf(0f, 0f, 0f)
        if (t <= times.first() || times.size == 1) return vecAt(0)
        if (t >= times.last()) return vecAt(times.size - 1)
        val hi = upperBound(times, t)
        val lo = hi - 1
        val span = times[hi] - times[lo]
        val f = if (span <= 0f) 0f else (t - times[lo]) / span
        val a = vecAt(lo)
        val b = vecAt(hi)
        return floatArrayOf(
            a[0] + (b[0] - a[0]) * f,
            a[1] + (b[1] - a[1]) * f,
            a[2] + (b[2] - a[2]) * f,
        )
    }

    private fun vecAt(i: Int) = floatArrayOf(values[i * 3], values[i * 3 + 1], values[i * 3 + 2])
}

/**
 * Index of the first entry strictly greater than [t]; assumes `times.first() < t < times.last()`.
 */
private fun upperBound(times: FloatArray, t: Float): Int {
    var lo = 0
    var hi = times.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (times[mid] > t) hi = mid else lo = mid + 1
    }
    return lo
}

// ── Animation extraction ───────────────────────────────────────────────────────

/**
 * One source clip, retargeted to bbmodel space but not yet flattened to a specific bbmodel file.
 * [worldDelta] holds, per bbmodel bone, the world-space rotation the bone must end up with, sampled
 * on the shared [times] timeline.
 */
private class AnimationData(
    val name: String,
    val sourceFile: String,
    val length: Float,
    val times: FloatArray,
    val worldDelta: Map<String, Array<FloatArray>>,
    val rootMotion: Array<FloatArray>?,
)

private fun extractAnimations(file: File): List<AnimationData> {
    val (gltf, bin) = parseGlb(file)
    val skel = Skeleton(gltf)

    // Reference pose: the source rest pose, with the arm chains pre-rotated so that they hang down
    // like the bbmodel arms do. Everything else already agrees between the two rigs.
    val armCorrection = HashMap<String, FloatArray>()
    for ((chainRoot, side) in listOf(ARM_CHAIN_ROOT_R to ".r", ARM_CHAIN_ROOT_L to ".l")) {
        val idx = skel.requireIndex(chainRoot)
        val restDir = rotateVec(basisQuat(skel.restWorldRot[idx]), floatArrayOf(0f, 1f, 0f))
        armCorrection[side] = shortestArc(quatNormalize3(restDir), ARM_REST_DIR)
    }

    val referenceWorld = HashMap<String, FloatArray>()
    for (bone in BB_BONES) {
        val idx = skel.requireIndex(bone.gltfNode)
        val converted = basisQuat(skel.restWorldRot[idx])
        val correction =
            if (bone.gltfNode in ARM_CHAIN_NODES) armCorrection[bone.gltfNode.takeLast(2)] else null
        referenceWorld[bone.name] =
            if (correction == null) converted else quatMul(correction, converted)
    }

    val rootMotionIdx = skel.requireIndex(ROOT_MOTION_NODE)

    return gltf.animations
        .filter { it.name != "T-Pose" }
        .mapNotNull { anim ->
            val rotTracks = HashMap<Int, QuatTrack>()
            var rootMotionTrack: Vec3Track? = null

            for (ch in anim.channels) {
                val nodeIdx = ch.target.node ?: continue
                val s = anim.samplers[ch.sampler]
                val cubic = s.interpolation == "CUBICSPLINE"
                when (ch.target.path) {
                    "rotation" ->
                        rotTracks[nodeIdx] =
                            QuatTrack(
                                readFloats(bin, gltf, s.input, false),
                                readFloats(bin, gltf, s.output, cubic))
                    "translation" ->
                        if (nodeIdx == rootMotionIdx) {
                            rootMotionTrack =
                                Vec3Track(
                                    readFloats(bin, gltf, s.input, false),
                                    readFloats(bin, gltf, s.output, cubic))
                        }
                }
            }

            if (rotTracks.isEmpty()) return@mapNotNull null

            // Composing world rotations requires every joint evaluated at the same instant. All
            // source
            // interpolations are LINEAR, so the union of the key times is exact — no resampling
            // loss.
            var times =
                mergeTimes(
                    rotTracks.values.map { it.times } + listOfNotNull(rootMotionTrack?.times))
            if (times.isEmpty()) return@mapNotNull null

            val worldRot = Array(skel.size) { IDENTITY_QUAT }
            val worldDeltaAt = { t: Float ->
                for (i in skel.topoOrder) {
                    val local = rotTracks[i]?.at(t) ?: skel.restLocalRot[i]
                    val p = skel.parentOf[i]
                    worldRot[i] = if (p < 0) local else quatMul(worldRot[p], local)
                }
                BB_BONES.associate { bone ->
                    bone.name to
                        quatMul(
                            basisQuat(worldRot[skel.requireIndex(bone.gltfNode)]),
                            quatConj(referenceWorld.getValue(bone.name)))
                }
            }

            times = refineTimeline(times, worldDeltaAt)

            val samples = times.map(worldDeltaAt)
            val worldDelta =
                BB_BONES.associate { bone ->
                    bone.name to Array(times.size) { samples[it].getValue(bone.name) }
                }

            val rootMotion =
                rootMotionTrack?.let { track ->
                    val rest = skel.restTranslation[rootMotionIdx]
                    Array(times.size) { ti ->
                        val p = track.at(times[ti])
                        basisVec(
                            floatArrayOf(
                                (p[0] - rest[0]) * TRANSLATION_SCALE,
                                (p[1] - rest[1]) * TRANSLATION_SCALE,
                                (p[2] - rest[2]) * TRANSLATION_SCALE,
                            ))
                    }
                }

            AnimationData(
                name = anim.name,
                sourceFile = file.nameWithoutExtension,
                length = times.last(),
                times = times,
                worldDelta = worldDelta,
                rootMotion = rootMotion,
            )
        }
}

/**
 * Blockbench interpolates Euler angles linearly between keys while the source interpolates the
 * quaternion (slerp). On fast or near-gimbal motion the two paths diverge sharply *between* the
 * source keys, even though both agree exactly on the keys themselves. Insert extra keys wherever
 * the linear-Euler path strays from the true rotation by more than [MAX_INTERP_ERROR_DEG].
 */
private fun refineTimeline(
    initial: FloatArray,
    worldDeltaAt: (Float) -> Map<String, FloatArray>
): FloatArray {
    var times = initial
    repeat(MAX_REFINEMENT_PASSES) {
        val samples = times.map(worldDeltaAt)
        val inserts = ArrayList<Float>()
        for (i in 0 until times.size - 1) {
            val midTime = (times[i] + times[i + 1]) / 2f
            if (midTime - times[i] < MIN_KEY_SPACING_SECONDS) continue
            val mid = worldDeltaAt(midTime)
            val diverges =
                BB_BONES.any { bone ->
                    eulerLerpError(
                        localOf(samples[i], bone),
                        localOf(samples[i + 1], bone),
                        localOf(mid, bone)) > MAX_INTERP_ERROR_DEG
                }
            if (diverges) inserts.add(midTime)
        }
        if (inserts.isEmpty()) return times
        times = mergeTimes(listOf(times, inserts.toFloatArray()))
    }
    return times
}

private fun localOf(sample: Map<String, FloatArray>, bone: BbBone): FloatArray {
    val own = sample.getValue(bone.name)
    val parent = bone.parent ?: return own
    return quatMul(quatConj(sample.getValue(parent)), own)
}

/** Angular gap between the linearly interpolated Euler midpoint and the true midpoint rotation. */
private fun eulerLerpError(q0: FloatArray, q1: FloatArray, qMid: FloatArray): Float {
    val e0 = quatToEulerZYX(q0)
    val e1 = pickSmoothestBranch(quatToEulerZYX(q1), e0)
    val approx = eulerZYXToQuat(FloatArray(3) { (e0[it] + e1[it]) / 2f })
    val d = abs(quatDot(quatNormalize(approx), quatNormalize(qMid))).coerceIn(0f, 1f)
    return Math.toDegrees(2.0 * acos(d.toDouble())).toFloat()
}

private fun quatNormalize3(v: FloatArray): FloatArray {
    val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    if (n < 1e-12f) return floatArrayOf(0f, 1f, 0f)
    return floatArrayOf(v[0] / n, v[1] / n, v[2] / n)
}

private fun mergeTimes(tracks: List<FloatArray>): FloatArray {
    val all = sortedSetOf<Float>()
    for (t in tracks) for (v in t) all.add(v)
    val out = ArrayList<Float>(all.size)
    for (v in all) {
        if (out.isEmpty() || v - out.last() > 1e-5f) out.add(v)
    }
    return out.toFloatArray()
}

// ── Flattening to a specific bbmodel file ──────────────────────────────────────

private data class RotationKey(val time: Float, val euler: FloatArray)

private data class PositionKey(val time: Float, val offset: FloatArray)

/** Nearest ancestor of [bone] that actually exists in the target bbmodel. */
private fun effectiveParent(bone: BbBone, present: Set<String>): String? {
    var p = bone.parent
    while (p != null && p !in present) p = BB_BONE_BY_NAME.getValue(p).parent
    return p
}

private fun localRotationKeys(
    anim: AnimationData,
    boneName: String,
    parentName: String?
): List<RotationKey> {
    val own = anim.worldDelta.getValue(boneName)
    val parent = parentName?.let { anim.worldDelta.getValue(it) }

    val keys = ArrayList<RotationKey>(anim.times.size)
    var prevQuat: FloatArray? = null
    var prevEuler: FloatArray? = null

    for (ti in anim.times.indices) {
        var local = if (parent == null) own[ti] else quatMul(quatConj(parent[ti]), own[ti])
        // Keep the quaternion path on a single hemisphere, otherwise the Euler extraction flips by
        // 360° between consecutive keys and Blockbench's linear interpolation spins the bone.
        prevQuat?.let { if (quatDot(it, local) < 0f) local = quatNegate(local) }
        prevQuat = local

        val euler = pickSmoothestBranch(quatToEulerZYX(local), prevEuler)
        prevEuler = euler

        keys.add(RotationKey(anim.times[ti], euler))
    }
    return dedupe(keys, ROTATION_EPSILON_DEG) { it.euler }
}

/**
 * Every rotation has two ZYX Euler representations — `(x, y, z)` and `(x+180, 180−y, z+180)` — and
 * each of those is defined modulo 360° per axis. Near a gimbal singularity the canonical branch
 * thrashes between consecutive keys (x jumping 216° → 342° → 194° over two frames), and since
 * Blockbench interpolates Euler angles linearly the bone takes a wild detour between the keys even
 * though the underlying quaternion path is smooth. Pick whichever branch stays closest to the
 * previous key.
 */
private fun pickSmoothestBranch(euler: FloatArray, previous: FloatArray?): FloatArray {
    if (previous == null) return euler
    val candidates = listOf(euler, floatArrayOf(euler[0] + 180f, 180f - euler[1], euler[2] + 180f))
    var best: FloatArray? = null
    var bestDistance = Float.MAX_VALUE
    for (candidate in candidates) {
        val unwrapped =
            FloatArray(3) { a ->
                var v = candidate[a]
                while (v - previous[a] > 180f) v -= 360f
                while (v - previous[a] < -180f) v += 360f
                v
            }
        val distance = (0..2).sumOf { abs(unwrapped[it] - previous[it]).toDouble() }.toFloat()
        if (distance < bestDistance) {
            bestDistance = distance
            best = unwrapped
        }
    }
    return best!!
}

private fun <T> dedupe(keys: List<T>, epsilon: Float, values: (T) -> FloatArray): List<T> {
    if (keys.size <= 2) return keys
    val out = ArrayList<T>(keys.size)
    for (i in keys.indices) {
        if (i == 0 || i == keys.size - 1) {
            out.add(keys[i])
            continue
        }
        val cur = values(keys[i])
        val prev = values(keys[i - 1])
        val next = values(keys[i + 1])
        val flat =
            cur.indices.all {
                abs(cur[it] - prev[it]) <= epsilon && abs(cur[it] - next[it]) <= epsilon
            }
        if (!flat) out.add(keys[i])
    }
    return out
}

// ── bbmodel JSON builders ──────────────────────────────────────────────────────

private fun Float.toValueString(): String =
    String.format(Locale.ROOT, "%.4f", this).trimEnd('0').trimEnd('.').ifEmpty { "0" }

private fun keyframeJson(channel: String, time: Float, v: FloatArray): JsonObject =
    buildJsonObject {
        put("channel", channel)
        put(
            "data_points",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("x", v[0].toValueString())
                        put("y", v[1].toValueString())
                        put("z", v[2].toValueString())
                    })
            })
        put("uuid", UUID.randomUUID().toString())
        put("time", time)
        put("color", -1)
        put("interpolation", "linear")
    }

private fun buildBbmodelAnimation(
    anim: AnimationData,
    displayName: String,
    boneUuids: Map<String, String>
): JsonObject {
    val present = boneUuids.keys
    return buildJsonObject {
        put("uuid", UUID.randomUUID().toString())
        put("name", "animation.default_player.$displayName")
        put("loop", "loop")
        put("override", false)
        // Blockbench treats length 0 as "unbounded" (Animation.time returns the raw timeline
        // time), which makes static poses unusable in the timeline. Clamp to one frame @24fps.
        put("length", maxOf(anim.length, anim.times.maxOrNull() ?: 0f, 1f / 24f))
        put("snapping", 24)
        put("anim_time_update", "")
        put("blend_weight", "")
        put("start_delay", "")
        put("loop_delay", "")
        put(
            "animators",
            buildJsonObject {
                for (bone in BB_BONES) {
                    val uuid = boneUuids[bone.name] ?: continue
                    val rotationKeys =
                        localRotationKeys(anim, bone.name, effectiveParent(bone, present))
                    val positionKeys =
                        if (bone.name == ROOT_MOTION_BB_BONE && anim.rootMotion != null) {
                            dedupe(
                                anim.times.indices.map {
                                    PositionKey(anim.times[it], anim.rootMotion[it])
                                },
                                POSITION_EPSILON_UNITS) {
                                    it.offset
                                }
                        } else emptyList()

                    if (rotationKeys.isEmpty() && positionKeys.isEmpty()) continue

                    put(
                        uuid,
                        buildJsonObject {
                            put("name", bone.name)
                            put("type", "bone")
                            put(
                                "keyframes",
                                buildJsonArray {
                                    for (k in rotationKeys) add(
                                        keyframeJson("rotation", k.time, k.euler))
                                    for (k in positionKeys) add(
                                        keyframeJson("position", k.time, k.offset))
                                })
                        })
                }
            })
    }
}

/**
 * Blockbench isolates animations per scope when the project declares a multi-file ruleset with
 * `scope_isolated_animations` (`bedrock_attachable`): `Animation.getBoneAnimator()` refuses to link
 * a bone whose `scope` differs from the animation's. An animation without `scope` defaults to 0, so
 * it would target none of the scope-1 groups — keyframes load but stay unreachable, and Blockbench
 * shows "The current animation does not target this node". Mirror the groups' scope instead of
 * hardcoding it, so the tool stays correct if a bbmodel is rescoped.
 */
internal fun bbmodelGroupScope(root: JsonObject): Int =
    (root["groups"] as? JsonArray)?.firstNotNullOfOrNull { group ->
        (group as? JsonObject)?.get("scope")?.jsonPrimitive?.intOrNull?.takeIf { it != 0 }
    } ?: 0

private val JsonElement.animationName: String?
    get() = (this as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull

/**
 * Merges the generated animations into the bbmodel by name: an existing animation with the same
 * `name` is overwritten in place (keeping its `uuid`, so Blockbench references and diffs stay
 * stable), unknown ones are appended, and hand-authored animations the extractor knows nothing
 * about are left untouched.
 */
internal fun mergeAnimations(root: JsonObject, animations: JsonArray): JsonArray {
    val scope = JsonPrimitive(bbmodelGroupScope(root))
    val existing = (root["animations"] as? JsonArray).orEmpty().toMutableList()
    val indexByName =
        existing.withIndex().mapNotNull { (i, a) -> a.animationName?.let { it to i } }.toMap()

    for (anim in animations) {
        val fields = (anim as JsonObject).toMutableMap().apply { put("scope", scope) }
        val target = anim.animationName?.let { indexByName[it] }
        if (target != null) {
            (existing[target] as? JsonObject)?.get("uuid")?.let { fields["uuid"] = it }
            existing[target] = JsonObject(fields)
        } else {
            existing.add(JsonObject(fields))
        }
    }
    return JsonArray(existing)
}

internal fun updateBbmodel(bbmodelFile: File, animations: JsonArray) {
    val root = BBMODEL_JSON.parseToJsonElement(bbmodelFile.readText()) as JsonObject
    val merged = mergeAnimations(root, animations)
    val updated = JsonObject(root.toMutableMap().apply { put("animations", merged) })
    bbmodelFile.writeText(BBMODEL_JSON.encodeToString(updated))
}

// ── Self-check ─────────────────────────────────────────────────────────────────

/**
 * Recomposes the emitted ZYX Euler angles back into world rotations and compares them with the
 * retarget targets. A generalised drift points at the basis change or the reference pose; an
 * isolated one points at the Euler unwrapping.
 */
private fun verifyRoundTrip(anim: AnimationData, boneUuids: Map<String, String>): Float {
    val present = boneUuids.keys
    val perBone =
        BB_BONES.filter { boneUuids.containsKey(it.name) }
            .associate { it.name to localRotationKeys(anim, it.name, effectiveParent(it, present)) }

    var worst = 0f
    for (ti in anim.times.indices) {
        val t = anim.times[ti]
        val world = HashMap<String, FloatArray>()
        for (bone in BB_BONES) {
            if (!boneUuids.containsKey(bone.name)) continue
            val keys = perBone.getValue(bone.name)
            val local = eulerZYXToQuat(sampleEuler(keys, t))
            val parent = effectiveParent(bone, present)
            world[bone.name] = if (parent == null) local else quatMul(world.getValue(parent), local)
            val expected = anim.worldDelta.getValue(bone.name)[ti]
            val actual = world.getValue(bone.name)
            val d = abs(quatDot(quatNormalize(expected), quatNormalize(actual))).coerceIn(0f, 1f)
            worst = max(worst, 2f * acos(d))
        }
    }
    return worst
}

private fun sampleEuler(keys: List<RotationKey>, t: Float): FloatArray {
    if (keys.isEmpty()) return floatArrayOf(0f, 0f, 0f)
    if (t <= keys.first().time) return keys.first().euler
    if (t >= keys.last().time) return keys.last().euler
    for (i in 1 until keys.size) {
        if (keys[i].time >= t) {
            val a = keys[i - 1]
            val b = keys[i]
            val span = b.time - a.time
            val f = if (span <= 0f) 0f else (t - a.time) / span
            return FloatArray(3) { a.euler[it] + (b.euler[it] - a.euler[it]) * f }
        }
    }
    return keys.last().euler
}

private fun reportBoneDirections(anim: AnimationData, boneNames: List<String>) {
    for (name in boneNames) {
        val deltas = anim.worldDelta.getValue(name)
        var bestIdx = 0
        var bestY = -2f
        for (i in deltas.indices) {
            val y = rotateVec(deltas[i], ARM_REST_DIR)[1]
            if (y > bestY) {
                bestY = y
                bestIdx = i
            }
        }
        val d0 = rotateVec(deltas[0], ARM_REST_DIR)
        val dp = rotateVec(deltas[bestIdx], ARM_REST_DIR)
        println(
            "      %-10s t=0 dir=(%+.2f, %+.2f, %+.2f)  peak t=%.2f dir=(%+.2f, %+.2f, %+.2f)"
                .format(
                    Locale.ROOT,
                    name,
                    d0[0],
                    d0[1],
                    d0[2],
                    anim.times[bestIdx],
                    dp[0],
                    dp[1],
                    dp[2]))
    }
}

// ── Entry point ────────────────────────────────────────────────────────────────

fun main() {
    val glbDir = File(GLB_DIR)
    require(glbDir.exists()) { "GLB directory not found: $GLB_DIR" }

    val glbFiles =
        glbDir.listFiles { f -> f.extension == "glb" }?.sortedBy { it.name } ?: emptyList()

    println("Found ${glbFiles.size} GLB files in ${glbDir.path}")

    val allAnimations = mutableListOf<AnimationData>()
    for (glbFile in glbFiles) {
        print("  ${glbFile.name} … ")
        val anims = extractAnimations(glbFile)
        println("${anims.size} animations")
        allAnimations.addAll(anims)
    }

    println("Total: ${allAnimations.size} animations extracted")

    // Disambiguate clips that share a name across GLB files.
    val nameCount = allAnimations.groupingBy { it.name }.eachCount()
    val displayNames =
        allAnimations.associateWith { anim ->
            if (nameCount.getValue(anim.name) > 1) "${anim.sourceFile}.${anim.name}" else anim.name
        }
    nameCount
        .filter { it.value > 1 }
        .keys
        .sorted()
        .forEach { println("  ! duplicate clip name '$it' — prefixed with its GLB name") }

    val sorted = allAnimations.sortedBy { displayNames.getValue(it) }

    val sample = sorted.firstOrNull { it.name == "Cheering" } ?: sorted.first()
    val worstErr = verifyRoundTrip(sample, ARTICULATED_BONE_UUIDS)
    println(
        "  Self-check on '${sample.name}': worst round-trip error = %.2e rad"
            .format(Locale.ROOT, worstErr))
    reportBoneDirections(sample, listOf("rightArm", "leftArm", "rightLeg", "head"))
    check(worstErr < 1e-3f) { "Round-trip verification failed: ${worstErr} rad" }

    for ((path, boneUuids) in BBMODEL_FILES) {
        val f = File(path)
        require(f.exists()) { "bbmodel not found: $path" }
        print("  Updating ${f.name} … ")
        val animationsJson =
            JsonArray(
                sorted.map { buildBbmodelAnimation(it, displayNames.getValue(it), boneUuids) })
        updateBbmodel(f, animationsJson)
        println("done (%.1f MB)".format(Locale.ROOT, f.length() / 1_048_576.0))
    }

    println(
        "All done — ${allAnimations.size} animations written to ${BBMODEL_FILES.size} bbmodel files.")
}
