---
status: accepted
---

# Binary protocol with a generated codec registry, no cross-version compatibility

Client and server exchange ProtoBuf-encoded WebSocket messages. Each `ServerMessage` / `ClientMessage` subclass
carries an explicit `@ProtoId(n)` wire id, and the codec registries are generated at build time by a KSP processor
(`:codec-processor`) instead of being hand-maintained. The build fails on a missing, duplicate or non-contiguous
id, which removes the "forgot to register the new message" class of bugs. The web client is served by the same
server and always deployed with it, so there is no wire compatibility between versions: removing a message
renumbers the following ids. A JSON encoding (`MessageEncoding.JSON`) exists only for debugging the traffic in
devtools; it is not a supported production mode.

## Consequences

- Never hand-edit the generated registries.
- A browser tab left open across a deploy talks to an incompatible server. Only Wasm load failures trigger a reload
  today; there is no version handshake on connect.
