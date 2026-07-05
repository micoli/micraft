## 3. Stats system

### The 6 base stats (inspired by D&D 5e, adapted for real-time)

| Stat | Abbrev. | Main effect | Derived formula |
|---|---|---|---|
| Strength | STR | Melee damage, carry capacity | Melee dmg = (STR − 10) / 2 + weapon bonus |
| Dexterity | DEX | Initiative, dodge, ranged attack | Dodge % = DEX × 2.5; Initiative = (DEX − 10) / 2 |
| Intelligence | INT | Magic damage, known spells | Spell dmg = (INT − 10) / 2 + spell level |
| Wisdom | WIS | Max mana, mental resistance, perception | Max mana = WIS × 5 |
| Constitution | CON | Max HP, physical resistance | Max HP = (CON − 10) / 2 × level + 10 |
| Charisma | CHA | NPC dialogue, trading, group leadership | Price reduction = CHA × 0.5% |

### Starting values

At creation, the player has **27 points to distribute** (D&D 5e point-buy system). Values range from 8 to 15 before class bonuses.

| Value | Point cost |
|---|---|
| 8 | 0 |
| 9 | 1 |
| 10 | 2 |
| 11 | 3 |
| 12 | 4 |
| 13 | 5 |
| 14 | 7 |
| 15 | 9 |

### Class bonuses at creation

Each class adds fixed bonuses on top of the 27 points:

| Class | Bonus |
|---|---|
| Warrior | +2 STR, +1 CON |
| Mage | +2 INT, +1 WIS |
| Ranger | +2 DEX, +1 WIS |
| Rogue | +2 DEX, +1 INT |
| Cleric | +2 WIS, +1 CON |

### Stat increases on level-up

Every 4 levels (4, 8, 12, 16, 20), the player gains **+2 points to freely distribute** among their stats.

### Derived stats — complete formulas

```
Max HP        = ((CON - 10) / 2) * level + 10 + equipment_bonus
HP regen      = CON / 10  per second out of combat

Max mana      = WIS * 5 + equipment_bonus
Mana regen    = WIS / 20  per second out of combat

Melee dmg     = floor((STR - 10) / 2) + weapon_dice + equipment_bonus
Ranged dmg    = floor((DEX - 10) / 2) + weapon_dice + equipment_bonus
Spell dmg     = floor((INT - 10) / 2) + spell_dice + wand_bonus

Crit chance   = 5% base + DEX * 0.2%
Crit dmg      = damage * 2

Dodge         = DEX * 2.5%  (capped at 75%)
Armor         = equipment only (no base stat contribution)
Magic resist  = (WIS - 10) * 2%

Initiative    = floor((DEX - 10) / 2)  (used for turn order)
```

---