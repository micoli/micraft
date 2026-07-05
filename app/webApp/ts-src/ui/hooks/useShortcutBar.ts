import { useState, useRef } from "react";
import { ShortcutSlot } from "../UIReducer";

export function useShortcutBar(
  onSlotDrop: (slot: number, content: ShortcutSlot | null) => void,
  slots: (ShortcutSlot | null)[],
) {
  const [dragOver, setDragOver] = useState<number | null>(null);
  const draggingSlot = useRef<number | null>(null);
  const ghostRef = useRef<HTMLDivElement | null>(null);
  const lastSlotRef = useRef<HTMLElement | null>(null);

  function startSlotDrag(e: React.PointerEvent<HTMLDivElement>, slotIdx: number) {
    e.currentTarget.setPointerCapture(e.pointerId);
    draggingSlot.current = slotIdx;
    const ghost = document.createElement("div");
    ghost.style.cssText = `position:fixed;pointer-events:none;z-index:9999;width:52px;height:52px;background:rgba(0,0,0,0.9);border:2px solid rgba(255,255,255,0.8);border-radius:4px;display:flex;align-items:center;justify-content:center;left:${e.clientX - 26}px;top:${e.clientY - 26}px;opacity:0.85;cursor:grabbing;`;
    document.body.appendChild(ghost);
    ghostRef.current = ghost;
  }

  function moveSlotDrag(e: React.PointerEvent<HTMLDivElement>) {
    if (!ghostRef.current || draggingSlot.current === null) return;
    ghostRef.current.style.left = `${e.clientX - 26}px`;
    ghostRef.current.style.top = `${e.clientY - 26}px`;
    const slotEl = document
      .elementsFromPoint(e.clientX, e.clientY)
      .find((el) => el instanceof HTMLElement && (el as HTMLElement).hasAttribute("data-mc-slot")) as
      | HTMLElement
      | undefined;
    if (slotEl !== lastSlotRef.current) {
      if (lastSlotRef.current) lastSlotRef.current.style.background = "";
      if (slotEl) {
        const idx = parseInt(slotEl.getAttribute("data-mc-slot")!);
        if (idx !== 0) {
          slotEl.style.background = "rgba(255,255,255,0.2)";
          setDragOver(idx);
        }
      } else {
        setDragOver(null);
      }
      lastSlotRef.current = slotEl ?? null;
    }
  }

  function endSlotDrag(e: React.PointerEvent<HTMLDivElement>) {
    const sourceIdx = draggingSlot.current;
    draggingSlot.current = null;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}
    ghostRef.current?.remove();
    ghostRef.current = null;
    if (lastSlotRef.current) lastSlotRef.current.style.background = "";
    lastSlotRef.current = null;
    setDragOver(null);
    if (sourceIdx === null) return;
    const slotEl = document
      .elementsFromPoint(e.clientX, e.clientY)
      .find((el) => el instanceof HTMLElement && (el as HTMLElement).hasAttribute("data-mc-slot")) as
      | HTMLElement
      | undefined;
    if (!slotEl) {
      onSlotDrop(sourceIdx, null);
      return;
    }
    const destIdx = parseInt(slotEl.getAttribute("data-mc-slot")!);
    if (destIdx === 0 || destIdx === sourceIdx) return;
    const content = slots[sourceIdx];
    if (content) onSlotDrop(destIdx, content);
    onSlotDrop(sourceIdx, null);
  }

  function handleDragOver(e: React.DragEvent, slotIdx: number) {
    if (slotIdx === 0) return;
    e.preventDefault();
    setDragOver(slotIdx);
  }

  function handleDragLeave() {
    setDragOver(null);
  }

  function handleDrop(e: React.DragEvent, slotIdx: number) {
    if (slotIdx === 0) return;
    e.preventDefault();
    e.stopPropagation();
    setDragOver(null);
    const attackId = e.dataTransfer.getData("application/x-mc-attack");
    if (attackId) {
      onSlotDrop(slotIdx, { kind: "attack", id: attackId });
      return;
    }
    const itemId = e.dataTransfer.getData("text/plain");
    onSlotDrop(slotIdx, itemId ? { kind: "item", id: itemId } : null);
  }

  function handleContextMenu(e: React.MouseEvent, slotIdx: number) {
    if (slotIdx === 0) return;
    e.preventDefault();
    onSlotDrop(slotIdx, null);
  }

  return {
    dragOver,
    startSlotDrag,
    moveSlotDrag,
    endSlotDrag,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    handleContextMenu,
  };
}
