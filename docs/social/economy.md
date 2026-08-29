---
title: Economy
---

# Economy

## How to play

The currency is **copper**.

- **`/give:money <amount> [playerName]`** *(admin)* — grant copper.
- **`/npcbuy <npcId> <itemType> [quantity]`** — buy from a `SELLER` NPC.
- **`/npcsell <npcId> <itemType> [quantity]`** — sell to a `SELLER` NPC.
- The [auction house](auction-house.md) and [player trade](trade.md) move copper
  and goods between players.

## Configuration

NPC shop inventories and prices come from the per-type NPC definitions
([NPCs](../entities/npcs.md)). Auction tax is in
[`auction.yaml`](auction-house.md). There is no standalone economy YAML.
