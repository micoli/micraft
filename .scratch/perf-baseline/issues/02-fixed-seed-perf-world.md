# Fixed-seed perf world

Status: resolved
Type: task

## Goal

`make perf-server` starts the server against a throwaway, fully procedural World with a fixed seed, so every run
generates the same terrain, NPCs and vegetation.

## Notes

- The seed comes from `GameConfig.worldSeed` (`di/WorldModule.kt:42`, `:79`); the World is selected by
  `MICRAFT_WORLD_NAME`, and the data root by `MICRAFT_DATA_DIR` (`config/ConfigPaths.kt`).
- The data root is fresh for each run (wiped `perf/.data/`), so chunk generation is measured instead of loaded from
  disk. An optional `PERF_KEEP_WORLD=1` keeps an already-generated world, to measure loading.
- Full tick sections and NPC auto-spawn stay enabled, so the run behaves like production (unlike `buildE2eGameWorld`).
- Auth: `local`, `requirePassword: false`, and the admin API is reachable so the runners can create Characters.

## Acceptance

- Two consecutive runs produce identical chunks around spawn (the hash of the generated chunk files matches).
- `make perf-server` / `make perf-server-stop` are documented in `make help`.

## Answer

Implemented 2026-09-26:

- Gradle `cleanPerfData` → `seedPerfAdmin` → `runPerfServer` (group `perf`), with data root `perf/.data/` (gitignored),
  World `perf_world`, port 8092, and `worldSeed` 42 from the bundled defaults. Options: `-PperfKeepWorld`, `-PperfXmx`, `-PperfPort`.
- Pitchfork daemon `perf-server` + `make perf-server` / `make perf-server-stop`.
- Runner account: `perf-admin@test.local` / `perf-admin-password`, `admin` group.
- Fix: `auth.local.usersFile` / `groupsFile` ignored `MICRAFT_DATA_DIR` (paths hardcoded under `data/`). They now go
  through `ConfigPaths.dataPath`, so a path under `data/` follows the data root.
- Determinism: terrain generation is already covered by the `sameSeed_*` tests in `ProceduralChunkGeneratorTest`. The
  two-run chunk-hash check was not run end to end. NPC spawning and vegetation growth still use unseeded randomness,
  so expect some run-to-run variance in scenarios that depend on them.
