// Rotate/flip applied to a clipboard's blocks before Confirm — purely geometric, computed fresh
// from the untransformed clipboard entries every time (never re-applied incrementally) so repeated
// rotate/flip toggles stay order-independent and reversible.

export interface PasteTransform {
  // Cumulative quarter-turns clockwise around Y (0-3) — matches BlockState.pack()'s 2-bit rotation
  // field (blockState.ts), so a paste rotation also rotates each block's own facing.
  rotation: 0 | 1 | 2 | 3;
  flipX: boolean;
  flipY: boolean;
  flipZ: boolean;
}

export const IDENTITY_PASTE_TRANSFORM: PasteTransform = { rotation: 0, flipX: false, flipY: false, flipZ: false };

export interface ClipboardEntry {
  relX: number;
  relY: number;
  relZ: number;
  type: string;
  state: number;
}

// Flips have no representation in BlockState's rotation bits (no mirror bit) — only the position is
// mirrored, a block's own facing is left as-is. Rotation, unlike flip, does update the rotation
// bits (mod 4) so an oriented block (e.g. a log) keeps pointing the right way after the paste turns.
export function applyPasteTransform(entries: ClipboardEntry[], transform: PasteTransform): ClipboardEntry[] {
  if (entries.length === 0) return entries;
  let sx = 0,
    sy = 0,
    sz = 0;
  for (const e of entries) {
    sx = Math.max(sx, e.relX + 1);
    sy = Math.max(sy, e.relY + 1);
    sz = Math.max(sz, e.relZ + 1);
  }
  return entries.map((e) => {
    let x = transform.flipX ? sx - 1 - e.relX : e.relX;
    const y = transform.flipY ? sy - 1 - e.relY : e.relY;
    let z = transform.flipZ ? sz - 1 - e.relZ : e.relZ;
    let curSx = sx;
    let curSz = sz;
    let rotBits = e.state & 0x03;
    for (let i = 0; i < transform.rotation; i++) {
      const nx = z;
      const nz = curSx - 1 - x;
      x = nx;
      z = nz;
      [curSx, curSz] = [curSz, curSx];
      rotBits = (rotBits + 1) & 0x03;
    }
    return { relX: x, relY: y, relZ: z, type: e.type, state: (e.state & ~0x03) | rotBits };
  });
}
