import { ReactNode } from "react";
import { useMiniGameModule } from "../../../game/minigames/useMiniGameModule";
import { OrgMicoliMicraftGameMinigameMiniGameDefinition as MiniGameDefinition } from "../../../generated/api/requests/types.gen";
import { SimAction, SimRoom } from "./miniGameSimulator";

interface Props {
  room: SimRoom;
  myPlayerId: string;
  games: MiniGameDefinition[];
  lastAction: SimAction | undefined;
  onSendAction: (payload: unknown) => void;
  onLeave: () => void;
}

/**
 * Renders the mini-game's own bundle for real, fed by the in-browser simulator instead of the
 * live websocket — same `MiniGameProps` contract as `MiniGameContainer.tsx` (the real host), so a
 * mini-game can be played end-to-end here with fake players. Fills its parent (the 4/5 game area
 * next to the participants sidebar in `FakePlayerCard`), never mounting the bundle until the room
 * has enough members.
 */
export function MiniGameLivePanel({ room, myPlayerId, games, lastAction, onSendAction, onLeave }: Props) {
  const definition = games.find((g) => g.gameType === room.gameType);
  const load = useMiniGameModule(definition?.entryUrl, room.gameType);
  const minPlayers = definition?.minPlayers ?? 1;
  const ready = room.members.length >= minPlayers;

  let content: ReactNode;
  if (load.status === "loading") {
    content = <span className="text-sm text-[#8A99AF] font-mono">Chargement du mini-jeu…</span>;
  } else if (load.status === "error") {
    content = <span className="text-sm text-red-400 font-mono">{load.message}</span>;
  } else if (!ready) {
    content = (
      <span className="text-sm text-[#8A99AF] font-mono">
        En attente de joueurs ({room.members.length}/{minPlayers})…
      </span>
    );
  } else {
    const { Component } = load;
    content = (
      <Component
        room={room}
        myPlayerId={myPlayerId}
        lastAction={lastAction ?? null}
        sendAction={onSendAction}
        onLeave={onLeave}
      />
    );
  }

  return (
    // `contain: layout` makes this the containing block for the mini-game's own
    // `position: fixed` full-viewport chrome, confining it to this panel instead of covering the
    // rest of the admin page (tabs, participants sidebar).
    <div
      className="rounded-xl border border-[#2E3A4E] overflow-hidden relative h-full flex items-center justify-center"
      style={{ contain: "layout" }}
    >
      {content}
    </div>
  );
}
