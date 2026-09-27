# `/quest accept` limited to the current Region's giver

Status: ready-for-agent
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

- [ ] Server tests: accept refused outside the offering Region, accepted inside, admin bypass.
- [ ] Quest E2E specs pass.
