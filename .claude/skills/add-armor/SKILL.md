---
name: add-armor
description: Generate a new armor piece — bbmodel (Blockbench geometry+texture) + resources/armors yaml config (wearable slots, stat bonus, armor type, required level). Use when adding a new piece of wearable armor to the game.
---

# Adding a new armor piece

Produces `resources/armors/<name>/<name>.bbmodel` + `resources/armors/<name>/<name>.yaml`.

## 1. Clarify specs (ask if not given)

- Name (snake_case directory/yaml name, e.g. `iron_greaves`)
- Slot(s) covered — which `WearableSlots` flags this piece sets to `true` (see
  `server/src/main/kotlin/org/micoli/micraft/game/armor/WearableSlots.kt` for the current full
  list: `head, body, cape, rightBiceps, rightForearm, rightHand, leftBiceps, leftForearm,
  leftHand, rightThigh, rightCalf, rightFoot, leftThigh, leftCalf, leftFoot`). A piece normally
  covers a natural group (e.g. a chestplate = `body`; greaves = both thighs+calves), never an
  arbitrary mix.
- Armor type — exactly one of `CLOTH, LEATHER, MAIL, PLATE` (see `ArmorType.kt`; grep before
  relying on this list, it can change). Drives which classes can equip it — see
  `ArmorClassRules.kt`.
- Required level — the character level needed to equip it (`requiredLevel` in yaml). Pick a value
  consistent with the zone tier it's meant to drop/reward in (see `ZoneTier.kt` and the
  "Zone/npc tier per skill level" table in `CLAUDE.md`).
- Stat bonus — `StatBonus{str,dex,intel,wis,con,cha,acBonus}`, scaled to the tier: a tier-1 piece
  should carry a small bonus (e.g. +1/+2 total), a tier-5 piece a large one. Keep the curve
  roughly linear across tiers so no single piece trivializes the next zone.
- Visual: base material look (metal/leather/cloth grain), one or two accent colors, any
  engraving/trim detail appropriate to the tier (higher tiers = more ornate).

## 2. Geometry — `<name>.bbmodel`

Armor pieces attach to the player's existing bone hierarchy (`head`, `body`, `rightArm`,
`leftArm`, `rightLeg`, `leftLeg`, …) rather than defining their own skeleton — size each cuboid to
sit just outside the corresponding body part's silhouette (a few pixels of clearance so it reads
as worn, not clipped into, the body). Follow the same authoring rules as NPC models (see
`add-npc-model`'s SKILL.md, steps 2–3, for the full bbmodel structure, UV-packing rule, and texture
generation pipeline — they apply unchanged here): `box_uv: false` everywhere, explicit non-
overlapping `faces{}` UV rects, opaque canvas with no transparent gutters, procedural per-pixel
texture (metal grain / leather grain / cloth weave as appropriate — never a flat fill).

## 3. Config — `<name>.yaml`

```yaml
wearable:
  body: true
statBonus:
  str: 2
  con: 1
armorType: PLATE
requiredLevel: 10
```

Omit any `wearable` flag that stays `false` and any `statBonus` field that stays `0` — both
default that way.

**`armorType` must be a real, currently-valid constant — grep
`server/src/main/kotlin/org/micoli/micraft/game/armor/ArmorType.kt` before relying on the value
above; it can change.**

## 4. Verify

```bash
make dev-restart-server   # picks up new resources/armors/<name>/
```

Check the server log for `ArmorRegistryLoader` errors on the new piece (`Armor registry loaded: N
wearable types` should include it). In-game: `/give <name>` (or the admin player-equipment route)
to grant it, then `/equip <name>` — confirm it's refused below `requiredLevel` or on a class
`ArmorClassRules` doesn't allow, and succeeds otherwise with no slot overlap against already-worn
pieces.
