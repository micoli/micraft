import { useRef, useEffect } from "react";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button, ButtonProps } from "../../primitives/Button";

interface PauseMenuItem {
  label: string;
  icon?: string;
  variant?: ButtonProps["variant"];
  callback: () => void;
}

interface PauseMenuProps {
  open: boolean;
  onClose: () => void;
  items: PauseMenuItem[];
}

export function PauseMenu({ open, onClose, items }: PauseMenuProps) {
  const firstButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (open) setTimeout(() => firstButton.current?.focus(), 50);
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        className="min-w-[420px] p-8 flex flex-col gap-3 z-[2001]"
        overlayClassName="z-[2000]"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <DialogTitle className="text-center font-mono text-xl tracking-[0.25em] mb-2">PAUSE</DialogTitle>
        <div className="grid grid-cols-2 gap-3">
          {items.map((item, k) => (
            <Button
              key={item.label}
              ref={k === 0 ? firstButton : null}
              variant={item.variant ?? "secondary"}
              onClick={item.callback}
              className="font-mono"
            >
              {item.icon ? `${item.icon} ` : ""}
              {item.label}
            </Button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
