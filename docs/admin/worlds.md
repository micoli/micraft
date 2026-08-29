---
title: Worlds
---

# Worlds

Browse worlds on disk (seed, chunk count, creation date) and create new ones.

| Route | Purpose |
|-------|---------|
| `GET /api/admin/worlds` | all worlds on disk, with stats |
| `POST /api/admin/worlds` | create a new world |
| `GET /api/admin/chunks/discovered` | all generated chunk coordinates |

The active world is selected by the `MICRAFT_WORLD_NAME` environment variable.
See [World persistence](../world/persistence.md).
