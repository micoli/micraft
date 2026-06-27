# MicCraft

Minecraft client/server clone — **Kotlin Multiplatform**, multiplayer voxel, procedural gen, persistent server world.

## Modules

| Module | Path | Role |
|--------|------|------|
| `core` | `core/src/commonMain` | Domain model, protocol, physics, chunk gen — shared all targets |
| `server` | `server/src/main/kotlin` | Ktor WebSocket, game loop, persistence |
| `app/webApp` | `app/webApp/src/wasmJsMain` | Web client (Kotlin/Wasm + BabylonJS) |
| `app/desktopApp` | `app/desktopApp/src/main` | Desktop client (JVM) |
| `app/shared` | `app/shared/src/commonMain` | Shared Compose code desktop/web |

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
| `server/.../world/BlockRegistryLoader.kt` | Loads `data/config/blocks.yaml` → `BlockRegistry` |
| `server/.../world/ItemRegistryLoader.kt` | Loads `data/config/items.yaml` → `ItemRegistry` |
| `core/.../world/BlockRegistry.kt` | Singleton holding `BlockDefinition` per `BlockType` |
| `core/.../world/ItemRegistry.kt` | Singleton holding `ItemDefinition` per `ItemType` |
| `server/.../world/WorldItemManager.kt` | Tracks live world items |
| `server/.../auth/AuthProvider.kt` | `AuthProvider` interface + `AuthResult(playerId, displayName, token)` |
| `server/.../auth/TokenStore.kt` | UUID→AuthResult in-memory store, TTL 10 min |
| `server/.../auth/LocalAuthProvider.kt` | bcrypt login; re-reads `data/config/auth/users.yaml` on every call |
| `server/.../auth/OAuthProvider.kt` | Google Authorization Code flow |
| `server/.../auth/AuthRoutes.kt` | HTTP auth routes (see Auth section below) |
| `server/.../auth/AddUserCommand.kt` | In-game `/adduser <email> <password> [displayName]` |
| `server/.../auth/AddUserCli.kt` | CLI entry point — run via `./gradlew :server:addUser` |
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
- `Connect(playerName, userName, preferredLanguage, token)` — join; `token` required when server auth enabled
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
Properties (hardness, solid, minimapColor, modelElement) in `data/config/blocks.yaml`. `hardness: -1` = unbreakable.

**Item types**: `COBBLESTONE DIRT SAND GRAVEL SANDSTONE SNOWBALL FLINT`
Properties (buildable, placesBlock) in `data/config/items.yaml`.

**Drop config**: `data/config/drops.yaml` — maps `BlockType → List<(ItemType, weight, minCount, maxCount)>`

**WorldConstants**: `CHUNK_SIZE=16`, `VIEW_RADIUS=2` (5×5 chunks), `Y ∈ [0, 1024]`

**PlayerConstants**: standing h=1.8/eye=1.62/speed=4.5 · sneaking h=1.5/eye=1.27/speed=1.3 · crawling h=0.6/eye=0.4/speed=1.0 · width=0.6

## Architecture

- **Server authoritative**: client sends `MoveIntent`, server validates, replies `PlayerUpdate`.
- **Client-side prediction**: `GameClient` predicts XZ locally ~60 fps, soft-corrects toward server. Y (gravity) always server-authoritative.
- All simulation logic (AABB physics, chunk gen) in `core` — keeps client prediction and server consistent.
- **Chunk rendering**: `VertexData` buffers per chunk (~200 draw calls). `WorldUpdate` triggers re-mesh of affected chunk.

## Data directory

```
data/
  config/
    blocks.yaml             # block properties (hardness, solid, minimapColor, modelElement)
    items.yaml              # item properties (buildable, placesBlock)
    drops.yaml              # block → item drop table
    biomes.yaml             # biome definitions
    server.yaml             # server config (auth provider, world, physics)
    game.yaml               # gameplay constants (tick rate, gravity, etc.)
    npc.yaml                # NPC behavior constants
    npcs.yaml               # NPC definitions
    roads.yaml              # road generation config
    houses.yaml             # house generation config
    weather.yaml            # weather config
    spawns.json             # NPC spawn state (runtime)
    auth/users.yaml         # local auth users (email, passwordHash, displayName)
    personal/keybindings.yaml
    i18n/en.yaml
    i18n/fr.yaml
    schemas/                # JSON Schemas for YAML config files (VS Code validation)
  world/default_world/
    world.json              # world metadata
    players/Player.json     # persisted player states
    chunks/*.mcc.gz         # binary chunk files (DO NOT READ)
```

