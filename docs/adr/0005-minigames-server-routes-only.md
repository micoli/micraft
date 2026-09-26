---
status: accepted
---

# The server routes Mini-games but never knows their rules

The server knows only a **Mini-game**'s identity and player bounds. It manages **Rooms** (create, invite, leave)
and relays each action as an opaque JSON payload to the other members, never deserializing it. All game rules live
in the Mini-game's own client bundle, built as an independent npm project and loaded at runtime. This lets a
Mini-game be added without touching the server or the web client, at the price of no server-side validation.

## Consequences

- A Mini-game outcome can never be trusted: **no reward that matters to the game (XP, Items, currency, Rank) may
  hang off a Mini-game.** Lifting this requires a new ADR, not a per-game exception.
