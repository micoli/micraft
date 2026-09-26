# Split Admin roles from RBAC groups

Status: needs-triage
Type: task

## Context

Domain decision (CONTEXT.md, 2026-09-26): an **Admin role** belongs to an Account and gates the admin panel and
admin operations. An **RBAC group** belongs to a Character and gates in-game commands and features. These are two
distinct concepts. The code still models them as one catalog.

## Current state

- A single catalog: `auth/GroupConfig.kt` `GroupsConfig` (`groups`, `defaultGroups = [player]`), loaded from
  `data/config/auth/groups.yaml` (falls back to `resources/config/groups.yaml`), schema `groups.schema.json`.
- Account side: `UserEntry.groups` → `LocalAuthProvider.kt:47` `groupsConfig.resolvePermissions(user.groups)`.
- Character side: `PlayerState.groups` → `RbacSync.kt:20` `groupsConfig.resolvePermissions(session.state.groups)`.
- Admin UI and routes manage both through one list: `AdminController.kt:1237-1268` (`config.groups`) and `/api/admin/users` for Account groups.
- A single permission space: `auth/Permission.kt`, where `CorePermissions.ADMIN` and the command permissions live together
  and `*` bypasses everything. A Character RBAC group can hold `admin`, and an Admin role can hold in-game command permissions.
- In-game commands `/rbac:setgroup` and `/rbac:removegroup` target Characters; `/adduser` sets Account groups.

## Scope

- Two catalogs:
  - **Admin roles**: Account-scoped, with admin permissions only (e.g. `admin:*` namespaces per admin area).
  - **RBAC groups**: Character-scoped, with command and feature permissions only.
- Separate storage (e.g. `auth/admin-roles.yaml` vs `auth/groups.yaml`) and separate schemas. Validation rejects a
  permission from the wrong space.
- Rename `UserEntry.groups` → `adminRoles` (with a migration of `users.yaml`).
- Admin UI: separate pages or tabs for Admin roles and RBAC groups.
- Update `docs/systems/auth-rbac.md`, the CLAUDE.md "Auth system" section and the OpenAPI spec (`make dc CMD="./gradlew :server:exportOpenApi"`, `make gen-api`).

## Open questions

- Does `*` stay valid in both catalogs, or does each have its own wildcard (`admin:*` vs in-game `*`)?
- Can an Admin role grant in-game powers (god mode, teleport) to every Character of the Account, or must that always
  go through a Character's RBAC group?
- Should the default Admin role for a new Account be empty?

## Related

- `.scratch/architecture-review/issues/06-authorizer.md`: a single `Authorizer` module; do both together, or this issue first.

## Acceptance

- An Admin role cannot contain in-game permissions, and an RBAC group cannot contain admin permissions (validation + test).
- Admin panel access depends only on Admin roles; in-game commands depend only on RBAC groups.
- Existing `users.yaml` and player saves load without manual editing.
- `make dc CMD="./gradlew :server:test"` passes.
