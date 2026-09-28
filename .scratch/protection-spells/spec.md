# Protection Spells — one per Class, from Level 1

Status: ready-for-agent
Type: spec

Decisions: grilling session 2026-09-28. Glossary: `CONTEXT.md` (Protection, Dodge, Magic resistance). ADR-0013.

## Problem Statement

A Level 1 Character has ~11 max HP and ~11 Armor class. A tier-1 NPC Rank 1 Ability hits it ~80 % of the time for
~11 damage, so a new Character dies in one or two hits and has no defensive Ability of any kind: every Class only
owns offensive Abilities. Two defensive Derived stats (Dodge, Magic resistance) are computed but no combat path
reads them, so DEX and WIS have no defensive role. Admins have no way to check whether a Class can survive a given
Danger tier short of playing it.

## Solution

Every Class owns a Protection from Level 1: a self-cast Spell that grants a timed Status effect raising one or two
defenses. Its Rank rises with Level on the existing Level → Rank bands (1–5, 6–10, 11–15, 16–20, 21+); a low Rank
keeps its value but is outpaced by higher Danger tiers.

| Class   | Protection    | Effect (Rank 1 → Rank 5)                                    |
|---------|---------------|-------------------------------------------------------------|
| Warrior | Iron Skin     | Armor class +4 / +5 / +6 / +7 / +8                          |
| Rogue   | Shadowstep    | Dodge +30 / +33 / +36 / +40 / +45 %                         |
| Mage    | Arcane Ward   | Magic resistance +30 / +33 / +36 / +40 / +45 %, AC +2 / +2 / +3 / +3 / +4 |
| Ranger  | Nature's Veil | Magic resistance +20 / +22 / +25 / +28 / +30 %, Dodge +15 / +17 / +19 / +21 / +24 % |
| Cleric  | Fortitude     | max HP +10 / +20 / +35 / +50 / +70, HP regen × 1.5          |

Target: at Rank 1, the chance a tier-1 NPC hits a protected Character drops from ~80 % to ~50–55 %.

Dodge and Magic resistance now apply to every Character (ADR-0013): once an Ability would hit, a Dodge roll
(physical, poison) or a Magic resistance roll (magic, fire, lightning, necrotic) can cancel it entirely — no damage,
no Status effect.

The admin Classes page gains a Protections section: the table above read live from config, plus a simulator that
shows, per Class, for a chosen Level and Danger tier, how often the Character is hit and how many attacks it survives,
with and without its Protection.

## User Stories