## UI (TypeScript / React)

Source in `app/webApp/ts-src/ui/`.

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

**Kotlin → JS bridge** (data flow for new state):
1. Add field to `McUiState` (Kotlin) → expose as `StateFlow`
2. Collect in `WebUiBridge` → call `BabylonBindings.jsXxx(json)`
3. `BabylonBindings`: `fun jsXxx(v: String) = js("mcXxx(v)")`
4. `GameUI.tsx`: `(window as any).mcXxx = (v) => dispatch({ type: 'xxx', data: ... })`
5. Add case to `reducer` in `GameUI.tsx`

**Adding new layout widget** (checklist):
1. `LayoutEngine.ts` — add entry to `DEFAULT_WIDGETS` and `MIN_WIDGET_SIZE`
2. `LayoutEditor.tsx` — add label to `WIDGET_LABELS` and color to `WIDGET_COLORS`
3. `GameUI.tsx` — pass `layoutStyle={widgetStyle(activeLayout, 'WIDGET_TYPE')}` to component
4. `fillMissingWidgets` called when editor opens — existing persisted layouts get new widget at default position automatically (no migration needed)

## Auth system

Provider selected via `data/config/server.yaml` → `auth.provider` (`none` | `local` | `oauth`). Default `none` = no auth.

**Flow**: client fetches `GET /api/auth/config` → login overlay shows matching UI → `POST /auth/login` or OAuth redirect → `TokenStore` issues UUID token (10-min TTL) → token sent in `ClientMessage.Connect` → `GameLoop.onConnect()` validates before creating session.

**HTTP routes** (all proxied through webpack dev server via `/auth` context):
| Route | Purpose |
|-------|---------|
| `GET /api/auth/config` | Returns `{"provider":"local\|oauth\|none"}` |
| `POST /auth/login` | `{email, password}` → `{token, displayName, playerId}` |
| `GET /auth/oauth/start?returnUrl=` | Redirect to Google |
| `GET /auth/callback?code=&state=` | Exchange code → redirect to `returnUrl#auth_token=&auth_name=` |
| `GET /auth/me` | `Authorization: Bearer <token>` → `{playerId, displayName}` |

**Adding local user**:
```bash
./gradlew :server:addUser -Pargs="email@example.com password [DisplayName]"
# or in-game: /adduser email@example.com password [DisplayName]
```

**Extending auth**: implement `AuthProvider` interface (`login`, `oauthStartUrl`, `oauthCallback`, `oauthReturnUrl`), add branch in `Application.module()`. Commands needing auth access `context.authProvider`.

**Login overlay** (`LoginOverlay.tsx`): fetches `/api/auth/config` on mount. Token stored in `sessionStorage`. OAuth token arrives in URL fragment `#auth_token=`. Result written to `loginResultRef.current` as `user\tplayerName\tlang\ttoken` — tab-separated, parsed in `main.kt`.

## Slash command
- Every in-game action (except movement) can have slash command; each bindable to key via keybinding
- Commands with arguments get autocompletion method attached

## Entities / animations
Models use **bbmodel** (Blockbench) format. Example: `resources/player.bbmodel`

```
node scripts/export_skin_presets.mjs ./resources/blockbench-export/.
```

## Code conventions

- Prefer immutable types (`data class`, `value class`) for positions, orientations, network messages.
- Centralise constants in `core` (`WorldConstants`, `PlayerConstants`). Never duplicate between client and server.
- All packages under `org.micoli.micraft.*`

## Server restart

After every server-side code change:
```bash
touch run.lock
```
`./gradlew dev` watchdog kills/restarts Ktor process. Web client reconnects automatically. **Always use `touch run.lock` — never ask user to restart manually.**

## Debug texture mode

```bash
./gradlew devDebug   # then open http://localhost:8081/?debug&bx=8&by=2&bz=8
```
Single GRASS block at (8, 2, 8), player spawns at (8, 1, 14) in fly mode.
Keys 1–6 position camera on each face (+Z, -Z, +X, -X, +Y, -Y).

## Docker execution

**All build/test/lint/run commands execute inside dev container — never directly on host.**

Dev container must be running (`make dev-up` in separate terminal, or detached).

```bash
# Run any command inside the container
make dc CMD="./gradlew :server:test"
make dc CMD="./gradlew :spotlessApply"
make dc CMD="npm run format"          # runs in /workspace/app/webApp/ts-src

# Open a shell
make shell

# Direct form (equivalent)
docker compose -f docker-compose.dev.yml exec micraft ./gradlew :server:test
```

