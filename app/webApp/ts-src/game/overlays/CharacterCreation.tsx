import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { CharacterCreationForm } from "./CharacterCreationForm";

interface Props {
  open: boolean;
  required: boolean;
  onClose: () => void;
  onSubmit: (cmd: string) => void;
}

export function CharacterCreation({ open, required, onClose, onSubmit }: Props) {
  return (
    <Dialog
      open={open}
      onOpenChange={(o: boolean) => {
        if (!o && !required) onClose();
      }}
    >
      <DialogContent
        className="min-w-180 font-mono p-9"
        onEscapeKeyDown={(e: KeyboardEvent) => {
          if (required) e.preventDefault();
        }}
      >
        <DialogTitle className="text-blue-300 tracking-widest mb-6">CREATE RPG CHARACTER</DialogTitle>
        <CharacterCreationForm required={required} onSubmit={onSubmit} onCancel={onClose} />
      </DialogContent>
    </Dialog>
  );
}
