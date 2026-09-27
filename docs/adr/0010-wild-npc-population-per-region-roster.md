---
status: accepted
---

# Wild NPC population per Region Roster

Wild NPCs used to spawn from every NPC type whose Biomes and Danger levels matched a location, bounded by
per-type world-wide floors and ceilings (`minTotal`/`maxTotal`) and a per-grid-square cap. Up to 27 types could
share one forest, and the floors forced restocking wherever players went. Each Region now has a **Roster**: a
small set of NPC types (a few animals, a few hostiles of its Danger level, rarely one rare) drawn
deterministically from the World seed, the Region and the eligible NPC types. The Region's wild population,
births included, never exceeds a per-Biome budget shared among the Roster by per-type weight. Quest givers offer
only the Quests whose kill targets are in their Region's Roster. The Roster gives each Region an identity and
is the single source of truth that keeps Quests consistent with the NPCs actually present.

## Considered options

- **Keep "every eligible type" and add spawn weights**: fewer NPCs of each type, but every Region still looks
  the same and Quests still cannot rely on what is there.
- **Hand-authored rosters per Biome and tier**: full control, but no variety between Regions of the same Biome
  and a table to maintain for every new NPC type.
- **Persisted rosters per World**: stable across config changes, but needs storage and a migration path.
  Rejected: config changes are rare and Quest progress survives a reshuffle since a kill counts anywhere.

## Consequences

- The Roster is derived, never stored: changing NPC types or level ranges and reloading may reshuffle Rosters.
- `minTotal` disappears; `maxTotal` only keeps rares rare. Reproduction stops when the Region budget is full.
- NPC activation and parking follow Regions and their Voronoi neighbours, not the square spawn grid.
- A Region with no suitable Quest has no Quest giver; a coverage check reports Biome × Danger tier pairs with no
  Quest instead of forcing Rosters to include Quest targets.
