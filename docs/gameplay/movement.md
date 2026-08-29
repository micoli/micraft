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

## Camera / view modes

`view_toggle` (`F` by default) cycles the camera; `/view_mode <MODE>` jumps
straight to one (ignoring the preference filter below). A notification shows the
current mode on every change. The chase camera pulls in when a solid block sits
between it and the player — the chunk mesh has no inward faces.

| Mode | Camera |
|------|--------|
| `FIRST_PERSON` | eyes, full body drawn |
| `THIRD_PERSON` | level chase camera, `rotate_left/right` turn it |
| `FIRST_PERSON_NO_ARMS` | eyes, arms hidden |
| `THIRD_PERSON_ORBIT` | chase camera orbits on the mouse, heading on the turn keys, wheel zooms |
| `THIRD_PERSON_ORBIT_CURSOR` | as orbit but pointer is free: the OS cursor aims block interaction, left-click breaks/places, `Alt`+left-drag orbits, the camera lifts over the player with the pitch |

In both orbit modes `rotate_up` / `rotate_down` (`Alt+Arrow Up/Down`) change the
pitch. Preferences → Game hides individual modes from the `view_toggle` cycle
(`FIRST_PERSON` always stays).

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
