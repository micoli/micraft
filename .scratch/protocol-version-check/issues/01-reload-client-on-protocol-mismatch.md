# Reload the client when its protocol does not match the server's

Status: resolved
Type: task

## Context

ADR-0003 rules out any cross-version wire compatibility: ids get renumbered when a message is removed, and the
client is always deployed with the server. A browser tab left open across a deploy then talks to an incompatible
server, and messages decode wrongly or fail silently. Only Wasm load failures trigger a reload today
(`app/webApp/ts-src/index.ts:464-469`).

## Current state

- `ServerMessage.Welcome.buildTimestamp` exists (`core/.../protocol/ServerMessage.kt:58`), but the client only
  displays it (`GameClient.kt:668` → `window.mcBuildInfo.server` → `Statistics.tsx`). It is never compared.
- `GET` server info (`http/ServerInfoController.kt`, `ServerInfo(buildTimestamp)`) is already fetched in `index.ts:68`.
- Checking inside `Welcome` is fragile: if ids shift, `Welcome` itself may not decode.

## Proposal

- The KSP `:codec-processor` also emits a **protocol fingerprint** (a hash of the ordered `@ProtoId` → class name
  + field list) as a constant in `core`, so both sides compile it.
- The server exposes it over HTTP (in `ServerInfo`). Before opening the game WebSocket, the client compares it with
  its own compiled fingerprint and reloads the page on mismatch (guarding against a reload loop, e.g. at most once per
  N seconds via `sessionStorage`).
- Defence in depth: `Connect` carries the fingerprint, and the server closes with a dedicated close code on mismatch,
  which the client handles by reloading.
- Use the fingerprint rather than `buildTimestamp`, so that a server-only rebuild with an unchanged protocol does not
  force players to reload.

## Acceptance

- Changing any `@ProtoId` message changes the fingerprint (codec-processor test).
- A client with a stale fingerprint reloads once and does not loop (TS unit test); the server rejects a mismatched
  `Connect` (server test).
- ADR-0003 consequence updated.

## Answer

Done in `0a811488`. The fingerprint covers 177 messages and 97 carried project types (fields, enum entries, sealed subclasses, param annotations). Checked in a browser: a forged `/api/server/info` fingerprint triggers one reload, then the character screen shows the "new version" message. `Connect.protocolFingerprint` defaults to the compiled constant, so JVM callers (tests, future load bots) match automatically.
