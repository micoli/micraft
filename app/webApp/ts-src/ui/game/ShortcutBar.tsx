import { cn } from "../primitives/cn";
import { useShortcutBar } from "../hooks/useShortcutBar";

interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  slots: (string | null)[];
  selectedSlot: number;
  onSlotDrop: (slot: number, itemType: string | null) => void;
  layoutStyle?: React.CSSProperties;
}

export function ShortcutBar({ inventory, itemMeta, slots, selectedSlot, onSlotDrop, layoutStyle }: Props) {
  const {
    dragOver,
    handleSlotDragStart,
    handleSlotDragEnd,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    handleContextMenu,
  } = useShortcutBar(onSlotDrop);

  return (
    <div
      className={cn(
        "flex gap-1 pointer-events-auto z-[999] bg-black/60 border border-white/20 rounded-md py-1.5 px-2.5 items-center justify-center",
        !layoutStyle && "fixed bottom-5 left-1/2 -translate-x-1/2",
      )}
      style={layoutStyle}
    >
      {slots.map((itemType, idx) => {
        const isSelected = idx === selectedSlot;
        const isHand = idx === 0;
        const meta = itemType ? itemMeta[itemType] : null;
        const count = itemType ? (inventory[itemType] ?? 0) : 0;
        const isDropTarget = dragOver === idx;

        return (
          <div
            key={idx}
            data-mc-slot={idx}
            draggable={!isHand && !!itemType}
            onDragStart={!isHand && itemType ? (e) => handleSlotDragStart(e, idx, itemType) : undefined}
            onDragEnd={!isHand && !!itemType ? handleSlotDragEnd : undefined}
            onDragOver={(e) => handleDragOver(e, idx)}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e, idx)}
            onContextMenu={(e) => handleContextMenu(e, idx)}
            className={cn(
              "w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 transition-colors",
              isDropTarget ? "bg-white/20" : "bg-black/72",
              isSelected
                ? "border-yellow-400/90 shadow-[0_0_6px_rgba(255,215,0,0.5)]"
                : "border-white/35",
              isHand ? "cursor-default" : itemType ? "cursor-grab" : "cursor-pointer",
            )}
          >
            <div className="absolute top-0.5 left-0.5 text-white/45 font-mono text-[8px]">
              {idx === 9 ? "0" : String(idx + 1)}
            </div>

            {isHand ? (
              <div className="text-white/60 font-mono text-base">✋</div>
            ) : meta ? (
              <>
                <div
                  className="w-[26px] h-[26px] rounded-sm"
                  style={{
                    background: meta.bg,
                    boxShadow:
                      "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
                  }}
                />
                <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px]">
                  {meta.label}
                </div>
                {count > 0 && (
                  <div className="absolute bottom-0.5 right-1 text-white font-mono font-bold text-[9px] [text-shadow:1px_1px_0_#000]">
                    {count}
                  </div>
                )}
              </>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
