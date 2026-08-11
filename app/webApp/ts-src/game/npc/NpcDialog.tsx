import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button } from "../../primitives/Button";

export interface NpcDialogData {
  type: string;
  name: string;
}

interface Props {
  data: NpcDialogData | null;
  onClose: () => void;
}

export function NpcDialog({ data, onClose }: Props) {
  return (
    <Dialog open={!!data} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="min-w-[260px] font-mono shadow-[0_8px_32px_rgba(0,0,0,0.7)]">
        <DialogTitle className="text-lg font-bold mb-2">{data?.name}</DialogTitle>
        <p className="text-sm text-white/60 mb-5">{data?.type}</p>
        <Button variant="secondary" onClick={onClose} className="font-mono">
          Close
        </Button>
      </DialogContent>
    </Dialog>
  );
}
