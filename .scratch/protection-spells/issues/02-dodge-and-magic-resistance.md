# 02: Dodge and Magic resistance for every Character

**What to build:** once an Ability would hit a Character, one avoidance roll can cancel it entirely — Dodge for physical and poison damage, Magic resistance for magic, fire, lightning and necrotic damage. An avoided hit deals no damage and applies no Status effect; the Character sees a floating "Dodge" / "Resisted" text and a combat log line. Applies to NPC → Character, Character → Character and AoE Spells (Magic resistance only, no to-hit roll there). Siege direct damage, damage-over-time ticks and NPC targets are unaffected. Spec: `../spec.md`; ADR-0013; glossary: Dodge, Magic resistance.

**Blocked by:** 01 (Injectable roll source per World).

**Status:** ready-for-agent

- [ ] Base Dodge = `(DEX − 10) × 1.5` %, floor 0; Magic resistance = `(WIS − 10) × 2` %, floor 0; each capped at 60 % (caps and magical damage-type set are `core` constants)
- [ ] One damage-type classification in `core`, used by combat (and later the simulator)
- [ ] Scripted-roll tests: a hitting NPC Ability is dodged / resisted / lands according to the avoidance roll; a dodged hit applies no Status effect
- [ ] PvP hits roll avoidance; AoE Spells roll Magic resistance on Character targets; NPC targets never roll
- [ ] Siege direct damage and DoT ticks bypass avoidance (tests)
- [ ] Combat log and hit messages carry the dodged / resisted outcome; client shows floating text; i18n keys in every locale
- [ ] Reference docs regenerated for the new constants (`make docs`)
- [ ] Server tests and `make quick-code-standard` green
