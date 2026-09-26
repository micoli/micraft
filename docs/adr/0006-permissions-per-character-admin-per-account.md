---
status: accepted
---

# In-game permissions belong to the Character, admin access to the Account

An Account may own several Characters, and each one holds its own **RBAC groups** that gate in-game commands and
features. A moderator's alt must not inherit moderator powers, and one Account can play both a game-master
Character and an ordinary one. Admin panel access is a separate concept, the **Admin role**, held by the Account,
because administration happens outside any Character. The two never share permissions.

## Consequences

- Session permissions are resolved from the Character's save file when it loads, not from the login.
- The code still uses one shared catalog for both; tracked in
  `.scratch/admin-roles/issues/01-split-admin-roles-from-rbac-groups.md`.
