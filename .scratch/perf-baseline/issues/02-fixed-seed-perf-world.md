# Fixed-seed perf world

Status: needs-triage
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
