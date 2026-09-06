---
title: Vehicles & siege weapons
---

# Vehicles & siege weapons

## How to play

- **`/vehicule:add <vehiculeName>`** — spawn a vehicle on the rail block you are
  standing on.
- **`/mount`** — mount or dismount the vehicle (or NPC mount) you are targeting
  (`vehicle_mount`, `Ctrl+X`).
- **Siege weapons** — `/siege_weapon <rotation|pitch|power> <value>` sets the
  targeted weapon's aim; `siege_weapon_fire` (`Z`) fires.
- **Furniture** — a decorative placeable. Give yourself the item
  (e.g. `/give FURNITURE_TABLE`), select its shortcut slot and click the ground
  to place it. Target it (`Tab`) then press `R` (`siege_weapon_rotate`) to turn
  it by 30°; `X` (`npc_interact`) removes it and returns the item.

## Configuration

**Vehicle models** — `data/config/vehicles.yaml` (bundled default
`resources/config/vehicles.yaml`, schema `vehicles.schema.json`):

```yaml
CART:
  bbmodelFile: CART
  width: 0.8
  height: 0.8
```

Per-vehicle runtime config (speed, seat offset) is served at
`GET /api/vehicles/{name}/config`; `VehicleModelRegistryLoader` /
`VehicleRegistryLoader` load them.

**Siege weapons / projectiles** — schemas `siege_weapons.schema.json` and
`siege_projectiles.schema.json`; loaded by `SiegeWeaponRegistryLoader` /
`SiegeProjectileRegistryLoader`, served at `GET /api/siege-weapons`.

**Furniture** — one `resources/furnitures/<name>/<name>.yaml` per type
(override in `data/resources/furnitures/`, schema `furniture.schema.json`),
loaded by `FurnitureRegistryLoader`:

```yaml
bbmodelFile: TABLE
width: 0.8
height: 0.8
rotatable: true
```

Each type is exposed as an item via `spawnsEntity: <name>` in `items.yaml`.
Siege weapons and furniture are both merged into the shared `PlaceableRegistry`.

Rails are regular blocks (`RAIL_STRAIGHT`, `RAIL_Y_SPLIT_90`, …) — see the
[block catalog](../world/blocks-catalog.md). Reload with `/reload`.
