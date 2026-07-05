## 9. Monsters & AI

### Monster hierarchy

```
Monster
├── id              UUID
├── template_id     STRING (goblin_warrior, skeleton_archer…)
├── level           INT
├── hp              INT (current)
├── hp_max          INT
├── stats           BaseStats (same structure as the player)
├── loot_table      JSONB
├── ai_state        ENUM (idle | patrol | aggro | flee | dead)
├── target_id       UUID | null
└── position        {x, y, z}
```

### AI states and transitions

```
IDLE ──(player detected within aggro range)──► AGGRO
IDLE ──(30s timer)──► PATROL
PATROL ──(player detected)──► AGGRO
AGGRO ──(player out of range 20 blocks)──► IDLE (HP reset)
AGGRO ──(HP < 20%)──► FLEE (some monsters only)
FLEE ──(player lost from sight)──► IDLE
AGGRO/FLEE ──(HP = 0)──► DEAD
```

### Behavior by type

**Melee** (Goblin, Skeleton warrior): charges the nearest target, attacks in melee, no advanced tactical movement.

**Ranged** (Spitting spider, Skeleton archer): maintains 8 to 12 blocks distance, moves laterally to avoid obstacles, retreats if the target closes in.

**Support** (Goblin shaman, Necromancer): stays at the back of the monster group, casts buff spells on allies, high flee priority.

**Boss**: phase-based behavior. Each boss has 2 to 3 phases triggered by HP thresholds (75%, 50%, 25%). Each phase unlocks new attacks or summons reinforcements.

### Aggro range

- Direct line of sight: 12 blocks
- Noise (combat): 20 blocks
- Active stealth (Rogue): halved

---
