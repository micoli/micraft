// Derived from server/build.gradle.kts:runE2eServer — MICRAFT_E2E_BOUNDS=8x8, groundY=64 — and
// GameConfig spawn (8, 200, 8). The player falls and settles standing on the grass at y=64.
export const GROUND_Y = 64;
export const SPAWN_X = 8;
export const SPAWN_Z = 8;

// Feet rest on top of the grass block. Confirmed ~65 on the first real run.
export const SETTLED_Y = GROUND_Y + 1;

// Centre of the bounded 8x8-chunk E2E world (chunks -4..3). Every spec except login-spawn
// re-centres here after god mode, a few blocks up, then lets gravity settle it (see connectClient).
export const CENTER_X = 0;
export const CENTER_Z = 0;
export const DROP_Y = 70;
