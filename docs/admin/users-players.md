---
title: Users & Players
---

# Users & Players

## Users

Create, update and delete local auth accounts and assign
[RBAC groups](../systems/auth-rbac.md).

| Route | Purpose |
|-------|---------|
| `GET /api/admin/users` | all accounts |
| `POST /api/admin/users` | create |
| `PUT /api/admin/users/{email}` | update display name / groups |
| `DELETE /api/admin/users/{email}` | delete |

In-game: `/adduser`, `/rbac:setgroup`, `/rbac:removegroup`.

## Players

Inspect and edit player files: keybindings, preferences, RPG class/stats, rename.

| Route | Purpose |
|-------|---------|
| `GET /api/admin/players` / `GET /api/admin/players/{name}` | list / full file |
| `PUT /api/admin/players/{name}/keybindings` | overwrite bindings |
| `PUT /api/admin/players/{name}/preferences` | partial preference update |
| `PUT /api/admin/players/{name}/rpg` | partial RPG update |
| `PUT /api/admin/players/{name}/equipment` | owned/equipped armor & hands |
| `POST /api/admin/players/{name}/give` | give item / grant armor/weapon/tool |
| `POST /api/admin/players/{name}/rename` | rename |

Player files: `data/world/<world>/players/<name>.yaml`, schema
`player.schema.json`.
