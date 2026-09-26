# MiCraft

Multiplayer RPG voxel game — Kotlin Multiplatform, procedural generation, persistent server World.

## Read first

- **`CONTEXT.md`** — domain glossary. Name things with its terms (Character not "player id", Danger tier not "zone
  tier", Rank not "skill level") in code, docs, commits and replies.
- **`docs/adr/`** — accepted decisions. Read the ADRs touching an area before changing it; a change that contradicts
  one needs a new ADR, not a workaround.
- **`.scratch/<effort>/`** — backlog and specs (`docs/agents/issue-tracker.md`, statuses in `docs/agents/triage-labels.md`).

## Modules

| Module | Path | Role |
|--------|------|------|
| `core` | `core/src/commonMain` | Domain model, protocol, physics, chunk gen — shared by server and client (ADR-0002) |
| `server` | `server/src/main/kotlin` | Ktor WebSocket, game loop, persistence |
| `app/webApp` | `app/webApp/src/wasmJsMain` + `app/webApp/ts-src` | Web client: Kotlin/Wasm + BabylonJS, React UI (ADR-0008) |
| `app/minigames/<name>` | own npm projects | Mini-game bundles (ADR-0005, `app/minigames/README.md`) |
| `codec-processor` | KSP | Generates the protocol codec registries (ADR-0003) |

Kotlin navigation: the `LSP` tool (`kotlin-lsp`) for go-to-def, find-refs and cross-module rename; Grep/Read for raw text.

## Running commands

Every build, test, lint and codegen goes through `make`. `RUN_MODE` in `.env` (`HOST` | `DOCKER`) decides where it
runs; `make dc CMD="..."` runs an ad-hoc command in that mode (prefer `./gradlew`, never bare `gradle`). `make help`
lists every target — check it before assuming a command doesn't exist.

- **After a server-side change**: `make dev-restart-server` (you run it; never ask the user to restart).
- **After a Wasm/Kotlin client change**: `make build` (detects changes, rebuilds JS→Wasm→server, restarts, the
  browser auto-reloads); `make build-all` forces everything; `make build-wasm` is Wasm-only. A compile-only Gradle
  task leaves the served bundle stale — always go through these targets.
- **Lint**: `make quick-code-standard` (modified files) or `make code-standard` (full).
- **Stale cache / proto errors**, escalating: `make dev-reset-wasm` → `make dev-reset` (~2 min) → `make dev-nuke`.
- Outputs land in `app/webApp/build/web/` (the only directory Ktor serves); a hard refresh is enough.
- The user starts the server and web client; you only restart/rebuild through the targets above.
- `rtk` wraps host commands through a hook; keep it out of `make dc CMD="..."` strings.

## Rules

- **Commits**: Conventional Commits, body ≤ 10 lines. Before committing: `make quick-code-standard`, and for any
  `server/src/main/` change `make dc CMD="./gradlew :server:test"`.
- **Server changes** come with a new or updated test in `server/src/test/`.
- **E2E tests** (`app/webApp/ts-src/e2e/`) drive the game the way a player does: slash commands
  (`actions(page).runCommand("/…")`), real key presses (`page.keyboard.press`), clicks on in-game UI. Assertions only
  *read* `window.mcE2E`; to expose new state, add a field to `E2eSnapshot` fed from the same server message the UI
  consumes. Per-test Account/World via `accountFor(testInfo)`. Never push raw events/WebSocket messages or mutate
  game state in memory.
- **Generated files — regenerate, never hand-edit**:
  - `mc_bindings.js` (BabylonJS glue): edit its sources.
  - Protocol codec registries: add the message subclass with the next free `@ProtoId`.
  - `server/openapi/openapi.yaml` + README "API Routes": `make dc CMD="./gradlew :server:exportOpenApi"` after a route
    change, then `make gen-api` for `app/webApp/ts-src/generated/api/**`. Checked by `make check-openapi`.
  - JSON Schemas (`server/src/main/resources/schemas/`): `make gen-schemas` in the same commit as the data class
    change. Checked by `make check-schemas`.
  - Reference docs (`docs/reference/_generated/`): `make docs` after a config default or `*Constants` change.
- **Every in-game action** (except movement) has a slash command bindable to a key; commands with arguments ship an
  autocompletion method.
- **Code**: immutable types (`data class`, `value class`) for positions, orientations and messages; constants live in
  `core` (`WorldConstants`, `PlayerConstants`); packages under `org.micoli.micraft.*`; no new process-global
  mutable state (ADR-0004).
- Skip `data/world/*/chunks/` (binary, compressed).

## Dépendances (supply-chain)

Voir `SECURITY.md`. Règles :

- **Gradle** : versions uniquement dans `gradle/libs.versions.toml`. Toute add/bump → dans le même commit :
  `make security-locks` (régénère `gradle.lockfile`) + `make security-verify` (régénère
  `gradle/verification-metadata.xml`), review des 2 diffs.
- **npm** (`app/webApp/ts-src`) : `npm ci` en script/CI. Ajout via `npm install --save-exact <pkg>@<ver>`, review
  complète du diff `package-lock.json`.
- Seuls les repos Maven / registres npm déclarés dans `settings.gradle.kts` / `.npmrc`.
- `make security` lance toute la chaîne (locks + verify + audit + osv + sbom).
- GitHub Actions épinglées au SHA de commit ; Dependabot (`.github/dependabot.yml`) gère les bumps.

## Where to look

| Task | Reference |
|------|-----------|
| Auth, login flow, tokens, admin API, Test worlds, E2E player setup | `docs/agents/auth.md` |
| Log levels at runtime, Rec XZ / Prediction gap | `docs/agents/debugging.md` |
| Docs site, generated pages, screenshots | `/update-docs` (positioning: "multiplayer RPG voxel game") |
| Translations (`server/src/main/resources/i18n/{locale}.yaml`, keys `feature:server\|client:key`) | `/add-i18n` |
| Key bindings, widgets, UI state, React conventions | `/add-keybinding`, `/add-widget`, `/add-ui-state`, `/ui-react` |
| Data directory layout, key source files | `/data-directory`, `/key-source-files` |
| New armor / NPC model / player preference | `/add-armor`, `/add-npc-model`, `/add-player-preferences` |
| Skin models: Blockbench `.bbmodel`, skin yaml (`eyes`, `firstPersonHiddenBones`) | `docs/gameplay/movement.md`; export with `node scripts/export_skin_presets.mjs ./resources/blockbench-export/.` |
| Gameplay systems (mini-games, equipment, claims, quests…) | `docs/` (nav in `docs/SUMMARY.md`) |
