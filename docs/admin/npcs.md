---
title: NPCs (admin)
---

# NPCs (admin)

A live WebSocket feed of every NPC instance, filterable by **type**, **zone**,
**level**, **gender** and **aggro**. The detail panel shows full animal state:
age, hunger, gestation, reproduction cooldown, mother level.

| Route | Purpose |
|-------|---------|
| `GET /api/admin/npcs` | live instances with full animal/combat state |
| `GET /api/admin/npc-types` | type definitions (codex info) |
| `GET /api/admin/ws/npcs` | live feed |

Gameplay side: [NPCs](../entities/npcs.md),
[Animal lifecycle](../entities/animal-lifecycle.md). Tuning:
[`npc.yaml`](../entities/npcs.md).
