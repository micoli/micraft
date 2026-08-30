---
title: Quests
---

# Quests

## How to play

- **Quest journal** — key `J`.
- **On-screen tracker** — a persistent widget (`QuestTracker`) shows the active
  quest's objectives.
- **`/quest [list|accept|abandon|status] [id]`** — manage quests from chat.

Quest types: **KILL** (defeat N of an NPC type) and **FETCH** (collect N of an
item). `QuestRegistryLoader` loads definitions at startup.

{{ story "story/game-layout-questtracker--tracking" caption="QuestTracker — active objectives with progress" }}

## Configuration

Quest definitions are loaded from YAML by `QuestRegistryLoader`. Objectives
reference NPC types ([NPCs](../entities/npcs.md)) and item types
([items](inventory-items.md)). All quest definitions are served at
`GET /api/quests`.

XP and rewards on completion flow through the RPG
[progression](../rpg/progression.md) system.
