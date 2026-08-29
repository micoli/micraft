---
title: Codex
---

# Codex

## How to play

**`/codex`** opens the in-game codex — a browsable reference of **blocks**,
**items**, and the **bestiary** (NPC types with their codex info). It is
populated from the same registries the server uses, so it always reflects the
active configuration.

## Configuration

The codex has no config of its own. Its content is derived from:

- Blocks — [`resources/blocks/`](building-blocks.md)
- Items — [`items.yaml`](inventory-items.md)
- Bestiary — NPC type definitions, see [NPCs](../entities/npcs.md)

The admin panel exposes the same data at `GET /api/admin/blocks`,
`GET /api/admin/items` and `GET /api/admin/npc-types`.
