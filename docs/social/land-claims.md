---
title: Land claims
---

# Land claims

## How to play

Claim the chunk you stand in to protect it from other players' edits.

- **`/claim trust <playerName>`** — allow a player to build in your claim.
- **`/claim untrust <playerName>`** — revoke.
- **`/claim abandon`** — release the claim.
- **`/claim info`** — show the claim you are standing in.

Players in the **same faction** as the claim owner can build and interact
without being explicitly trusted (see
[groups, guilds & factions](groups-guilds-factions.md)).

## Configuration

`data/config/claims.yaml` (bundled default `resources/config/claims.yaml`, schema
`claims.schema.json` — see [config files](../reference/config-files.md)):

```yaml
costPerChunk: 50           # copper
maxChunksPerClaim: 64
maxClaimsPerPlayer: 3
```

Admin management:

| Route | Purpose |
|-------|---------|
| `GET /api/admin/claims` | all claims |
| `PUT /api/admin/claims/{id}/bounds` | update Y bounds |
| `PUT /api/admin/claims/{id}/trust` | grant/revoke trust (online or offline) |
| `DELETE /api/admin/claims/{id}` | delete |

Reload with `/reload`.
