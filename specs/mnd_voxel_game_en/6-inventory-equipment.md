## 6. Inventory & equipment

### Inventory structure

```
Inventory
├── character_id    UUID (FK)
├── slots           ARRAY[40] → ItemSlot   (backpack)
└── gold            INT
```

```
EquipmentSlots (separate from backpack)
├── head            ItemSlot | null
├── chest           ItemSlot | null
├── legs            ItemSlot | null
├── feet            ItemSlot | null
├── hands           ItemSlot | null
├── main_hand       ItemSlot | null
├── off_hand        ItemSlot | null
├── ring_1          ItemSlot | null
├── ring_2          ItemSlot | null
└── amulet          ItemSlot | null
```

### Item rarity system

| Rarity | Color | Max bonus | Main source |
|---|---|---|---|
| Common | Gray | +1 to one stat | Basic monsters, crafting |
| Uncommon | Green | +1 to two stats | Chests, quests |
| Rare | Blue | +2–3 to two stats, 1 special effect | Level 1–5 bosses |
| Epic | Purple | +3–5, 2 special effects | Level 6–10 bosses |
| Legendary | Orange | +5–8, 3 special effects, unique passive | Final boss, advanced craft |

### Equipment special effects (examples)

- `Sharp`: +5% crit chance
- `Flaming`: +1d4 fire damage per attack
- `Protective`: reduces incoming damage by 5%
- `Regenerating`: +2 HP per second in combat
- `Vampiric`: steals 5% of damage dealt as HP
- `Celestial`: +20% damage against undead

### Durability

- Each item has a durability from 0 to 100
- Items lose durability in combat (−1 to −3 per hit received)
- At 0 durability, the item loses all its bonuses (but stays in inventory)
- Repair available at a blacksmith NPC or via a crafting table

---

