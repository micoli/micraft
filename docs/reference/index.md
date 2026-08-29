---
title: Reference
---

# Reference

The tables in this section are **generated from the real sources** — bundled
config under `resources/config/`, per-block yaml under `resources/blocks/`, and
the shared constants in `core`.

- Generate: `make docs` (`:server:generateReferenceDocs`).
- Verify: `make check-docs` (`:server:checkReferenceDocs`) — runs in CI and fails
  the build if a committed fragment drifts from the code.

Do not edit `docs/reference/_generated/*.md` by hand.

| Page | Source |
|------|--------|
| [World & player constants](constants.md) | `core` — `WorldConstants`, `PlayerConstants` |
| [Configuration files](config-files.md) | `resources/config/*.yaml` ↔ `server/src/main/resources/schemas/` |

Domain tables are embedded in their topic pages:
[Blocks](../world/blocks-catalog.md) ·
[Items](../gameplay/inventory-items.md) ·
[Recipes](../gameplay/crafting.md) ·
[Biomes](../world/biomes.md) ·
[Classes](../rpg/classes.md) ·
[Experience](../rpg/progression.md) ·
[Weather](../world/weather.md) ·
[Keybindings](../systems/keybindings.md).
