import { Button } from "../../../primitives/Button";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { GroupInfo, SocialInvite } from "../../types";

interface Props {
  open: boolean;
  group: GroupInfo | null;
  invite?: SocialInvite;
  myPlayerId: string;
  onClose: () => void;
}

function emit(ev: string) {
  window.mcState?.events?.push(ev);
}

export function GroupPanel({ open, group, invite, myPlayerId, onClose }: Props) {
  const isLeader = !!group && group.leaderId === myPlayerId;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="w-[360px] max-w-[95vw]" movable>
        <DialogTitle>Groupe</DialogTitle>
        {!group ? (
          <div style={{ marginTop: 12 }}>
            <p style={{ fontSize: 12, opacity: 0.7, marginBottom: 8 }}>{"Vous n'êtes dans aucun groupe."}</p>
            {invite && (
              <div style={{ marginBottom: 8 }}>
                <p style={{ fontSize: 12 }}>{invite.from} vous a invité.</p>
                <div style={{ display: "flex", gap: 6 }}>
                  <Button size="sm" onClick={() => emit(`group_respond:${invite.id}\t1`)}>
                    Accepter
                  </Button>
                  <Button size="sm" variant="danger" onClick={() => emit(`group_respond:${invite.id}\t0`)}>
                    Refuser
                  </Button>
                </div>
              </div>
            )}
            <Button size="sm" onClick={() => emit("group_create")}>
              Créer un groupe
            </Button>
          </div>
        ) : (
          <div style={{ marginTop: 12 }}>
            <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
              {group.members.map((m) => (
                <li key={m.playerId} style={{ display: "flex", alignItems: "center", gap: 8, padding: "3px 0" }}>
                  <span style={{ opacity: m.online ? 1 : 0.4 }}>
                    {m.playerId === group.leaderId ? "★ " : ""}
                    {m.playerName}
                  </span>
                  {isLeader && m.playerId !== myPlayerId && (
                    <>
                      <Button size="sm" variant="danger" onClick={() => emit(`group_kick:${m.playerId}`)}>
                        Exclure
                      </Button>
                      <Button size="sm" variant="secondary" onClick={() => emit(`group_transfer:${m.playerId}`)}>
                        Responsable
                      </Button>
                    </>
                  )}
                </li>
              ))}
            </ul>
            <div style={{ marginTop: 10, display: "flex", gap: 6, flexWrap: "wrap" }}>
              {isLeader && (
                <Button
                  size="sm"
                  onClick={() => {
                    const n = prompt("Inviter quel joueur ?");
                    if (n) emit(`group_invite:${n}`);
                  }}
                >
                  Inviter
                </Button>
              )}
              <Button size="sm" variant="secondary" onClick={() => emit("group_leave")}>
                Quitter
              </Button>
              {isLeader && (
                <Button size="sm" variant="danger" onClick={() => emit("group_disband")}>
                  Dissoudre
                </Button>
              )}
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
