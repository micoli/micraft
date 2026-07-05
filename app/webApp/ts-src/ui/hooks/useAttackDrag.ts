import { useRef } from "react";

export function useAttackDrag(color: (id: string) => string, kind: "attack" | "macro" = "attack") {
  const ghostRef = useRef<HTMLDivElement | null>(null);
  const lastSlotRef = useRef<Element | null>(null);
  const dragIdRef = useRef<string | null>(null);
  const didMoveRef = useRef(false);

  function startDrag(e: React.PointerEvent<HTMLDivElement>, id: string) {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragIdRef.current = id;
    didMoveRef.current = false;
    const ghost = document.createElement("div");
    ghost.style.cssText = `position:fixed;pointer-events:none;z-index:9999;width:52px;height:52px;background:rgba(0,0,0,0.9);border:2px solid rgba(255,255,255,0.8);border-radius:4px;display:flex;align-items:center;justify-content:center;left:${e.clientX - 26}px;top:${e.clientY - 26}px;opacity:0.85;cursor:grabbing;`;
    const inner = document.createElement("div");
    if (kind === "macro") {
      inner.style.cssText = `font-size:22px;line-height:1;`;
      inner.textContent = "⚡";
    } else {
      inner.style.cssText = `width:26px;height:26px;border-radius:50%;background:${color(id)};box-shadow:inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.2);`;
    }
    ghost.appendChild(inner);
    document.body.appendChild(ghost);
    ghostRef.current = ghost;
  }

  function moveDrag(e: React.PointerEvent<HTMLDivElement>) {
    if (!ghostRef.current) return;
    didMoveRef.current = true;
    ghostRef.current.style.left = `${e.clientX - 26}px`;
    ghostRef.current.style.top = `${e.clientY - 26}px`;
    const slotEl = document
      .elementsFromPoint(e.clientX, e.clientY)
      .find((el) => el instanceof HTMLElement && (el as HTMLElement).hasAttribute("data-mc-slot"));
    if (slotEl !== lastSlotRef.current) {
      if (lastSlotRef.current instanceof HTMLElement) lastSlotRef.current.style.background = "rgba(0,0,0,0.72)";
      if (slotEl instanceof HTMLElement) slotEl.style.background = "rgba(255,255,255,0.2)";
      lastSlotRef.current = slotEl ?? null;
    }
  }

  function endDrag(e: React.PointerEvent<HTMLDivElement>) {
    const id = dragIdRef.current;
    dragIdRef.current = null;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}
    ghostRef.current?.remove();
    ghostRef.current = null;
    if (lastSlotRef.current instanceof HTMLElement) lastSlotRef.current.style.background = "rgba(0,0,0,0.72)";
    lastSlotRef.current = null;
    if (!id) return;
    const slotEl = document
      .elementsFromPoint(e.clientX, e.clientY)
      .find((el) => el instanceof HTMLElement && (el as HTMLElement).hasAttribute("data-mc-slot")) as
      | HTMLElement
      | undefined;
    if (slotEl) {
      const slotIdx = parseInt(slotEl.getAttribute("data-mc-slot")!);
      if (slotIdx > 0) window.mcState.slotDrop?.(slotIdx, { kind, id });
    }
  }

  function guardClick(cb: () => void) {
    if (didMoveRef.current) {
      didMoveRef.current = false;
      return;
    }
    cb();
  }

  return { startDrag, moveDrag, endDrag, guardClick };
}
