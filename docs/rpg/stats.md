---
title: Stats
---

# Stats

## Overview

Six base stats — **STR, DEX, INT, WIS, CON, CHA** — are chosen at character
creation (point-buy). Derived stats (HP, mana, rage, regen, attack/spell power)
are computed from:

```
base stat  +  class bonus  +  equipped armor bonuses
```

HP / mana / rage formulas and regeneration are defined per class in
[`classes.yaml`](classes.md). `RegenProcessor` applies regen every
`regen.regenIntervalMs`; `StatusEffectProcessor` layers timed buffs/debuffs on
top.

## Configuration

Base-stat point-buy limits and derived-stat formulas live in
`data/config/classes.yaml`. Combat ranges and the rage cap are in
`data/config/combat.yaml`:

```yaml
maxCombatRange: 10.0
npcMaxAttackRange: 3.0
downingRollIntervalMs: 3000
maxRage: 100
```

Schema: `combat.schema.json`, `classes.schema.json`. Reload with `/reload`.
