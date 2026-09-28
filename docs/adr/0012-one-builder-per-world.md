---
status: accepted
---

# Every World is assembled by one builder; the DI container holds only process-level services

Every World (the persistent default World, Test worlds, the admin simulator's arena) is built by the same function
from the process-level services plus per-World options, and owns its gameplay subsystems (combat, NPCs, quests,
claims, trade, mail…). Koin provides only what the whole process shares: configuration, auth, persistence roots, the
shared registries and loaders. We had three wiring sites (Koin graph, `GameLoop` constructor defaults, the Test
world factory) and they drifted: NPCs never cast Spells in production, Faction friendly fire was not blocked,
quest credit was lost on mail attachments. A single builder makes a missing wire a bug in every World at once,
where tests see it.

## Considered options

- **Keep per-World Koin providers, verified by a parity test**: rejected, the test only checks what someone thought
  to compare, and Koin singletons assume a single World (ADR-0004).

## Consequences

- No `@Single` for a per-World subsystem. Process-level code that needs one (HTTP controllers) goes through the
  World it targets, by default `GameLoop.defaultWorld`.
- Persistence is the one switch between a persistent and a memory-only World; there is no half-persistent World.
- `GameLoop` receives a built World and only handles connections and the World registry.
