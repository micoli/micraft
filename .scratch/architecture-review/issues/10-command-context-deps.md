# Commands declare their dependencies instead of receiving a bag of nullables

Status: needs-triage
Strength: Worth exploring

Split out of [06](06-authorizer.md) on 2026-09-28. The Authorizer part stays in 06; this ticket is only about how a
command receives its services.

## Files
- `command/CommandContext.kt`: 52 parameters, 50 of them nullable or defaulted (only `world` and `i18n` are required)
- 83 command files under `server/src/main/kotlin/org/micoli/micraft/command/`, 61 test files under
  `server/src/test/`
- `GameLoop.commandContextFor(session)` builds the whole bag on every command invocation

## Problem
- A command needing one manager receives 52 fields and has to null-check it, because production wires it and tests
  do not. The null-check is defensive noise, not a real branch.
- Adding a manager means editing `CommandContext`, `commandContextFor`, and nothing tells you which commands
  actually use it.
- A test must either build the bag or rely on the defaults, so a command's real dependency set is invisible.
- `09-loadout-grants` left `armorRegistry` / `weaponRegistry` / `toolRegistry` / category closures on the context
  beside `equipmentCatalog` for exactly this reason: no way to drop a field without auditing every command.

## Solution
Undecided — needs grilling. Candidate shapes: capability interfaces a command declares and the dispatcher satisfies;
a per-command context built from a declared dependency list; splitting `CommandContext` into a few cohesive service
groups. The choice drives whether this is mechanical (agent) or design work (human).

## Open questions
1. What replaces the bag: interfaces, constructor injection at registration, or grouped sub-contexts?
2. Do the closures (`broadcast`, `savePlayer`, `refetchChunks`, `reload*`) belong in the same mechanism as the
   managers, or are they a separate seam?
3. Can this land incrementally (one group of commands at a time) or does it need one sweep across all 83 files?
4. What keeps the 61 existing command tests green without rewriting their setup?

## Tests
Every command test builds or defaults a `CommandContext` today; the migration strategy has to keep them passing.
