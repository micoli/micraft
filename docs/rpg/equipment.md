---
title: Equipment
---

# Equipment

## How to play

- **Armor** — `/equip <name>` / `/unequip <name>`, or the Character screen (key
  `Y`, or Pause → Character) with a 3-D `PlayerModelPreview`. The client resolves
  slot conflicts before sending commands.
- **Weapons & tools** — `/wield <name> [hand]` / `/unwield <hand>`. Some items are
  `mainHandOnly`.
- Armor bonuses feed into [derived stats](stats.md).

{{ story "story/game-windows-character--with-stats" caption="Character screen — equipment, hands and derived stats" }}

## Configuration

**Armor** — `resources/armors/<name>/<name>.yaml`, loaded by
`ArmorRegistryLoader`. Each defines `wearable` slot flags: `head`, `body`,
`rightArm`, `leftArm`, `rightLeg`, `leftLeg`.

**Weapons** — `data/config/weapons.yaml` (schema `weapons.schema.json`):

```yaml
SWORD:
  allowedClasses: [WARRIOR, RANGER, CLERIC]
  mainHandOnly: false
BOW:
  allowedClasses: [RANGER]
  mainHandOnly: true
```

**Tools** — `data/config/tools.yaml` (schema `tools.schema.json`):

```yaml
AXE:   { mainHandOnly: false }
HAMMER: { mainHandOnly: false }
```

Served at `GET /api/armors`, `GET /api/weapons`, `GET /api/tools`,
`GET /api/player/:name/armors`. Reload with `/reload`.
