import { useEffect, useState } from "react";
import { getApiPlayersNames } from "../../../generated/api/requests";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { ClaimData } from "../../types";
import { ClaimRow } from "./ClaimRow";

interface Props {
  open: boolean;
  claims: ClaimData[];
  myPlayerId: string;
  onClose: () => void;
  onAbandon: (claimId: string) => void;
  onSetTrusted: (claimId: string, playerName: string, trusted: boolean) => void;
  /** Radix modal behavior (focus trap + `aria-hidden` outside the dialog) — see
   *  MailboxOverlay.tsx's `modal` prop doc for why the hub passes `false`. */
  modal?: boolean;
  /** "dialog" (default): the game's floating window. "page": no Dialog/Portal — fills whatever
   *  block-level container it's rendered in, for the hub where each screen owns the whole main
   *  content area. See MailboxOverlay.tsx's `chrome` prop doc. */
  chrome?: "dialog" | "page";
}

export function ClaimPanel({
  open,
  claims,
  myPlayerId,
  onClose,
  onAbandon,
  onSetTrusted,
  modal = true,
  chrome = "dialog",
}: Props) {
  const [playerNames, setPlayerNames] = useState<string[]>([]);

  useEffect(() => {
    if (open && playerNames.length === 0) {
      getApiPlayersNames({ throwOnError: true })
        .then((r) => setPlayerNames(r.data))
        .catch(() => {});
    }
  }, [open, playerNames.length]);

  const body = (
    <>
      {chrome === "page" ? (
        <div className="text-lg font-semibold text-white/90">My Claims</div>
      ) : (
        <DialogTitle>My Claims</DialogTitle>
      )}
      <div style={{ fontSize: 12, opacity: 0.7, marginTop: 4, marginBottom: 12 }}>
        Press the claim-selection key while looking at a block to mark a corner, again to confirm.
      </div>
      <div
        style={{
          maxHeight: chrome === "page" ? "none" : 420,
          flex: chrome === "page" ? 1 : undefined,
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          gap: 8,
        }}
      >
        {claims.length === 0 && (
          <div style={{ color: "rgba(255,255,255,0.4)", fontSize: 13, textAlign: "center", padding: 20 }}>
            You don&apos;t own or have access to any claim yet.
          </div>
        )}
        {claims.map((claim) => (
          <ClaimRow
            key={claim.id}
            claim={claim}
            isOwner={claim.ownerId === myPlayerId}
            playerNames={playerNames}
            onAbandon={onAbandon}
            onSetTrusted={onSetTrusted}
          />
        ))}
      </div>
    </>
  );

  if (chrome === "page") {
    if (!open) return null;
    return <div className="flex flex-col h-full p-4">{body}</div>;
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
      modal={modal}
    >
      <DialogContent className="w-[480px] max-w-[95vw]" movable>
        {body}
      </DialogContent>
    </Dialog>
  );
}
