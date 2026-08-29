import * as RadixDialog from "@radix-ui/react-dialog";
import { useCallback, useRef, useState } from "react";
import { DialogOverlay } from "./DialogOverlay";
import { cn } from "./cn";

interface DialogContentProps extends React.ComponentPropsWithoutRef<typeof RadixDialog.Content> {
  className?: string;
  overlayClassName?: string;
  children: React.ReactNode;
  movable?: boolean;
  windowMode?: "maximized" | "floating";
}

export function DialogContent({
  className,
  overlayClassName,
  children,
  movable = false,
  windowMode,
  ...props
}: DialogContentProps) {
  const isMaximized = windowMode === "maximized";
  const draggable = (movable || windowMode === "floating") && !isMaximized;
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [isDraggingWindow, setIsDraggingWindow] = useState(false);
  const dragStart = useRef<{ mx: number; my: number; ox: number; oy: number } | null>(null);

  const onMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (!draggable) return;
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
    [draggable, offset],
  );

  return (
    <RadixDialog.Portal>
      <DialogOverlay className={overlayClassName} />
      <RadixDialog.Content
        className={cn(
          "fixed left-1/2 top-1/2 z-[1001] -translate-x-1/2 -translate-y-1/2",
          "min-w-[320px] rounded-lg border border-white/20 bg-black/80 p-6 shadow-2xl",
          "text-white",
          "data-[state=open]:animate-in data-[state=closed]:animate-out",
          "data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
          "data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95",
          isMaximized && "h-[80vh] max-h-[80vh] w-[80vw] max-w-[80vw]",
          draggable && isDraggingWindow && "select-none cursor-move",
          className,
        )}
        style={draggable ? { translate: `calc(-50% + ${offset.x}px) calc(-50% + ${offset.y}px)` } : undefined}
        onMouseDown={onMouseDown}
        {...props}
      >
        {children}
      </RadixDialog.Content>
    </RadixDialog.Portal>
  );
}
