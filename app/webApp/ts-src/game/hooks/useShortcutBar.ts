import { useState, useRef, useEffect, useLayoutEffect } from "react";
import { ShortcutSlot } from "../types";

export function useShortcutBar(
  onSlotDrop: (slot: number, content: ShortcutSlot | null) => void,
  slots: (ShortcutSlot | null)[],
) {
  const [dragOver, setDragOver] = useState<number | null>(null);
  const [pressedSlot, setPressedSlot] = useState<number | null>(null);
  const slotsRef = useRef(slots);
  useLayoutEffect(() => { slotsRef.current = slots; });

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const bindings = window.mcState?.bindings;
      if (!bindings) return;
      for (let i = 0; i < 10; i++) {
        const action = `slot_${i + 1}`;
        const keys: string[] = bindings[action] ?? [];
        if (keys.some((k) => k === e.code || k === e.key)) {
          const slot = slotsRef.current[i];
          if (slot?.kind === "attack" || slot?.kind === "macro") {
            setPressedSlot(i);
            setTimeout(() => setPressedSlot(null), 150);
          }
          break;
        }
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);
  const draggingSlot = useRef<number | null>(null);
  const didDragRef = useRef(false);
  const ghostRef = useRef<HTMLDivElement | null>(null);
  const lastSlotRef = useRef<HTMLElement | null>(null);

  function startSlotDrag(e: React.PointerEvent<HTMLDivElement>, slotIdx: number) {
    if (!e.altKey) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    draggingSlot.current = slotIdx;
    didDragRef.current = true;
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
    if (sourceIdx !== null) e.preventDefault();
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch { /* empty */ }
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
    const macroId = e.dataTransfer.getData("application/x-mc-macro");
    if (macroId) {
      onSlotDrop(slotIdx, { kind: "macro", id: macroId });
      return;
    }
    const attackId = e.dataTransfer.getData("application/x-mc-attack");
    if (attackId) {
      onSlotDrop(slotIdx, { kind: "attack", id: attackId });
      return;
    }
    const spellId = e.dataTransfer.getData("application/x-mc-spell");
    if (spellId) {
      onSlotDrop(slotIdx, { kind: "spell", id: spellId });
      return;
    }
    const itemId = e.dataTransfer.getData("text/plain");
    onSlotDrop(slotIdx, itemId ? { kind: "item", id: itemId } : null);
  }

  function handleSlotClick(slotIdx: number) {
    if (didDragRef.current) {
      didDragRef.current = false;
      return;
    }
    const slot = slots[slotIdx];
    if (!slot) return;
    setPressedSlot(slotIdx);
    setTimeout(() => setPressedSlot(null), 150);
    if (slot.kind === "macro") {
      window.mcRunMacro?.(slot.id);
    } else if (slot.kind === "attack") {
      window.mcState?.events?.push(`slot_${slotIdx + 1}`);
    } else if (slot.kind === "spell") {
      window.mcState?.events?.push(`slot_${slotIdx + 1}`);
    }
  }

  function handleContextMenu(e: React.MouseEvent, slotIdx: number) {
    if (slotIdx === 0) return;
    e.preventDefault();
    onSlotDrop(slotIdx, null);
  }

  return {
    dragOver,
    pressedSlot,
    startSlotDrag,
    moveSlotDrag,
    endSlotDrag,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    handleContextMenu,
    handleSlotClick,
  };
}
