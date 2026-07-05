## 8. Dungeon generation

### Integration with existing world generation

Dungeons are generated underground, **beneath** the already-generated surface world. The entrance is marked by a carved-stone portal visible on the world map.

### Dungeon types

| Type | Recommended level | Theme | Estimated duration |
|---|---|---|---|
| Crypt | 1–3 | Undead, simple traps | 15–20 min |
| Abandoned mine | 2–4 | Goblins, cave-ins | 20–30 min |
| Cursed forest | 3–6 | Forest creatures, dark magic | 30–40 min |
| Chaos temple | 5–8 | Demons, magical puzzles | 40–60 min |
| Dragon citadel | 8–12 | Dragons, epic treasure | 60–90 min |

### Generation algorithm

**Step 1 — Starting room**: always a safe room with a rest point, info sign (level, type), and emergency exit.

**Step 2 — Room graph**: generated via BSP (Binary Space Partitioning) adapted for voxel. Each room is a volume of carved blocks connected by corridors. Parameters: 8 to 20 rooms per dungeon depending on level.

**Step 3 — Population**:
- 60% of rooms: common monsters (2 to 5)
- 20% of rooms: elite monsters + chest
- 15% of rooms: traps + hidden reward
- 5%: boss room (always at the back of the dungeon)

**Step 4 — Loot table**: Loot levels scale with dungeon level. The boss room guarantees at least one item of Rare rarity or above.

### Instances vs shared world

Dungeons are **instanced per group**: each team that enters generates their own copy of the dungeon. This prevents competition for bosses and griefing between groups. The surface world remains shared and persistent.

---
