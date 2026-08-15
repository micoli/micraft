import { buildBlockPreviewMeshes } from "../../../../game/lib/chunkBuilder";
import { boxLines } from "../../../../game/lib/targeting/targeting";

// Placement ghost, target outline, and break overlay — mirrors the in-game preview (ghostBlock.ts /
// targeting.ts) but driven by scene.pick() hover instead of a first-person raycast. Shared by the
// Instance and Scene editors: the Scene editor has no sub-voxel slot system, so it always calls
// with xOffset/zOffset left at their default of 0 (equivalent to no offset).
export function createOverlayController(
  B: typeof BABYLON,
  scene: InstanceType<typeof BABYLON.Scene>,
  overlayMeshes: Set<InstanceType<typeof BABYLON.Mesh>>,
) {
  let placementRotation = 0;
  let ghostMeshes: ReturnType<typeof buildBlockPreviewMeshes> = [];
  let ghostGeoKey: string | null = null;
  let outlineMesh: InstanceType<typeof BABYLON.LinesMesh> | null = null;

  let breakMeshes: ReturnType<typeof buildBlockPreviewMeshes> = [];
  let breakMeshKey: string | null = null;
  // Single shared red translucent material for every break-overlay mesh — kept separate from
  // getOrCreateGhostMat's cache (chunkBuilder.ts) since that cache is keyed by texture/color and
  // shared with the green placement ghost; tinting it red here would bleed into placement.
  let breakMat: InstanceType<typeof BABYLON.StandardMaterial> | null = null;

  function disposeGhost() {
    ghostMeshes.forEach((m) => {
      overlayMeshes.delete(m);
      m.dispose();
    });
    ghostMeshes = [];
    ghostGeoKey = null;
  }

  function disposeOutline() {
    if (outlineMesh) overlayMeshes.delete(outlineMesh);
    outlineMesh?.dispose();
    outlineMesh = null;
  }

  function getOrCreateBreakMat(): InstanceType<typeof BABYLON.StandardMaterial> {
    if (breakMat) return breakMat;
    const mat = new B.StandardMaterial("breakOverlayMat", scene);
    mat.diffuseColor = new B.Color3(1, 0, 0);
    mat.emissiveColor = new B.Color3(1, 0, 0);
    mat.alpha = 0.3;
    mat.disableDepthWrite = true;
    mat.backFaceCulling = false;
    breakMat = mat;
    return mat;
  }

  function disposeBreakOverlay() {
    breakMeshes.forEach((m) => {
      overlayMeshes.delete(m);
      m.dispose();
    });
    breakMeshes = [];
    breakMeshKey = null;
  }

  function disposeAll() {
    disposeGhost();
    disposeOutline();
    disposeBreakOverlay();
  }

  // In break mode the ghost is replaced by the targeted block's real mesh tinted red, instead of
  // the placement preview — mirrors showGhostAndOutline's shape/rotation/sub-slot handling (same
  // buildBlockPreviewMeshes + brickSize offset math) so the overlay matches classic blocks,
  // fractional pieces (LEGO_PIECE) and non-cubic footprints (LEGO_ARCH_4X1) alike.
  function showBreakOverlay(
    x: number,
    y: number,
    z: number,
    ordinal: number,
    rotation: number,
    xOffset = 0,
    zOffset = 0,
  ) {
    const key = `${x},${y},${z},${ordinal},${rotation},${xOffset},${zOffset}`;
    if (breakMeshKey === key) return;
    disposeBreakOverlay();
    const mat = getOrCreateBreakMat();
    breakMeshes = buildBlockPreviewMeshes(scene, ordinal, rotation);
    const blockDef = window.mc.getBlockDef(ordinal);
    // brickSize is in half-voxel units (2 = 1 full voxel) — divide by 2 for the 0..1 voxel
    // fraction used below.
    const bs = (blockDef?.brickSize ?? [2, 2, 2]).map((v) => v / 2);
    const fracX = bs[0] < 1 ? bs[0] : bs[0] > 1 ? 0.5 : 0;
    const fracZ = bs[2] < 1 ? bs[2] : bs[2] > 1 ? 0.5 : 0;
    const pos = new B.Vector3(x + xOffset * fracX, y, z + zOffset * fracZ);
    for (const m of breakMeshes) {
      m.position = pos;
      m.material = mat;
      m.isPickable = false;
      m.renderingGroupId = 1;
      overlayMeshes.add(m);
    }
    breakMeshKey = key;
  }

  function showGhostAndOutline(
    x: number,
    y: number,
    z: number,
    ordinal: number,
    colorIndex: number,
    xOffset = 0,
    zOffset = 0,
  ) {
    const geoKey = `${ordinal},${placementRotation},${colorIndex}`;
    if (ghostGeoKey !== geoKey) {
      disposeGhost();
      ghostMeshes = buildBlockPreviewMeshes(scene, ordinal, placementRotation, colorIndex);
      ghostGeoKey = geoKey;
      // The ghost shader (see getOrCreateGhostMat in chunkBuilder.ts) has backFaceCulling=false
      // and a zOffset/zOffsetUnits bias tuned for the in-game FPS-scale render distance. Both are
      // harmless up close but misbehave at this editor's much larger orbit-camera distances: the
      // depth bias becomes a huge world-space displacement, and because the alpha-blended mesh
      // still writes depth, its own back faces and front faces fight over which "wins" per pixel —
      // which reads as the ghost smearing/thickening toward the camera. Neutralize the bias and
      // stop it from writing depth — standard practice for a translucent overlay.
      for (const m of ghostMeshes) {
        if (m.material) {
          m.material.zOffset = 0;
          m.material.zOffsetUnits = 0;
          m.material.disableDepthWrite = true;
        }
        m.renderingGroupId = 1;
        overlayMeshes.add(m);
      }
    }
    // Multi-voxel props (brickSize > 1 on an axis) have a real footprint bigger than one block — a
    // hardcoded 1x1x1 outline would look like a mismatched sliver stuck in the corner of a much
    // bigger ghost mesh. Same rotation-aware sizing as the in-game target outline (targeting.ts).
    const blockDef = window.mc.getBlockDef(ordinal);
    // brickSize is in half-voxel units (2 = 1 full voxel) — divide by 2 for the 0..1 voxel
    // fraction / voxel-count semantics used below.
    const bs = (blockDef?.brickSize ?? [2, 2, 2]).map((v) => v / 2);

    const fracX = bs[0] < 1 ? bs[0] : bs[0] > 1 ? 0.5 : 0;
    const fracZ = bs[2] < 1 ? bs[2] : bs[2] > 1 ? 0.5 : 0;
    const pos = new B.Vector3(x + xOffset * fracX, y, z + zOffset * fracZ);
    for (const m of ghostMeshes) m.position = pos;

    const rot90 = placementRotation === 1 || placementRotation === 3;
    const worldSizeX = rot90 ? (bs[2] < 1 ? bs[2] : Math.ceil(bs[2])) : bs[0] < 1 ? bs[0] : Math.ceil(bs[0]);
    const worldSizeY = bs[1] < 1 ? bs[1] : Math.ceil(bs[1]);
    const worldSizeZ = rot90 ? (bs[0] < 1 ? bs[0] : Math.ceil(bs[0])) : bs[2] < 1 ? bs[2] : Math.ceil(bs[2]);

    disposeOutline();
    const ox = x + xOffset * fracX;
    const oz = z + zOffset * fracZ;
    const ls = B.MeshBuilder.CreateLineSystem(
      "placeOutline",
      { lines: boxLines(ox, y, oz, ox + worldSizeX, y + worldSizeY, oz + worldSizeZ) },
      scene,
    );
    ls.color = new B.Color3(1, 1, 1);
    ls.isPickable = false;
    ls.renderingGroupId = 1;
    outlineMesh = ls;
    overlayMeshes.add(ls);
  }

  return {
    getPlacementRotation: () => placementRotation,
    rotatePlacement: () => {
      placementRotation = (placementRotation + 1) % 4;
      // Force a rebuild at the last known cursor position so the rotation is visible immediately.
      ghostGeoKey = null;
    },
    showGhostAndOutline,
    showBreakOverlay,
    disposeGhost,
    disposeOutline,
    disposeBreakOverlay,
    disposeAll,
  };
}
