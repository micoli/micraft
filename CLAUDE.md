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

`/key-source-files`


## Protocol messages

`/protocol-messages`

## Domain types

**Block types**: `AIR BEDROCK STONE DIRT GRASS SAND SANDSTONE GRAVEL SNOW OAK_LOG OAK_LEAVES PINE_LOG PINE_LEAVES PINE_LEAVES_SNOW FLOWER WEED`
Properties (hardness, solid, minimapColor, modelElement) in `resources/blocks/<name>/<name>.yaml`, overridable per-block in `data/resources/blocks/<name>/<name>.yaml`. `hardness: -1` = unbreakable. Optional `drops:` list (`item`, `dropRate`, `minCount`, `maxCount`) — omitted if the block drops nothing.

**Biome types**: `snow_peaks desert dry_plains plains forest pine_forest`
Defined in `data/config/biomes.yaml` (optional — falls back to `BiomeRegistry.default()` if missing). Properties (surface/subsurface/filler blocks, elevationMin/Max, grassColor, vegetation entries) in `core/.../world/BiomeDefinition.kt`. Distributed via Voronoi zones by moisture value (0→1).

**Item types**: `COBBLESTONE DIRT SAND GRAVEL SANDSTONE SNOWBALL FLINT`
Properties (buildable, placesBlock) in `data/config/items.yaml`.

**WorldConstants**: `CHUNK_SIZE=16`, `VIEW_RADIUS=2` (5×5 chunks), `Y ∈ [0, 1024]`

**PlayerConstants**: standing h=1.8/eye=1.62/speed=4.5 · sneaking h=1.5/eye=1.27/speed=1.3 · crawling h=0.6/eye=0.4/speed=1.0 · width=0.6

## Architecture

- **Server authoritative**: client sends `MoveIntent`, server validates, replies `PlayerUpdate`.
- **Client-side prediction**: `GameClient` predicts XZ locally ~60 fps, soft-corrects toward server. Y (gravity) always server-authoritative.
- All simulation logic (AABB physics, chunk gen) in `core` — keeps client prediction and server consistent.
- **Chunk rendering**: `VertexData` buffers per chunk (~200 draw calls). `WorldUpdate` triggers re-mesh of affected chunk.

## Data directory

`/data-directory`

## UI (TypeScript / React)

`/ui-react`

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

## Armor system

Armor configs in `resources/armors/<name>/<name>.yaml`. Each defines `wearable` slot flags (`head`, `body`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`). Loaded by `ArmorRegistryLoader`.

**HTTP routes**:
| Route | Purpose |
|-------|---------|
| `GET /api/armors` | `Map<name, WearableSlots>` — all available armors |
| `GET /api/player/:name/armors` | currently equipped armor names |
| `GET /api/player/:name/skin` | `{skin: string}` |

**In-game**: `/equip <name>` / `/unequip <name>`. Client resolves slot conflicts before sending commands.
**UI**: `Character.tsx` (key `Y`, or Pause → Character). Shared preview: `PlayerModelPreview.tsx`.

## Entities / animations
Models use **bbmodel** (Blockbench) format. Example: `resources/skins/player/player.bbmodel`

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
make dev-restart-server
```
Pitchfork watchdog rebuilds and restarts Ktor. Web client reconnects automatically. **Never ask user to restart manually — always use `make dev-restart-server`.**

## WASM build

WASM watcher is **opt-in** (`boot_start = false`). Use these host-level commands:

```bash
make build-wasm        # one-shot WASM recompile (use after any Kotlin/WASM change)
make dev-reset-wasm    # proto decode errors / stale output after core type changes
make wasm-watch        # start continuous watcher (optional, for heavy WASM iteration)
make trigger-wasm      # trigger rebuild when wasm-watch is running
```

## Zone/npc tier per skill level
Skill level → zone tier mapping for future zone-tiered entities:

| skill level | npc/zone level |
|-------------|----------------|
| 1 | 1–5            |
| 2 | 6–10           |
| 3 | 11–15          |
| 4 | 16–20          |
| 5 | 21–25+         |


## JS / CSS build

esbuild watchers for mc_bindings, css, map, admin auto-start with the container.

```bash
make build-js          # one-shot mc_bindings rebuild (also copies babylon.js)
```

## DX — stale cache recovery

| Situation | Command |
|---|---|
| WASM stale / proto errors | `make dev-reset-wasm` |
| Everything weird | `make dev-reset` (stop all → clear caches → restart, ~2 min) |
| Nuclear | `make dev-nuke` (destroy all volumes + full restart) |

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
```

`rtk` runs on host as hook proxy — wraps `docker compose exec` automatically. Do not add `rtk` inside container command; hook injects it at host level.

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
- never update mc_bindings.js, it's a generated JS-side BabylonJS binding glue, you should rather update source files.

## Schema maintenance

JSON Schemas in `data/config/schemas/`. See `/update-schema` for the full mapping table. Update schema in same commit as data class changes.

## i18n (translations)

Translation YAML files in `data/config/i18n/{locale}.yaml`. Key format: `feature:scope:key` (scope = `server` or `client`).

- Server: `context.i18n.t(session.state.language, "feature:server:key", ...args)` via `ServerMessage.Notification`
- Client (TypeScript): `window.mcT("feature:client:key")` — served via `GET /api/i18n/{locale}`
- `I18nConfig` instantiated in `GameLoop`, reloaded with `/reload`. Language in `PlayerState.language`, changed via `/lang <locale>`.

**Adding new strings**: `/add-i18n`