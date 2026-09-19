import { useState } from "react";
import { Button } from "../../primitives/Button";
import { PlayerNameInput } from "../shared/PlayerNameInput";
import { MiniGameRoom } from "../types";
import { inviteMiniGame } from "./minigameActions";

interface Props {
  room: MiniGameRoom;
  gameDisplayName: string;
  isHost: boolean;
  minPlayers: number;
  onLeave: () => void;
}

/** Room roster + invite form + leave — the "manage participants" side of a mini-game, shared by
 * the in-game overlay sidebar (`MiniGameContainer`) and the `/minigame`-with-no-args dialog. */
export function MiniGameRoomPanel({ room, gameDisplayName, isHost, minPlayers, onLeave }: Props) {
  const [inviteTarget, setInviteTarget] = useState("");

  return (
    <div className="flex flex-col gap-3 h-full">
      <div>
        <p style={{ fontSize: 12, opacity: 0.7 }}>{gameDisplayName}</p>
        {room.members.length < minPlayers && (
          <p style={{ fontSize: 11, opacity: 0.6, marginTop: 2 }}>
            En attente de joueurs ({room.members.length}/{minPlayers})…
          </p>
        )}
      </div>
      <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 4 }}>
        {room.members.map((m) => (
          <li key={m.playerId} style={{ fontSize: 12, opacity: m.online ? 1 : 0.4 }}>
            {m.playerId === room.hostId ? "★ " : ""}
            {m.playerName}
          </li>
        ))}
      </ul>
      {isHost && (
        <div style={{ display: "flex", gap: 6 }}>
          <PlayerNameInput placeholder="Nom du joueur" value={inviteTarget} onChange={setInviteTarget} />
          <Button
            size="sm"
            onClick={() => {
              if (!inviteTarget.trim()) return;
              inviteMiniGame(room.id, inviteTarget.trim());
              setInviteTarget("");
            }}
          >
            Inviter
          </Button>
        </div>
      )}
      <Button size="sm" variant="secondary" onClick={onLeave} className="mt-auto">
        Quitter
      </Button>
    </div>
  );
}
