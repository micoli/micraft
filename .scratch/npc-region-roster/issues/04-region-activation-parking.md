# Activation and parking per Region

Status: resolved
Type: task
Blocked by: 03

## Context

Spec: `../spec.md` (Activation and parking). Today candidate chunks are a ±`npcZoneSize` box around each Character,
orphans are parked in `pendingRespawns` keyed by grid square, and `onZoneCrossed` respawns the 3×3 neighbouring
squares (`NpcTickPipeline`, `NpcManager`).

## What to build

- Active Regions = the Region of each Character plus its Voronoi neighbours.
- NPCs of inactive Regions are parked, keyed by Region; they come back when their Region becomes active.
- `onZoneCrossed` fires on Region change.
- On parking, NPCs whose type is not in their Region's Roster are dropped instead of kept. Pets are never dropped.
- `npcZoneSize` no longer drives spawning; remove it if nothing else uses it (update generated reference docs).

## Acceptance criteria

- [x] Server tests: activation set for a Character on a Region border; park/respawn round trip; non-Roster NPC
      dropped on parking; pet kept.
- [x] `make dc CMD="./gradlew :server:test"` clean.

## Comments

- 2026-09-27: `NpcTickPipeline` computes the active Regions (each Character's Region plus the Regions whose seed lies
  within 2 × `npcZoneSize` of its seed), only offers the spawners chunks of active Regions, and parks NPCs of inactive
  Regions in `NpcManager.park` keyed by `Region.key`. On parking, Quest givers are dropped (their spawner recreates
  them with fresh offers) and wild NPCs outside their Region's Roster are dropped; Pets are never parked.
- `onZoneCrossed` → `onRegionEntered` (per session, throttled by `REGION_CHANGE_COOLDOWN_TICKS`); `PlayerSession`
  tracks `lastRegionKey`. The grid `zoneKey`/`countInZone` and the per-zone density snapshot are gone.
- `npcZoneSize` stays: it is the spawn candidate radius around a player and the admin NPC view's grid.
