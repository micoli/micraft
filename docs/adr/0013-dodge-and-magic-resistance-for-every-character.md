---
status: accepted
---

# Dodge and Magic resistance apply to every Character, not only under a Protection

Once an Ability has hit a Character, a Dodge roll (physical and poison damage) or a Magic resistance roll (magic,
fire, lightning, necrotic damage) can still cancel it entirely. Both are Derived stats every Character has from its
Effective stats, and a Protection only adds to them; each is capped at 60 %. The fields already existed but no combat
path read them, so this shifts the balance of all combat, not just of protected Characters. We needed them for the
Rogue, Mage and Ranger Protections, and a defense that only exists while a Spell is active would make DEX and WIS
worthless as defensive stats.

## Considered options

- **Bonus exists only under a Protection**: rejected, it leaves two computed stats dead and DEX/WIS without a
  defensive role.
- **Keep the old formulas**: rejected, `DEX × 2.5` gave ~35 % Dodge at DEX 14 before any Protection. Dodge becomes
  `(DEX − 10) × 1.5`; Magic resistance keeps `(WIS − 10) × 2`.

## Consequences

- Every path where an Ability hits a Character rolls them, AoE Spells included; guaranteed-hit direct damage
  (siege projectiles) and damage-over-time ticks do not.
- A dodged or resisted Ability applies no Status effect either.
- Player-versus-player hits roll them too.
- NPCs do not get Dodge or Magic resistance.
