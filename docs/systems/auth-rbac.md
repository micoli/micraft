---
title: Auth & RBAC
---

# Auth & RBAC

## Providers

Selected via `data/config/server.yaml` → `auth.provider`:

| Provider | Behaviour |
|----------|-----------|
| `none` | no auth (default) — `POST /auth/noauth-login` creates/reuses an account by email |
| `local` | email + password, bcrypt hashed |
| `oauth` | Google Authorization Code flow |

### Flow

Client `GET /api/auth/config` → login overlay picks UI → `POST /auth/login` or
OAuth redirect → `TokenStore` issues a UUID token (10-min TTL) → token sent in
`ClientMessage.Connect` → `GameLoop.onConnect()` validates before creating a
session.

| Route | Purpose |
|-------|---------|
| `GET /api/auth/config` | `{"provider": "..."}` |
| `POST /auth/login` | `{email, password}` → `{token, displayName, playerId}` |
| `GET /auth/oauth/start?returnUrl=` | redirect to Google |
| `GET /auth/callback?code=&state=` | exchange code → redirect with token fragment |
| `GET /auth/me` | `Bearer <token>` → `{playerId, displayName}` |

### Adding a local user

```bash
./gradlew :server:addUser -Pargs="email@example.com password [DisplayName]"
# or in-game: /adduser email@example.com password [DisplayName] [group1,group2]
```

### Extending

Implement the `AuthProvider` interface (`login`, `oauthStartUrl`,
`oauthCallback`, `oauthReturnUrl`) and add a branch in `Application.module()`.

## RBAC

Group-based permissions. Groups are assigned with `/rbac:setgroup` /
`/rbac:removegroup`; each command's `permission` field is gated by the session's
groups; disabled commands per player are stored in player state.

`data/config/groups.yaml` (bundled default `resources/config/groups.yaml`, schema
`groups.schema.json`):

```yaml
groups: []
defaultGroups: [player]
```

In-game: `/rbac:listgroups`. Reload with `/reload` or `/config:reload rbac`.
