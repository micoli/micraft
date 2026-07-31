import { cn } from "../../primitives/cn";
import { useShortcutBar } from "../hooks/useShortcutBar";
import { ShortcutSlot, AttackMeta, SpellMeta } from "../types";
import { UiState } from "../UIReducer";
import { damageTypeColor, AttackCooldownOverlay } from "./AttackPanel";

interface Props {
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  attackMeta: Record<string, AttackMeta>;
  spellMeta?: Record<string, SpellMeta>;
  slots: (ShortcutSlot | null)[];
  selectedSlot: number;
  currentPage?: number;
  nonEmptyPages?: number[];
  onSlotDrop: (slot: number, content: ShortcutSlot | null) => void;
  layoutStyle?: React.CSSProperties;
  macros?: Record<string, string>;
  macroIcons?: Record<string, string>;
  playerStatus?: UiState["playerStatus"];
}

export function ShortcutBar({
  inventory,
  itemMeta,
  attackMeta,
  spellMeta = {},
  slots,
  selectedSlot,
  currentPage = 0,
  nonEmptyPages = [],
  onSlotDrop,
  layoutStyle,
  macros: _macros,
  macroIcons,
  playerStatus,
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
        "relative flex flex-wrap gap-1 pointer-events-auto z-[999] rounded-md py-1.5 px-2.5 items-center justify-center",
        !layoutStyle && "fixed bottom-5 left-1/2 -translate-x-1/2 bg-black/60",
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
        const isSpell = slot?.kind === "spell";
        const attackDef = isAttack ? attackMeta[slot!.id] : null;
        const spellDef = isSpell ? spellMeta[slot!.id] : null;
        const itemMeta_ = slot?.kind === "item" ? itemMeta[slot.id] : null;
        const count = slot?.kind === "item" ? (inventory[slot.id] ?? 0) : 0;

        const slotHasCd = isAttack && (playerStatus?.attackCooldownsRemainingMs?.[slot!.id] ?? 0) > 0;
        return (
          <div
            key={idx}
            data-mc-slot={idx}
            onPointerDown={!isHand && slot ? (e) => startSlotDrag(e, idx) : undefined}
            onPointerMove={!isHand && slot ? moveSlotDrag : undefined}
            onPointerUp={!isHand && slot ? endSlotDrag : undefined}
            onPointerCancel={!isHand && slot ? endSlotDrag : undefined}
            onClick={!isHand && (isAttack || isMacro || isSpell) ? () => handleSlotClick(idx) : undefined}
            onDragOver={(e) => handleDragOver(e, idx)}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e, idx)}
            onContextMenu={(e) => handleContextMenu(e, idx)}
            className={cn(
              "w-[52px] h-[52px] shrink-0 flex flex-col items-center justify-center relative rounded border-2 transition-all touch-none",
              isDropTarget ? "bg-white/20" : "bg-black/72",
              isSelected ? "border-yellow-400/90 shadow-[0_0_6px_rgba(255,215,0,0.5)]" : "border-white/35",
              isHand
                ? "cursor-default"
                : isAttack || isMacro || isSpell
                  ? "cursor-pointer"
                  : slot
                    ? "cursor-grab"
                    : "cursor-pointer",
              isPressed && "scale-90 brightness-150",
              slotHasCd && "opacity-50",
            )}
          >
            <div className="absolute top-0.5 left-0.5 text-white/45 font-mono text-[8px]">
              {idx === 9 ? "0" : String(idx + 1)}
            </div>

            {isHand ? (
              <div className="text-white/60 font-mono text-base">✋</div>
            ) : isMacro ? (
              <>
                <div className="text-amber-400/80 font-mono text-base leading-none">
                  {macroIcons?.[slot!.id] ?? "⚡"}
                </div>
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
                <AttackCooldownOverlay id={slot!.id} meta={attackDef} playerStatus={playerStatus} />
              </>
            ) : isSpell ? (
              <>
                <div className="text-orange-400 text-lg leading-none">⚡</div>
                <div className="text-orange-300/80 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                  {slot!.id}
                </div>
                <AttackCooldownOverlay id={slot!.id} meta={spellDef} playerStatus={playerStatus} />
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
                <div className="absolute bottom-0.5 right-1 font-mono font-bold text-[9px] [text-shadow:1px_1px_0_#000]">
                  {count > 0 ? <span className="text-white">{count}</span> : <span className="text-red-500/80">⊘</span>}
                </div>
              </>
            ) : null}
          </div>
        );
      })}
      {nonEmptyPages.length > 1 && (
        <div className="absolute -bottom-4 left-1/2 -translate-x-1/2 flex gap-1">
          {nonEmptyPages.map((p) => (
            <div
              key={p}
              className={cn("w-1.5 h-1.5 rounded-full", p === currentPage ? "bg-yellow-400" : "bg-white/40")}
            />
          ))}
        </div>
      )}
    </div>
  );
}
