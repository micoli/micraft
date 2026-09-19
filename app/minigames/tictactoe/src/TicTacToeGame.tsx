import { useEffect, useState } from "react";
import { TicTacToeBoard } from "./TicTacToeBoard";
import {
  Board,
  CellValue,
  EMPTY_BOARD,
  canPlay,
  currentTurn,
  isDraw,
  playMove,
  symbolForMemberIndex,
  winner,
} from "./ticTacToeLogic";

// Mirrors the host's `MiniGameRoom`/`MiniGameActionMsg` shapes structurally — this module never
// imports from app/webApp (independent build, see app/minigames/README.md).
interface MiniGameMemberLike {
  playerId: string;
  playerName: string;
  online: boolean;
}

interface MiniGameRoomLike {
  id: string;
  hostId: string;
  hostName: string;
  gameType: string;
  members: MiniGameMemberLike[];
}

interface MiniGameActionMsgLike {
  roomId: string;
  fromPlayerId: string;
  payload: string;
}

export interface TicTacToeGameProps {
  room: MiniGameRoomLike;
  /** `room.members[i].playerId` this instance renders for — never read a host global for this. */
  myPlayerId: string;
  lastAction: MiniGameActionMsgLike | null;
  sendAction: (payload: unknown) => void;
  onLeave: () => void;
}

interface MovePayload {
  cellIndex: number;
}

function isMovePayload(v: unknown): v is MovePayload {
  return typeof v === "object" && v !== null && typeof (v as { cellIndex?: unknown }).cellIndex === "number";
}

export default function TicTacToeGame({ room, myPlayerId, lastAction, sendAction, onLeave }: TicTacToeGameProps) {
  const [board, setBoard] = useState<Board>(EMPTY_BOARD);
  const [lastAppliedActionRoomAndPayload, setLastAppliedActionRoomAndPayload] = useState<string | null>(null);

  const myIndex = room.members.findIndex((m) => m.playerId === myPlayerId);
  const mySymbol: CellValue = symbolForMemberIndex(myIndex);

  // Apply an opponent's move as soon as it arrives, guarded by a signature of the action so the
  // same MiniGameAction message (e.g. re-delivered on a resync) is never double-applied.
  useEffect(() => {
    if (!lastAction || lastAction.roomId !== room.id) return;
    const signature = `${lastAction.roomId}:${lastAction.fromPlayerId}:${lastAction.payload}`;
    if (signature === lastAppliedActionRoomAndPayload) return;
    setLastAppliedActionRoomAndPayload(signature);
    if (lastAction.fromPlayerId === myPlayerId) return; // our own move, already applied optimistically
    let parsed: unknown;
    try {
      parsed = JSON.parse(lastAction.payload);
    } catch {
      return;
    }
    if (!isMovePayload(parsed)) return;
    setBoard((prev) => {
      if (!canPlay(prev, parsed.cellIndex, currentTurn(prev))) return prev;
      return playMove(prev, parsed.cellIndex, currentTurn(prev));
    });
  }, [lastAction, room.id, lastAppliedActionRoomAndPayload, myPlayerId]);

  // e2e hook: lets a Playwright test drive a real move the same way a click on the board would,
  // instead of poking network/game state directly (see CLAUDE.md e2e rules).
  useEffect(() => {
    const w = window as unknown as { mcE2E?: { actions?: Record<string, unknown> } };
    if (!w.mcE2E) return;
    w.mcE2E.actions = { ...(w.mcE2E.actions ?? {}), playTicTacToeCell: (cellIndex: number) => handlePlay(cellIndex) };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [board, mySymbol]);

  function handlePlay(cellIndex: number) {
    if (!canPlay(board, cellIndex, mySymbol)) return;
    setBoard((prev) => playMove(prev, cellIndex, mySymbol));
    sendAction({ cellIndex });
  }

  const win = winner(board);
  const draw = isDraw(board);
  const turn = currentTurn(board);

  let status: string;
  if (win) status = win === mySymbol ? "Vous avez gagné !" : "Vous avez perdu.";
  else if (draw) status = "Match nul.";
  else if (mySymbol === null) status = "Spectateur.";
  else status = turn === mySymbol ? "À vous de jouer" : "En attente de l'adversaire…";

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1500,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 16,
        background: "rgba(0,0,0,0.7)",
        fontFamily: "monospace",
        color: "white",
      }}
    >
      <h2 style={{ margin: 0 }}>Morpion</h2>
      <p style={{ margin: 0, opacity: 0.8 }}>{status}</p>
      <TicTacToeBoard board={board} disabled={!!win || draw || mySymbol === null} onPlay={handlePlay} />
      <button
        type="button"
        onClick={onLeave}
        style={{
          marginTop: 8,
          padding: "6px 14px",
          background: "transparent",
          border: "1px solid rgba(255,255,255,0.4)",
          borderRadius: 4,
          color: "white",
          cursor: "pointer",
        }}
      >
        Quitter
      </button>
    </div>
  );
}
