import { UiState } from "../UIReducer";

interface Props {
  target: NonNullable<UiState["combatTarget"]>;
  layoutStyle?: React.CSSProperties;
}

function HpBar({ current, max, height = "h-4" }: { current: number; max: number; height?: string }) {
  const pct = max > 0 ? Math.min(100, Math.max(0, (current / max) * 100)) : 0;
  const color = pct > 50 ? "#27ae60" : pct > 25 ? "#e67e22" : "#c0392b";
  return (
    <div className={`relative w-full ${height} bg-black/60 rounded overflow-hidden border border-white/10`}>
      <div
        className="h-full rounded transition-[width] duration-150 ease-out"
        style={{ width: `${pct}%`, background: color }}
      />
      <span className="absolute inset-0 flex items-center justify-center text-[10px] text-white font-mono leading-none">
        {current}/{max}
      </span>
    </div>
  );
}

export function CombatTargetFrame({ target, layoutStyle }: Props) {
  if (!target.targetId) return null;

  return (
    <div
      className="flex flex-col gap-1 bg-black/55 rounded-md px-3 py-2 pointer-events-none z-[998] overflow-hidden"
      style={{ ...layoutStyle, userSelect: "none" }}
    >
      <span className="text-[13px] text-white font-mono truncate text-center">
        {target.displayName ?? target.targetId}
      </span>
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
