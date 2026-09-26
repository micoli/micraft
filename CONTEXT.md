# MiCraft

A multiplayer RPG voxel game: players share one persistent, procedurally generated world, explore it, build in it, and level up their Characters against NPCs and Quests.

## Language

### Identity & access

**Account**:
A login identity, keyed by email, that may own several Characters in the same World and holds Admin roles.
_Avoid_: User (for the in-game actor), player

**Character**:
A named persona chosen at login that lives in the World, with its own save file, inventory, position, in-game permissions, Class, level and stats. Every in-game identifier designates a Character, never an Account.
_Avoid_: Player (ambiguous), avatar, RPG character

**Player**:
The human playing a Character through a Session; used for gameplay rules ("the player jumps"), never as an identifier.
_Avoid_: using "player id" to mean either Account or Character

**Session**:
The live connection of one Character to one World, from connect to disconnect.
_Avoid_: connection, client

**RBAC group**:
A named, persistent set of permissions that decides which commands and features a Character may use. It is assigned by administrators, not formed through play.
_Avoid_: group (alone — reserved for the social Group)

**Admin role**:
A permission set held by an Account that grants access to the admin panel and admin operations, independent of any Character's RBAC groups.
_Avoid_: admin group, account group, RBAC group

### World & space

**World**:
One persistent, seeded voxel map with its own Characters, NPCs, Claims and game time.
_Avoid_: map, level, server

**Test world**:
An isolated, ephemeral World spawned for one automated test and never persisted.
_Avoid_: game session, E2E world

**Chunk**:
A 16×16 column of blocks spanning the full world height; the unit of generation, streaming, persistence and Claims.

**Biome**:
The terrain character of a Region (surface blocks, elevation, vegetation), chosen by moisture.

**Region**:
A named, fixed area of the World carved by the Voronoi layout, carrying exactly one Biome.
_Avoid_: zone, biome zone, cell

**Danger level**:
The difficulty of a location, derived only from its distance to spawn (not from its Region); drives which NPCs and Quest givers appear there.
_Avoid_: zone level, area level

**Danger tier**:
A band of Danger levels (1–5). NPCs in a tier use Abilities of the matching Rank, and a Character needs Abilities of that Rank to survive there.
_Avoid_: zone tier

**Instance**:
A bounded, admin-defined set of existing Chunks used as a dungeon, arena or test bed.
_Avoid_: instance zone, zone

**Weather cell**:
A temporary, moving circular area where a weather effect (rain, snow…) is active.
_Avoid_: weather zone, zone

**Scene**:
An off-world block structure edited in isolation and stamped into the World as a prefab.
_Avoid_: prefab, schematic

**Claim**:
A set of Chunks owned by a Character, where only the owner, trusted Characters and faction allies may build.
_Avoid_: land, plot

**Action block**:
A placed block given a unique name and scripted handlers that react to targeting, activation or other Action blocks.

**Placeable**:
A non-block object positioned in the World: vehicle, siege weapon or furniture.

**Game time**:
The World's in-game clock, driving day/night and time-based systems.

### Movement

**Stance**:
The body posture of a Character — standing, sneaking, crawling, swimming — which sets its height, eye level and speed.

**Move intent**:
What the Player asks to do this instant (direction, jump, stance); the server decides the actual movement.

**Prediction**:
The client's local anticipation of its own Character's movement before the server confirms it.

**Reconciliation**:
Correcting the predicted position toward the server's authoritative one.

### RPG

**Level**:
A Character's progression step, raised by earning XP from kills and Quests.

**Class**:
The archetype of a Character (e.g. warrior) that sets resource type, stat bonuses and unlocked abilities.

**Base stats**:
STR, DEX, INT, WIS, CON, CHA, chosen by point-buy at creation.

**Effective stats**:
Base stats plus the stat bonuses of the equipped Loadout.
_Avoid_: effective base stats

**Derived stats**:
Values computed from Effective stats, Level and active effects: max HP, max mana, etc.

**Ability**:
Anything a Character or NPC can use in combat — an Attack or a Spell.
_Avoid_: skill

**Attack**:
A physical Ability, usually costing rage.

**Spell**:
A magical Ability, usually costing mana, sometimes affecting an area.

**Rank**:
The power tier of an Ability, unlocked at a given Level and matched to a Danger tier.
_Avoid_: skill level, attack level, spell level

**Global cooldown**:
The short lockout shared by all Abilities after any use.
_Avoid_: GCD in prose

**Cooldown**:
The per-Ability delay before that Ability can be used again.

**Status effect**:
A timed modifier on a combatant (damage over time, boost, slow…).

**Target**:
The entity a Character has currently selected (NPC, Action block, Placeable).

### NPCs & quests

**NPC**:
A non-player creature or person in the World, spawned by Danger level; may be hostile, an animal, a pet, a quest giver or a merchant.
_Avoid_: mob, entity (for NPCs specifically)

**Pet**:
An NPC tamed by a Character that follows, fights for it and can be summoned.

**Quest giver**:
An NPC that offers Quests suited to its Danger tier.

**Quest**:
A task offered by a Quest giver (kill, fetch…) with objectives and a reward, turned in on completion.

### Social

**Group**:
A small party of online Characters, formed and dissolved during play, that shares chat and XP for the game experience. It grants no permissions.
_Avoid_: party, team

**Guild**:
A permanent player organisation with Guild ranks and a shared bank.

**Guild rank**:
A position inside a Guild carrying permission flags.
_Avoid_: rank (alone — reserved for Ability Rank)

**Faction**:
A server-defined allegiance that disables friendly fire and shares Claim access between allies.

**Mini-game**:
A 1..N-player game played inside MiCraft whose rules live entirely in its own client bundle.

**Room**:
A temporary gathering of Characters playing one Mini-game together.

### Items & economy

**Block type**:
A kind of voxel (stone, grass, oak log…) with hardness, solidity and drops.

**Item**:
Something a Character holds in its inventory; some Items place a Block type.

**Armor**:
A wearable piece occupying body slots and granting stat bonuses.

**Loadout**:
The set of armor, weapons and tools a Character owns and has equipped.

**Drop**:
An Item released when a block is broken or an NPC dies.

## Flagged ambiguities

- **"playerId"** carries the Account email in some flows (auth token) and the Character id in others (Session keys). Resolved: in-game identifiers designate the Character; the Account is only named at login and in admin.
- **"group"** resolved: a **Group** is a gameplay party, an **RBAC group** is a permission set on a Character; never say "group" alone for the latter. Account-level access is a distinct concept: the **Admin role**.
- **"zone"** resolved: never used alone. It is split into **Region**, **Danger level**, **Danger tier**, **Instance** and **Weather cell**. The NPC spawn grid (`npcZoneSize`, `onZoneCrossed`) is an implementation detail, not a domain term.
- **"skill level"** resolved: it is the Ability **Rank** expected in a Danger tier.
- **"rank"** resolved: **Rank** is the Ability tier; a Guild's positions are **Guild ranks**.
