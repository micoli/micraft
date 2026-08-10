import { useEffect, useLayoutEffect, useRef, useState } from "react";

// Slot 0 on every page is the fixed "break" tool; slots 1-9 hold block type names.
export type InstanceShortcutSlot = string | null;

const SLOTS_PER_PAGE = 10;
const DIGIT_CODES = [
  "Digit1",
  "Digit2",
  "Digit3",
  "Digit4",
  "Digit5",
  "Digit6",
  "Digit7",
  "Digit8",
  "Digit9",
  "Digit0",
];

function emptyPage(): InstanceShortcutSlot[] {
  return new Array(SLOTS_PER_PAGE).fill(null);
}

export function useInstanceShortcutBar({
  onSelectBreak,
  onSelectBlock,
}: {
  onSelectBreak: () => void;
  onSelectBlock: (blockName: string) => void;
}) {
  const [pages, setPages] = useState<InstanceShortcutSlot[][]>([emptyPage()]);
  const [currentPage, setCurrentPage] = useState(0);
  const [selectedSlot, setSelectedSlot] = useState(0);
  const [dragOver, setDragOver] = useState<number | null>(null);

  const pagesRef = useRef(pages);
  useLayoutEffect(() => {
    pagesRef.current = pages;
  });

  function selectSlot(idx: number) {
    setSelectedSlot(idx);
    if (idx === 0) {
      onSelectBreak();
      return;
    }
    const blockName = pagesRef.current[currentPage][idx];
    if (blockName) onSelectBlock(blockName);
  }

  function goToPage(page: number) {
    if (page < 0) return;
    setPages((prev) => {
      if (page < prev.length) return prev;
      // Growing to fresh pages the user hasn't touched yet.
      return [...prev, ...Array.from({ length: page - prev.length + 1 }, emptyPage)];
    });
    setCurrentPage(page);
  }

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      const digitIdx = DIGIT_CODES.indexOf(e.code);
      if (digitIdx === -1) return;
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        goToPage(digitIdx);
        return;
      }
      selectSlot(digitIdx);
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- selectSlot/goToPage read live state via refs/updaters
  }, [currentPage]);

  function assignSlot(idx: number, blockName: InstanceShortcutSlot) {
    if (idx === 0) return;
    setPages((prev) => {
      const next = prev.map((p) => p.slice());
      next[currentPage][idx] = blockName;
      return next;
    });
  }

  function handleDragOver(e: React.DragEvent, idx: number) {
    if (idx === 0) return;
    e.preventDefault();
    setDragOver(idx);
  }

  function handleDragLeave() {
    setDragOver(null);
  }

  function handleDrop(e: React.DragEvent, idx: number) {
    if (idx === 0) return;
    e.preventDefault();
    setDragOver(null);
    const blockName = e.dataTransfer.getData("text/plain");
    if (blockName) assignSlot(idx, blockName);
  }

  function handleContextMenu(e: React.MouseEvent, idx: number) {
    if (idx === 0) return;
    e.preventDefault();
    assignSlot(idx, null);
  }

  return {
    slots: pages[currentPage],
    pageCount: pages.length,
    currentPage,
    selectedSlot,
    dragOver,
    selectSlot,
    goToPage,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    handleContextMenu,
  };
}
