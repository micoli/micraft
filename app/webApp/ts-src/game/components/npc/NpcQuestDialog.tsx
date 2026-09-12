import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Button } from "../../../primitives/Button";
import { QuestGiverDialogData } from "../../types";

interface Props {
  data: QuestGiverDialogData | null;
  onClose: () => void;
  onAccept: (questId: string) => void;
}

export function NpcQuestDialog({ data, onClose, onAccept }: Props) {
  if (!data) return null;

  return (
    <Dialog open={!!data} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        windowMode="floating"
        className="w-[480px] max-w-[95vw] font-mono shadow-[0_8px_32px_rgba(0,0,0,0.7)]"
      >
        <DialogTitle className="text-base font-bold mb-3">{data.npcType}</DialogTitle>

        {data.turnInable.length > 0 && (
          <div className="flex flex-col gap-1 mb-3">
            <div className="text-[10px] text-white/45 uppercase tracking-wider">En cours</div>
            {data.turnInable.map((questId) => (
              <div key={questId} className="bg-white/5 rounded px-2 py-1 text-xs text-white/60">
                {questId}
              </div>
            ))}
          </div>
        )}

        <div className="flex flex-col gap-1.5">
          <div className="text-[10px] text-white/45 uppercase tracking-wider">Quêtes disponibles</div>
          <div className="flex flex-col gap-1.5 max-h-[320px] overflow-y-auto pr-0.5">
            {data.offerable.map((quest) => (
              <div key={quest.id} className="flex flex-col gap-1 bg-white/5 rounded px-2 py-1.5">
                <div className="flex items-baseline justify-between gap-2">
                  <span className="text-sm text-white/90 font-bold">{quest.title}</span>
                  <span className="text-[9px] text-white/40 flex-shrink-0">Niv. {quest.level}</span>
                </div>
                <p className="text-[11px] text-white/60">{quest.description}</p>
                <Button
                  variant="secondary"
                  className="text-[9px] px-2 py-0.5 h-auto font-mono self-end"
                  onClick={() => onAccept(quest.id)}
                >
                  Accepter
                </Button>
              </div>
            ))}
            {data.offerable.length === 0 && (
              <p className="text-xs text-white/40 text-center py-4">Rien à offrir pour l&apos;instant.</p>
            )}
          </div>
        </div>

        <Button variant="secondary" onClick={onClose} className="font-mono mt-3 w-full">
          Fermer
        </Button>
      </DialogContent>
    </Dialog>
  );
}
