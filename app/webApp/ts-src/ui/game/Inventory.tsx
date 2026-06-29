import { useRef } from "react";

interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  visible: boolean;
  layoutStyle?: React.CSSProperties;
}

export function Inventory({ inventory, itemMeta, visible, layoutStyle }: Props) {
  const ghostRef = useRef<HTMLDivElement | null>(null);
  const lastSlotRef = useRef<Element | null>(null);

  if (!visible) return null;

  const items = Object.keys(inventory)
    .filter((type) => (inventory[type] ?? 0) > 0 && itemMeta[type] !== undefined)
    .map((type) => ({ type, count: inventory[type], meta: itemMeta[type] }));

  const containerStyle: React.CSSProperties = layoutStyle
    ? {
        display: "flex",
        gap: 4,
        pointerEvents: "all",
        zIndex: 998,
        alignItems: "center",
        background: "rgba(0,0,0,0.75)",
        border: "1px solid rgba(255,255,255,0.3)",
        borderRadius: 6,
        padding: "8px 12px",
        minWidth: 120,
        minHeight: 68,
        flexWrap: "wrap",
        ...layoutStyle,
      }
    : {
        position: "fixed",
        bottom: 100,
        left: "50%",
        transform: "translateX(-50%)",
        display: "flex",
        gap: 4,
        pointerEvents: "all",
        zIndex: 998,
        alignItems: "center",
        background: "rgba(0,0,0,0.75)",
        border: "1px solid rgba(255,255,255,0.3)",
        borderRadius: 6,
        padding: "8px 12px",
        minWidth: 120,
        minHeight: 68,
      };

  const startDrag = (e: React.PointerEvent<HTMLDivElement>, type: string, bg: string) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    (window as any).__mcDragItem = type;
    const ghost = document.createElement("div");
    ghost.style.cssText = `position:fixed;pointer-events:none;z-index:9999;width:52px;height:52px;background:rgba(0,0,0,0.9);border:2px solid rgba(255,255,255,0.8);border-radius:4px;display:flex;align-items:center;justify-content:center;left:${e.clientX - 26}px;top:${e.clientY - 26}px;opacity:0.85;cursor:grabbing;`;
    const inner = document.createElement("div");
    inner.style.cssText = `width:26px;height:26px;border-radius:3px;background:${bg};box-shadow:inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15);`;
    ghost.appendChild(inner);
    document.body.appendChild(ghost);
    ghostRef.current = ghost;
  };

  const moveDrag = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!ghostRef.current) return;
    ghostRef.current.style.left = `${e.clientX - 26}px`;
    ghostRef.current.style.top = `${e.clientY - 26}px`;
    // Highlight slot under cursor
    const slotEl = document
      .elementsFromPoint(e.clientX, e.clientY)
      .find((el) => el instanceof HTMLElement && (el as HTMLElement).hasAttribute("data-mc-slot"));
    if (slotEl !== lastSlotRef.current) {
      if (lastSlotRef.current instanceof HTMLElement) lastSlotRef.current.style.background = "rgba(0,0,0,0.72)";
      if (slotEl instanceof HTMLElement) slotEl.style.background = "rgba(255,255,255,0.2)";
      lastSlotRef.current = slotEl ?? null;
    }
  };

  const endDrag = (e: React.PointerEvent<HTMLDivElement>) => {
    const item = (window as any).__mcDragItem as string | null;
    (window as any).__mcDragItem = null;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}
    ghostRef.current?.remove();
    ghostRef.current = null;
    if (lastSlotRef.current instanceof HTMLElement) lastSlotRef.current.style.background = "rgba(0,0,0,0.72)";
    lastSlotRef.current = null;
    if (!item) return;
    const slotEl = document
      .elementsFromPoint(e.clientX, e.clientY)
      .find((el) => el instanceof HTMLElement && (el as HTMLElement).hasAttribute("data-mc-slot")) as
      | HTMLElement
      | undefined;
    if (slotEl) {
      const slotIdx = parseInt(slotEl.getAttribute("data-mc-slot")!);
      if (slotIdx > 0) (window as any).__mcSlotDrop?.(slotIdx, item);
    }
  };

  return (
    <div style={containerStyle}>
      {items.length === 0 ? (
        <div
          style={{
            color: "rgba(255,255,255,0.35)",
            font: "12px monospace",
            padding: "8px 16px",
            textAlign: "center",
            width: "100%",
          }}
        >
          Inventory empty
        </div>
      ) : (
        items.map(({ type, count, meta }) => (
          <div
            key={type}
            onPointerDown={(e) => startDrag(e, type, meta.bg)}
            onPointerMove={moveDrag}
            onPointerUp={endDrag}
            onPointerCancel={endDrag}
            style={{
              width: 52,
              height: 52,
              background: "rgba(0,0,0,0.72)",
              border: "2px solid rgba(255,255,255,0.45)",
              borderRadius: 4,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              position: "relative",
              cursor: "grab",
              touchAction: "none",
            }}
          >
            <div
              style={{
                width: 26,
                height: 26,
                borderRadius: 3,
                background: meta.bg,
                boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
              }}
            />
            <div
              style={{ color: "rgba(255,255,255,0.7)", font: "8px monospace", marginTop: 3, letterSpacing: "0.5px" }}
            >
              {meta.label}
            </div>
            <div
              style={{
                position: "absolute",
                bottom: 2,
                right: 4,
                color: "#fff",
                font: "bold 10px monospace",
                textShadow: "1px 1px 0 #000",
              }}
            >
              {count}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
