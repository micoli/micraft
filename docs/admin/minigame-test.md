---
title: Mini-game test
---

# Mini-game test

`/admin/minigame-test` simulates the [mini-game](../gameplay/mini-games.md) room
protocol entirely in the browser, with fake players instead of real logged-in
clients — no live websocket needed.

Each fake player is a tab. For the active tab you can create a room, invite
another fake player, accept/decline a pending invite, and — once the room has
enough members — actually play the real mini-game bundle (loaded the same way
the live client does), confirming the create/invite/accept/leave/broadcast flow
end-to-end without needing a second browser session.

Four fake players (Alice, Bob, Charlie, Diana) are seeded by default. A player
leaving mid-match dissolves the room for every remaining tab, mirroring the
real server's `MiniGameManager`.
