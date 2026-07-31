import type { Mesh, Scene } from "@babylonjs/core";
import { buildBlockPreviewMeshes } from "../chunks/chunkBuilder";

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
    showBlockPreview: (scene: Scene, x: number, y: number, z: number, typeOrd: number, rotation: number): void => {
      if (typeOrd < 0) {
        disposeGhost();
        return;
      }

      const geoKey = `${typeOrd},${rotation}`;
      const existing = window.mcState.ghostMesh as GhostAnchor | null;

      if (existing && existing._gGeoKey === geoKey) {
        const pos = new BABYLON.Vector3(x, y, z);
        (existing._gMeshes ?? [existing]).forEach((m) => {
          m.position = pos;
        });
        return;
      }

      disposeGhost();

      const meshes = buildBlockPreviewMeshes(scene, typeOrd, rotation);
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

      const pos = new BABYLON.Vector3(x, y, z);
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
