---
title: Classes (admin)
---

# Classes (admin)

A read view of the RPG class definitions and the per-level attack/spell access
table.

| Route | Purpose |
|-------|---------|
| `GET /api/admin/classes` | class definitions, keyed by class name |
| `GET /api/admin/skills` | all attack and spell ids |
| `GET /api/classes` | attack ids accessible per class, keyed by level |

Edit these through the [Config editor](config.md)
([`classes.yaml`](../rpg/classes.md)), not this page.