1. As a Level 1 Character of any Class, I want a Protection Spell available immediately, so that I can survive my first fights.
2. As a Character, I want to cast my Protection with `/protect`, so that I can trigger it the same way as every other in-game action.
3. As a Character, I want to bind `/protect` to a key, so that I can raise my defense mid-fight without typing.
4. As a Character, I want `/protect` to need no argument, so that it always casts my own Class's Protection.
5. As a Warrior, I want Iron Skin to raise my Armor class, so that NPC attacks miss me more often.
6. As a Warrior, I want Iron Skin to cost no rage, so that I can cast it before engaging, when my rage is 0.
7. As a Rogue, I want Shadowstep to raise my Dodge, so that I avoid physical and poison attacks outright.
8. As a Mage, I want Arcane Ward to raise my Magic resistance, so that hostile Spells fail against me.
9. As a Mage, I want Arcane Ward to also raise my Armor class a little, so that I survive the mostly physical NPCs of the early tiers.
10. As a Ranger, I want Nature's Veil to raise both my Magic resistance and my Dodge, so that I am covered against both kinds of attack.
11. As a Cleric, I want Fortitude to raise my max HP and HP regeneration, so that I can absorb more damage from Level 1.
12. As a Character, I want my Protection to last 60 s, so that it covers a whole fight.
13. As a Character, I want my Protection to have a 90 s Cooldown that survives reconnection, so that its uptime stays a tactical choice.
14. As a Character, I want my Protection to cost mana scaled by Rank (10 / 15 / 20 / 25 / 30), so that it competes with my offensive Spells.
15. As a Character, I want my Protection's Rank to follow my Level like every other Ability, so that it grows stronger as I progress.
16. As a Character, I want to see the Protection in my Status effect bar with its remaining time, so that I know when it lapses.
17. As a Character, I want to lose my Protection when I die, so that a respawn starts clean.
18. As a Character, I want an attack that I dodge to deal no damage and apply no Status effect, so that dodging is a full avoidance.
19. As a Character, I want a magical Ability that I resist to deal no damage and apply no Status effect, so that resistance is a full avoidance.
20. As a Character, I want a floating "Dodge" / "Resisted" text and a combat log line when I avoid a hit, so that I understand why I took no damage.
21. As a Character with high DEX, I want a base Dodge of `(DEX − 10) × 1.5` %, so that DEX has a defensive role even without a Protection.
22. As a Character with high WIS, I want a base Magic resistance of `(WIS − 10) × 2` %, so that WIS has a defensive role even without a Protection.
23. As a Character, I want Dodge and Magic resistance each capped at 60 % including my Protection, so that nobody becomes untouchable.
24. As a Character in PvP, I want my Dodge and Magic resistance to apply against other Characters, so that defenses mean the same thing against everyone.
25. As a Character caught in an area Spell, I want my Magic resistance to apply, so that AoE is not a way around it.
26. As a Character, I want siege projectiles and damage-over-time ticks to ignore Dodge and Magic resistance, so that those guaranteed-hit sources keep working as designed.
27. As a Character attacking an NPC, I want NPCs to have no Dodge or Magic resistance, so that fight length and XP pace stay unchanged.
28. As an admin, I want a Protections section on the Classes page listing every Class's Protection and its values per Rank, read from the live config, so that I can review the balance.
29. As an admin, I want the Protections table to reflect a config change after a reload, so that I can tune values without a rebuild.
30. As an admin, I want to pick a Level (1–30) and a Danger tier in a simulator, so that I can check survival at any point of progression.
31. As an admin, I want the simulator to show, per Class, the Protection Rank active at that Level, so that I see which values apply.
32. As an admin, I want the simulator to show, per Class, the chance to be hit with and without the Protection, so that I can measure its effect.
33. As an admin, I want the simulator to show, per Class, the mean damage per incoming attack and the mean number of attacks survived with and without the Protection, so that I can verify "enough to survive".
34. As an admin, I want the simulator to use the real Abilities of the NPC types allowed in the chosen Danger tier, at that tier's Rank, and show their physical / magical share, so that the numbers match the game.
35. As an admin, I want to edit the simulated Character's Base stats and an equipment AC bonus (defaults: 10 everywhere plus Class bonuses, no armor), so that I can test other builds.
36. As an admin, I want the simulator computed on the server with the same formulas as combat, so that it never drifts from the rules.
37. As a game designer, I want each Protection's per-Rank values, duration, Cooldown and cost in the Spell's YAML, so that tuning needs no recompilation.
38. As a French or English player, I want the Protection names, descriptions and avoidance messages translated, so that the game stays localized.

## Implementation Decisions

- **Protection is a new Spell type** alongside the existing ones (direct damage, necrotic AoE, token rage consume). A
  Protection Spell targets its caster only. Its per-Rank definition carries: duration, Cooldown, mana cost, rage cost
  (0 for the Warrior), and a set of defense bonuses — Armor class, Dodge %, Magic resistance %, max HP, HP regen
  multiplier — any of which may be absent.
- **Five Protection Spell YAMLs**, one per Class, granted at Level 1 in the class config with Ranks 1–5. The usable
  Rank is chosen by the existing Level → Rank mapping.
- **Status effect with magnitude.** Today's Status effects are fixed singletons without magnitude. A Protection needs
  per-Rank values, so the active Status effect records which Protection and which Rank granted it; Derived stats
  resolve the bonuses from the Spell config at computation time. Re-casting refreshes the duration (existing
  behaviour) rather than stacking. The Status effect wire format must carry the Protection identity and Rank so the
  client can show the right icon and name; this is a protocol change (next free `@ProtoId` if a new message is needed).
- **Derived stats.** The calculator takes active Protections into account: AC, Dodge, Magic resistance, max HP, HP
  regen. New base Dodge formula `(DEX − 10) × 1.5`, floor 0; Magic resistance keeps `(WIS − 10) × 2`, floor 0; both
  capped at 60 % after the Protection bonus. The caps and the magical damage-type set live as constants in `core`.
- **Damage type classification.** Magical: MAGIC, FIRE, LIGHTNING, NECROTIC. Non-magical (Dodge): PHYSICAL, POISON.
  A single classification function in `core`, reused by combat and the simulator.
