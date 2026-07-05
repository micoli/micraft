## 14. Development phases

### Phase 1 — RPG foundation (absolute priority)

**Estimated duration: 6 to 8 weeks**

- [ ] `Character` and `BaseStats` data model in DB
- [ ] Character creation screen (class choice, 27-point distribution)
- [ ] Derived stats calculation server-side
- [ ] HUD display: HP, mana, level, XP
- [ ] Inventory system (40 slots) and equipment slots
- [ ] Equipment bonus calculation
- [ ] Full persistence: character survives reconnections

**Validation criterion**: A player creates a character, equips an item, and sees their stats change correctly after reconnecting.

### Phase 2 — Combat & monsters

**Estimated duration: 6 to 8 weeks**

- [ ] Basic monsters with AI (idle, patrol, aggro)
- [ ] Server-side attack resolution (d20 roll, AC, damage)
- [ ] 5 monster types (Goblin, Skeleton, Spider, Wolf, Zombie)
- [ ] XP system and leveling up
- [ ] 2 class abilities per class (level 1 and level 3)
- [ ] Monster health bar display
- [ ] Item drops on monster death

**Validation criterion**: A group of 2 players can kill monsters, earn XP, level up, and loot items.

### Phase 3 — Dungeons

**Estimated duration: 5 to 7 weeks**

- [ ] Dungeon generator (BSP, 2 types: Crypt and Mine)
- [ ] Per-group instance system
- [ ] Dungeon boss (1 per type) with 2 phases
- [ ] Chests with level-scaled loot tables
- [ ] Entry portals in the surface world
- [ ] Starting room with rest point

**Validation criterion**: A group of 4 players can enter a dungeon, kill the boss, and loot a rare item.

### Phase 4 — Crafting & quests

**Estimated duration: 5 to 7 weeks**

- [ ] 3 crafting tables (Workshop, Forge, Alchemy)
- [ ] 20 base recipes
- [ ] Crafting skill system
- [ ] 3 quest types (Kill, Fetch, Boss)
- [ ] Quest NPCs in generated villages
- [ ] Gold and item rewards

**Validation criterion**: A player can complete a quest and use the reward to craft an improved item.

### Phase 5 — Advanced multiplayer & economy

**Estimated duration: 4 to 6 weeks**

- [ ] Full group system (invitations, XP sharing, round-robin loot)
- [ ] Auction house
- [ ] Direct P2P trade
- [ ] Local and group chat
- [ ] Global balancing (all classes and dungeons)

---
