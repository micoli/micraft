// Shared fractional/lego block placement math for the admin voxel editors (Instance zone editor
// AND Scene editor — see InstanceEditorViewport.tsx and SceneEditorViewport.tsx). Extracted from
// InstanceEditorViewport.tsx, where this logic (including the lateral-face axis-degenerate fix)
// was first written and fixed once; duplicating it into SceneEditorViewport.tsx risked the same
// bug being fixed in one editor and forgotten in the other (as already happened once between the
// real game client and the instance editor).
//
// Deliberately wasm-agnostic: callers pass small lookup callbacks (ordinal-at, used-slot-at)
// instead of a wasm module reference, since Instance and Scene each have their own wasm preview
// module (AdminChunkPreview.kt vs AdminScenePreview.kt) with differently-named exports.

// Minimal Vector3-like shape — avoids coupling this module to a specific BABYLON.Vector3 type.
export interface Vec3Like {
  x: number;
  y: number;
  z: number;
}

export interface BrickSizeLike {
  brickSize?: number[];
}

// Voxel-picking epsilon: nudges the picked point across the hit face along its normal before
// flooring, so the coordinate resolves to the block on the correct side of the face.
export const PICK_EPSILON = 0.01;

// Decodes the wasm side's packed x*4+z "used slot" return (-1 = none) into a slot pair.
export function decodeUsedSlot(packed: number): [number, number] | null {
  return packed < 0 ? null : [Math.floor(packed / 4), packed % 4];
}

// XZ sub-voxel slot targeted within cell (tx,tz), mirroring the in-game hover math
// (LocalPlayerController.kt) so the admin editor's ghost/break-overlay and the resulting
// place/break call agree on the same slot the player is visually aiming at.
// brickSizeX/Z are in half-voxel units (2 = 1 full voxel), same as BlockDefinition.brickSize.
export function computeSlotOffset(
  pickedX: number,
  pickedZ: number,
  tx: number,
  tz: number,
  brickSizeX: number,
  brickSizeZ: number,
  rotation: number,
  // A lateral face's normal axis carries no positional info in the pick point (Babylon's
  // scene.pick pins it to the face boundary, same as the game's raycastBlock) — that axis must
  // snap to an already-placed neighbor's slot instead of the meaningless boundary value. Mirrors
  // LocalPlayerController.kt's xAxisDegenerate/zAxisDegenerate fix.
  degenerateX = false,
  degenerateZ = false,
  usedSlot: [number, number] | null = null,
): [number, number] {
  const effFracX = (rotation % 2 === 0 ? brickSizeX : brickSizeZ) / 2;
  const effFracZ = (rotation % 2 === 0 ? brickSizeZ : brickSizeX) / 2;
  const studStepX = effFracX < 1 ? effFracX : effFracX > 1 ? 0.5 : 0;
  const studStepZ = effFracZ < 1 ? effFracZ : effFracZ > 1 ? 0.5 : 0;
  if (studStepX <= 0 && studStepZ <= 0) return [0, 0];
  const fracX = Math.min(0.9999, Math.max(0, pickedX - tx));
  const fracZ = Math.min(0.9999, Math.max(0, pickedZ - tz));
  const slotsX = studStepX > 0 ? Math.max(1, Math.floor(1 / studStepX)) : 1;
  const slotsZ = studStepZ > 0 ? Math.max(1, Math.floor(1 / studStepZ)) : 1;
  const xOffset = degenerateX
    ? (usedSlot?.[0] ?? 0)
    : studStepX > 0
      ? Math.min(slotsX - 1, Math.floor(fracX / studStepX))
      : 0;
  const zOffset = degenerateZ
    ? (usedSlot?.[1] ?? 0)
    : studStepZ > 0
      ? Math.min(slotsZ - 1, Math.floor(fracZ / studStepZ))
      : 0;
  return [xOffset, zOffset];
}

// Resolves the cell a placement click should target: normally the empty neighbor cell in the
// direction of the clicked face (standard adjacent-placement), but redirected into the clicked
// block's OWN cell when that block is a Y-fractional entity (e.g. a lego plate) — otherwise a
// second piece stacked in the same voxel could never be reached, since the first piece is solid
// and blocks the ray from ever reaching past it. Mirrors the same lateral-redirect fix in
// LocalPlayerController.kt for the real game client.
export function resolvePlacementCell(
  pickedPoint: Vec3Like,
  normal: Vec3Like,
  getOrdinalAt: (x: number, y: number, z: number) => number,
  getBlockDef: (ordinal: number) => BrickSizeLike | null | undefined,
  pickEpsilon: number = PICK_EPSILON,
): [number, number, number] {
  const adjX = Math.floor(pickedPoint.x + normal.x * pickEpsilon);
  const adjY = Math.floor(pickedPoint.y + normal.y * pickEpsilon);
  const adjZ = Math.floor(pickedPoint.z + normal.z * pickEpsilon);
  const tgtX = Math.floor(pickedPoint.x - normal.x * pickEpsilon);
  const tgtY = Math.floor(pickedPoint.y - normal.y * pickEpsilon);
  const tgtZ = Math.floor(pickedPoint.z - normal.z * pickEpsilon);
  if (adjY === tgtY) {
    const targetOrdinal = getOrdinalAt(tgtX, tgtY, tgtZ);
    const targetDef = getBlockDef(targetOrdinal);
    if ((targetDef?.brickSize?.[1] ?? 2) < 2) return [tgtX, tgtY, tgtZ];
  }
  return [adjX, adjY, adjZ];
}

// Convenience wrapper: looks up the used slot at (wx,wy,wz) via the wasm export's packed x*4+z
// return, decoding -1 to null. Kept separate from decodeUsedSlot so callers with a plain wasm
// lookup function (mcAdminGetUsedXZOffsetAt / mcSceneGetUsedXZOffsetAt) don't need to inline the
// decode step themselves.
export function usedSlotAt(
  getUsedXZOffsetAt: (wx: number, wy: number, wz: number) => number,
  wx: number,
  wy: number,
  wz: number,
): [number, number] | null {
  return decodeUsedSlot(getUsedXZOffsetAt(wx, wy, wz));
}
