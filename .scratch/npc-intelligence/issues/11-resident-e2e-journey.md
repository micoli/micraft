# 11: Resident E2E journey

**What to build:** One player-level E2E spec proving the feature end to end: walk up to the Quest giver and read
its greeting Bark, turn in a Quest, check the band with `/npc disposition`, use the admin command to make the
Resident Hostile, then see dialogue refused with a Bark. Driven only by slash commands, key presses and in-game
UI; assertions only read the E2E snapshot; per-test Account/World. Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** 03 (Admin set Disposition + E2E snapshot), 06 (Bark rate limits), 08 (Hitting a Resident, and
Hostile refusal)

**Status:** ready-for-agent

- [ ] New E2E spec following `quest-giver-dialog.spec.ts` conventions
- [ ] Greeting Bark, band after turn-in, and Hostile refusal asserted via `window.mcE2E`
- [ ] Passes locally alongside the existing suite (known pre-existing failures excepted)
