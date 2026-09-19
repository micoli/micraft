# Mini-games — independent modules

Each mini-game under `app/minigames/<name>/` is a **standalone npm project**, built and
published independently of `app/webApp`. The host (`app/webApp`) never imports a mini-game's
code at build time — it only knows a `gameType -> entryUrl` mapping served by the server
(`GET /api/minigames`, backed by `data/config/minigames.yaml`), and loads the bundle at
**runtime** via a dynamic `import()` (see `app/webApp/ts-src/game/minigames/MiniGameContainer.tsx`).

Adding a new mini-game never requires touching `server/` or `app/webApp/` — just a new
`app/minigames/<name>/` project plus one entry in `data/config/minigames.yaml`.

## Contract

A mini-game's build output (`dist/bundle.js`, ESM) must `export default` a React component with
this prop shape:

```ts
interface MiniGameProps {
  room: {
    id: string;
    hostId: string;
    hostName: string;
    gameType: string;
    members: { playerId: string; playerName: string; online: boolean }[];
  };
  lastAction: { roomId: string; fromPlayerId: string; payload: string } | null;
  sendAction: (payload: unknown) => void;
  onLeave: () => void;
}
```

- `room` — current room snapshot (membership only; the host never knows or persists the game's
  internal state, e.g. the board).
- `lastAction` — the most recent `MiniGameAction` broadcast for this room (`payload` is
  whatever JSON string the sender passed to `sendAction`); `null` until a first action arrives.
  Re-renders on every new action, including your own (`fromPlayerId` lets you tell your move
  from an opponent's and avoid double-applying it).
- `sendAction(payload)` — broadcasts `payload` (JSON-stringified) to every other room member.
  The server never parses it — pure opaque relay.
- `onLeave()` — leaves the mini-game room.

**All game rules (turns, win/draw detection, move validation) run entirely client-side, in the
mini-game's own code.** The server only routes room membership and opaque action payloads —
it never validates a move.

## React sharing

The bundle declares `react`/`react-dom` as `external` (esbuild `--external:react
--external:react-dom`, also listed in `package.json` as `peerDependencies` so they're never
bundled) and imports them the normal way (`import { useState } from "react"`), producing bare
`import "react"` / `import "react/jsx-runtime"` specifiers in the output ESM.

**Wired up in the host**: `app/webApp/ts-src/index.ts` (the `mc_bindings.js` entry, loaded by
both `index.html` and `admin.html`) exposes its own React instance as `window.React` /
`window.ReactJsxRuntime` / `window.ReactDomClient`. Both pages declare a
`<script type="importmap">` mapping `"react"`, `"react/jsx-runtime"` and `"react-dom/client"` to
tiny shim modules under `app/webApp/src/wasmJsMain/resources/minigame-shims/` that just
re-export those globals as named exports. A mini-game's dynamic `import()` therefore resolves its
bare specifiers to the **exact same React instance** the host renders with — never a second copy,
which would otherwise break hooks (`Invalid hook call`) since two React instances can't share a
dispatcher. Add a specifier to the import map (and a matching shim) if a mini-game needs another
React-family export not already covered.

## Building a mini-game

```bash
npm --prefix app/minigames/<name> install
npm --prefix app/minigames/<name> run build   # -> app/minigames/<name>/dist/bundle.js
```

`make build-minigames` (root Makefile) builds every `app/minigames/*/` project and copies its
`dist/` to `app/webApp/build/web/minigames/<name>/`, which Ktor already serves as a static
directory (`entryUrl: /minigames/<name>/bundle.js` in `data/config/minigames.yaml`). `make
build` chains `build-minigames` automatically.

## Publishing a new mini-game

1. Create `app/minigames/<name>/` with its own `package.json`, bundler config, and `src/`
   exporting a default component matching the contract above.
2. Add an entry to `data/config/minigames.yaml`:
   ```yaml
   - gameType: <name>
     displayName: "Display Name"
     entryUrl: /minigames/<name>/bundle.js
     minPlayers: 2
     maxPlayers: 2
   ```
3. `make build-minigames` (or `make build`, which chains it).
4. `/minigame create <name>` in-game, or the `/minigame` dialog.

## `tictactoe` (reference implementation)

Two-player morpion. Symbol assignment: `room.members[0]` is X, `room.members[1]` is O
(`ticTacToeLogic.ts`). A move sends `sendAction({ cellIndex })`; the current turn is derived
purely from how many cells are filled (`currentTurn`), never from a locally-held "my turn" flag,
so a late-joining spectator or a resync always agrees with the board. Win/draw detection and
move validation (`canPlay`) are pure functions with no React/network dependency, in
`ticTacToeLogic.ts`.
