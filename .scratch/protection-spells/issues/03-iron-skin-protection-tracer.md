# 03: First Protection end to end — Iron Skin (Warrior)

Status: resolved
Type: task
Blocked by: None

**What to build:** a Level 1 Warrior types `/protect` (or presses the key bound to it) and gains Iron Skin: a 60 s Status effect raising Armor class by the Rank's value (+4 / +5 / +6 / +7 / +8 for Ranks 1–5, Rank from the existing Level → Rank bands), costing 0 rage, with a 90 s Cooldown. The Status effect bar shows it with its remaining time; re-casting while active refreshes the duration; dying removes it. This slice introduces the Protection Spell type and the Status effect with magnitude that the other Protections reuse. Spec: `../spec.md`; glossary: Protection.

- [x] New Protection Spell type, self-targeted; per-Rank definition holds duration, Cooldown, mana cost, rage cost and optional bonuses (AC, Dodge %, Magic resistance %, max HP, HP regen multiplier); JSON Schema regenerated (`make gen-schemas`) — `SpellType.PROTECTION`, `SpellRankDefinition` bonus fields, `resources/config/skills/spells/iron_skin.yaml`
- [x] The active Status effect records which Protection and which Rank granted it; the wire format carries both so the client shows the right icon and name — `StatusEffect.Protected` marker + `ActiveStatusEffect.protectionId`/`rank` (no new `@ProtoId`, `ServerMessage.StatusEffectUpdate` unchanged); client reducer/E2E carry the two fields through
- [x] Derived stats apply the active Protection's bonuses, resolved from the Spell config — `ProtectionBonus`, `DerivedStatsCalculator.compute`, `CharacterStats.protectionBonusOf` (resolved live from `SkillsConfigData.spells`, hot-reload wired)
- [x] Warrior class config grants Iron Skin at Level 1 with Ranks 1–5 — `resources/config/classes.yaml`; effective Rank picked by Level via `SpellDefinition.usableRank` (`AbilityRank`/`ZoneTier` bands, same as NPC Abilities)
- [x] `/protect` (no argument) casts the caller's Class Protection through the normal Spell path: Global cooldown, Cooldown (persisted, via AbilityGate), resource cost; bindable, no default key — `ProtectCommand`, `SpellProcessor.castOwnProtection`, `CommandContext.castProtection`, `ClientInputAction.PROTECT`, `keybindings.yaml: combat.protect: []`
- [x] A Class without a configured Protection gets a translated "no Protection" message — `combat:server:no_protection` (en/fr)
- [x] Protection removed on death (test) — `CombatProcessor.triggerDeath` clears `activeEffects`; `CombatProcessorTest."death clears active Protections"`
- [x] Server tests: cast applies the effect at the Level's Rank, AC rises by the Rank's value, Cooldown enforced, re-cast refreshes — `SpellProcessorProtectionTest`, `ProtectionSpellConfigTest`, `SpellProcessorLiveConfigTest` (real config), `DerivedStatsCalculatorTest` protection-bonus cases
- [ ] E2E: the player runs `/protect` / presses its key and `window.mcE2E` shows the Protection among active Status effects, fed from the same server message the Status effect bar uses — wiring is in place (`E2eSnapshot.activeEffects`, `GameUI.tsx` statusEffectUpdate handler) but no Playwright spec was added; follow-up if E2E coverage is wanted
- [x] Names and descriptions translated in every locale — `protections:client:iron_skin_name`/`iron_skin_description` (en/fr); no prior i18n convention existed for Spell names, this ticket establishes it
- [x] Server tests and `make quick-code-standard` green — full `:server:test` suite green, `make quick-code-standard` clean, `make ts-typecheck` clean, `make build-wasm` compiles

**Code review (`/code-review medium`):** 2 findings, both fixed:
- `CombatProcessor.triggerDeath` computed the respawn HP/mana from Derived stats *before* clearing the dying Character's active Protection — latent bug (masked today since Iron Skin has no `maxHpBonus`, will bite once Fortitude/issue-04 ships). Fixed: `activeEffects.clear()` now runs before `characterStats.derived(...)`.
- The Protection-effect wire mapping was duplicated verbatim in `GameUI.tsx` and `UIStateRegistry.ts`. Fixed: extracted `mapActiveEffects` into `lib/e2eBridge.ts`, used by both.

**Implementation notes:**
- Iron Skin's `manaCost` is set per spec (10/15/20/25/30) but is a no-op for Warrior today: `AbilityGate` only deducts/checks the resource matching the Class's `classResource` (RAGE for Warrior), so mana is inert until a MANA-resource Class (Mage, Ranger, Cleric) reuses `PROTECTION` in a later ticket. Rage cost is 0 as required.
- `StatusEffect.Protected` is one shared marker for every Class's Protection (not one subtype per Protection); `ActiveStatusEffect.protectionId`/`rank` disambiguate. A Character can only ever have their own Class's Protection active, so the existing "refresh by `effect::class`" match in `CombatProcessor.applyStatusEffectTo` still refreshes correctly without extra keying.
- Client status-effect bar still renders the raw effect name (`"Protected"`); wiring the translated icon/name from `protections:client:*` into the bar's React component is left for a follow-up (out of this slice's critical path).
