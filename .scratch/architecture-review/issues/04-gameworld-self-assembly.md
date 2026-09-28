# GameWorld builds its own subsystems

Status: needs-triage
Strength: Worth exploring

## Files
- `game/GameLoop.kt` (2235 LOC, ~80 ctor params, `GameWorld(...)` L680-730 with ~45 args)
- `di/GameLoopModule.kt` (801 LOC)
- `game/world/GameWorldFactory.kt` (`buildGameWorld` L103, `buildE2eGameWorld` L489)
- `game/world/GameWorld.kt` (616 LOC)

## Problem
This duplication has already cost a production defect: `GameLoop`'s `GameWorld(...)` call dropped `spellProcessor`,
so no NPC cast a Spell in the production world. Split out as [11](11-npc-spells-dead-in-production.md).

`CombatProcessor`, `SpellProcessor`, `NpcSubsystemFactory`, `BlockBreaker`, `ClaimManager`, `TradeManager` and `PetManager` are each constructed in 3 places.
Adding a manager means editing 3 wiring sites plus `CommandContext`. GameWorld calls back into GameLoop through lambdas
(`commandContextProvider`, `broadcastPlayerAdminSink`, `intentCollectorProvider`, `onCommand`). It has a wide interface and no depth of its own.

## Solution
GameWorld assembles its per-world subsystems from `SharedGameServices` + options (`buildGameWorld` is already close to this).
GameLoop keeps only connection handling and the registry. Related: E2E GameWorld refactor (A9.1+).

Also in scope: `ClaimRegistry.factionAlly` is a post-construction `var` defaulting to "never an ally", so a world
built without the wiring silently denies faction allies. Turning it into a constructor dependency needs the
construction order this ticket reworks. See [06](06-authorizer.md).

## Tests
13 test files construct `GameLoop(`. Its heavy defaults make it hard to fake a single subsystem.
