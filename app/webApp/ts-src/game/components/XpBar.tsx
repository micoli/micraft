import { useGameContext } from "../GameContext";

interface Props {
  layoutStyle?: React.CSSProperties;
}

export function XpBar({ layoutStyle }: Props) {
  const { state } = useGameContext();
  const xp = state.xpState;

  if (!xp) return null;

  const pct = xp.nextLevelXp > 0 ? Math.min(100, Math.max(0, (xp.totalXp / xp.nextLevelXp) * 100)) : 0;

  return (
    <div
      className="flex items-center gap-2 bg-black/55 rounded-md px-3 py-1 pointer-events-none z-[998]"
      style={{ ...layoutStyle, userSelect: "none" }}
    >
      <span className="text-[11px] text-white/60 shrink-0 font-mono">Lv.{xp.level}</span>
      <div className="relative flex-1 h-3 bg-black/60 rounded overflow-hidden border border-white/10">
        <div
          className="h-full rounded transition-[width] duration-300 ease-out"
          style={{ width: `${pct}%`, background: "#2ecc71" }}
        />
        <span className="absolute inset-0 flex items-center justify-center text-[9px] text-white font-mono leading-none">
          {xp.totalXp} / {xp.nextLevelXp} XP
        </span>
      </div>
    </div>
  );
}
