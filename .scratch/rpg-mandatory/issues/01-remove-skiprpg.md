# Make RPG mandatory: remove /skiprpg and rpgOptOut

Status: needs-triage
Type: task

## Context

Domain decision (CONTEXT.md, 2026-09-26): every **Character** has a Class, Level and stats. There is no
non-RPG Character any more. The code still models RPG as opt-in.

## Current state

- `server/.../game/rpg/character/SkipRpgCommand.kt`: `/skiprpg` sets `rpgOptOut = true`, i18n `rpg:server:skipped_rpg` (en/fr).
- `core/.../player/Player.kt:92-93`: `characterData: CharacterData? = null`, `rpgOptOut: Boolean = true`.
- Defaults disagree:
  - `PlayerState` defaults `rpgOptOut = true`.
  - `http/CharacterController.kt:114` creates new characters with `rpgOptOut = true`, but `:205` uses `false`.
  - `GameLoop.kt:1968-1970` falls back to `false` on load.
- `GameLoop.kt:2131`: sends `CharacterCreationRequired` only when not opted out.
- `ExperienceProcessor.kt:95`, `:216`: skip XP for opted-out Characters.
- Schema: `server/src/main/resources/schemas/player.schema.json:398` (`rpgOptOut`).
- Docs: `docs/rpg/index.md:7-8` ("The RPG layer is **opt-in**").
- Tests: `SkipRpgCommandTest`, `CreateCharacterCommandTest:101-106`, `ExperienceProcessorTest` (rpgOptOut cases), `GameLoopTest:249`.

## Scope

- Delete `SkipRpgCommand` and its test, and remove the i18n key.
- Remove `rpgOptOut` from `PlayerState`, the schema, `GameLoop`, `CharacterController` and `ExperienceProcessor`.
- A Character without `characterData` always gets `CharacterCreationRequired`.
- Update `docs/rpg/index.md` and the generated slash-command reference (`make docs`).

## Open questions

- Existing save files with `rpgOptOut: true` and no `characterData`: should they be forced through creation on next login, or migrated?
- Can `characterData` become non-null once creation is mandatory at Character creation (the REST `CharacterController` path)? That would remove the nullable checks, but it is a bigger change.

## Acceptance

- `/skiprpg` is unknown; no `rpgOptOut` remains in code, schema or docs.
- Logging in with a Character that has no `characterData` always routes to RPG creation.
- `make dc CMD="./gradlew :server:test"` passes.
