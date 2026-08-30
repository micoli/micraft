---
title: Pets
---

# Pets

A pet is a tamed [NPC](npcs.md) that belongs to a player: it follows its owner,
fights alongside them and can be re-summoned after it dies. A player keeps an
unlimited roster (capped at 12) but only **one pet is active at a time**.

{{ story "story/game-layout-pethud--roster-active-and-dead" caption="Pet HUD — active pet plus a fallen pet on its revive cooldown" }}

## How to play

- **`/tame`** (or the `combat.tame` key, default `Alt+T`) — target a wild
  creature and attempt to tame it. Success is chance-based:
    - higher when your level is above the creature's,
    - higher the lower the creature's remaining HP,
    - lower against an aggressive mob — and a failed attempt on one turns it on
      you.
  - You can only tame a creature whose level is **≤ your own**.
  - The first pet you tame is summoned automatically.
- **`/pet list`** — show your roster (level, type, status).
- **`/pet spawn <name>`** — summon a pet; any pet already out is dismissed first.
- **`/pet dismiss`** (or `combat.pet_dismiss`, default `Alt+P`) — send the active
  pet away. Its level, XP and **current HP** are saved and restored on the next
  summon (dismissing is not a heal).
- **`/pet resurrect <name>`** — revive a dead pet. Free, but on a 60-second
  cooldown from the moment it fell; it does not re-summon — follow with
  `/pet spawn`.
- **`/pet rename <old> <new>`** — rename a pet.

### Behaviour

- **Out of combat** the pet holds a station just to the owner's left, matches the
  owner's facing, and — while the owner is flying — flies at the owner's
  altitude. It teleports to the owner if left more than 40 blocks behind.
- **In combat** it moves to a point in front of the mob targeted by the owner
  (the owner's current combat target, else the nearest mob aggroing the owner)
  and attacks it, picking its attacks automatically. Kills credit the owner with
  XP and also grant XP to the active pet, which levels up like any NPC.
- A pet never takes wild aggro and is never targeted by the owner's own pets.

The **Pet HUD** widget (name, level, HP, resurrect countdown) can be repositioned
in the [HUD layout editor](../systems/layout-editor.md).

## Configuration

Which creatures can be tamed is per NPC type, in the entity definition
(`resources/entities/<type>/<type>.yaml`, override under
`data/resources/entities/`):

```yaml
tameable: true       # default false
tameBaseChance: 0.6  # base success chance before level / HP / aggro modifiers
```

Bundled defaults: `cat`, `duck`, `goat` (`0.6`), `wolf` (`0.25`),
`polar_bear` (`0.15`).

Roster and active pet are stored in the player file
(`pets`, `activePetId`); the pet entity itself is transient and dismissed on
disconnect. Server side: `PetManager` owns the roster lifecycle, `PetCoordinator`
drives each pet every tick before `NpcManager.tick`.
