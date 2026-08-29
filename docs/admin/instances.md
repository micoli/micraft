---
title: Instances
---

# Instance zones

An **instance zone** wraps a set of already-generated chunks into a bounded,
optionally-disabled area — used for dungeons, arenas, and world-simulator test
beds. Each has Y bounds, an enabled flag, and a layout (clip planes + shortcut
bar).

| Route | Purpose |
|-------|---------|
| `GET/POST /api/admin/instances` | list / create over generated chunks |
| `GET/PUT/DELETE /api/admin/instances/{id}` | read / rename / delete |
| `PUT /api/admin/instances/{id}/bounds` | update Y bounds |
| `PUT /api/admin/instances/{id}/chunks` | update covered chunk set |
| `PUT /api/admin/instances/{id}/enabled` | enable / disable |
| `PUT /api/admin/instances/{id}/layout` | clip planes + shortcut bar |
| `GET /api/admin/instances/{id}/blocks` | non-air blocks as NDJSON (cap 300000) |
| `GET /api/admin/ws/instances/{id}` | live feed |

## Configuration

Instance zones are runtime data, not YAML — created and edited through the admin
panel or the routes above. Related: [Scenes](scenes.md),
[World simulator](../world/world-simulator.md).
