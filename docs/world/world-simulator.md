---
title: World simulator
---

# World simulator

## Overview

The world simulator is an **admin tool** for exercising world systems (liquids,
vegetation, weather, NPC spawning) against already-generated chunks without
disturbing live play. It runs from the admin panel and streams results over a
WebSocket.

## Routes

| Route | Purpose |
|-------|---------|
| `GET /api/admin/simulation/defaults` | values the simulator UI prefills its editors with |
| `GET /api/admin/ws/simulation` | live simulation feed |

## Configuration

The simulator has no standalone YAML — it drives the same managers configured by
[`vegetation.yaml`](vegetation.md), [`weather.yaml`](weather.md) and
[`npc.yaml`](../entities/npcs.md). Test areas are scoped with
[instance zones](../admin/instances.md).
