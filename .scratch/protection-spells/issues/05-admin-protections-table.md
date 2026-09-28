# 05: Admin — Protections table on the Classes page

**What to build:** an admin opens the Classes page and sees a Protections section listing each Class's Protection with its per-Rank values (bonuses, duration, Cooldown, cost), read live from config; after editing the YAML and reloading, the table shows the new values without a rebuild. Spec: `../spec.md` (Admin API, Admin UI).

**Blocked by:** 03 (Iron Skin tracer).

**Status:** ready-for-agent

- [ ] Read-only admin route returning every Class's Protection with its per-Rank values from live config
- [ ] OpenAPI exported and generated TS client regenerated (`make check-openapi` green)
- [ ] Protections section on the Classes page, one React component per file, i18n (en, fr)
- [ ] A Class without a Protection shows as such rather than failing
- [ ] Route test in the server HTTP tests
- [ ] Server tests and `make quick-code-standard` green
