# 10: Ambient Barks

**What to build:** Residents remark on the World: when the time-of-day phase changes (dawn, dusk, night) and when
the weather changes in their Weather cell, a Resident may say a Bark to Characters within the radius, coloured
by its Temperament. Subject to the rate limits. Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** 06 (Bark rate limits)

**Status:** ready-for-agent

- [ ] Phase change of Game time triggers an ambient Bark to nearby Characters
- [ ] Weather change in the Resident's Weather cell triggers one
- [ ] No ambient Bark when nobody is within the radius
- [ ] Test-world scenarios with controlled Game time and weather; lines in every locale