`rtk` runs on host as hook proxy — wraps `docker compose exec` automatically. Do not add `rtk` inside container command; hook injects it at host level.

Restart server after server-side change (file in mounted volume, works from host or container):
```bash
touch run.lock   # from host — still valid
# or:
make dc CMD="touch run.lock"
```

## Commands

```bash
make dev-up                                          # start dev container (foreground)
make dc CMD="./gradlew dev"                          # server :8080 + webpack dev :8081
make dc CMD="./gradlew devDebug"                     # debug texture mode
make dc CMD="./gradlew build"                        # full build
make dc CMD="./gradlew test"                         # all tests
make dc CMD="./gradlew :server:test"                 # server tests only
make dc CMD="./gradlew :app:shared:jvmTest"
make dc CMD="./gradlew ktlintCheck"
make dc CMD="./gradlew :server:addUser -Pargs='email pass [name]'"
```

## Rules

- **Never run `./gradlew`, `npm`, `node`, or `gradle` directly on host.** Use `make dc CMD="..."`.
- Always prefer `./gradlew` (never `gradle` directly).
- Use `rtk` before verbose host-level commands (git diff, git status, find). For in-container commands via `make dc`, rtk applied automatically by hook.
- Never run unfiltered `find`, `grep`, `ls -R`, `git diff`, or `gradlew test` without `rtk`.
- Read only necessary files.
- Never start server or web client — user runs these.
- Never read `data/world/default_world/chunks/` — binary compressed, useless.
- Commits must respect Conventional Commits + Semantic Commit Messages standard; body ≤10 lines.
- Every server-side change (`server/src/main/`) needs new or updated test in `server/src/test/`. Run `make dc CMD="./gradlew :server:test"` before committing.
- Before any commit use `make dc CMD="./gradlew :spotlessApply"` and `make dc CMD="npm run format"` (ts-src working dir handled by Makefile target).

## Schema maintenance

JSON Schemas for YAML configs in `data/config/schemas/`. Keep in sync with Kotlin data classes:

| Modified file | Update schema |
|---|---|
| `core/.../world/BiomeDefinition.kt`, `Block.kt` | `data/config/schemas/biomes.schema.json` |
| `server/.../world/DropConfig.kt`, `core/.../world/ItemType.kt` | `data/config/schemas/drops.schema.json` |
| `server/.../world/BlockRegistryLoader.kt`, `core/.../world/BlockDefinition.kt`, `Block.kt` | `data/config/schemas/blocks.schema.json` |
| `server/.../world/ItemRegistryLoader.kt`, `core/.../world/ItemDefinition.kt`, `Block.kt` | `data/config/schemas/items.schema.json` |
| `server/.../world/KeyBindingsConfig.kt` | `data/config/schemas/keybindings.schema.json` |
| `server/.../world/I18nConfig.kt` | `data/config/schemas/i18n.schema.json` |
| `server/.../world/ServerConfigLoader.kt` (`AuthSection`, `OAuthConfig`, `LocalAuthConfig`) | `data/config/schemas/server.schema.json` |
| `server/.../auth/LocalAuthProvider.kt` (`UserEntry`, `UsersConfig`) | `data/config/schemas/auth-users.schema.json` |

When adding/removing/renaming field or enum value in these data classes, update corresponding schema in same commit.

## i18n (translations)

Translation YAML files in `data/config/i18n/{locale}.yaml` (e.g. `en.yaml`, `fr.yaml`).

Key format: `feature:scope:key` where scope is `server` or `client`.

- **Server notifications** (sent via `ServerMessage.Notification`) → always use `context.i18n.t(session.state.language, "feature:server:key", ...args)`.
- **Client UI strings** → served via `GET /api/i18n/{locale}`, accessed in TypeScript via `window.mcT("feature:client:key")`.
- `I18nConfig` instantiated once in `GameLoop`, reloaded with `/reload`.
- Player language stored in `PlayerState.language`, persisted to `players/*.json`.
- Language changed with `/lang <locale>` or language selector in login overlay.

**Adding new user-visible strings:**
1. Add key to **both** `data/config/i18n/en.yaml` and `data/config/i18n/fr.yaml`.
2. Server-side: `context.i18n.t(session.state.language, "feature:server:key", ...args)`.
3. Client-side (TypeScript): `window.mcT("feature:client:key")`.