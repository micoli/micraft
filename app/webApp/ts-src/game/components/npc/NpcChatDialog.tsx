import { useState } from "react";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
import { Input } from "../../../primitives/Input";
import { NpcChatDialogData } from "../../types";

interface Props {
  data: NpcChatDialogData | null;
  onClose: () => void;
  onSend: (npcId: string, text: string) => void;
  onAcceptQuest: (questId: string) => void;
  onAcceptGift: (npcId: string, itemId: string) => void;
}

export function NpcChatDialog({ data, onClose, onSend, onAcceptQuest, onAcceptGift }: Props) {
  const [draft, setDraft] = useState("");
  if (!data) return null;

  const send = () => {
    const text = draft.trim();
    if (!text || data.pending) return;
    onSend(data.npcId, text);
    setDraft("");
  };

  return (
    <Dialog open={!!data} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        windowMode="floating"
        className="w-[420px] max-w-[95vw] font-mono shadow-[0_8px_32px_rgba(0,0,0,0.7)]"
        onEscapeKeyDown={(e) => e.preventDefault()}
      >
        <DialogTitle className="text-base font-bold mb-3">{data.npcType}</DialogTitle>

        <div className="flex flex-col gap-1.5 max-h-[280px] min-h-[120px] overflow-y-auto pr-0.5 mb-3">
          {data.history.map((turn, i) => (
            <div
              key={i}
              className={
                turn.role === "user"
                  ? "self-end bg-white/10 rounded px-2 py-1 text-xs text-white/80 max-w-[85%]"
                  : "self-start bg-white/5 rounded px-2 py-1 text-xs text-white/70 max-w-[85%]"
              }
            >
              {turn.text}
            </div>
          ))}
          {data.pending && <p className="text-[10px] text-white/40 italic">…</p>}
        </div>

        {data.questOffer && (
          <div className="flex flex-col gap-1 bg-white/5 rounded px-2 py-1.5 mb-3">
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-sm text-white/90 font-bold">{data.questOffer.title}</span>
              <span className="text-[9px] text-white/40 flex-shrink-0">Niv. {data.questOffer.level}</span>
            </div>
            <p className="text-[11px] text-white/60">{data.questOffer.description}</p>
            <Button
              variant="secondary"
              className="text-[9px] px-2 py-0.5 h-auto font-mono self-end"
              onClick={() => onAcceptQuest(data.questOffer!.id)}
            >
              Accepter
            </Button>
          </div>
        )}

        {data.itemOffer && (
          <div className="flex items-center justify-between gap-2 bg-white/5 rounded px-2 py-1.5 mb-3">
            <span className="text-xs text-white/80">{data.itemOffer.displayName}</span>
            <Button
              variant="secondary"
              className="text-[9px] px-2 py-0.5 h-auto font-mono flex-shrink-0"
              onClick={() => onAcceptGift(data.npcId, data.itemOffer!.itemId)}
            >
              Accepter le cadeau
            </Button>
          </div>
        )}

        <div className="flex gap-2 mb-3">
          <Input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && send()}
            disabled={data.pending}
            className="text-xs py-1.5"
            placeholder="Votre message…"
            autoFocus
          />
          <Button
            variant="secondary"
            className="text-[9px] px-3 font-mono flex-shrink-0"
            disabled={data.pending || !draft.trim()}
            onClick={send}
          >
            Envoyer
          </Button>
        </div>

        <Button variant="secondary" onClick={onClose} className="font-mono w-full">
          Fermer
        </Button>
      </DialogContent>
    </Dialog>
  );
}
