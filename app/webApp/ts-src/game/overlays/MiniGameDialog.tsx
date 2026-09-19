import { Button } from "../../primitives/Button";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { useGameContext } from "../GameContext";
import { createMiniGame, respondMiniGameInvite, leaveMiniGame } from "../minigames/minigameActions";
import { MiniGameRoomPanel } from "../minigames/MiniGameRoomPanel";

interface Props {
  onClose: () => void;
}

/** Opened by `/minigame` with no arguments — mirrors the slash-command actions as clicks. Once a
 * room exists, `MiniGameContainer`'s always-visible sidebar (`MiniGameRoomPanel`) covers the same
 * roster/invite/leave controls, so this dialog is mainly for the "not in a room yet" step. */
export function MiniGameDialog({ onClose }: Props) {
  const { state } = useGameContext();
  const room = state.miniGameRoom;
  const invite = state.socialInvites.find((i) => i.kind === "minigame");
  const isHost = !!room && room.hostId === window.mcState.playerId;
  const minPlayers = room ? (state.miniGameAvailable.find((d) => d.gameType === room.gameType)?.minPlayers ?? 1) : 1;

  return (
    <Dialog open={true} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="w-[360px] max-w-[95vw]" movable onEscapeKeyDown={(e) => e.preventDefault()}>
        <DialogTitle>Mini-jeux</DialogTitle>

        {invite && (
          <div style={{ marginTop: 12, marginBottom: 8 }}>
            <p style={{ fontSize: 12 }}>
              {invite.from} vous invite à jouer à {invite.name}.
            </p>
            <div style={{ display: "flex", gap: 6 }}>
              <Button size="sm" onClick={() => respondMiniGameInvite(invite.id, true)}>
                Accepter
              </Button>
              <Button size="sm" variant="danger" onClick={() => respondMiniGameInvite(invite.id, false)}>
                Refuser
              </Button>
            </div>
          </div>
        )}

        {!room ? (
          <div style={{ marginTop: 12 }}>
            <p style={{ fontSize: 12, opacity: 0.7, marginBottom: 8 }}>{"Vous n'êtes dans aucune partie."}</p>
            {state.miniGameAvailable.length === 0 ? (
              <p style={{ fontSize: 12, opacity: 0.5 }}>Aucun mini-jeu disponible.</p>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {state.miniGameAvailable.map((def) => (
                  <Button key={def.gameType} size="sm" onClick={() => createMiniGame(def.gameType)}>
                    Créer {def.displayName}
                  </Button>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div style={{ marginTop: 12 }}>
            <MiniGameRoomPanel
              room={room}
              gameDisplayName={
                state.miniGameAvailable.find((d) => d.gameType === room.gameType)?.displayName ?? room.gameType
              }
              isHost={isHost}
              minPlayers={minPlayers}
              onLeave={() => leaveMiniGame(room.id)}
            />
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
