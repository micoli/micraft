# 08: Hitting a Resident, and Hostile refusal

**What to build:** When a Character hits a Resident, the Resident's Disposition towards them drops sharply, a
Memory is recorded, and the Resident reacts with a Bark. A Resident whose band is Hostile refuses dialogue,
trade and LLM chat with a templated refusal Bark; no LLM call is made. Requires a new NPC subsystem hook
"Character hits an NPC", alongside the existing ones. Spec: `.scratch/npc-intelligence/spec.md`; ADR 0011.

**Blocked by:** 04 (Trade and Disposition), 06 (Bark rate limits)

**Status:** ready-for-agent

- [ ] New hook fired when a Character damages an NPC
- [ ] Relations module unit test: hit delta, refusal at Hostile
- [ ] Hit → reaction Bark + lower band (Test world)
- [ ] Hostile: dialogue, shop and chat refused with a Bark; `NpcChatServiceTest` asserts the fake Ollama is not
      called
- [ ] Refusal lines in every locale, per Temperament
