# MicCraft

— **Kotlin Multiplatform**, multiplayer voxel, procedural gen, persistent server world.

## Modules

| Module | Path | Role |
|--------|------|------|
| `core` | `core/src/commonMain` | Domain model, protocol, physics, chunk gen — shared all targets |
| `server` | `server/src/main/kotlin` | Ktor WebSocket, game loop, persistence |
| `app/webApp` | `app/webApp/src/wasmJsMain` | Web client (Kotlin/Wasm + BabylonJS) |
| `app/desktopApp` | `app/desktopApp/src/main` | Desktop client (JVM) |
| `app/shared` | `app/shared/src/commonMain` | Shared Compose code desktop/web |

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

## Key source files

`/key-source-files`


## Protocol messages

`/protocol-messages`

Wire id = the `@ProtoId(n)` on each `ServerMessage` / `ClientMessage` subclass. The
`ServerMessageCodec` / `ClientMessageCodec` registries are **generated** by `:codec-processor`
(KSP, runs on `kspCommonMainKotlinMetadata`) — never hand-edit them. New message = add the
subclass with the next free `@ProtoId`; the build fails on a missing / duplicate / non-contiguous id.

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


## Entities / animations
Models use **bbmodel** (Blockbench) format. Example: `resources/models/articulated/articulated.bbmodel`

```
node scripts/export_skin_presets.mjs ./resources/blockbench-export/.
```

**Skin config**: optional `resources/models/<name>/<name>.yaml` (overridable in `data/resources/models/<name>/<name>.yaml`), served by `GET /api/skins/{name}/config`.
`eyes: {x,y,z}` = first-person camera anchor in bbmodel pixels (16 px = 1 block, feet at y=0);
`firstPersonHiddenBones` = bones hidden (subtree included) while in first person.
First person shows the real player model minus those bones — there is no separate FP arm rig.
Skins without a yaml fall back to `PlayerConstants` stance eye offsets.


## Code conventions

- Prefer immutable types (`data class`, `value class`) for positions, orientations, network messages.
- Centralise constants in `core` (`WorldConstants`, `PlayerConstants`). Never duplicate between client and server.
- All packages under `org.micoli.micraft.*`

## Makefile

`make help` lists every target, grouped, self-documented (each rule has an inline `## description`) — check it before assuming a command doesn't exist. Key targets by task:

- **After any server-side code change**: `make dev-restart-server` (never ask the user to restart manually)
- **After any WASM/Kotlin client change**: `make build` (detect changes, rebuild JS→WASM→server, restart, browser auto-reloads); `make build-all` forces a full rebuild; `make build-wasm` is WASM-only
- **Lint before commit**: `make quick-code-standard` (modified files only) or `make code-standard` (full) — never call `make dc CMD="./gradlew :spotlessApply"` / `npm run format` standalone
- **Stale cache / proto errors**: `make dev-reset-wasm` → `make dev-reset` (~2 min) → `make dev-nuke` (nuclear), in escalating order
- **Anything else in-container**: `make dc CMD="..."`, or `make shell` for a bash shell

Rebuild outputs write directly into `app/webApp/build/web/` (the only dir Ktor serves) — a browser hard-refresh is always enough, no manual copy step.

## Docker execution

**All build/test/lint/run commands execute inside the dev container — never directly on host.** Dev container must be running (`make dev-up`). `rtk` runs on host as a hook proxy wrapping `docker compose exec` automatically — never add `rtk` inside a `make dc CMD="..."` string.

## Rules

