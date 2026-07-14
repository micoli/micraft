import { useRef, useEffect } from "react";
import { Dialog, DialogContent, DialogTitle } from "../../primitives/Dialog";
import { Button } from "../../primitives/Button";

interface PauseMenuProps {
  open: boolean;
  onClose: () => void;
  onDisconnect: () => void;
  onPreferences: () => void;
  onCharacter: () => void;
  onMacros: () => void;
  onRefresh: () => void;
}

export function PauseMenu({
  open,
  onClose,
  onDisconnect,
  onPreferences,
  onCharacter,
  onMacros,
  onRefresh,
}: PauseMenuProps) {
  const prefsButtonRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (open) setTimeout(() => prefsButtonRef.current?.focus(), 50);
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        className="min-w-[220px] p-8 flex flex-col gap-3"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <DialogTitle className="text-center font-mono text-xl tracking-[0.25em] mb-2">PAUSE</DialogTitle>
        <Button ref={prefsButtonRef} variant="secondary" onClick={onPreferences} className="font-mono">
          Preferences
        </Button>
        <Button variant="secondary" onClick={onMacros} className="font-mono">
          Macros
        </Button>
        <Button variant="secondary" onClick={onCharacter} className="font-mono">
          Character
        </Button>
        <Button variant="secondary" onClick={onRefresh} className="font-mono">
          Refresh
        </Button>
        <Button variant="danger" onClick={onDisconnect} className="font-mono">
          Disconnect
        </Button>
      </DialogContent>
    </Dialog>
  );
}
