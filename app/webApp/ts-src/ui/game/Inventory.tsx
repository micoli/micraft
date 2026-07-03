import { cn } from "../primitives/cn";
import { useInventory } from "../hooks/useInventory";

interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  visible: boolean;
  layoutStyle?: React.CSSProperties;
}

export function Inventory({ inventory, itemMeta, visible, layoutStyle }: Props) {
  const { startDrag, moveDrag, endDrag } = useInventory();

  if (!visible) return null;

  const items = Object.keys(inventory)
    .filter((type) => (inventory[type] ?? 0) > 0 && itemMeta[type] !== undefined)
    .map((type) => ({ type, count: inventory[type], meta: itemMeta[type] }));

  return (
    <div
      className={cn(
        "flex gap-1 pointer-events-auto z-[998] items-center bg-black/75 border border-white/30 rounded-md py-2 px-3 min-w-[120px] min-h-[68px] flex-wrap",
        !layoutStyle && "fixed bottom-24 left-1/2 -translate-x-1/2",
      )}
      style={layoutStyle}
    >
      {items.length === 0 ? (
        <div className="text-white/35 font-mono text-xs text-center w-full px-4 py-2">Inventory empty</div>
      ) : (
        items.map(({ type, count, meta }) => (
          <div
            key={type}
            onPointerDown={(e) => startDrag(e, type, meta.bg)}
            onPointerMove={moveDrag}
            onPointerUp={endDrag}
            onPointerCancel={endDrag}
            className="w-[52px] h-[52px] bg-black/72 border-2 border-white/45 rounded flex flex-col items-center justify-center relative cursor-grab touch-none"
          >
            <div
              className="w-[26px] h-[26px] rounded-sm"
              style={{
                background: meta.bg,
                boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
              }}
            />
            <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px]">{meta.label}</div>
            <div className="absolute bottom-0.5 right-1 text-white font-mono font-bold text-[10px] [text-shadow:1px_1px_0_#000]">
              {count}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
