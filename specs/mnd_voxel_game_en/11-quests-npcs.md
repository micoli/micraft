## 11. Quests & NPCs

### NPC types

**Static NPCs** (generated in existing villages):
- Blacksmith — sells equipment, repairs
- Alchemist — sells potions, buys resources
- Guild master — gives main quests
- Merchant — general economy, auction house

**Quest NPCs**: Procedurally spawnable in generated buildings. Each quest NPC has an archetype (old sage, distressed farmer, wounded knight) and generates a quest adapted to the average level of nearby players.

### Quest structure

```
Quest
├── id              UUID
├── giver_npc_id    UUID
├── title           STRING
├── type            ENUM (kill | fetch | escort | explore | boss)
├── target          JSONB (monster, item, location…)
├── reward_xp       INT
├── reward_gold     INT
├── reward_item     ItemTemplate | null
├── level_min       INT
├── level_max       INT
├── status          ENUM (available | active | completed | failed)
└── expires_at      TIMESTAMP | null
```

### Quest types

- **Kill**: kill N monsters of a type in an area. Simple, effective for XP farming.
- **Fetch**: collect resources or items and bring them back. Drives crafting.
- **Escort**: protect an NPC to point B. Group cooperation recommended.
- **Explore**: discover and map an area (dungeon, ruin). Connected to world generation.
- **Boss**: kill the boss of a specific dungeon. Maximum reward.

### Dialogues

Simple multiple-choice dialogue system (no NLP). Each NPC has 3 to 5 dialogue nodes. Charisma (CHA) unlocks additional negotiation options (price reductions, exclusive quests).

---
