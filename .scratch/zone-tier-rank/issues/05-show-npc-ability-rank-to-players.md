# Show NPC Ability Rank to players

Status: needs-triage
Type: task
Blocked by: 01

## Context

After issue 01, an NPC's Ability Rank follows its Level and a Pet gains Ranks as it levels up. Issue 01 only
surfaces the effective Rank in admin views and the world simulator. Players cannot see it.

Deferred from the grilling of issue 01 (2026-09-27).

## Candidates

- Pet sheet: current Rank per Ability, and the Level at which the next Rank is reached.
- NPC nameplate or target frame: Rank band (or Danger tier) of the targeted NPC.
- Combat log / tooltip: Rank of the Ability that hit the Character.

## Open questions

- Which of the candidates are worth it? Pet sheet first?
- New server message field vs. derivable client-side from the NPC Level already sent (the Level → Rank function
  lives in `core`, ADR-0002)?

## Acceptance

- To be written after triage. Any new displayed state is exposed in `E2eSnapshot` and covered by an E2E spec.