- **Never run `./gradlew`, `npm`, `node`, or `gradle` directly on host.** Use `make dc CMD="..."` (prefer `./gradlew`, never bare `gradle`).
- Always view files in docker instance, not on host filesystem
- Use `rtk` before verbose host-level commands (git diff, git status, find). For in-container commands via `make dc`, rtk applied automatically by hook.
- Never run unfiltered `find`, `grep`, `ls -R`, `git diff`, or `gradlew test` without `rtk`.
- Read only necessary files.
- Never start server or web client — user runs these.
- Never read `data/world/default_world/chunks/` — binary compressed, useless.
- Commits must respect Conventional Commits + Semantic Commit Messages standard; body ≤10 lines.
- Every server-side change (`server/src/main/`) needs new or updated test in `server/src/test/`. Run `make dc CMD="./gradlew :server:test"` before committing.
- Before any commit use `make dc CMD="./gradlew :spotlessApply"` and `make dc CMD="npm run format"` (ts-src working dir handled by Makefile target).
- never update mc_bindings.js, it's a generated JS-side BabylonJS binding glue, you should rather update source files.
- never edit `app/webApp/ts-src/generated/api/**` by hand — TanStack Query hooks/types generated from `server/openapi/openapi.yaml` via `make gen-api` (or `npm run gen:api`). Committed like mc_bindings.js, regenerate after changing a server route.

## Schema maintenance

JSON Schemas in `data/config/schemas/`. See `/update-schema` for the full mapping table. Update schema in same commit as data class changes.

**Not to be confused with** `server/openapi/openapi.yaml` — the REST API spec (auth/game/admin/map HTTP routes), auto-generated from `io.github.smiley4.ktoropenapi`-annotated Ktor routes, unrelated to the `data/config/schemas/` game-data JSON Schemas above. Never hand-edit it; regenerate with `make dc CMD="./gradlew :server:exportOpenApi"` after adding/changing a route (also regenerates the README.md "API Routes" table). `make check-openapi` (part of `code-standard`) fails CI if either drifts from the annotated routes. Browsable at `/api/docs` (Redoc) when the server is running.

## Documentation site

MkDocs + Material in `docs/**` (`mkdocs.yml`, nav `docs/SUMMARY.md`), published to
`micoli.github.io/micraft` via `.github/workflows/docs.yml`. Reference tables under
`docs/reference/_generated/` are generated from bundled config + `core` constants by
`:server:generateReferenceDocs` and verified by `:server:checkReferenceDocs` (part of
`make check-docs` / `code-standard` / CI). `scripts/docs/gen_docs.py` builds the
slash-commands / api-routes / releases pages at `mkdocs build` time. After any
`data/config/*.yaml` default or `*Constants` change: `make docs`. Preview:
`make docs-site-serve`. Full workflow: **`/update-docs`**. Positioning: "multiplayer
RPG voxel game" — never "Minecraft".

## i18n (translations)

Translation YAML files in `data/config/i18n/{locale}.yaml`. Key format: `feature:scope:key` (scope = `server` or `client`).

- Server: `context.i18n.t(session.state.language, "feature:server:key", ...args)` via `ServerMessage.Notification`
- Client (TypeScript): `window.mcT("feature:client:key")` — served via `GET /api/i18n/{locale}`
- `I18nConfig` instantiated in `GameLoop`, reloaded with `/reload`. Language in `PlayerState.language`, changed via `/lang <locale>`.

**Adding new strings**: `/add-i18n`

## Dépendances (supply-chain)

Voir `SECURITY.md`. Règles :

- **Gradle** : versions uniquement dans `gradle/libs.versions.toml`. Toute add/bump →
  dans le même commit : `make security-locks` (régénère `gradle.lockfile`) +
  `make security-verify` (régénère `gradle/verification-metadata.xml`), review des 2 diffs.
- **npm** (`app/webApp/ts-src`) : jamais `npm install` en script/CI — toujours `npm ci`.
  Ajout via `npm install --save-exact <pkg>@<ver>`, review complète du diff `package-lock.json`.
- Ne jamais ajouter un repo Maven ou registre npm non déclaré dans `settings.gradle.kts` / `.npmrc`.
- `make security` lance toute la chaîne (locks + verify + audit + osv + sbom).
- GitHub Actions épinglées au SHA de commit ; Dependabot (`.github/dependabot.yml`) gère les bumps.

## Zone/npc tier per skill level
Skill level → zone tier mapping for future zone-tiered entities:

| skill level | npc/zone level |
|-------------|----------------|
| 1 | 1–5            |
| 2 | 6–10           |
| 3 | 11–15          |
| 4 | 16–20          |
| 5 | 21–25+         |

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
