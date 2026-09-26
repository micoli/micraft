---
status: accepted
---

# Test worlds are hosted inside the running server process

Browser E2E tests drive the real Wasm client, and each test gets its own isolated, ephemeral **Test world**. Test
worlds are extra worlds spawned inside the same server process (selected by a game-session id, only when
`MICRAFT_E2E` is set), not a server instance per test. A server per test was rejected for its startup cost (config
loading, world generation, JVM warm-up) and its CI resource usage. The price is that the server must host several
worlds at once, which drove the extraction of a per-world object out of the game loop. Production keeps a single
default World.

## Consequences

- World-scoped admin routes and sockets must resolve the target world explicitly (`X-Micraft-Game-Session`);
  process-level routes ignore it.
- **No new process-global mutable state.** Anything tunable or stateful belongs to a World (or is passed in).
  Existing offenders (`GameConstants` `var`s, `MessageEncoding.current`, mutable `PlayerConstants`) are debt to remove.
