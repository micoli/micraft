# 03: First Protection end to end — Iron Skin (Warrior)

Status: ready-for-agent
Type: task
Blocked by: None

**What to build:** a Level 1 Warrior types `/protect` (or presses the key bound to it) and gains Iron Skin: a 60 s Status effect raising Armor class by the Rank's value (+4 / +5 / +6 / +7 / +8 for Ranks 1–5, Rank from the existing Level → Rank bands), costing 0 rage, with a 90 s Cooldown. The Status effect bar shows it with its remaining time; re-casting while active refreshes the duration; dying removes it. This slice introduces the Protection Spell type and the Status effect with magnitude that the other Protections reuse. Spec: `../spec.md`; glossary: Protection.

- [ ] New Protection Spell type, self-targeted; per-Rank definition holds duration, Cooldown, mana cost, rage cost and optional bonuses (AC, Dodge %, Magic resistance %, max HP, HP regen multiplier); JSON Schema regenerated (`make gen-schemas`)
- [ ] The active Status effect records which Protection and which Rank granted it; the wire format carries both so the client shows the right icon and name (protocol change via the generated codec, next free `@ProtoId` if a new message is needed)
- [ ] Derived stats apply the active Protection's bonuses, resolved from the Spell config
- [ ] Warrior class config grants Iron Skin at Level 1 with Ranks 1–5
- [ ] `/protect` (no argument) casts the caller's Class Protection through the normal Spell path: Global cooldown, Cooldown (persisted, via AbilityGate), resource cost; bindable, no default key
- [ ] A Class without a configured Protection gets a translated "no Protection" message
- [ ] Protection removed on death (test)
- [ ] Server tests: cast applies the effect at the Level's Rank, AC rises by the Rank's value, Cooldown enforced, re-cast refreshes
- [ ] E2E: the player runs `/protect` / presses its key and `window.mcE2E` shows the Protection among active Status effects, fed from the same server message the Status effect bar uses
- [ ] Names and descriptions translated in every locale
- [ ] Server tests and `make quick-code-standard` green
