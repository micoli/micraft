# 07: Client floating text for dodged / resisted hits

Status: ready-for-agent
Type: task
Blocked by: 02

**What to build:** the "Dodge" / "Resisted" floating text over a Character promised by 02's spec, and
the `combat:server:dodged` / `combat:server:resisted` i18n keys it will show, translated. Split out of
02 because no floating-text mechanism exists client-side today — not even for miss/crit — so this is a
new client feature, not a wire-up of an existing one. Spec: `../spec.md`; ADR-0013; glossary: Dodge,
Magic resistance.

- [ ] A protocol field or message carries the dodged/resisted outcome to the client for a hit on the
      local player or a visible Character (`CombatProcessor`'s `HitOutcome` in
      `server/src/main/kotlin/org/micoli/micraft/game/combat/CombatProcessor.kt` is the source of
      truth — reuse it rather than re-deriving the outcome client-side)
- [ ] Babylon-side floating text above the Character showing "Dodge" / "Resisted", translated via the
      new i18n keys
- [ ] `combat:server:dodged` / `combat:server:resisted` keys added to every locale (`en.yaml`,
      `fr.yaml`) once they are actually consumed (the i18n coverage test rejects unused keys)
- [ ] E2E test driving a scripted-roll Test world (per ADR-0012) where a hit is forced to be avoided,
      asserting the floating text (or its `window.mcE2E` equivalent) appears
- [ ] `make quick-code-standard` and the e2e suite green
