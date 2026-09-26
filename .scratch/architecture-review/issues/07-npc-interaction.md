# NpcInteraction with always-present dependencies

Status: needs-triage
Strength: Worth exploring

## Files
- `game/npc/NpcManager.kt` (1133 LOC, ~55 public functions, 38 referencing files; L573-636)
- `NpcTickContext.kt`, `NpcChatService.kt`

## Problem
Interact, chat send, test chat and accept gift each fill a different subset of the optional `NpcTickContext` fields:
`testChat` passes no `i18n`, `handleChatAcceptGift` passes no `questManager`, and a non-quest-giver interaction gets `questManager = null`.
NpcManager also mixes spawn, persistence, damage, aggro, visibility, admin listeners and shops.

## Solution
Extract an `NpcInteraction` / chat module whose dependencies are non-null constructor parameters. NpcManager keeps the registry and tick.

## Tests
Good (23+ npc test files, including `NpcChatServiceTest` and `NpcTickParityTest`).
