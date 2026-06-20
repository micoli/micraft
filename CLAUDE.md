# MicCraft

Minecraft client/server clone in **Kotlin Multiplatform** — multiplayer voxel game with procedural generation and a persistent server-side world.

## Modules

| Module | Path | Role |
|--------|------|------|
| `core` | `core/src/commonMain` | Domain model, network protocol, physics, chunk generation — shared across all targets |
| `server` | `server/src/main/kotlin` | Ktor WebSocket, game loop, persistence |
| `app/webApp` | `app/webApp/src/wasmJsMain` | Web client (Kotlin/Wasm + BabylonJS) |
| `app/desktopApp` | `app/desktopApp/src/main` | Desktop client (JVM) |
| `app/shared` | `app/shared/src/commonMain` | Shared Compose code for desktop/web |

## Architecture

- **Server authoritative**: the client sends `MoveIntent`, the server validates and replies with `PlayerUpdate`.
- **Client-side prediction**: `GameClient` predicts XZ position locally at ~60 fps and soft-corrects toward the server position. Y (gravity) is always server-authoritative.
- All simulation logic (AABB physics, chunk generation) lives in `core` to keep client prediction and server authority consistent.

## World rules

- Base unit: **1 block** (voxel).
- Elevation: **Y ∈ [0, 1024]** — enforce these bounds in all generation, collision, and position logic.
- Procedural generation: no static map.

## Players

| Stance  | Height | Width |
|---------|--------|-------|
| Standing | 1.8 b | 0.6 b |
| Sneaking | 1.5 b | 0.6 b |
| Crawling | 0.6 b | 0.6 b |

Eye offset (first-person camera): standing 1.62 b · sneaking 1.27 b · crawling 0.4 b.

## Code conventions

- Prefer immutable types (`data class`, `value class`) for positions, orientations, and network messages.
- Centralise gameplay constants in `core` (`WorldConstants`, `PlayerConstants`).
- Never duplicate constants between client and server.

## Commands

```bash
# Development: starts server (:8080) + webpack dev server (:8081) in parallel
./gradlew dev

# Full build
rtk ./gradlew build

# Server only
./gradlew :server:run

# Web client only (Wasm)
./gradlew :app:webApp:wasmJsBrowserDevelopmentRun

# Desktop client
./gradlew :app:desktopApp:run

# Tests
rtk ./gradlew test
rtk ./gradlew :server:test
rtk ./gradlew :app:shared:jvmTest

# Lint
rtk ./gradlew ktlintCheck
```

## Rules

- Always prefer `./gradlew` (never `gradle` directly).
- Use `rtk` in front of verbose commands (build, test, diff, status).
- Do not run unfiltered `find`, `ls -R`, `git diff`, or `gradlew test` without `rtk`.
- Read only necessary files; ask for more targeted output if a result is too long.
- Never start the server or web client (`./gradlew dev`, `:server:run`, `wasmJsBrowserDevelopmentRun`) — the user runs these themselves.
