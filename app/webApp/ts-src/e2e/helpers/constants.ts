// Derived from server/build.gradle.kts:runE2eServer — MICRAFT_E2E_BOUNDS=8x8, groundY=64 — and
// GameConfig spawn (8, 200, 8). The player falls and settles standing on the grass at y=64.
export const GROUND_Y = 64;
export const SPAWN_X = 8;
export const SPAWN_Z = 8;

// Feet rest on top of the grass block. Confirmed ~65 on the first real run.
export const SETTLED_Y = GROUND_Y + 1;
