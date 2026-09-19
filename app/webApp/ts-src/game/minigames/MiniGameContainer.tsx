import { ReactNode } from "react";
import { useGameContext } from "../GameContext";
import { MiniGameRoom } from "../types";
import { leaveMiniGame, sendMiniGameAction } from "./minigameActions";
import { miniGameRoomIsReady } from "./miniGameRoomState";
import { MiniGameRoomPanel } from "./MiniGameRoomPanel";
import { useMiniGameModule } from "./useMiniGameModule";

interface Props {
  room: MiniGameRoom;
}

/**
 * Generic host-side container: resolves `room.gameType` to its `entryUrl` (from the
 * `/api/minigames` registry) and loads the mini-game's independent bundle via `useMiniGameModule`.
 * A new mini-game ships without rebuilding this app.
 *
 * Layout: the game itself (4/5, left) next to a "manage participants" sidebar (1/5, right) that's
 * always visible — inviting/leaving never requires backing out of the game screen, and the game
 * area shows a waiting placeholder instead of mounting the bundle until the room has enough
 * players (`minPlayers`).
 */
export function MiniGameContainer({ room }: Props) {
  const { state } = useGameContext();
  const definition = state.miniGameAvailable.find((d) => d.gameType === room.gameType);
  const load = useMiniGameModule(definition?.entryUrl, room.gameType);
  const isHost = room.hostId === window.mcState.playerId;
  const minPlayers = definition?.minPlayers ?? 1;
  const ready = miniGameRoomIsReady(room, state.miniGameAvailable);

  let gameArea: ReactNode;
  if (load.status === "loading") {
    gameArea = <p>Chargement du mini-jeu…</p>;
  } else if (load.status === "error") {
    gameArea = <p>{load.message}</p>;
  } else if (!ready) {
    gameArea = (
      <p>
        En attente d&apos;autres joueurs ({room.members.length}/{minPlayers})…
      </p>
    );
  } else {
    const { Component } = load;
    gameArea = (
      <Component
        room={room}
        myPlayerId={window.mcState.playerId}
        lastAction={state.miniGameLastAction}
        sendAction={(payload) => sendMiniGameAction(room.id, payload)}
        onLeave={() => leaveMiniGame(room.id)}
      />
    );
  }

  return (
    <div className="fixed inset-0 z-[1500] flex bg-black/70 text-white font-mono">
      {/* `contain: layout` makes this the containing block for the mini-game's own
          `position: fixed` full-viewport chrome, confining it to the 4/5 game area instead of
          letting it cover the roster sidebar. */}
      <div className="flex-[4] relative flex items-center justify-center" style={{ contain: "layout" }}>
        {gameArea}
      </div>
      <div className="flex-1 min-w-[220px] max-w-[320px] border-l border-white/10 bg-black/40 p-4">
        <MiniGameRoomPanel
          room={room}
          gameDisplayName={definition?.displayName ?? room.gameType}
          isHost={isHost}
          minPlayers={minPlayers}
          onLeave={() => leaveMiniGame(room.id)}
        />
      </div>
    </div>
  );
}
