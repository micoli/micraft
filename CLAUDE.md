# MicCraft

Minecraft client/server clone in **Kotlin Multiplatform** — multiplayer voxel game with procedural generation and a persistent server-side world.

## Modules

| Module | Path | Role |
|--------|------|------|
| `core` | `core/src/commonMain` | Domain model, protocol, physics, chunk generation — shared across all targets |
| `server` | `server/src/main/kotlin` | Ktor WebSocket, game loop, persistence |
| `app/webApp` | `app/webApp/src/wasmJsMain` | Web client (Kotlin/Wasm + BabylonJS) |
| `app/desktopApp` | `app/desktopApp/src/main` | Desktop client (JVM) |
| `app/shared` | `app/shared/src/commonMain` | Shared Compose code for desktop/web |

## Key source files

| File | Purpose |
|------|---------|
| `core/.../protocol/ClientMessage.kt` | All client→server messages (sealed class) |
| `core/.../protocol/ServerMessage.kt` | All server→client messages (sealed class) |
| `core/.../world/Block.kt` | `BlockType` enum + hardness + `BlockPos` |
| `core/.../world/WorldConstants.kt` | `WorldConstants`, `PlayerConstants` |
| `core/.../player/Player.kt` | `Vec3`, `Orientation`, `PlayerState` |
| `core/.../world/ItemType.kt` | `ItemType` enum |
| `core/.../world/WorldItem.kt` | `WorldItem(id, pos, type, count)` |
| `server/.../GameLoop.kt` | Tick driver, coordinates all tick processors |
| `server/.../session/PlayerSession.kt` | Per-player WebSocket session |
| `server/.../tick/BlockBreaker.kt` | Block-break progress + drop spawning |
| `server/.../world/DropConfig.kt` | YAML-driven drop table loader |
| `server/.../world/WorldItemManager.kt` | Tracks live world items |
| `app/webApp/.../GameClient.kt` | Client-side prediction + server reconciliation |
| `app/webApp/.../babylon/BabylonBindings.kt` | BabylonJS interop |
| `app/webApp/.../resources/mc_bindings.js` | JS-side BabylonJS binding glue |

## Protocol messages

**Client → Server** (`ClientMessage`):
- `Connect(playerName)` — join
- `MoveIntent(dx, dz, yaw, pitch, stance, jump, dy, flyToggle, speedUp, speedDown)`
- `ChunkUnload(positions)` — client unloaded these chunks
- `BlockBreakStart(pos)` / `BlockBreakStop`
- `Command(text)` — slash command
- `Disconnect(reason)`

**Server → Client** (`ServerMessage`):
- `Welcome(playerId, playerName, spawnPos)`
- `ChunkData(pos, topY, wireBlocks: ByteArray)`
- `PlayerUpdate(state: PlayerState)`
- `WorldUpdate(changes: List<BlockChange>)`
- `PlayerLeft(playerId)`
- `BlockBreakProgress(pos, progress, hardness)`
- `Notification(message)`
- `ItemsSpawned(items: List<WorldItem>)`
- `ItemDespawned(id)`
- `InventoryUpdate(inventory: Map<ItemType, Int>)`

## Domain types

**Block types**: `AIR BEDROCK STONE DIRT GRASS SAND SANDSTONE GRAVEL SNOW`
Hardness: BEDROCK=∞, STONE=5, SANDSTONE=4, DIRT/GRASS/GRAVEL=3, SAND=2, SNOW=1

**Item types**: `COBBLESTONE DIRT SAND GRAVEL SANDSTONE SNOWBALL FLINT`

**Drop config**: `data/drops/drops.yaml` — maps `BlockType → List<(ItemType, weight, minCount, maxCount)>`

**WorldConstants**: `CHUNK_SIZE=16`, `VIEW_RADIUS=2` (5×5 chunks), `Y ∈ [0, 1024]`

**PlayerConstants**: standing h=1.8/eye=1.62/speed=4.5 · sneaking h=1.5/eye=1.27/speed=1.3 · crawling h=0.6/eye=0.4/speed=1.0 · width=0.6

## Architecture

- **Server authoritative**: client sends `MoveIntent`, server validates and replies with `PlayerUpdate`.
- **Client-side prediction**: `GameClient` predicts XZ locally at ~60 fps, soft-corrects toward server. Y (gravity) is always server-authoritative.
- All simulation logic (AABB physics, chunk gen) lives in `core` to keep client prediction and server consistent.
- **Chunk rendering**: `VertexData` buffers per chunk (~200 draw calls). `WorldUpdate` triggers re-mesh of affected chunk.

## Data directory

```
data/
  drops/drops.yaml          # block → item drop table
  biomes/biomes.json        # biome definitions
  world/default_world/
    world.json              # world metadata
    players/Player.json     # persisted player states
    chunks/*.mcc.gz         # binary chunk files (DO NOT READ)
```

## Textures
[textures.md](.claude/textures.md)

## Entities / animations
Models use **bbmodel** (Blockbench) format. Example: `resources/player.bbmodel`

## Code conventions

- Prefer immutable types (`data class`, `value class`) for positions, orientations, and network messages.
- Centralise constants in `core` (`WorldConstants`, `PlayerConstants`). Never duplicate between client and server.
- All packages under `org.micoli.micraft.*`

## Server restart

After every server-side code change:
```bash
touch run.lock
```
The `./gradlew dev` watchdog kills/restarts the Ktor process. The web client reconnects automatically. **Always use `touch run.lock` — never ask the user to restart manually.**

## Debug texture mode

```bash
./gradlew devDebug   # then open http://localhost:8081/?debug&bx=8&by=2&bz=8
```
Single GRASS block at (8, 2, 8), player spawns at (8, 1, 14) in fly mode.
Keys 1–6 position the camera on each face (+Z, -Z, +X, -X, +Y, -Y).

## Commands

```bash
./gradlew dev                                      # server :8080 + webpack dev :8081
./gradlew devDebug                                 # debug texture mode
rtk ./gradlew build                                # full build
rtk ./gradlew test                                 # all tests
rtk ./gradlew :server:test
rtk ./gradlew :app:shared:jvmTest
rtk ./gradlew ktlintCheck
```

## Rules

- Always prefer `./gradlew` (never `gradle` directly).
- Use `rtk` before verbose commands (build, test, diff, status, find).
- Do not run unfiltered `find`, `ls -R`, `git diff`, or `gradlew test` without `rtk`.
- Read only necessary files.
- Never start the server or web client — the user runs these themselves.
- Do not read `data/world/default_world/chunks/` — binary compressed files, useless to read.
