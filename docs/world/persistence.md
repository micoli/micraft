---
title: World persistence
---

# World persistence

## Overview

- **Chunks** — saved as gzip-compressed binary (`.mcc.gz`) under
  `data/world/<world>/chunks/`. Do not read these directly.
- **Players** — saved as YAML under `data/world/<world>/players/<name>.yaml`
  (state, keybindings, RPG data). Schema `player.schema.json`.
- **NPCs and vegetation state** — flushed every 600 ticks alongside dirty chunks.

`/save` forces an immediate flush of world and player state.

## Configuration

Persistence cadence and world root are wired in the server. The active world name
comes from the `MICRAFT_WORLD_NAME` environment variable (defaults to
`default_world`). Manage worlds from the [admin Worlds page](../admin/worlds.md)
or `POST /api/admin/worlds`.

The `persistence:` block of [`server.yaml`](../systems/server-config.md) exposes
the tunables.
