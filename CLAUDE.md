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

## Textures
  [textures.md](.claude/textures.md)

## Code conventions

- Prefer immutable types (`data class`, `value class`) for positions, orientations, and network messages.
- Centralise gameplay constants in `core` (`WorldConstants`, `PlayerConstants`).
- Never duplicate constants between client and server.

## Testing / triggering a server restart

After every server-side code change, touch `run.lock` to restart the server:

```bash[comment une texture est appliquée sur un bloc (ori.md](../../Downloads/comment%20une%20texture%20est%20appliqu%C3%A9e%20sur%20un%20bloc%20%28ori.md)
touch run.lock
```

The `./gradlew dev` watchdog detects the modification and kills/restarts the Ktor process automatically. The web client reconnects on its own (it shows a "DISCONNECTED" overlay while waiting). **Always use `touch run.lock` instead of manually restarting the server or asking the user to do it.**

## Debug texture mode

To inspect block textures face by face, use the `devDebug` task:

```bash
./gradlew devDebug
```

This starts the server with `MICRAFT_DEBUG_WORLD=1`:
- Single GRASS block at world (8, 2, 8) — all other positions are AIR
- Player spawns at (8, 1, 14) in fly mode, facing the block

Then open: **`http://localhost:8081/?debug&bx=8&by=2&bz=8`**

| Key | Camera position |
|-----|----------------|
| 1 | Face +Z (front) |
| 2 | Face -Z (back) |
| 3 | Face +X (right) |
| 4 | Face -X (left) |
| 5 | Face +Y (top) |
| 6 | Face -Y (bottom) |
| Échap | Libère le verrou caméra |

`run.lock` continue de fonctionner normalement (redémarre le serveur en conservant le mode debug).
To target a different block, change `bx`, `by`, `bz` in the URL.

## Commands

```bash
# Development: starts server (:8080) + webpack dev server (:8081) in parallel
# Watches run.lock — touch it to restart the server on the fly
./gradlew dev

# Debug texture mode (single block world + keys 1-6)
./gradlew devDebug   # then open http://localhost:8081/?debug&bx=8&by=2&bz=8

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
