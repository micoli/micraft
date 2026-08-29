---
title: Movement & stances
---

# Movement & stances

## How to play

Move with the [movement keybindings](../systems/keybindings.md) (`W A S D` +
arrows by default). Three stances change height, eye level and speed:

| Stance | Height | Eye offset | Speed |
|--------|--------|------------|-------|
| Standing | `HEIGHT_STANDING` | `EYE_OFFSET_STANDING` | `SPEED_STANDING` |
| Sneaking | `HEIGHT_SNEAKING` | `EYE_OFFSET_SNEAKING` | `SPEED_SNEAKING` |
| Crawling | `HEIGHT_CRAWLING` | `EYE_OFFSET_CRAWLING` | `SPEED_CRAWLING` |

Exact values: [player constants](../reference/constants.md).

- **Fly mode** and **speed boost** are toggleable (bindable keys). Fly disables
  gravity for that player; Y stays server-authoritative otherwise.
- **`/mount`** mounts or dismounts the vehicle you are targeting — see
  [Vehicles](../entities/vehicles.md).

Movement is the only action that is *not* a slash command: the client sends
`MoveIntent`, the server validates against AABB physics in `core`, and replies
`PlayerUpdate`. The client predicts XZ locally and soft-corrects.

## Configuration

Stance dimensions and speeds are centralised in `PlayerConstants` (`core`). They
can be overridden at server startup under the `player:` block of
[`server.yaml`](../systems/server-config.md):

```yaml
player:
  heightStanding: 1.8
  heightSneaking: 1.5
  heightCrawling: 0.6
  width: 0.6
```

A skin's `<name>.yaml` may also set a first-person camera anchor (`eyes: {x,y,z}`)
and `firstPersonHiddenBones`; skins without a yaml fall back to the stance eye
offsets above.
