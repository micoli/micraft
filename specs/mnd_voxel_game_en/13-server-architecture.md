## 13. Server architecture

### General principle

Full server authority. The client sends **intentions** (I want to attack, I want to move) — the server validates, computes, and returns the resulting state. The client can never directly modify stats, HP, or inventory.

### Synchronization flow

```
Client                          Server
  │                               │
  ├── ACTION(attack, target_id) ──►│
  │                               ├── Validates the action
  │                               ├── Computes damage
  │                               ├── Updates target HP in DB
  │                               ├── Notifies all clients in the zone
  │◄── STATE_UPDATE(target_hp) ───┤
  │                               │
```

### Real-time events

WebSockets or a custom UDP protocol for position and combat updates. Frequency:
- Player position: 20 times/second (50ms)
- Combat state (HP, statuses): on every change
- Inventory: on every modification
- Chat: immediate

### Persistence

- Stats, inventory, quests: relational database (PostgreSQL recommended)
- Position and combat state: Redis (real-time cache, synced to DB every 5 seconds)
- Voxel world: already implemented — do not modify the existing structure

### Anti-cheat security

- All damage values recalculated server-side — the client never sends damage values
- Rate limiting on actions (max 1 attack per 900ms server-side)
- Range checks server-side (a player cannot attack 50 blocks away with a sword)
- All combat actions logged for audit

---
