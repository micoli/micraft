# 02: Disposition from Quest turn-in, visible and persisted

**What to build:** Turning in a Quest raises the Quest giver's Disposition towards that Character and records a
Memory. The Player sees the Disposition band and its progress in the Quest giver dialogue, and can check it with
`/npc disposition [resident]` (bindable, autocompleted, defaults to the targeted Resident). Relations survive a
server restart. Rules live in a new pure "Resident relations" module: hidden score starting Neutral, five
Disposition bands (Hostile / Wary / Neutral / Friendly / Devoted), progress within band, FIFO of 5 Memories,
lazy drift towards Neutral over Game time, entry dropped once back to Neutral. Tuning in a new layered YAML
section. Spec: `.scratch/npc-intelligence/spec.md`; LLM never involved (ADR 0011).

**Blocked by:** 01 (Resident identity)

**Status:** ready-for-agent

- [ ] Relations module unit tests: Quest delta, band thresholds and progress, drift over Game time, FIFO of 5,
      drop-when-neutral
- [ ] Relations stored in the Character save, keyed by Resident key; orphan entries kept on load
- [ ] Quest turn-in raises Disposition and adds a dated Memory
- [ ] Quest giver dialogue message carries band + progress; client shows band label and gauge
- [ ] `/npc disposition` with autocompletion, bindable to a key; i18n keys in every locale
- [ ] Test-world scenario: turn in Quest → higher band in dialogue → save/reload keeps it
- [ ] New config defaults: schemas and reference docs regenerated; protocol changes via next free proto ids
