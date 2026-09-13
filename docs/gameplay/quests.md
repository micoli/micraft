---
title: Quests
---

# Quests

## How to play

- **Quest journal** — key `J`.
- **On-screen tracker** — a persistent widget (`QuestTracker`) shows the active
  quest's objectives.
- **`/quest [list|accept|abandon|turnin|status] [id]`** — manage quests from chat.

Quest types: **KILL** (defeat N of an NPC type) and **FETCH** (collect N of an
item). `QuestRegistryLoader` loads definitions at startup.

### Claiming the reward: autoloot vs. turn-in

A quest's `autoLoot` flag decides how its reward is claimed:

- **Autoloot** (`autoLoot: true`, the default) — the reward is granted the
  instant the objective is met; the quest goes straight to `COMPLETED`.
- **Turn-in required** (`autoLoot: false`) — meeting the objective only moves
  the quest to `READY_TO_TURN_IN`; the player must go back to the quest-giver
  NPC that offers it and use `/quest turnin <id>` (surfaced as a "Récupérer"
  button in the NPC's quest dialog) to actually receive the reward. All
  tier-1 (`zone_tier1_*`) quests require a turn-in.

{{ story "story/game-layout-questtracker--tracking" caption="QuestTracker — active objectives with progress" }}

## Configuration

Quest definitions are loaded from YAML by `QuestRegistryLoader`. Objectives
reference NPC types ([NPCs](../entities/npcs.md)) and item types
([items](inventory-items.md)). All quest definitions are served at
`GET /api/quests`, including the `autoLoot` flag.

XP and rewards on completion flow through the RPG
[progression](../rpg/progression.md) system.
