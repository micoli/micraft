---
title: Protocol messages
---

# Protocol messages

Client and server talk over WebSocket with a binary-encoded message protocol
defined in `core`.

- The **wire id** is the `@ProtoId(n)` annotation on each `ServerMessage` /
  `ClientMessage` subclass.
- The `ServerMessageCodec` / `ClientMessageCodec` registries are **generated** by
  `:codec-processor` (KSP, runs on `kspCommonMainKotlinMetadata`) — never
  hand-edit them.
- A new message = add the subclass with the next free `@ProtoId`. The build fails
  on a missing, duplicate, or non-contiguous id, and a test asserts every subclass
  is in the codec registry.

## Flow at a glance

1. Client `Connect` (carries auth token + language) → `GameLoop.onConnect()` validates.
2. Server `Welcome` (chunk transport, spawn, config) → client initialises.
3. Client `MoveIntent` / `BlockBreak` / `BlockPlace` / `Command` → server validates.
4. Server `PlayerUpdate` / `WorldUpdate` / `ChunkData` / `Notification` → clients apply.

The full HTTP surface is documented in [HTTP API routes](../api-routes.md).
