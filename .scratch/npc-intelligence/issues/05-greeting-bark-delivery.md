# 05: Greeting Bark delivery

**What to build:** When a Character comes within the Bark radius (config, default 24 blocks) of a Resident, the
Resident greets them with a Bark: a bubble above the Resident and a line in the "around" chat with the Resident
as sender, sent only to Characters within the radius, each in their own language. The line is picked from
translation keys by trigger × Temperament × Disposition band, and mentions the latest Memory when there is one
("thanks again for the wolves"). No rate limiting yet (ticket 06). Barks are never generated (ADR 0011).
Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** 01 (Resident identity), 02 (Disposition from Quest turn-in)

**Status:** ready-for-agent

- [ ] New server message for the Bark bubble (next free proto id); `ChatMessage` on the "around" channel
- [ ] Small "send to Characters within radius" helper, reused by later tickets
- [ ] Server-side localisation per recipient
- [ ] Line variants by Temperament and band; Memory-aware greeting
- [ ] Client shows the bubble above the Resident for a few seconds; `E2eSnapshot` exposes received Barks
- [ ] Test-world scenarios: greeting received inside radius, not beyond it; two Characters with different
      languages each get their own; band changes the line
- [ ] Minimal Bark lines in every locale
