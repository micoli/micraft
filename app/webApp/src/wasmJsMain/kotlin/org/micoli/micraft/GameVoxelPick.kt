@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package org.micoli.micraft

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.physics.VoxelRaycast

/** The in-game world's chunk data, for the exports TypeScript queries. Set once by GameClient. */
internal var gameChunkManager: ChunkManager? = null

/**
 * First non-air block along a ray, for creative-mode targeting: terrain meshes keep no CPU copy of
 * their geometry, so `scene.pick()` cannot hit them. Returns "px,py,pz,nx,ny,nz" (hit point on the
 * entered face, face normal) or "" on a miss.
 */
@JsExport
fun mcRaycastVoxel(
    ox: Double,
    oy: Double,
    oz: Double,
    dx: Double,
    dy: Double,
    dz: Double,
    maxDistance: Double,
): String {
    val chunks = gameChunkManager ?: return ""
    val hit =
        VoxelRaycast.cast(ox, oy, oz, dx, dy, dz, maxDistance) { x, y, z ->
            chunks.getBlockAtWorld(x, y, z) != BlockType.AIR
        } ?: return ""
    return "${hit.pointX},${hit.pointY},${hit.pointZ},${hit.normalX},${hit.normalY},${hit.normalZ}"
}
