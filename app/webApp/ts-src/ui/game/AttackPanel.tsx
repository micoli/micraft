import { cn } from "../primitives/cn";
import { AttackMeta } from "../UIReducer";
import { useAttackDrag } from "../hooks/useAttackDrag";

export function damageTypeColor(damageType: string): string {
  switch (damageType) {
    case "FIRE":
      return "#e05a00";
    case "POISON":
      return "#4a9e2f";
    case "MAGIC":
      return "#7b2fe0";
    case "LIGHTNING":
      return "#c8d400";
    case "NECROTIC":
      return "#3d1a5e";
    case "PHYSICAL":
    default:
      return "#8a6a3a";
  }
}

interface Props {
  attackMeta: Record<string, AttackMeta>;
  layoutStyle?: React.CSSProperties;
  pinnedMacros?: string[];
}

export function AttackPanel({ attackMeta, layoutStyle, pinnedMacros = [] }: Props) {
  const attacks = Object.entries(attackMeta);
  const { startDrag, moveDrag, endDrag, guardClick } = useAttackDrag((id) =>
    damageTypeColor(attackMeta[id]?.damageType ?? ""),
  );
  const {
    startDrag: startMacroDrag,
    moveDrag: moveMacroDrag,
    endDrag: endMacroDrag,
    guardClick: guardMacroClick,
  } = useAttackDrag(() => "#b45309", "macro");
  if (attacks.length === 0 && pinnedMacros.length === 0) return null;

  return (
    <div
      className={cn(
        "pointer-events-auto z-[999] bg-black/60 border border-white/20 rounded-md p-2",
        !layoutStyle && "fixed bottom-24 left-1/2 -translate-x-1/2",
      )}
      style={layoutStyle}
    >
      {pinnedMacros.length > 0 && (
        <>
          <div className="text-white/40 font-mono text-[9px] mb-1 uppercase tracking-widest">Macros</div>
          <div className="flex gap-1 flex-wrap mb-2">
            {pinnedMacros.map((name) => (
              <div
                key={name}
                onClick={() => guardMacroClick(() => window.mcRunMacro?.(name))}
                onPointerDown={(e) => startMacroDrag(e, name)}
                onPointerMove={moveMacroDrag}
                onPointerUp={endMacroDrag}
                onPointerCancel={endMacroDrag}
                title={name}
                className="w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-amber-400/40 bg-black/72 cursor-grab hover:border-amber-400/80 transition-colors touch-none"
              >
                <div className="text-amber-400/80 font-mono text-base">⚡</div>
                <div className="text-amber-300/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                  {name}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
      {attacks.length > 0 && (
        <div className="text-white/40 font-mono text-[9px] mb-1 uppercase tracking-widest">Attacks</div>
      )}
      <div className="flex gap-1 flex-wrap">
        {attacks.map(([id, meta]) => (
          <div
            key={id}
            onClick={() => guardClick(() => window.mcState?.events?.push(`attack:${id}`))}
            onPointerDown={(e) => startDrag(e, id)}
            onPointerMove={moveDrag}
            onPointerUp={endDrag}
            onPointerCancel={endDrag}
            title={`${id}\n${meta.damageType}${meta.manaCost > 0 ? ` · ${meta.manaCost} mana` : ""}${meta.rageCost > 0 ? ` · ${meta.rageCost} rage` : ""}`}
            className="w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-white/25 bg-black/72 cursor-grab hover:border-white/60 transition-colors touch-none"
          >
            <div
              className="w-[26px] h-[26px] rounded-full"
              style={{
                background: damageTypeColor(meta.damageType),
                boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.2)",
              }}
            />
            <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">{id}</div>
            {meta.manaCost > 0 && (
              <div className="absolute top-0.5 right-1 text-blue-300 font-mono font-bold text-[8px]">
                {meta.manaCost}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
