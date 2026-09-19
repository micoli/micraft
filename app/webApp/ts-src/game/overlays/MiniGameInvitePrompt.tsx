import { Button } from "../../primitives/Button";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { SocialInvite } from "../types";
import { respondMiniGameInvite } from "../minigames/minigameActions";

interface Props {
  invite: SocialInvite;
  onRespond: () => void;
}

/** Pops up as soon as a mini-game invite arrives (`GameScreen` mounts this whenever
 * `state.socialInvites` has a `kind: "minigame"` entry) — the invited player never has to open
 * `/minigame` themselves to see it. */
export function MiniGameInvitePrompt({ invite, onRespond }: Props) {
  const respond = (accept: boolean) => {
    respondMiniGameInvite(invite.id, accept);
    onRespond();
  };

  return (
    <Dialog open={true} onOpenChange={(v) => !v && respond(false)}>
      <DialogContent className="w-[320px] max-w-[95vw]" onEscapeKeyDown={(e) => e.preventDefault()}>
        <DialogTitle>Invitation à un mini-jeu</DialogTitle>
        <p style={{ fontSize: 13, marginTop: 12, marginBottom: 12 }}>
          <strong>{invite.from}</strong> vous invite à jouer à {invite.name}.
        </p>
        <div style={{ display: "flex", gap: 8 }}>
          <Button onClick={() => respond(true)}>Accepter</Button>
          <Button variant="danger" onClick={() => respond(false)}>
            Refuser
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
