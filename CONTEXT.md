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
The difficulty of a Region, derived from the distance of its Voronoi seed to spawn and constant across the whole Region; drives which NPC types can enter its Roster and which Quests its Quest giver offers.
_Avoid_: zone level, area level

**Danger tier**:
A band of Danger levels (1–5). NPCs born in a tier mostly use Abilities of the matching Rank, and a Character needs Abilities of that Rank to survive there.
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
Building on a Claim and administering one are separate rights: abandoning a Claim or changing its trusted list
belongs to the owner alone. An RBAC group may grant either right as an override.
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
The progression step of a Character or NPC. A Character (or a Pet) raises it by earning XP; a wild NPC's Level is set at birth from its Region's Danger level.

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
The power tier of an Ability (1–5), set by Level: a Character unlocks it at a given Level, an NPC uses the Rank of its Level's band. The bands match the Danger tiers.
_Avoid_: skill level, attack level, spell level

**Global cooldown**:
The short lockout shared by all Abilities after any use. It lapses with the session; reconnecting clears it.
_Avoid_: GCD in prose

**Cooldown**:
The per-Ability delay before that Ability can be used again. It belongs to the Character and outlives the session,
so reconnecting never shortens it.

**Status effect**:
A timed modifier on a combatant (damage over time, boost, slow…).

**Protection**:
The self-cast Spell every Class owns from Level 1 that grants a timed Status effect raising one or two defenses (Armor class, Dodge, Magic resistance, max HP). Its Rank rises with Level like any Ability; a low Rank is outpaced by higher Danger tiers, not weakened.
_Avoid_: shield, ward (as generic names), buff (in prose)

**Dodge**:
The chance for a Character to avoid entirely a non-magical Ability that would have hit it.
_Avoid_: evasion

**Magic resistance**:
The chance that a magical Ability that would have hit a Character fails entirely: no damage, no Status effect. A magical Ability deals magic, fire, lightning or necrotic damage; Dodge covers the others.
_Avoid_: spell failure rate, spell resist

**Target**:
The entity a Character has currently selected (NPC, Action block, Placeable).

### NPCs & quests

**NPC**:
A non-player creature or person in the World; wild NPCs spawn from their Region's Roster. May be hostile, an animal, a pet, a quest giver or a merchant.
_Avoid_: mob, entity (for NPCs specifically)

**NPC type**:
A kind of NPC (wolf, bandit, hermit…) defined once, with the Biomes and Danger levels it can live in.
_Avoid_: species, entity type, mob type

**Roster**:
The fixed set of NPC types that populate one Region, drawn from those its Biome and Danger level allow; stable for the life of the World. The Region's wild population, births included, never exceeds its budget, shared among the Roster.
_Avoid_: spawn list, spawn table

**Pet**:
An NPC tamed by a Character that follows, fights for it and can be summoned.

**Quest giver**:
The one NPC per Region that offers Quests suited to that Region: every kill target is in the Region's Roster and the Quest level fits the Region's Danger tier. A Region with no suitable Quest has no Quest giver.

**Quest**:
A task offered by a Quest giver (kill, fetch…) with objectives and a reward, turned in on completion.

**Resident**:
A social NPC that lives in a Region (its Quest giver, its merchants) with an identity that survives restarts: a stable name, a Temperament and a Disposition towards each Character. Wild NPCs and Pets are not Residents.
_Avoid_: villager, notable, townsfolk

**Disposition**:
What one Resident thinks of one Character, moved only by game events between them (Quests turned in, purchases, attacks…), never by chat wording. It shapes the NPC's tone, greeting and prices, and at its lowest the NPC refuses to talk or trade. Wild NPCs have none. Read as one of five Disposition bands; it starts Neutral and drifts back towards Neutral without contact.
_Avoid_: affinity, reputation (reserved for a Region-wide opinion), relationship

**Disposition band**:
The named step a Disposition falls in: Hostile (refuses to talk or trade), Wary (higher prices), Neutral, Friendly (lower prices), Devoted (lowest prices, warm greeting).
_Avoid_: level, tier (both taken)

**Memory**:
A dated fact a Resident keeps about one Character (a Quest turned in, a blow received…); a Resident keeps only the latest few, and they feed its greetings and chat.
_Avoid_: history (the chat transcript), log

**Temperament**:
The fixed character trait of a Resident (gruff, cheerful, fearful…), drawn from the World seed, that picks which variant of a Bark it says and colours its chat tone.
_Avoid_: personality, mood (mood changes; a Temperament does not)

**Bark**:
A short unprompted line a Resident says aloud (a greeting, a reaction to something nearby, a remark on the weather or the hour), shown above it and in the chat of nearby Characters only. Drawn from written lines, never generated.
_Avoid_: shout, NPC message, ambient chat

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
- **"zone"** resolved: never used alone. It is split into **Region**, **Danger level**, **Danger tier**, **Instance** and **Weather cell**. `npcZoneSize` (the spawn candidate radius) is an implementation detail, not a domain term. "NPCs of a zone" means the **Roster** of a **Region**.
- **"skill level"** resolved: it is the Ability **Rank** expected in a Danger tier.
- **"rank"** resolved: **Rank** is the Ability tier; a Guild's positions are **Guild ranks**.
