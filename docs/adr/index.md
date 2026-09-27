---
title: Architecture decisions
---

# Architecture decision records

Short records of decisions that are hard to reverse, surprising without context, and the result of a real
trade-off. Read the ones touching an area before changing it; supersede an ADR with a new one rather than
editing its decision. Domain vocabulary comes from `CONTEXT.md` at the repo root.

| ADR | Decision |
|-----|----------|
| [0001](0001-server-authoritative-with-client-prediction.md) | Server-authoritative simulation with client-side XZ prediction |
| [0002](0002-shared-simulation-rules-in-core.md) | Rules run by both client and server live in `core` |
| [0003](0003-generated-protocol-codec.md) | Binary protocol with a generated codec registry |
| [0004](0004-test-worlds-in-process.md) | Test worlds are hosted inside the running server process |
| [0005](0005-minigames-server-routes-only.md) | The server routes Mini-games but never knows their rules |
| [0006](0006-permissions-per-character-admin-per-account.md) | In-game permissions belong to the Character, admin access to the Account |
| [0007](0007-ephemeral-social-structures.md) | Groups and Mini-game Rooms live in memory only |
| [0008](0008-web-client-stack.md) | Web client: Kotlin/Wasm for the game, BabylonJS for 3D, React for the UI |
| [0009](0009-layered-yaml-config.md) | Layered YAML configuration: bundled defaults, data overrides |
| [0010](0010-wild-npc-population-per-region-roster.md) | Wild NPCs spawn from a derived per-Region Roster and budget; Quests follow the Roster |
