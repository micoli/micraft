import type { Mesh } from "@babylonjs/core";

/**
 * Bounding info straight from the position array. `mesh.refreshBoundingInfo()` also keeps one
 * `Vector3` per vertex for picking — ~5× the vertex data, the largest live allocation of the client
 * heap for terrain. Picking rebuilds that array on demand (`_generatePointsArray`), so static
 * meshes skip it.
 */
export function setBoundsFromPositions(mesh: Mesh, positions: ArrayLike<number>): void {
  let minX = Infinity;
  let minY = Infinity;
  let minZ = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  let maxZ = -Infinity;
  for (let i = 0; i < positions.length; i += 3) {
    const x = positions[i];
    const y = positions[i + 1];
    const z = positions[i + 2];
    if (x < minX) minX = x;
    if (y < minY) minY = y;
    if (z < minZ) minZ = z;
    if (x > maxX) maxX = x;
    if (y > maxY) maxY = y;
    if (z > maxZ) maxZ = z;
  }
  mesh.buildBoundingInfo(new BABYLON.Vector3(minX, minY, minZ), new BABYLON.Vector3(maxX, maxY, maxZ));
}
