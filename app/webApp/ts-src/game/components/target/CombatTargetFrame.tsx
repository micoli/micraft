import { UiState } from "../../UIReducer";
import { HpBar } from "./HpBar";

interface Props {
  target: NonNullable<UiState["combatTarget"]>;
  layoutStyle?: React.CSSProperties;
}

export function CombatTargetFrame({ target, layoutStyle }: Props) {
  if (!target.targetId) return null;

  return (
    <div
      className="flex flex-col gap-1 bg-black/55 rounded-md px-3 py-2 pointer-events-none z-[998]"
      style={{ ...layoutStyle, userSelect: "none" }}
    >
      <span className="text-[13px] text-white font-mono truncate text-center">
        {target.displayName ?? target.targetId}
        {target.distance != null && <span className="text-white/50"> · {target.distance.toFixed(1)}m</span>}
      </span>
      {target.level != null && (
        <span className="text-[10px] text-yellow-300/80 font-mono text-center">Lv {target.level}</span>
      )}
      <HpBar current={target.currentHp} max={target.maxHp} />
      {target.targetOfTarget && (
        <div className="mt-1 flex flex-col gap-1 border-t border-white/10 pt-1">
          <span className="text-[10px] text-white/50 font-mono truncate">↳ {target.targetOfTarget.name}</span>
          <HpBar current={target.targetOfTarget.currentHp} max={target.targetOfTarget.maxHp} height="h-2" />
        </div>
      )}
    </div>
  );
}
