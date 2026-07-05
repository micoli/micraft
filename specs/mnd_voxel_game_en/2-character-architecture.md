## 2. Character architecture

### Data structure (server)

Each player has a single `Character` record per account. The character is tied to a server `Session`.

```
Character
├── id                UUID (PK)
├── player_id         UUID (FK → Player)
├── name              STRING (3–24 chars)
├── class             ENUM (warrior | mage | ranger | rogue | cleric)
├── level             INT (1–20)
├── xp                INT
├── xp_to_next        INT (computed)
├── base_stats        JSONB → BaseStats
├── current_hp        INT
├── current_mana      INT
├── position          JSONB { world_id, x, y, z }
├── created_at        TIMESTAMP
└── updated_at        TIMESTAMP
```

```
BaseStats
├── strength         INT (1–20)
├── dexterity        INT (1–20)
├── intelligence     INT (1–20)
├── wisdom           INT (1–20)
├── constitution     INT (1–20)
└── charisma         INT (1–20)
```

### Core rule

Derived stats (max HP, max mana, damage, dodge…) are **never stored**. They are recalculated server-side on every game event. The client receives computed values as read-only — it can never write them directly.

---