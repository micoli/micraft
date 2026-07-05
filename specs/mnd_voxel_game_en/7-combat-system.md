## 7. Combat system

### Model: real-time with cooldowns (action RPG)

No true turn-by-turn combat (incompatible with real-time voxel). Instead: real-time combat with clear decision windows.

- Each basic attack has a **1-second** cooldown
- Class abilities have cooldowns of **5 to 30 seconds**
- Spells cost **mana** in addition to their cooldown
- Initiative order determines who attacks first in the opening round

### Attack resolution

```
1. Attacker launches their attack
2. Server computes: attack roll = d20 + modifier (STR or DEX)
3. If roll ≥ target's Armor Class (AC) → hit
4. If roll = 20 (natural) → automatic critical (×2 damage)
5. If hit: damage = weapon_dice + stat_modifier + equipment_bonus - target_armor
6. Apply effects (fire, poison, vampiric…)
7. Update target HP → synchronize to all clients
```

### Armor Class (AC)

```
AC = 10 + equipped_armor_bonus + floor((DEX - 10) / 2)
```

Ex: Warrior with plate armor (+8) and DEX 12 → AC = 10 + 8 + 1 = 19

### Death and permadeath

The game is **not permadeath**. However:

- At 0 HP, the character is **incapacitated** (downed, 3 stabilization rolls d20 ≥ 10)
- After 3 successes: stabilized, HP = 1
- After 3 failures: temporary death
- Temporary death: respawn at the last rest point with 50% HP and mana, lose 10% of current level's XP
- An allied Cleric can use `Resurrection` on the spot

### Status conditions

| Status | Effect | Typical duration |
|---|---|---|
| Poisoned | −2 HP/s | 10 seconds |
| Burning | −3 HP/s, cancelled by water | 5 seconds |
| Paralyzed | Unable to act | 2 seconds |
| Stunned | −50% accuracy | 3 seconds |
| Blessed | +10% damage | 30 seconds |
| Cursed | −2 to all rolls | 60 seconds |

---
