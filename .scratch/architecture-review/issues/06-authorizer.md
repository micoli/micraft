# Single Authorizer; CommandContext typed per need

Status: needs-triage
Strength: Worth exploring

## Files
- `auth/Permission.kt:38` and `game/session/PlayerSession.kt:34`: two identical `hasPermission`
- `Permission.WILDCARD` checked inline: `GameLoop.kt:1076`, `:1812`, `:2001` (fallback), `ClaimManager.kt:100`, `:124`, `Claim.kt:39`
- `command/CommandContext.kt` (52 fields, 45 nullable/defaulted)

## Problem
- Claims bypass only on `*`, so an admin override can't be granted through RBAC groups.
- GameLoop has 2 separate command-permission gates.
- "No token store = full access" is duplicated between the controllers and GameLoop.
- Commands re-null-check context services ≥25 times.

## Solution
An `Authorizer` (session / `AuthResult` → `can(perm)`) that owns wildcard, owner, trusted and fallback rules. The dispatcher gates
commands; each command declares its dependencies instead of receiving a bag of nullable ones.

## Tests
56 command tests for 78 command files.
