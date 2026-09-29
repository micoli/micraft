# 05: Admin — Protections table on the Classes page

Status: resolved
Type: task
Blocked by: 03

**What to build:** an admin opens the Classes page and sees a Protections section listing each Class's Protection with its per-Rank values (bonuses, duration, Cooldown, cost), read live from config; after editing the YAML and reloading, the table shows the new values without a rebuild. Spec: `../spec.md` (Admin API, Admin UI).

- [x] Read-only admin route returning every Class's Protection with its per-Rank values from live config — `GET /api/admin/protections` in `AdminController.kt`, resolves each Class's `SpellType.PROTECTION` grant from `gameLoop.classRegistry`/`gameLoop.spellRegistry` (live, reload-aware — same registries `/api/admin/classes`/`/api/admin/skills` already use)
- [x] OpenAPI exported and generated TS client regenerated (`make check-openapi` green) — `server/openapi/openapi.yaml` + README table regenerated, `getApiAdminProtections`/`ClassProtectionDto`/`ProtectionRankDto` generated and re-exported in `admin/apiTypes.ts`; `make check-openapi` and `make check-schemas` both green
- [x] Protections section on the Classes page, one React component per file, i18n (en, fr) — `ProtectionsSection.tsx` + `ProtectionRankCell.tsx`, wired into `ClassesPage.tsx`; `classes.protections*`/`classes.noProtection` keys in `admin/i18n/{en,fr}.ts`
- [x] A Class without a Protection shows as such rather than failing — `spellId: null` renders `t("classes.noProtection")` (italic placeholder), no crash
- [x] Route test in the server HTTP tests — 3 new tests in `AdminContentRoutesTest.kt` (200 with real `iron_skin` data, 401 without token, 200 with admin token)
- [x] Server tests and `make quick-code-standard` green — full `:server:test`, `make ts-typecheck`, `make quick-code-standard`, `make build-wasm` all green

**Code review (`/code-review medium`):** 2 findings, both fixed:
- The admin route duplicated `SpellProcessor.ownProtectionSpell`'s "which spellId is this Class's Protection" lookup, minus its Level filter — the two could silently resolve a different spellId if a Class ever granted two different Protection Spells at different Levels. Fixed: extracted `ClassDefinitionEntry.protectionSpellGrants(spellRegistry)` (sorted by Level, deduped by spellId) in `SpellDefinition.kt`, used by both `SpellProcessor` (which still applies its own Level filter) and `AdminController`.
- `ProtectionRankCell`'s cost label fell back to "0 rage" when a Rank had both `manaCost` and `rageCost` at 0. Fixed: omits the cost segment entirely when both are 0.
