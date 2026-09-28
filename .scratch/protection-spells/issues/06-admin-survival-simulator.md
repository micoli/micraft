# 06: Admin — survival simulator

**What to build:** in the Protections section, an admin picks a Level (1–30) and a Danger tier, optionally edits the simulated Character's Base stats (default 10 everywhere plus Class bonuses) and an equipment AC bonus (default 0), and sees per Class: the Protection Rank active at that Level, the chance to be hit without / with the Protection, mean damage per incoming attack, and mean number of attacks survived without / with the Protection, plus the physical / magical share of that tier's NPC Abilities. Numbers are computed on the server, analytically, with the same formulas as combat. Spec: `../spec.md`.

**Blocked by:** 02 (Dodge and Magic resistance for every Character), 05 (Admin Protections table).

**Status:** ready-for-agent

- [ ] Pure server-side simulator module (no World, no session): inputs + config → per-Class report; exact probabilities over d20 and dice, no Monte-Carlo
- [ ] NPC side uses the real Abilities of the NPC types allowed in the chosen Danger tier, at that tier's Rank
- [ ] Reuses the combat hit / avoidance formulas and the damage-type classification; no duplicated rule in TS
- [ ] Admin route as a thin adapter; OpenAPI + generated TS client regenerated
- [ ] UI form and results table in the Protections section, one React component per file, i18n (en, fr)
- [ ] Unit tests on known inputs (e.g. AC 11 vs power 6 → 80 % hit chance; Iron Skin Rank 1 → 60 %); route test
- [ ] Server tests and `make quick-code-standard` green
