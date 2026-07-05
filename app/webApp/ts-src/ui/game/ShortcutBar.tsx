import { cn } from "../primitives/cn";
import { useShortcutBar } from "../hooks/useShortcutBar";
import { ShortcutSlot, AttackMeta } from "../UIReducer";
import { damageTypeColor } from "./AttackPanel";

interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  attackMeta: Record<string, AttackMeta>;
  slots: (ShortcutSlot | null)[];
  selectedSlot: number;
  onSlotDrop: (slot: number, content: ShortcutSlot | null) => void;
  layoutStyle?: React.CSSProperties;
  macros?: Record<string, string>;
}

export function ShortcutBar({
  inventory,
  itemMeta,
  attackMeta,
  slots,
  selectedSlot,
  onSlotDrop,
  layoutStyle,
  macros,
}: Props) {
  const {
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
  } = useShortcutBar(onSlotDrop, slots);

  return (
    <div
      className={cn(
        "flex gap-1 pointer-events-auto z-[999] bg-black/60 border border-white/20 rounded-md py-1.5 px-2.5 items-center justify-center",
        !layoutStyle && "fixed bottom-5 left-1/2 -translate-x-1/2",
      )}
      style={layoutStyle}
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        const slotEl = (e.target as HTMLElement).closest("[data-mc-slot]") as HTMLElement | null;
        if (slotEl) {
          const idx = parseInt(slotEl.getAttribute("data-mc-slot")!);
          handleDrop(e, idx);
        }
      }}
    >
      {slots.map((slot, idx) => {
        const isSelected = idx === selectedSlot;
        const isHand = idx === 0;
        const isDropTarget = dragOver === idx;
        const isPressed = pressedSlot === idx;
        const isAttack = slot?.kind === "attack";
        const isMacro = slot?.kind === "macro";
        const attackDef = isAttack ? attackMeta[slot!.id] : null;
        const itemMeta_ = slot?.kind === "item" ? itemMeta[slot.id] : null;
        const count = slot?.kind === "item" ? (inventory[slot.id] ?? 0) : 0;

        return (
          <div
            key={idx}
            data-mc-slot={idx}
            onPointerDown={!isHand && slot ? (e) => startSlotDrag(e, idx) : undefined}
            onPointerMove={!isHand && slot ? moveSlotDrag : undefined}
            onPointerUp={!isHand && slot ? endSlotDrag : undefined}
            onPointerCancel={!isHand && slot ? endSlotDrag : undefined}
            onClick={!isHand && (isAttack || isMacro) ? () => handleSlotClick(idx) : undefined}
            onDragOver={(e) => handleDragOver(e, idx)}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e, idx)}
            onContextMenu={(e) => handleContextMenu(e, idx)}
            className={cn(
              "w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 transition-all touch-none",
              isDropTarget ? "bg-white/20" : "bg-black/72",
              isSelected ? "border-yellow-400/90 shadow-[0_0_6px_rgba(255,215,0,0.5)]" : "border-white/35",
              isHand
                ? "cursor-default"
                : isAttack || isMacro
                  ? "cursor-pointer"
                  : slot
                    ? "cursor-grab"
                    : "cursor-pointer",
              isPressed && "scale-90 brightness-150",
            )}
          >
            <div className="absolute top-0.5 left-0.5 text-white/45 font-mono text-[8px]">
              {idx === 9 ? "0" : String(idx + 1)}
            </div>

            {isHand ? (
              <div className="text-white/60 font-mono text-base">✋</div>
            ) : isMacro ? (
              <>
                <div className="text-amber-400/80 font-mono text-base">⚡</div>
                <div
                  className="text-amber-300/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate"
                  title={slot!.id}
                >
                  {slot!.id}
                </div>
              </>
            ) : isAttack ? (
              <>
                <div
                  className="w-[26px] h-[26px] rounded-full"
                  style={{
                    background: attackDef ? damageTypeColor(attackDef.damageType) : "#888",
                    boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.2)",
                  }}
                />
                <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                  {slot!.id}
                </div>
              </>
            ) : itemMeta_ ? (
              <>
                <div
                  className="w-[26px] h-[26px] rounded-sm"
                  style={{
                    background: itemMeta_.bg,
                    boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
                  }}
                />
                <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px]">{itemMeta_.label}</div>
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
