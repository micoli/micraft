# Auth, tokens and the admin API (agent reference)

Vocabulary: **Account**, **Character**, **RBAC group**, **Admin role**, **Test world** (see `CONTEXT.md`); decisions in
ADR-0004 and ADR-0006.

## Provider

Provider selected via `data/config/server.yaml` → `auth.provider` (`local` | `oauth`). Default `local` with
`auth.local.requirePassword: false` — accounts still live in `users.yaml` with real groups, just without a password
check; an unknown email is auto-provisioned into `defaultGroups` on first login. Set `requirePassword: true` to
require a real bcrypt-checked password instead.

**Extending auth**: implement `AuthProvider` (`login`, `oauthStartUrl`, `oauthCallback`, `oauthReturnUrl`), add a
branch in `Application.module()`. Commands needing auth access `context.authProvider`.

**Adding a local user**: `./gradlew :server:addUser -Pargs="email@example.com password [DisplayName]"` (through
`make dc CMD="..."`), or in-game `/adduser email@example.com password [DisplayName]`.

## Login flow

Client fetches `GET /api/auth/config` → login overlay shows matching UI → `POST /auth/login` or OAuth redirect →
`TokenStore` issues a UUID token (10-min TTL) → token sent in `ClientMessage.Connect` → `GameLoop.onConnect()`
validates before creating the session.

| Route | Purpose |
|-------|---------|
| `GET /api/auth/config` | `{"provider":"local\|oauth","requirePassword":bool}` |
| `POST /auth/login` | `{email, password}` → `{token, displayName, playerId}` |
| `GET /auth/oauth/start?returnUrl=` | Redirect to Google |
| `GET /auth/callback?code=&state=` | Exchange code → redirect to `returnUrl#auth_token=&auth_name=` |
| `GET /auth/me` | `Authorization: Bearer <token>` → `{playerId, displayName}` |

All proxied through the webpack dev server via the `/auth` context.

**Login overlay** (`LoginOverlay.tsx`): fetches `/api/auth/config` on mount. Access token in `sessionStorage`. OAuth
token arrives in the URL fragment `#auth_token=`. Result written to `loginResultRef.current` as
`user\tplayerName\tlang\ttoken\trefreshToken` — tab-separated, parsed in `main.kt`.

**Refresh token**: `POST /auth/login`, `GET /auth/callback` and `POST /auth/refresh` all issue a refresh token next
to the access token (`TokenStore.issueRefreshToken` / `TokenStore.refresh`). Rotated on every use (the old one is
invalidated), valid 30 days, stored in `localStorage` (`authStorage.ts`) so it survives a tab close. `GameClient.kt`
refreshes proactively every 5 min and reactively on a `1008 VIOLATED_POLICY` close before falling back to a full
re-login.

## Admin API and Test worlds

World-scoped admin routes (`status`, `players/*`, `gametime`, `instances/*`, `claims/*`, `scenes/*`, `social/*`,
`npcs`) honor an `X-Micraft-Game-Session` header (WS edit/npcs sockets: `?gameSession=`). Absent / `default` /
outside `MICRAFT_E2E` → the default World; under `MICRAFT_E2E` a fresh id spawns a dedicated Test world (like the
`/game` WS). Process-level routes (`restart`, `reload`, `users`, `configs`, `schemas`, `loggers`) ignore it.
`AdminController.adminWorld()` resolves it via `GameWorldRegistry`.

`POST /api/admin/players` `{name, email?, characterClass?, str?…cha?}` reserves the Character id (and, with
`characterClass`, a fresh RPG character built by `RpgCharacterBuilder`) that `onConnect` consumes —
`GameWorld.reservedPlayers`. An E2E test's Character is therefore ready before the browser connects: the client gets
`CharacterSync`, never `CharacterCreationRequired`. Not persisted. E2E helpers:
`app/webApp/ts-src/e2e/helpers/admin.ts` (`admin()`, `createUser()`, `createPlayer()`); `connectClient()` calls
`createPlayer` (default WARRIOR). RPG character construction (point-buy + class bonus + derived HP/mana) lives once in
`RpgCharacterBuilder`, used by `/api/character/rpgcreate`, `/createcharacter` and `POST /api/admin/players`.
