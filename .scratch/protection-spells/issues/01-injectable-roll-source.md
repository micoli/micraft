# 01: Injectable roll source per World

**What to build:** every die rolled by combat and Spell processing (to-hit d20, damage dice, downing rolls, future avoidance rolls) comes from a `Random` supplied per World by the World builder (ADR-0012), defaulting to a real random source. Tests can supply a scripted source and get deterministic outcomes. No gameplay change. Spec: `../spec.md` (Implementation Decisions → Injectable roll source).

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] No combat or Spell code reads the process-global random source anymore (no new process-global mutable state, ADR-0004)
- [ ] The World builder is the single place the roll source is chosen; production, Test worlds and the admin simulator arena all get one
- [ ] A test with a scripted source proves a forced natural 20 crits and a forced 1 misses, through the existing combat seam
- [ ] Existing combat and Spell tests pass unchanged
- [ ] `make dc CMD="./gradlew :server:test"` and `make quick-code-standard` green
