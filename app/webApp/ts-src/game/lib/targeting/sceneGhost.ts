import type { Mesh, Scene, StandardMaterial } from "@babylonjs/core";

export interface SceneGhostCell {
  x: number;
  y: number;
  z: number;
}

interface Face {
  dir: [number, number, number];
  corners: [number, number, number][];
}

// Unit-cube faces (corners CCW as seen from outside), one entry per axis direction.
const FACES: Face[] = [
  {
    dir: [1, 0, 0],
    corners: [
      [1, 0, 0],
      [1, 1, 0],
      [1, 1, 1],
      [1, 0, 1],
    ],
  },
  {
    dir: [-1, 0, 0],
    corners: [
      [0, 0, 1],
      [0, 1, 1],
      [0, 1, 0],
      [0, 0, 0],
    ],
  },
  {
    dir: [0, 1, 0],
    corners: [
      [0, 1, 0],
      [0, 1, 1],
      [1, 1, 1],
      [1, 1, 0],
    ],
  },
  {
    dir: [0, -1, 0],
    corners: [
      [0, 0, 1],
      [0, 0, 0],
      [1, 0, 0],
      [1, 0, 1],
    ],
  },
  {
    dir: [0, 0, 1],
    corners: [
      [1, 0, 1],
      [1, 1, 1],
      [0, 1, 1],
      [0, 0, 1],
    ],
  },
  {
    dir: [0, 0, -1],
    corners: [
      [0, 0, 0],
      [0, 1, 0],
      [1, 1, 0],
      [1, 0, 0],
    ],
  },
];

function normalizeSteps(steps: number): number {
  return ((steps % 4) + 4) % 4;
}

// Continuous 90°-step rotation of a point around the scene's Y axis, matching the server's
// per-cell ScenePlacer.rotate2D exactly at cell boundaries (see that file for the derivation).
function rotateXZ(x: number, z: number, steps: number, width: number, depth: number): [number, number] {
  switch (normalizeSteps(steps)) {
    case 1:
      return [depth - z, x];
    case 2:
      return [width - x, depth - z];
    case 3:
      return [z, width - x];
    default:
      return [x, z];
  }
}

// Same rotation, applied to a direction vector (no translation).
function rotateNormalXZ(nx: number, nz: number, steps: number): [number, number] {
  switch (normalizeSteps(steps)) {
    case 1:
      return [-nz, nx];
    case 2:
      return [-nx, -nz];
    case 3:
      return [nz, -nx];
    default:
      return [nx, nz];
  }
}

let ghostMaterial: StandardMaterial | null = null;

function getOrCreateMaterial(scene: Scene): StandardMaterial {
  // Cached and reused across calls — never disposed independently of the module lifetime, so a
  // plain null check is enough (no .isDisposed() on this Babylon build's Material instances).
  if (ghostMaterial) return ghostMaterial;
  const mat = new BABYLON.StandardMaterial("sceneGhostMat", scene) as StandardMaterial;
  mat.diffuseColor = new BABYLON.Color3(0.35, 0.75, 1);
  mat.emissiveColor = new BABYLON.Color3(0.35, 0.75, 1);
  mat.specularColor = new BABYLON.Color3(0, 0, 0);
  mat.alpha = 0.35;
  mat.backFaceCulling = false;
  ghostMaterial = mat;
  return mat;
}

export function hideScenePreview(): void {
  const existing = window.mcState.sceneGhostMesh;
  if (existing) existing.dispose();
  window.mcState.sceneGhostMesh = null;
}

/**
 * Builds a single merged, untextured mesh (positions + normals only) previewing every non-air
 * cell of a scene, rotated by `rotationSteps` quarter-turns and translated to `origin`. One draw
 * call regardless of scene size — deliberately no per-block material/UV, to keep the ghost cheap.
 */
export function showScenePreview(
  scene: Scene,
  cells: SceneGhostCell[],
  width: number,
  height: number,
  depth: number,
  rotationSteps: number,
  origin: { x: number; y: number; z: number },
): void {
  hideScenePreview();
  if (cells.length === 0) return;

  const occupied = new Set(cells.map((c) => `${c.x},${c.y},${c.z}`));
  const positions: number[] = [];
  const normals: number[] = [];
  const indices: number[] = [];

  for (const cell of cells) {
    for (const face of FACES) {
      const nx = cell.x + face.dir[0];
      const ny = cell.y + face.dir[1];
      const nz = cell.z + face.dir[2];
      const inBounds = nx >= 0 && nx < width && ny >= 0 && ny < height && nz >= 0 && nz < depth;
      if (inBounds && occupied.has(`${nx},${ny},${nz}`)) continue; // hidden internal face

      const base = positions.length / 3;
      const [rnx, rnz] = rotateNormalXZ(face.dir[0], face.dir[2], rotationSteps);
      for (const [dx, dy, dz] of face.corners) {
        const [rx, rz] = rotateXZ(cell.x + dx, cell.z + dz, rotationSteps, width, depth);
        positions.push(origin.x + rx, origin.y + cell.y + dy, origin.z + rz);
        normals.push(rnx, face.dir[1], rnz);
      }
      indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }

  if (positions.length === 0) return;

  const mesh = new BABYLON.Mesh("sceneGhost", scene) as Mesh;
  const vd = new BABYLON.VertexData();
  vd.positions = positions;
  vd.normals = normals;
  vd.indices = indices;
  vd.applyToMesh(mesh, false);
  mesh.material = getOrCreateMaterial(scene);
  mesh.isPickable = false;
  window.mcState.sceneGhostMesh = mesh;
}