- **Hit resolution.** After a to-hit roll succeeds against a Character, one avoidance roll: Dodge for non-magical,
  Magic resistance for magical. An avoided hit deals no damage and applies no Status effect. Applies on every path
  where an Ability hits a Character: NPC → Character, Character → Character, AoE Spells (which have no to-hit roll,
  so only the Magic resistance roll applies). Not applied to guaranteed-hit direct damage (siege) nor to
  damage-over-time ticks. NPC targets are unchanged.
- **Injectable roll source.** Combat and Spell processing draw every die from a `Random` supplied per World by the
  World builder (ADR-0012), default a real random source; tests supply a scripted one. No process-global dice.
- **Combat feedback.** New outcomes "dodged" and "resisted" in the combat log and in the hit/damage messages the
  client already consumes, with floating text; i18n keys in every locale.
- **Slash command `/protect`** with no argument, casting the caller's Class Protection through the normal Spell path
  (Global cooldown, Cooldown via AbilityGate, resource cost). Bindable in keybindings, no default key.
- **Loss on death**: Protections are removed when the Character dies. Reconnection follows the existing Status
  effect behaviour.
- **Admin API.** A read-only admin route returns every Class's Protection with its per-Rank values, read from the
  live config. A second admin route takes Level, Danger tier, optional Base stats and equipment AC bonus, and returns,
  per Class: active Protection Rank, hit chance without / with Protection, mean damage per incoming attack, mean
  attacks survived without / with Protection, and the physical / magical share of the tier's NPC Abilities. The
  computation is analytic (exact probabilities over d20 and dice), not Monte-Carlo, and reuses the combat formulas
  from `core` / the server combat module rather than duplicating them. OpenAPI and generated TS client regenerated.
- **Simulator module**: a pure server-side module (no World, no session) taking config + inputs and returning the
  report; the route is a thin adapter.
- **Admin UI**: a Protections section on the existing Classes page — the per-Rank table, then the simulator form
  (Level, Danger tier, Base stats, equipment AC bonus) and its per-Class results. One React component per file.
- **Generated artifacts** in the same change: JSON Schemas for the Spell config, OpenAPI + generated API client,
  reference docs for new constants.

## Testing Decisions

- Tests assert external behaviour only: messages sent, HP / Status effects on the Character, Derived stats values,
  API responses. No assertion on private helpers or call counts.
- **CombatProcessor** (prior art: `CombatProcessorTest`): with a scripted roll source, an NPC attack that hits is
  dodged / resisted / lands depending on the avoidance roll; a dodged hit applies no Status effect; PvP rolls
  avoidance; NPC targets never do; siege direct damage and DoT ticks bypass avoidance.
- **SpellProcessor** (prior art: `SpellProcessorAoeTest`, `SpellProcessorDirectDamageTest`, `SpellProcessorLiveConfigTest`):
  casting each Protection applies its Status effect with the Rank of the caster's Level, spends the right resource,
  respects Cooldown; re-cast refreshes; AoE Spells roll Magic resistance on Character targets.
- **DerivedStatsCalculator** (prior art: `DerivedStatsCalculatorTest`): new Dodge formula, Magic resistance, 60 % caps,
  each Protection's bonuses per Rank.
- **Simulator module**: pure unit tests on known inputs (e.g. AC 11 vs power 6 → 80 % hit; Iron Skin R1 → 60 %);
  route test in `server/src/test/.../http` (prior art: existing admin controller tests).
- **Config**: `SkillsConfigTest`-style test that every Class has a Protection at Level 1 with Ranks 1–5.
- **Death**: Protection removed on death.
- **E2E** (prior art: `combat-vitals.spec.ts`): a Character presses the key bound to `/protect` (or runs the command)
  and `window.mcE2E` exposes the Protection in its active Status effects, fed from the same server message the
  Status effect bar consumes.

## Out of Scope

- Casting a Protection on another Character or Group member.
- NPCs casting Protections; NPCs having Dodge or Magic resistance.
- Differentiating Mage and Ranger beyond their values.
- Editing Protection values from the admin page (ConfigEditor + reload covers it).
- Monte-Carlo simulation, fight-by-fight replay, or simulating the Character's own offense.
- Rebalancing existing NPC Abilities or Danger tiers.

## Further Notes

- Enabling Dodge and Magic resistance for every Character shifts all combat balance (ADR-0013); watch early-tier
  fights after release and tune via the simulator.
- Constitution was the original ask for the Cleric; it was replaced by flat max HP because +4 CON is only +2 max HP
  at Level 1.
- The original request listed the Rogue twice; the constitution line was assigned to the Cleric.
