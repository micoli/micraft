---
title: Combat
---

# Combat

## How to play

- **Target** — cycle targets with `combat_target_cycle` (`Tab`). The
  `CombatTargetFrame` shows the focused entity's HP.
- **Attack** — `combat_attack` (`R`) triggers the selected action.
- **Action bar** (`AttackPanel`) — draggable slots for attacks and spells, each
  with its own cooldown and resource cost.
- **Aggro indicators** — angular markers show nearby hostile NPCs.
- **Downed / respawn** — `PlayerDownedOverlay`; `/resurect [playerName]` revives.
- **`/god:on` / `/god:off`** *(admin)* — damage immunity.
- **`/rest`** — restore rage and tokens to max.
- **`/buff <hp|mana|hpregen|manaregen>`** — apply a temporary self buff.

Processors: `SpellProcessor` (alongside the attack processor),
`StatusEffectProcessor` (timed buffs/debuffs), `RegenProcessor` (HP/mana regen).

## Configuration

**Global combat** — `data/config/combat.yaml`:

```yaml
maxCombatRange: 10.0
npcMaxAttackRange: 3.0
downingRollIntervalMs: 3000
maxRage: 100
```

**Attacks** — `resources/config/skills/attacks/<id>.yaml` (schema
`skill-attack.schema.json`):

```yaml
damageType: PHYSICAL
enabled: true
levels:
  1: { power: 5, weaponDice: 1d8, cooldownMs: 800 }
```

**Spells** — `resources/config/skills/spells/<id>.yaml` (schema
`skill-spell.schema.json`):

```yaml
type: NECROTIC_AOE
manaCost: 20
cooldownMs: 6000
aoeRadius: 3.0
maxRange: 20.0
```

Which class gets which attack/spell at which level is in
[`classes.yaml`](classes.md). Served at `GET /api/attacks`, `GET /api/spells`.
Reload with `/reload`.
