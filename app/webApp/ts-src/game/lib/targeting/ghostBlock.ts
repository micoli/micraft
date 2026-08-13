import type { Mesh, Scene } from "@babylonjs/core";
import { buildBlockPreviewMeshes } from "../chunkBuilder";

type GhostAnchor = Mesh & { _gpos?: string; _gGeoKey?: string; _gMeshes?: Mesh[] };

function disposeGhost(): void {
  const existing = window.mcState.ghostMesh as GhostAnchor | null;
  if (!existing) return;
  if (existing._gMeshes) existing._gMeshes.forEach((m) => m.dispose());
  else existing.dispose();
  window.mcState.ghostMesh = null;
}

export function registerGhostBlock(): Pick<McBindings, "showBlockPreview" | "hideBlockPreview"> {
  return {
    showBlockPreview: (
      scene: Scene,
      x: number,
      y: number,
      z: number,
      typeOrd: number,
      rotation: number,
      colorIdx = 0,
      xOffset = 0,
      zOffset = 0,
    ): void => {
      if (typeOrd < 0) {
        disposeGhost();
        return;
      }

      const geoKey = `${typeOrd},${rotation},${colorIdx}`;
      const existing = window.mcState.ghostMesh as GhostAnchor | null;

      // Compute sub-voxel position offset from brickSize fractions
      const blockDef = window.mc.getBlockDef(typeOrd);
      const bs = blockDef?.brickSize ?? [1, 1, 1];
      // Offsets are world-space (client already accounts for rotation in effectiveFracX/Z)
      const fracX = bs[0] < 1 ? bs[0] : bs[0] > 1 ? 0.5 : 0;
      const fracZ = bs[2] < 1 ? bs[2] : bs[2] > 1 ? 0.5 : 0;
      const offsetX = xOffset * fracX;
      const offsetZ = zOffset * fracZ;

      if (existing && existing._gGeoKey === geoKey) {
        const pos = new BABYLON.Vector3(x + offsetX, y, z + offsetZ);
        (existing._gMeshes ?? [existing]).forEach((m) => {
          m.position = pos;
        });
        return;
      }

      disposeGhost();

      const meshes = buildBlockPreviewMeshes(scene, typeOrd, rotation, colorIdx);
      if (meshes.length === 0) {
        console.warn(
          "[MiCraft] Ghost: no preview mesh for typeOrd=" +
            typeOrd +
            " rot=" +
            rotation +
            " (block not in faceTable or defs not ready)",
        );
        return;
      }

      const pos = new BABYLON.Vector3(x + offsetX, y, z + offsetZ);
      for (const m of meshes) m.position = pos;

      const anchor = meshes[0] as GhostAnchor;
      anchor._gGeoKey = geoKey;
      anchor._gMeshes = meshes;
      window.mcState.ghostMesh = anchor;
    },

    hideBlockPreview: (): void => {
      disposeGhost();
    },
  };
}
