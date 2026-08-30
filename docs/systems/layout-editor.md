---
title: HUD layout editor
---

# HUD layout editor

## How to play

- **`layout_editor`** key (`G`) or **`/layouts`** — open the editor.
- Move and resize widgets on a **48×48 grid**; the layout is persisted per player.
- **`/layout <name>`** — switch to a named layout.

Every registered HUD widget (hotbar, bars, minimap, quest tracker, action bar,
target frame, pet HUD, …) can be repositioned.

| Route | Purpose |
|-------|---------|
| `GET /api/layout/registry` | all widgets registered for the editor |

## Configuration

Layouts are player-scoped runtime data stored in the player file. Widgets
register themselves in code; there is no layout YAML.
