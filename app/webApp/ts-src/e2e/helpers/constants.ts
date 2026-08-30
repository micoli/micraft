// Derived from server/build.gradle.kts:runE2eServer — MICRAFT_E2E_BOUNDS=8x8, groundY=64 — and
// GameConfig spawn (8, 200, 8). The player falls and settles standing on the grass at y=64.
export const BOUNDS_HALF_CHUNKS = 4; // world X/Z in [-64, 63]
export const GROUND_Y = 64;
export const SPAWN_X = 8;
export const SPAWN_Z = 8;

// Feet rest on top of the grass block; capture the exact value on the first real run and pin it.
export const SETTLED_Y = GROUND_Y + 1; // ~65 — refine after first run

export const VIEW_RADIUS = 3; // applyE2eOverridesIfEnabled pins this

/** The 7x7 chunk halo that must be loaded around the spawn chunk (0,0). */
export function expectedChunkHalo(): { cx: number; cz: number }[] {
  const out: { cx: number; cz: number }[] = [];
  for (let cx = -VIEW_RADIUS; cx <= VIEW_RADIUS; cx++) {
    for (let cz = -VIEW_RADIUS; cz <= VIEW_RADIUS; cz++) out.push({ cx, cz });
  }
  return out;
}
