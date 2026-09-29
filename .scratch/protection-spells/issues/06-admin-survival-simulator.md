# 06: Admin — survival simulator

Status: resolved
Type: task
Blocked by: 02, 05

**What to build:** in the Protections section, an admin picks a Level (1–30) and a Danger tier, optionally edits the simulated Character's Base stats (default 10 everywhere plus Class bonuses) and an equipment AC bonus (default 0), and sees per Class: the Protection Rank active at that Level, the chance to be hit without / with the Protection, mean damage per incoming attack, and mean number of attacks survived without / with the Protection, plus the physical / magical share of that tier's NPC Abilities. Numbers are computed on the server, analytically, with the same formulas as combat. Spec: `../spec.md`.

- [x] Pure server-side simulator module (no World, no session): inputs + config → per-Class report; exact probabilities over d20 and dice, no Monte-Carlo — `ProtectionSimulator.kt` (`game/combat/simulator/`), takes `classRegistry`/`spellRegistry`/`attackRegistry`/`npcDefinitions` + `ProtectionSimulationInput` (level, dangerTier, baseStats, equipmentAcBonus)
- [x] NPC side uses the real Abilities of the NPC types allowed in the chosen Danger tier, at that tier's Rank — `tierAbilities()` filters `NpcDefinition`s whose `minLevel..maxLevel` overlaps the tier's `ZoneTier.npcLevelRange`, resolves each Attack's usable Rank via the existing `AttackDefinition.usableRank`/`AbilityRank`
- [x] Reuses the combat hit / avoidance formulas and the damage-type classification; no duplicated rule in TS — `hitChancePct` is the exact-probability form of `CombatProcessor`'s to-hit roll (doc-linked), avoidance uses the shared `DamageType.isMagical`/`CombatConstants`/`DerivedStatsCalculator`; everything stays server-side Kotlin, nothing duplicated in TS
- [x] Admin route as a thin adapter; OpenAPI + generated TS client regenerated — `GET /api/admin/protections/simulate` (query params) in `AdminController.kt`, `ProtectionSimulationDto`/`ClassSurvivalDto`; `make gen-api`, `make check-openapi`, `make check-schemas` green
- [x] UI form and results table in the Protections section, one React component per file, i18n (en, fr) — `ProtectionSimulator.tsx` (container/state), `ProtectionSimulatorForm.tsx`, `ProtectionSimulatorResults.tsx`, wired into `ClassesPage.tsx`; `classes.simulator*` keys in `admin/i18n/{en,fr}.ts`
- [x] Unit tests on known inputs (e.g. AC 11 vs power 6 → 80 % hit chance; Iron Skin Rank 1 → 60 %); route test — `ProtectionSimulatorTest.kt` (pure formulas + fixture-based `simulate()` + real shipped config sanity check), 4 new tests in `AdminContentRoutesTest.kt` (200, 400×2, 401)
- [x] Server tests and `make quick-code-standard` green — full `:server:test`, `make ts-typecheck`, `make quick-code-standard` all green
