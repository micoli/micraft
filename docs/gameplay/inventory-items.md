---
title: Inventory & items
---

# Inventory & items

## How to play

- **Hotbar** — 10 slots, number keys select the active slot.
- **Inventory UI** — drag-and-drop between slots.
- **Collecting** — walking over a dropped item picks it up (`WorldItemManager`,
  proximity based).
- **`/give <name> [N]`** *(admin)* — give an item, or grant an armor/weapon/tool.
- **`/drink <itemType>`** — consume a consumable (restores health/mana).

{{ story "story/game-layout-hotbar--with-items" caption="Hotbar — 10 slots, number keys select the active one" }}

{{ story "story/game-layout-inventory--with-wallet" caption="Inventory UI with the currency wallet" }}

## Configuration

Items are defined in `data/config/items.yaml` (bundled default in
`resources/config/items.yaml`):

```yaml
COBBLESTONE:
  buildable: true
  placesBlock: STONE
  label: COB
  bg: "#7A7A7A"
SNOWBALL:
  buildable: false
  healthRestore: 0
  manaRestore: 0
```

- `buildable` + `placesBlock` — makes the item placeable.
- `healthRestore` / `manaRestore` — non-zero marks the item consumable via `/drink`.
- `label` / `bg` — hotbar rendering.
- `spawnsEntity` — throwing the item spawns an entity.

Schema: `items.schema.json`. Reload with `/reload`.

--8<-- "reference/_generated/items.md"
