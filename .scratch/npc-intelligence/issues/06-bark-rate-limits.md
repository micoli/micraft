# 06: Bark rate limits and once-per-visit greeting

**What to build:** Residents stop repeating themselves. A Resident greets a Character once per visit, and again
only after the Character has left the radius and come back (enter/exit hysteresis). A Resident says at most one
Bark every ~20 s, and a Character receives at most one Bark every ~5 s, so a crowd of Residents cannot flood
their screen or chat. All values in YAML config. Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** 05 (Greeting Bark delivery)

**Status:** ready-for-agent

- [ ] Standing next to a Resident yields exactly one greeting
- [ ] Leaving beyond the exit distance and returning yields a new greeting
- [ ] Per-Resident and per-recipient limits hold, tested with an injected clock in the Test world
- [ ] Limits and hysteresis distances in YAML config; schemas/docs regenerated
