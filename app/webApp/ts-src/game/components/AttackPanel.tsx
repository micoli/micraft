import { cn } from "../../primitives/cn";
import { AttackMeta, UiState } from "../UIReducer";
import { useAttackDrag } from "../hooks/useAttackDrag";
import { useCooldownDisplay } from "../hooks/useCooldownDisplay";

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

export function hasEnoughResources(meta: AttackMeta, status: UiState["playerStatus"] | undefined): boolean {
  if (!status) return true;
  if (meta.manaCost > 0 && status.currentMana < meta.manaCost) return false;
  if (meta.rageCost > 0 && status.currentRage < meta.rageCost) return false;
  return true;
}

interface Props {
  attackMeta: Record<string, AttackMeta>;
  layoutStyle?: React.CSSProperties;
  pinnedMacros?: string[];
  playerStatus?: UiState["playerStatus"];
}

export function AttackCooldownOverlay({
  id,
  meta,
  playerStatus,
}: {
  id: string;
  meta: AttackMeta | null;
  playerStatus: UiState["playerStatus"] | undefined;
}) {
  const serverCd = playerStatus?.attackCooldownsRemainingMs?.[id] ?? 0;
  const cooldownDisplay = useCooldownDisplay(serverCd);
  const hasCd = cooldownDisplay > 0;
  const hasRes = meta ? hasEnoughResources(meta, playerStatus) : true;

  return (
    <>
      {hasCd ? (
        <div className="absolute bottom-0.5 right-0.5 text-white font-mono text-[8px] leading-none [text-shadow:1px_1px_0_#000]">
          {(cooldownDisplay / 1000).toFixed(1)}
        </div>
      ) : !hasRes ? (
        <div className="absolute bottom-0 right-0 text-[10px] leading-none select-none">🚫</div>
      ) : null}
    </>
  );
}

export function AttackPanel({ attackMeta, layoutStyle, pinnedMacros = [], playerStatus }: Props) {
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
        "pointer-events-auto z-[999] bg-black/60 border border-white/20 rounded-md p-2 overflow-hidden",
        !layoutStyle && "fixed bottom-24 left-1/2 -translate-x-1/2",
      )}
      style={layoutStyle}
    >
      <div className="flex flex-wrap gap-1 content-start">
        {pinnedMacros.map((name) => (
          <div
            key={`macro-${name}`}
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
        {attacks.map(([id, meta]) => {
          const hasCd = (playerStatus?.attackCooldownsRemainingMs?.[id] ?? 0) > 0;
          const displayName = meta.attackId ?? id;
          return (
            <div
              key={`attack-${id}`}
              onClick={() => guardClick(() => window.mcState?.events?.push(`attack:${id}`))}
              onPointerDown={(e) => startDrag(e, id)}
              onPointerMove={moveDrag}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
              title={`${displayName} (rank ${meta.level})\n${meta.damageType}${meta.manaCost > 0 ? ` · ${meta.manaCost} mana` : ""}${meta.rageCost > 0 ? ` · ${meta.rageCost} rage` : ""}${meta.power > 0 ? ` · power ${meta.power}` : ""}`}
              className={cn(
                "w-[52px] h-[52px] flex flex-col items-center justify-center relative rounded border-2 border-white/25 bg-black/72 cursor-grab hover:border-white/60 transition-colors touch-none",
                hasCd && "opacity-50",
              )}
            >
              <div
                className="w-[26px] h-[26px] rounded-full"
                style={{
                  background: damageTypeColor(meta.damageType),
                  boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.2)",
                }}
              />
              <div className="text-white/70 font-mono text-[8px] mt-0.5 tracking-[0.5px] max-w-[48px] truncate">
                {displayName}
              </div>
              {meta.level > 1 && (
                <div className="absolute top-0.5 left-1 text-yellow-300 font-mono font-bold text-[8px]">
                  {meta.level}
                </div>
              )}
              {meta.manaCost > 0 && (
                <div className="absolute top-0.5 right-1 text-blue-300 font-mono font-bold text-[8px]">
                  {meta.manaCost}
                </div>
              )}
              <AttackCooldownOverlay id={id} meta={meta} playerStatus={playerStatus} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
