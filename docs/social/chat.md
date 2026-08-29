---
title: Chat
---

# Chat

## How to play

Multi-channel chat:

- **`/createchat <channelName>`** — create a channel.
- **`/join <channelName>` / `/leave <channelName>`** — membership.
- **`/talk <playerName>`** — open a private channel with a player.
- **`around`** — an automatic proximity channel (radius 64 blocks).
- **combat log** — an automatic channel for combat events.
- **`console_toggle`** (`H`) — open the console.
- **`/yield <message>`** *(plugin)* — broadcast to all connected players.

## Configuration

Channel behaviour (proximity radius, default channels) is wired in the server
chat system; there is no dedicated YAML. Language for system messages is
per-player (`/lang`), see [i18n](../systems/i18n.md).
