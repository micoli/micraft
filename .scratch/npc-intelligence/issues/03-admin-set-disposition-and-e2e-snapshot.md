# 03: Admin set Disposition + E2E snapshot

**What to build:** An Admin can set a Character's Disposition with a Resident through
`/npc disposition set <resident> <character> <value>` (autocompletion of Resident and Character), so bands can
be reached for debugging and tests. The E2E snapshot exposes the Disposition band of the Resident in dialogue,
fed from the same server message as the UI. Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** 02 (Disposition from Quest turn-in)

**Status:** ready-for-agent

- [ ] Admin-only command with autocompletion; non-admins are refused
- [ ] Setting a value updates the band seen by the Character in dialogue and via `/npc disposition`
- [ ] `E2eSnapshot` gains a Disposition field fed by the dialogue message (no in-memory mutation from tests)
- [ ] Server test for the command; i18n keys in every locale
