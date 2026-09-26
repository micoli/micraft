---
status: accepted
---

# Groups and Mini-game Rooms live in memory only

**Groups** and Mini-game **Rooms** are never persisted: they exist only while their members are online. A Room is
dissolved for everyone as soon as any member leaves or disconnects (no host promotion, no partial continuation),
because Mini-game state lives only in the clients and a subset of players cannot resume it. Guilds and Factions,
by contrast, are persistent. Keeping transient structures out of storage avoids stale-state cleanup and migrations
for things players expect to be short-lived.

## Consequences

- A server restart dissolves every Group and Room. This is accepted; Groups are not rebuilt on reconnect.
