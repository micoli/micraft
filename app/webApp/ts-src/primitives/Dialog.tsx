import * as RadixDialog from "@radix-ui/react-dialog";
import { useCallback, useRef, useState } from "react";
import { cn } from "./cn";

export const Dialog = RadixDialog.Root;
export const DialogTrigger = RadixDialog.Trigger;
export const DialogClose = RadixDialog.Close;

interface DialogOverlayProps {
  className?: string;
}

function DialogOverlay({ className }: DialogOverlayProps) {
  return (
    <RadixDialog.Overlay
      className={cn(
        "fixed inset-0 z-[900] bg-black/60 backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
        className,
      )}
    />
  );
}

interface DialogContentProps extends React.ComponentPropsWithoutRef<typeof RadixDialog.Content> {
  className?: string;
  overlayClassName?: string;
  children: React.ReactNode;
  movable?: boolean;
}

function DialogContent({ className, overlayClassName, children, movable = false, ...props }: DialogContentProps) {
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [isDraggingWindow, setIsDraggingWindow] = useState(false);
  const dragStart = useRef<{ mx: number; my: number; ox: number; oy: number } | null>(null);

  const onMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (!movable) return;
      const target = e.target as HTMLElement;
      const tag = target.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "BUTTON" || tag === "SELECT" || tag === "A") return;
      if (target.isContentEditable) return;
      if (target.closest("[draggable]")) return;
      dragStart.current = { mx: e.clientX, my: e.clientY, ox: offset.x, oy: offset.y };
      setIsDraggingWindow(true);

      const onMove = (ev: MouseEvent) => {
        if (!dragStart.current) return;
        setOffset({
          x: dragStart.current.ox + ev.clientX - dragStart.current.mx,
          y: dragStart.current.oy + ev.clientY - dragStart.current.my,
        });
      };
      const onUp = () => {
        dragStart.current = null;
        setIsDraggingWindow(false);
        window.removeEventListener("mousemove", onMove);
        window.removeEventListener("mouseup", onUp);
      };
      window.addEventListener("mousemove", onMove);
      window.addEventListener("mouseup", onUp);
    },
    [movable, offset],
  );

  return (
    <RadixDialog.Portal>
      <DialogOverlay className={overlayClassName} />
      <RadixDialog.Content
        className={cn(
          "fixed left-1/2 top-1/2 z-[901] -translate-x-1/2 -translate-y-1/2",
          "min-w-[320px] rounded-lg border border-white/20 bg-black/80 p-6 shadow-2xl",
          "text-white",
          "data-[state=open]:animate-in data-[state=closed]:animate-out",
          "data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
          "data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95",
          movable && isDraggingWindow && "select-none cursor-move",
          className,
        )}
        style={movable ? { translate: `calc(-50% + ${offset.x}px) calc(-50% + ${offset.y}px)` } : undefined}
        onMouseDown={onMouseDown}
        {...props}
      >
        {children}
      </RadixDialog.Content>
    </RadixDialog.Portal>
  );
}

function DialogTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <RadixDialog.Title className={cn("text-lg font-semibold text-white/90", className)} {...props} />;
}

export { DialogContent, DialogTitle };
