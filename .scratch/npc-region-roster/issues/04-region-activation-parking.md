# Activation and parking per Region

Status: ready-for-agent
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

- [ ] Server tests: activation set for a Character on a Region border; park/respawn round trip; non-Roster NPC
      dropped on parking; pet kept.
- [ ] `make dc CMD="./gradlew :server:test"` clean.
