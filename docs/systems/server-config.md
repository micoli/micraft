---
title: Server configuration
---

# Server configuration

## Overview

`data/config/server.yaml` is the master server config (bundled default
`resources/config/server.yaml`, schema `server.schema.json`). It overrides the
shared [`WorldConstants` / `PlayerConstants`](../reference/constants.md) at
startup and sets the game-loop and network parameters. It is **not** hot-reloaded
— restart with `make dev-restart-server`.

## Full default

```yaml
world:
  worldMinY: 0
  worldMaxY: 256
  chunkSize: 16
  viewRadius: 3
  forwardViewRadius: 7
  waterLevel: 65
  impostorSkirtDepth: 12
player:
  heightStanding: 1.8
  heightSneaking: 1.5
  heightCrawling: 0.6
  width: 0.6
  eyeOffsetStanding: 1.62
  eyeOffsetSneaking: 1.27
  eyeOffsetCrawling: 0.4
  speedStanding: 4.5
  speedSneaking: 1.3
  speedCrawling: 1.0
auth:
  provider: none              # none | local | oauth
  local:
    usersFile: data/config/auth/users.yaml
    groupsFile: data/config/auth/groups.yaml
chunks:
  transport: websocket        # websocket | http
  httpWorkers: 4
network:
  messageEncoder: protobuf
game:
  tickMs: 50
  gravity: -20.0
  jumpSpeed: 8.5
  flyVerticalSpeed: 8.0
  saveIntervalSeconds: 30
  spawnX: 8.0
  spawnY: 200.0
  spawnZ: 8.0
  ticksPerDay: 72000
  timeBroadcastTicks: 20
  maxInteractionDistance: 14.0
  debugWorld: false
  reconcileToleranceXz: 0.5
  reconcileToleranceY: 0.99
  blockBreakBufferSize: 1000
```

See [Auth & RBAC](auth-rbac.md) for the `auth:` block, and
[Chunk transport](../architecture/chunk-transport.md) for `chunks.transport`.
