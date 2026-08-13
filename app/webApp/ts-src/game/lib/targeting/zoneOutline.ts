// Shared footprint-outline computation for instance zones: chunks in a zone are always
// contiguous, so the outline is just the set of chunk edges that face a chunk NOT in the zone,
// with colinear edges merged into runs. Used by both the 3D zone wireframe (zoneBounds.ts) and
// the 2D minimap overlay (minimap.ts) so neither draws interior chunk seams.

export type Interval = [number, number];

export interface ZoneOutlineEdges {
  northH: Map<number, Interval[]>; // z1 -> x intervals (edge faces +Z)
  southH: Map<number, Interval[]>; // z0 -> x intervals (edge faces -Z)
  eastV: Map<number, Interval[]>; // x1 -> z intervals (edge faces +X)
  westV: Map<number, Interval[]>; // x0 -> z intervals (edge faces -X)
}

// Merges touching/overlapping [start,end] block-index intervals (inclusive) into runs, so
// consecutive chunk edges along the same line become a single segment.
export function mergeIntervals(intervals: Interval[]): Interval[] {
  const sorted = [...intervals].sort((a, b) => a[0] - b[0]);
  const merged: Interval[] = [];
  for (const [start, end] of sorted) {
    const last = merged[merged.length - 1];
    if (last && start <= last[1] + 1) {
      last[1] = Math.max(last[1], end);
    } else {
      merged.push([start, end]);
    }
  }
  return merged;
}

export function computeZoneOutlineEdges(chunks: { cx: number; cz: number }[], chunkSize: number): ZoneOutlineEdges {
  const chunkSet = new Set(chunks.map(({ cx, cz }) => `${cx},${cz}`));
  const has = (cx: number, cz: number) => chunkSet.has(`${cx},${cz}`);

  const northH = new Map<number, Interval[]>();
  const southH = new Map<number, Interval[]>();
  const eastV = new Map<number, Interval[]>();
  const westV = new Map<number, Interval[]>();
  const push = (map: Map<number, Interval[]>, key: number, iv: Interval) => map.set(key, [...(map.get(key) ?? []), iv]);

  for (const { cx, cz } of chunks) {
    const x0 = cx * chunkSize;
    const x1 = x0 + chunkSize - 1;
    const z0 = cz * chunkSize;
    const z1 = z0 + chunkSize - 1;
    if (!has(cx, cz + 1)) push(northH, z1, [x0, x1]);
    if (!has(cx, cz - 1)) push(southH, z0, [x0, x1]);
    if (!has(cx - 1, cz)) push(westV, x0, [z0, z1]);
    if (!has(cx + 1, cz)) push(eastV, x1, [z0, z1]);
  }

  return { northH, southH, eastV, westV };
}
