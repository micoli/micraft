# 04: The other four Protections — Shadowstep, Arcane Ward, Nature's Veil, Fortitude

Status: resolved
Type: task
Blocked by: 02, 03

**What to build:** every Class owns its Protection from Level 1. A Rogue's Shadowstep raises Dodge; a Mage's Arcane Ward raises Magic resistance and a little Armor class; a Ranger's Nature's Veil raises Magic resistance and Dodge; a Cleric's Fortitude raises max HP and multiplies HP regen by 1.5. Values per Rank and mana cost (10 / 15 / 20 / 25 / 30) are in the spec's table. Spec: `../spec.md` (Solution table).

- [x] Four Protection Spell YAMLs with Ranks 1–5 and the spec's values; each Class grants its Protection at Level 1 — `shadowstep.yaml` (ROGUE), `arcane_ward.yaml` (MAGE), `natures_veil.yaml` (RANGER), `fortitude.yaml` (CLERIC); grants added to `resources/config/classes.yaml`
- [x] Protection Dodge / Magic resistance bonuses add to the base values and respect the 60 % caps — **found and fixed a formula bug while testing this**: `DerivedStatsCalculator` floored the base Dodge/Magic-resist term together with the Protection bonus instead of flooring the base alone first (ADR-0013's "floor 0" applies to the base formula, independently of the Protection bonus); a low-DEX/WIS Character was getting less bonus than their Rank grants. Fixed, `ProtectionBonusFromConfigTest` pins the real config values per Rank.
- [x] Fortitude raises max HP and HP regen while active; current HP is clamped when it lapses — reuses the generic `StatusEffect.Protected` ∈ `STAT_EFFECTS` resync path from ticket 03 (no new code needed); covered by `ProtectionBonusFromConfigTest`
- [x] Config test: every Class has a Protection at Level 1 with Ranks 1–5 — `ProtectionSpellConfigTest` generalized to all 5 Classes
- [x] Derived stats tests per Protection per Rank; one scripted-roll combat test per defense type (a Shadowstep Rogue dodges, an Arcane Ward Mage resists) — `ProtectionBonusFromConfigTest` (all 5, all Ranks, from real config), `CombatProcessorTest`'s two new scripted-roll tests
- [x] Names, descriptions and icons for each Protection; i18n in every locale — `protections:client:<id>_name`/`_description` (en/fr); client `BuffBadge` now keys icon/color by `protectionId` (was always showing generic "Protected")
- [x] Server tests and `make quick-code-standard` green — full `:server:test` green, `make quick-code-standard` clean, `make ts-typecheck` clean, `make build-wasm` compiles

**Code review (`/code-review medium`):** 3 findings, 2 fixed, 1 accepted as debt:
- `SpellProcessor.applyProtection` passed `rankDef.durationSec` straight through with no fallback (unlike the attack-effect path, which falls back to the marker's own duration) — a misconfigured Rank (durationSec unset/0) would grant an already-expired Protection with no error. Fixed: falls back to `StatusEffect.Protected.durationSec` when `durationSec <= 0`.
- `handleSpell` dispatched `PROTECTION`/`DIRECT_DAMAGE` via an ad hoc `if`-chain while `handleCastAoeSpell` used a `when(spell.type)` for the same enum — same case, two shapes. Fixed: `handleSpell` now uses the same `when` shape.
- `BuffBadge.tsx`'s `PROTECTION_LABELS`/`PROTECTION_COLORS` hardcode the five known `protectionId`s, no shared source of truth with the server's Spell config/i18n. Accepted as debt: fixing it properly needs an icon field in the server config + API exposure, which is a bigger change than this ticket's scope; the existing fallback (generic 🛡️/grey) degrades gracefully rather than erroring, so a 6th Class's Protection stays functional, just visually generic until that follow-up.
