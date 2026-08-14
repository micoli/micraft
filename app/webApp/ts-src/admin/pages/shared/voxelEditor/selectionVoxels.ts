import { type SelectionBox, type SelectionShape } from "./selectionGizmo";

// Two-block pattern for Fill/Shell: a solid block (`b` unset), or a 3D checkerboard alternation of
// two blocks by (x+y+z) parity when `b` is set.
export interface Pattern {
  a: string;
  b?: string;
}

export function resolvePatternBlock(pattern: Pattern, x: number, y: number, z: number): string {
  if (!pattern.b) return pattern.a;
  return (x + y + z) % 2 === 0 ? pattern.a : pattern.b;
}

// Per-op cap on the number of voxels a Fill/Shell/Cut may touch — bounds the WS batch payload size,
// the server-side apply loop, and the client-side local optimistic-apply loop (chunk remesh for
// instances, WASM setBlock for scenes). Well below the gizmo's own preview cap (2,000,000 cells),
// which only does rendering, not network sends or remeshing.
export const MAX_SELECTION_OP_VOXELS = 20000;

// Same ellipsoid/cylinder membership test as selectionGizmo.ts's insideShape — duplicated rather
// than exported since that module stays Babylon-rendering-only.
function insideShape(
  shape: SelectionShape,
  cx: number,
  cy: number,
  cz: number,
  rx: number,
  ry: number,
  rz: number,
  x: number,
  y: number,
  z: number,
): boolean {
  if (shape === "box") return true;
  if (shape === "cylinder") {
    const dx = (x - cx) / rx;
    const dz = (z - cz) / rz;
    return dx * dx + dz * dz <= 1;
  }
  const dx = (x - cx) / rx;
  const dy = (y - cy) / ry;
  const dz = (z - cz) / rz;
  return dx * dx + dy * dy + dz * dz <= 1;
}

export interface VoxelCoord {
  x: number;
  y: number;
  z: number;
}

// Voxel-aligned grid (floor/ceil of the box's bounds, which may be fractional from the gizmo's
// quarter-voxel drag snap) intersected with the shape's membership test at each cell's center.
// `mode: "shell"` keeps only cells with at least one of their 6 neighbors outside the shape (or
// outside the grid) — the exposed boundary layer, uniform across box/sphere/spheroid/cylinder.
// Returns null if the voxel count exceeds MAX_SELECTION_OP_VOXELS.
export function computeSelectionVoxels(
  box: SelectionBox,
  shape: SelectionShape,
  mode: "fill" | "shell",
): VoxelCoord[] | null {
  const minX = Math.floor(box.minX);
  const minY = Math.floor(box.minY);
  const minZ = Math.floor(box.minZ);
  const maxX = Math.ceil(box.maxX);
  const maxY = Math.ceil(box.maxY);
  const maxZ = Math.ceil(box.maxZ);
  const nx = Math.max(1, maxX - minX);
  const ny = Math.max(1, maxY - minY);
  const nz = Math.max(1, maxZ - minZ);
  if (nx * ny * nz > MAX_SELECTION_OP_VOXELS) return null;

  const cx = (box.minX + box.maxX) / 2;
  const cy = (box.minY + box.maxY) / 2;
  const cz = (box.minZ + box.maxZ) / 2;
  const rx = box.maxX - cx;
  const ry = box.maxY - cy;
  const rz = box.maxZ - cz;

  const inside = new Uint8Array(nx * ny * nz);
  const at = (ix: number, iy: number, iz: number) => (ix * ny + iy) * nz + iz;
  for (let ix = 0; ix < nx; ix++) {
    const x = minX + ix + 0.5;
    for (let iy = 0; iy < ny; iy++) {
      const y = minY + iy + 0.5;
      for (let iz = 0; iz < nz; iz++) {
        const z = minZ + iz + 0.5;
        inside[at(ix, iy, iz)] = insideShape(shape, cx, cy, cz, rx, ry, rz, x, y, z) ? 1 : 0;
      }
    }
  }

  const voxels: VoxelCoord[] = [];
  const NEIGHBORS: [number, number, number][] = [
    [1, 0, 0],
    [-1, 0, 0],
    [0, 1, 0],
    [0, -1, 0],
    [0, 0, 1],
    [0, 0, -1],
  ];
  for (let ix = 0; ix < nx; ix++) {
    for (let iy = 0; iy < ny; iy++) {
      for (let iz = 0; iz < nz; iz++) {
        if (!inside[at(ix, iy, iz)]) continue;
        if (mode === "fill") {
          voxels.push({ x: minX + ix, y: minY + iy, z: minZ + iz });
          continue;
        }
        const isShell = NEIGHBORS.some(([dx, dy, dz]) => {
          const nix = ix + dx;
          const niy = iy + dy;
          const niz = iz + dz;
          return !(nix >= 0 && nix < nx && niy >= 0 && niy < ny && niz >= 0 && niz < nz && inside[at(nix, niy, niz)]);
        });
        if (isShell) voxels.push({ x: minX + ix, y: minY + iy, z: minZ + iz });
      }
    }
  }
  return voxels;
}
