import type { PickingInfo, Scene, Vector3 } from "@babylonjs/core";

/** Farther than the orbit camera can zoom out over loaded terrain. */
const MAX_PICK_DISTANCE = 512;

export interface VoxelPick {
  pick: PickingInfo;
  point: Vector3;
  normal: Vector3;
}

type Raycast = (ox: number, oy: number, oz: number, dx: number, dy: number, dz: number, max: number) => string;

let raycast: Raycast | null = null;

/** Resolve the Kotlin export once; picks before it resolves miss. */
export async function loadVoxelPick(): Promise<void> {
  const exports = await window.webApp;
  raycast = exports?.mcRaycastVoxel ?? null;
}

/** "px,py,pz,nx,ny,nz" from `mcRaycastVoxel`; null on a miss. */
export function parseVoxelHit(csv: string): { point: number[]; normal: number[] } | null {
  if (!csv) return null;
  const v = csv.split(",").map(Number);
  if (v.length !== 6 || v.some(Number.isNaN)) return null;
  return { point: v.slice(0, 3), normal: v.slice(3) };
}

/**
 * Terrain under the screen point, from the client's block data rather than the meshes (which keep
 * no CPU geometry). Shaped like `scene.pick()` + `getNormal()` for the orbit pointer controller.
 */
export function voxelPickAt(scene: Scene, x: number, y: number): VoxelPick | null {
  if (!raycast || !scene.activeCamera) return null;
  const ray = scene.createPickingRay(x, y, BABYLON.Matrix.Identity(), scene.activeCamera);
  const { origin: o, direction: d } = ray;
  const hit = parseVoxelHit(raycast(o.x, o.y, o.z, d.x, d.y, d.z, MAX_PICK_DISTANCE));
  if (!hit) return null;
  const point = BABYLON.Vector3.FromArray(hit.point);
  const pick = new BABYLON.PickingInfo();
  pick.hit = true;
  pick.pickedPoint = point;
  pick.distance = BABYLON.Vector3.Distance(o, point);
  pick.ray = ray;
  return { pick, point, normal: BABYLON.Vector3.FromArray(hit.normal) };
}
