import type { LinesMesh, Scene, Vector3 } from "@babylonjs/core";
import { computeZoneOutlineEdges, mergeIntervals } from "./zoneOutline";

const CHUNK_SIZE = 16;
const H = 0.05;

// Aggregated red wireframe around the whole footprint of an instance zone's chunks — chunks in
// a zone are always contiguous, so only edges facing a chunk NOT in the zone are boundary edges.
// Colinear boundary edges are merged, and interior chunk seams are never drawn.
export function registerZoneBounds(): Pick<McBindings, "showZoneBounds" | "hideZoneBounds"> {
  return {
    showZoneBounds: (scene: Scene, yMin: number, yMax: number, chunksJson: string): void => {
      if (window.mcState.zoneMesh) {
        window.mcState.zoneMesh.dispose();
        window.mcState.zoneMesh = null;
      }
      let chunks: { cx: number; cz: number }[];
      try {
        chunks = JSON.parse(chunksJson);
      } catch {
        return;
      }
      if (chunks.length === 0) return;

      // Kept separate by facing (rather than one map keyed by raw coordinate) because the
      // constant coordinate of each edge must be offset outward by H — north/east edges push
      // +H, south/west edges push -H — so a horizontal and a vertical edge meeting at the same
      // footprint corner land on the exact same world point.
      const { northH, southH, eastV, westV } = computeZoneOutlineEdges(chunks, CHUNK_SIZE);

      const V = (x: number, y: number, z: number) => new BABYLON.Vector3(x, y, z);
      const lines: [Vector3, Vector3][] = [];
      const corners = new Set<string>();

      for (const [zRaw, intervals] of northH) {
        const z = zRaw + H;
        for (const [x0, x1] of mergeIntervals(intervals)) {
          lines.push([V(x0 - H, yMin - H, z), V(x1 + H, yMin - H, z)]);
          lines.push([V(x0 - H, yMax + H, z), V(x1 + H, yMax + H, z)]);
          corners.add(`${x0 - H},${z}`);
          corners.add(`${x1 + H},${z}`);
        }
      }
      for (const [zRaw, intervals] of southH) {
        const z = zRaw - H;
        for (const [x0, x1] of mergeIntervals(intervals)) {
          lines.push([V(x0 - H, yMin - H, z), V(x1 + H, yMin - H, z)]);
          lines.push([V(x0 - H, yMax + H, z), V(x1 + H, yMax + H, z)]);
          corners.add(`${x0 - H},${z}`);
          corners.add(`${x1 + H},${z}`);
        }
      }
      for (const [xRaw, intervals] of eastV) {
        const x = xRaw + H;
        for (const [z0, z1] of mergeIntervals(intervals)) {
          lines.push([V(x, yMin - H, z0 - H), V(x, yMin - H, z1 + H)]);
          lines.push([V(x, yMax + H, z0 - H), V(x, yMax + H, z1 + H)]);
          corners.add(`${x},${z0 - H}`);
          corners.add(`${x},${z1 + H}`);
        }
      }
      for (const [xRaw, intervals] of westV) {
        const x = xRaw - H;
        for (const [z0, z1] of mergeIntervals(intervals)) {
          lines.push([V(x, yMin - H, z0 - H), V(x, yMin - H, z1 + H)]);
          lines.push([V(x, yMax + H, z0 - H), V(x, yMax + H, z1 + H)]);
          corners.add(`${x},${z0 - H}`);
          corners.add(`${x},${z1 + H}`);
        }
      }
      for (const key of corners) {
        const [x, z] = key.split(",").map(Number);
        lines.push([V(x, yMin - H, z), V(x, yMax + H, z)]);
      }

      if (lines.length === 0) return;
      const ls = BABYLON.MeshBuilder.CreateLineSystem("zoneBounds", { lines }, scene) as LinesMesh;
      ls.color = new BABYLON.Color3(1, 0, 0);
      ls.isPickable = false;
      window.mcState.zoneMesh = ls;
    },

    hideZoneBounds: (): void => {
      if (window.mcState.zoneMesh) {
        window.mcState.zoneMesh.dispose();
        window.mcState.zoneMesh = null;
      }
    },
  };
}
