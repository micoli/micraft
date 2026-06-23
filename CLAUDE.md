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
| `server/.../world/BlockRegistryLoader.kt` | Loads `data/blocks/blocks.yaml` → `BlockRegistry` |
| `server/.../world/ItemRegistryLoader.kt` | Loads `data/items/items.yaml` → `ItemRegistry` |
| `core/.../world/BlockRegistry.kt` | Singleton holding `BlockDefinition` per `BlockType` |
| `core/.../world/ItemRegistry.kt` | Singleton holding `ItemDefinition` per `ItemType` |
| `server/.../world/WorldItemManager.kt` | Tracks live world items |
| `app/webApp/.../GameClient.kt` | Client-side prediction + server reconciliation |
| `app/webApp/.../babylon/BabylonBindingsScene.kt` | BabylonJS interop — engine, scene, camera, lights |
| `app/webApp/.../babylon/BabylonBindingsWorld.kt` | BabylonJS interop — meshes, materials, chunk geometry, block defs, fog/sky |
| `app/webApp/.../babylon/BabylonBindingsInput.kt` | BabylonJS interop — keyboard, mouse, camera controls, target/break overlays, event queue |
| `app/webApp/.../babylon/BabylonBindingsUI.kt` | BabylonJS interop — overlays, console, hotbar, HUD, layout, minimap, autocomplete |
| `app/webApp/.../babylon/BabylonBindingsModels.kt` | BabylonJS interop — player model, NPC models, FP arms |
| `app/webApp/.../babylon/BabylonBindingsUtil.kt` | BabylonJS interop — logging, URL/page utils, i18n, biome colors, block registry, debug camera |
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
- `RegistrySync(blocks: List<BlockInfo>, items: Map<String, ItemInfo>)` — sent on connect and `/reload`; client uses for AO, minimap colors

## Domain types

**Block types**: `AIR BEDROCK STONE DIRT GRASS SAND SANDSTONE GRAVEL SNOW OAK_LOG OAK_LEAVES PINE_LOG PINE_LEAVES PINE_LEAVES_SNOW FLOWER WEED`
Properties (hardness, solid, minimapColor, modelElement) are in `data/blocks/blocks.yaml`. `hardness: -1` = unbreakable.

**Item types**: `COBBLESTONE DIRT SAND GRAVEL SANDSTONE SNOWBALL FLINT`
Properties (buildable, placesBlock) are in `data/items/items.yaml`.

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
  blocks/blocks.yaml        # block properties (hardness, solid, minimapColor, modelElement)
  items/items.yaml          # item properties (buildable, placesBlock)
  drops/drops.yaml          # block → item drop table
  biomes/biomes.yaml        # biome definitions
  schemas/                  # JSON Schemas for YAML config files (VS Code validation)
  world/default_world/
    world.json              # world metadata
    players/Player.json     # persisted player states
    chunks/*.mcc.gz         # binary chunk files (DO NOT READ)
```

## UI (TypeScript / React)

Source lives in `app/webApp/ts-src/ui/`.

| File | Purpose |
|------|---------|
| `LayoutEngine.ts` | Grid math (`DEFAULT_WIDGETS`, `MIN_WIDGET_SIZE`, `widgetStyle`, `fillMissingWidgets`) |
| `types.ts` | `UiState`, `UiAction`, `GameLayout`, `LayoutWidget` |
| `GameUI.tsx` | Central coordinator: state reducer, window-function bridge, widget render tree |
| `Inventory.tsx` | Draggable inventory bag (shown when `hotbarVisible`) |
| `ShortcutBar.tsx` | 10-slot bar with drag-drop from Inventory |
| `HUD.tsx` | Player stats overlay |
| `LayoutEditor.tsx` | Interactive layout editor (move/resize on 48×48 grid) |
| `ServerLog.tsx` | Chat/server log |
| `Console.tsx` | Command input box |
| `Notifications.tsx` | Toast notifications |

**Grid system**: 48×48 units mapped to viewport (`calc(n / 48 * 100vw/vh)`).

**Kotlin → JS bridge** (data flow for any new state):
1. Add field to `McUiState` (Kotlin) → expose as `StateFlow`
2. Collect in `WebUiBridge` → call `BabylonBindings.jsXxx(json)`
3. `BabylonBindings`: `fun jsXxx(v: String) = js("mcXxx(v)")`
4. `GameUI.tsx`: `(window as any).mcXxx = (v) => dispatch({ type: 'xxx', data: ... })`
5. Add case to `reducer` in `GameUI.tsx`

**Adding a new layout widget** (checklist):
1. `LayoutEngine.ts` — add entry to `DEFAULT_WIDGETS` and `MIN_WIDGET_SIZE`
2. `LayoutEditor.tsx` — add label to `WIDGET_LABELS` and color to `WIDGET_COLORS`
3. `GameUI.tsx` — pass `layoutStyle={widgetStyle(activeLayout, 'WIDGET_TYPE')}` to the component
4. `fillMissingWidgets` is called when the editor opens — existing persisted layouts get the new widget at its default position automatically (no migration needed)

## Slash command
 - each in game actions (except movement) can have a slash command, each slashcommand can be binded to a key though keybinding
 - when a command has an argument, arguments will have an autocompletion method attached
 
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
- Commits must respect Conventional Commits standard and Semantic Commit Messages standard, body must not exceed 10 lines
- Every server-side change (`server/src/main/`) must be accompanied by a new or updated test in `server/src/test/`. Run `rtk ./gradlew :server:test` to verify before committing.

## Schema maintenance

JSON Schemas for YAML configs live in `data/schemas/`. Keep them in sync with Kotlin data classes:

| Modified file | Update schema |
|---|---|
| `core/.../world/BiomeDefinition.kt`, `Block.kt` | `data/schemas/biomes.schema.json` |
| `server/.../world/DropConfig.kt`, `core/.../world/ItemType.kt` | `data/schemas/drops.schema.json` |
| `server/.../world/BlockRegistryLoader.kt`, `core/.../world/BlockDefinition.kt`, `Block.kt` | `data/schemas/blocks.schema.json` |
| `server/.../world/ItemRegistryLoader.kt`, `core/.../world/ItemDefinition.kt`, `Block.kt` | `data/schemas/items.schema.json` |
| `server/.../world/KeyBindingsConfig.kt` | `data/schemas/keybindings.schema.json` |
| `server/.../world/I18nConfig.kt` | `data/schemas/i18n.schema.json` |

Whenever you add/remove/rename a field or enum value in these data classes, update the corresponding schema in the same commit.

## i18n (translations)

Translation YAML files live in `data/i18n/{locale}.yaml` (e.g. `en.yaml`, `fr.yaml`).

Key format: `feature:scope:key` where scope is `server` or `client`.

- **Server notifications** (sent via `ServerMessage.Notification`) → always go through `context.i18n.t(session.state.language, "feature:server:key", ...args)`.
- **Client UI strings** → served via `GET /api/i18n/{locale}` and accessed in TypeScript via `window.mcT("feature:client:key")`.
- `I18nConfig` is instantiated once in `GameLoop` and reloaded with `/reload`.
- Player language is stored in `PlayerState.language` and persisted to `players/*.json`.
- Language is changed with `/lang <locale>` or the language selector in the login overlay.

**When adding new user-visible strings:**
1. Add the key to **both** `data/i18n/en.yaml` and `data/i18n/fr.yaml`.
2. Use `context.i18n.t(session.state.language, "feature:server:key", ...args)` server-side.
3. Use `window.mcT("feature:client:key")` client-side (TypeScript).