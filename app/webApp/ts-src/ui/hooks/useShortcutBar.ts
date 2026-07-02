import { useState, useRef } from "react";

export function useShortcutBar(onSlotDrop: (slot: number, itemType: string | null) => void) {
  const [dragOver, setDragOver] = useState<number | null>(null);
  const draggingSlot = useRef<number | null>(null);

  function handleSlotDragStart(e: React.DragEvent, slotIdx: number, itemType: string) {
    draggingSlot.current = slotIdx;
    e.dataTransfer.setData("text/plain", itemType);
    e.dataTransfer.effectAllowed = "move";
  }

  function handleSlotDragEnd(e: React.DragEvent) {
    if (e.dataTransfer.dropEffect === "none" && draggingSlot.current !== null) {
      onSlotDrop(draggingSlot.current, null);
    }
    draggingSlot.current = null;
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
    setDragOver(null);
    const itemType = e.dataTransfer.getData("text/plain");
    onSlotDrop(slotIdx, itemType || null);
  }

  function handleContextMenu(e: React.MouseEvent, slotIdx: number) {
    if (slotIdx === 0) return;
    e.preventDefault();
    onSlotDrop(slotIdx, null);
  }

  return {
    dragOver,
    handleSlotDragStart,
    handleSlotDragEnd,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    handleContextMenu,
  };
}
