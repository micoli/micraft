# `/quest accept` limited to the current Region's giver

Status: resolved
Type: task
Blocked by: 05

## Context

Spec: `../spec.md` (Quests). `QuestManager.accept` checks status, cooldown and prerequisites only, so any Quest can
be accepted anywhere and bypasses the Roster filter.

## What to build

- `/quest accept <id>` succeeds only if the Quest giver of the Character's current Region offers that Quest;
  otherwise a translated refusal. Characters with the admin permission bypass the check.
- Autocompletion lists only the Quests offered in the current Region.
- `quests-kill.spec.ts` and `quests-fetch.spec.ts`: move the Character (slash command) into a Region whose giver
  offers the Quest before accepting; expose what they need through `E2eSnapshot` if required.

## Acceptance criteria

- [x] Server tests: accept refused outside the offering Region, accepted inside, admin bypass.
- [x] Quest E2E specs pass.

## Comments

- 2026-09-27: `QuestCommand` refuses `/quest accept <id>` (`quest:server:not_offered_here`) unless the Quest suits the
  Character's current Region (`RegionQuests.suitedTo` on its Roster — exactly the giver's offer, without depending on
  the giver being spawned yet). Admins and Worlds without Regions are unrestricted. Autocompletion of the id lists the
  Region's Quests plus the Character's own (the same argument serves `abandon`/`status`).
- E2E players are admins, so `quests-kill` / `quests-fetch` keep accepting directly. `quests-kill` was already broken
  since issue 01 (it spawned `goat` and matched the old `Goat #` name): it now spawns `mountain_goat`, finds it by
  type and reads `progress.mountain_goat`. Both specs pass (`npx playwright test quests-kill quests-fetch`).
