# Quest coverage check per Biome × Danger tier

Status: ready-for-agent
Type: task
Blocked by: 01, 05

## Context

Spec: `../spec.md` (Guards and tooling). With the Roster filter, some (Biome, Danger tier) pairs may have Regions
whose Quest giver offers nothing, so no giver appears there.

## What to build

- A check, run at Quest load and in a server test, that reports each (Biome, Danger tier) pair where no Quest can
  pass the filter for any possible Roster. Warning only, never a load failure.
- Surface the result where Quest writers see it (load log and the test's output), naming the uncovered pairs and
  the NPC types available there.

## Acceptance criteria

- [ ] Server test prints the coverage table and fails only on unexpected exceptions.
- [ ] The current gaps are listed in this issue's comments as a backlog of Quests to write.
