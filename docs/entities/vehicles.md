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

Rails are regular blocks (`RAIL_STRAIGHT`, `RAIL_Y_SPLIT_90`, …) — see the
[block catalog](../world/blocks-catalog.md). Reload with `/reload`.
