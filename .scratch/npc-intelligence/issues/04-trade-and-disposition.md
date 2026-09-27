# 04: Trade and Disposition

**What to build:** Buying from or selling to a merchant Resident raises its Disposition a little and records a
Memory, capped per in-game day so trades cannot be farmed. Shop prices follow the band's price modifier
(Wary costs more, Friendly and Devoted cost less), computed server-side. The shop window shows the band and its
progress. Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** 02 (Disposition from Quest turn-in)

**Status:** ready-for-agent

- [ ] Relations module unit tests: trade delta, daily cap and its reset on the next in-game day, price modifier
      per band
- [ ] Shop prices sent to the Character reflect its band
- [ ] Shop message carries band + progress; client shows them
- [ ] Test-world scenario: repeated trades stop raising Disposition once the daily cap is hit; prices change when
      the band changes
- [ ] Price modifiers and cap in YAML config; schemas/docs regenerated
