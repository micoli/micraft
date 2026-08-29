---
title: Classes
---

# Classes

## Overview

Each class sets stat bonuses, a **class resource** (`RAGE`, `MANA`, or tokens),
HP/mana/rage regen formulas, and a per-level list of unlocked attacks and spells.

## Configuration

`data/config/classes.yaml` (bundled default `resources/config/classes.yaml`,
schema `classes.schema.json`):

```yaml
regen:
  regenIntervalMs: 1000
  default:
    hpFormula: "hpRegenPerSec * dt"
    manaFormula: "manaRegenPerSec * dt"
classes:
  WARRIOR:
    strBonus: 2
    conBonus: 1
    classResource: RAGE
    hpFormula: "hpRegenPerSec * dt"
    manaFormula: "0"
    rageFormula: "inCombat ? 0.0 : (-con / 20.0 * dt)"
    levels:
      1:
        attacks:
          - { attack: slash, level: 1 }
          - { attack: heavy_slash, level: 1 }
        spells: [tokenRageConsume]
      3:
        attacks:
          - { attack: heavy_slash, level: 2 }
```

Formulas are JEXL-style expressions evaluated with `con`, `dt`, `inCombat`,
`hpRegenPerSec`, `manaRegenPerSec`, etc. Attacks/spells reference ids under
`resources/config/skills/` — see [Combat](combat.md). Reload with `/reload` or
`/config:reload`.

--8<-- "reference/_generated/classes.md"
