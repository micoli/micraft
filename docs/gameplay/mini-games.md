---
title: Mini-games
---

# Mini-games

Play a standalone mini-game (e.g. tic-tac-toe) with one or more other players,
independently of the main world session.

The server only routes: it knows which mini-game types exist and their player
bounds (`data/config/minigames.yaml`), but never their rules. Every mini-game
ships as its own module under `app/minigames/<name>/`, loaded by your client on
demand — the game's logic (turns, win conditions, board state) all runs
client-side.

## Playing

- **`/minigame create <gameType>`** — start a room, you become the host.
- **`/minigame invite <player>`** — host only. The target gets an invite
  prompt immediately, wherever they are.
- **`/minigame accept`** / **`/minigame decline`**
- **`/minigame leave`** — leaving (or disconnecting) while a match is running
  stops it for **every** member, no partial continuation or re-hosting.
- **`/minigame who`**
- **`/minigame`** with no arguments opens a dialog with the same actions as
  buttons, for when you'd rather not type the sub-commands.

Once in a room, the game window shows the game itself (left) next to a
participants panel (right) with the roster and an invite field — the invite
input autocompletes online player names. The game area only starts the actual
match once the room has reached the game's `minPlayers`; until then it shows a
waiting placeholder while you invite more people.

## Adding a mini-game

Nothing server-side changes: create `app/minigames/<name>/`, build it
(`npm run build`), register it in `data/config/minigames.yaml`
(`displayName`, `entryUrl`, `minPlayers`, `maxPlayers`), and run
`make build-minigames`. See `app/minigames/README.md` for the contract a
mini-game's default-exported component must satisfy.
