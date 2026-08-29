---
title: Admin panel
---

# Admin panel

`/admin` (React app at `/admin/`) is the operations console. Access is gated by
[RBAC](../systems/auth-rbac.md) group permissions.

| Page | Covers |
|------|--------|
| [Status](status.md) | TPS, players, chunks, game time, heap/CPU, restart |
| [Users & Players](users-players.md) | auth accounts, groups, keybindings, preferences, RPG stats, rename |
| [NPCs](npcs.md) | live WebSocket feed with filters, animal-state detail |
| [Classes](classes.md) | RPG class definitions and per-level attack/spell access |
| [Worlds](worlds.md) | world browser, create new world |
| [Config](config.md) | live YAML editor with JSON Schema validation |
| [Instances](instances.md) | instance zones over generated chunks |
| [Scenes](scenes.md) | off-world block-structure buffers |
| [World simulator](../world/world-simulator.md) | exercise world systems safely |

All admin data is also available under `GET /api/admin/*` — see the
[HTTP API routes](../api-routes.md).
