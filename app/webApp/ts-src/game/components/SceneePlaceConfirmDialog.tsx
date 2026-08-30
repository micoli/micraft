import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button } from "../../primitives/Button";

interface Props {
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function SceneePlaceConfirmDialog({ open, onConfirm, onCancel }: Props) {
  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onCancel();
      }}
    >
      <DialogContent className="flex flex-col gap-4">
        <DialogTitle>Place this scene here?</DialogTitle>
        <div className="flex gap-2 justify-end">
          {/* Radix auto-focuses the first focusable child, so Enter triggers Yes. */}
          <Button variant="primary" onClick={onConfirm}>
            Yes
          </Button>
          <Button variant="secondary" onClick={onCancel}>
            No
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
