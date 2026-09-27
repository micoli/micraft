# 09: Nearby event Barks and witnesses

**What to build:** Residents react out loud to what happens within the Bark radius: a Character downed by an
NPC, an NPC killed, a fight (NPC damaged by NPC, Character hitting an NPC). When a Character kills a Resident,
every other Resident within the radius drops its Disposition towards the killer strongly and records a Memory
(online Characters only). Killing wild NPCs has no Disposition effect. Built on the existing NPC subsystem
callbacks plus the hook from ticket 08. Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** 08 (Hitting a Resident, and Hostile refusal)

**Status:** ready-for-agent

- [ ] Reaction Barks for downed Character, NPC killed and fight, within radius only, under the rate limits
- [ ] Resident murder penalises witnessing Residents only; non-witnesses unchanged
- [ ] Killing a wild NPC near a Resident leaves its Disposition unchanged
- [ ] Test-world scenarios for each trigger; lines in every locale
