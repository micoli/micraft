---
title: Animal lifecycle
---

# Animal lifecycle

## Overview

Animal NPCs age and reproduce. Each tracks:

- **age** in game-days (`gameDayDurationSeconds` in [`npc.yaml`](npcs.md))
- **hunger** meter
- **gestation** timer
- **reproduction cooldown**
- **mother level**

All fields are visible in the [admin NPCs panel](../admin/npcs.md) detail view and
in `GET /api/admin/npcs`.

## Configuration

Lifecycle timing derives from `gameDayDurationSeconds` and per-type definitions
loaded by `NpcRegistryLoader`. There is no separate lifecycle YAML — tune the day
length and per-type breeding parameters there.
