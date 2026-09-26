# Remove stringified client input-event layer

Status: needs-triage
Strength: Speculative

## Files
- `core/.../input/*.kt` (12 files, e.g. `GuildEvents.kt` `GuildEventHandler` L66-92), `ClientInputEvent.parse`
- `app/webApp/ts-src/game/**`: 19 hand-built `emit(\`prefix:${a}\t${b}\`)` (e.g. `GuildPanel.tsx:55`, `Inventory.tsx:179`)
- `ts-src/generated/input/clientEvents.ts` (prefixes only)

## Problem
The handlers map events 1:1 to a `ClientMessage`; the deletion test removes complexity. Payloads are untyped on both sides, and
names drift across 3 layers (TS player name → `playerId` → `targetName`). `GuildRankUpsert` already bypasses the layer with `ClientMessage` JSON.

## Solution
TS sends a typed `ClientMessage` (generated TS types) through one bridge function. Keep `ClientInputAction` only for
client-local, key-bound actions. Caveat: this layer was refactored recently (71666e61, eeac8906), so weigh that before reopening it.
