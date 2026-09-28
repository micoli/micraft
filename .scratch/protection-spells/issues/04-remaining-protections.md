# 04: The other four Protections — Shadowstep, Arcane Ward, Nature's Veil, Fortitude

Status: ready-for-agent
Type: task
Blocked by: 02, 03

**What to build:** every Class owns its Protection from Level 1. A Rogue's Shadowstep raises Dodge; a Mage's Arcane Ward raises Magic resistance and a little Armor class; a Ranger's Nature's Veil raises Magic resistance and Dodge; a Cleric's Fortitude raises max HP and multiplies HP regen by 1.5. Values per Rank and mana cost (10 / 15 / 20 / 25 / 30) are in the spec's table. Spec: `../spec.md` (Solution table).

- [ ] Four Protection Spell YAMLs with Ranks 1–5 and the spec's values; each Class grants its Protection at Level 1
- [ ] Protection Dodge / Magic resistance bonuses add to the base values and respect the 60 % caps
- [ ] Fortitude raises max HP and HP regen while active; current HP is clamped when it lapses
- [ ] Config test: every Class has a Protection at Level 1 with Ranks 1–5
- [ ] Derived stats tests per Protection per Rank; one scripted-roll combat test per defense type (a Shadowstep Rogue dodges, an Arcane Ward Mage resists)
- [ ] Names, descriptions and icons for each Protection; i18n in every locale
- [ ] Server tests and `make quick-code-standard` green
