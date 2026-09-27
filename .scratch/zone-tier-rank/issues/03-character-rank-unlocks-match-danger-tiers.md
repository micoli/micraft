# Character Rank unlocks match the Danger tier bands

Status: needs-triage
Type: task
Blocked by: —

## Context

Domain rule (CONTEXT.md, **Rank** / **Danger tier**): a Character unlocks each Rank at a given Level, and needs
Abilities of Rank N to survive in Danger tier N (Levels 1–5 / 6–10 / 11–15 / 16–20 / 21+). NPCs use the Rank of
their Level's band (issue 01). The Class unlock tables do not follow those bands.

Split out of the grilling of issue 01 (2026-09-27).

## Current state

- Unlocks per Class and Level in `resources/config/classes.yaml` (`<CLASS>.levels.<Level>.attacks[].{attack, rank}`),
  only for WARRIOR, MAGE, RANGER (NPCs also reference ROGUE, which has no table).
- Examples (RANGER): `wolf_bite` R1 → R2 at L7 → R3 at L11 → R4 at L15; `poison_bite` R3 early, R4 at L9 (tier 2).

## Target rule (to confirm)

Rank N of an Ability a Class offers is unlocked **at the latest** at the first Level of tier N (1, 6, 11, 16, 21).

## Open questions

- Is an early unlock (e.g. `poison_bite` R4 at L9, i.e. tier 2) acceptable, or must Rank N also not unlock before
  tier N starts?
- Enforce by a validation test on `classes.yaml`, or derive Character unlocks from the same bands as NPCs (a per-Rank
  unlock Level on the Ability definition, shared by Characters and NPCs)?
- Should ROGUE get a Class table, or NPC `characterClass: ROGUE` be remapped?

## Acceptance

- `classes.yaml` follows the rule chosen above, checked by a test.
- `make dc CMD="./gradlew :server:test"` passes; `make docs` if generated reference changes.
