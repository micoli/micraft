# Rebalance NPCs after Level-based Rank

Status: needs-triage
Type: task
Blocked by: 01

## Context

Issue 01 derives an NPC's Ability Rank from its Level instead of the yaml `rank:`. Many low-Level NPC types lose
Ranks (e.g. `wolf_man`, L2–4: `wolf_bite` R3 → R1, `shadow_claw` / `heavy_slash` R2 → R1), so tier 1–2 Regions get
easier. Grilling decision (2026-09-27): accept the change in 01, measure, compensate separately.

## Scope

- Run the world simulator (`server/.../simulation/WorldSimulator.kt`) with the same seed before and after 01; compare
  Character survival / time-to-kill per Danger tier.
- List NPC types whose effective damage dropped most (yaml `rank:` before 01 vs Level band).
- If needed, compensate with hp / base stats / Level range — not with Ranks (the Rank stays the Level band).

## Open questions

- Which simulator metrics define "balanced" per tier (deaths per hour, time-to-kill, XP per hour)?
- Target: restore pre-01 difficulty, or accept an easier tier 1 as onboarding?

## Acceptance

- Before/after simulator report committed under `.scratch/zone-tier-rank/`.
- Any yaml tuning is backed by the report; `make dc CMD="./gradlew :server:test"` passes.
