import { UiState } from "../UIReducer";

interface Props {
  status: NonNullable<UiState["playerStatus"]>;
  layoutStyle?: React.CSSProperties;
}

function Bar({ value, max, color, label }: { value: number; max: number; color: string; label: string }) {
  const pct = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  return (
    <div className="flex items-center gap-2">
      <span className="text-[11px] text-white/60 w-6 shrink-0">{label}</span>
      <div className="relative flex-1 h-4 bg-black/60 rounded overflow-hidden border border-white/10">
        <div
          className="h-full rounded transition-[width] duration-150 ease-out"
          style={{ width: `${pct}%`, background: color }}
        />
        <span className="absolute inset-0 flex items-center justify-center text-[10px] text-white font-mono leading-none">
          {value}/{max}
        </span>
      </div>
    </div>
  );
}

function GcdBar({ remainingMs }: { remainingMs: number }) {
  if (remainingMs <= 0) return null;
  return (
    <div className="flex items-center gap-2">
      <span className="text-[11px] text-white/60 w-6 shrink-0">GCD</span>
      <div className="flex-1 h-1 bg-white/20 rounded overflow-hidden">
        <div
          className="h-full bg-white/70 rounded"
          style={{
            width: `${Math.min(100, (remainingMs / 1500) * 100)}%`,
            transition: "width 50ms linear",
          }}
        />
      </div>
    </div>
  );
}

export function PlayerStatusBar({ status, layoutStyle }: Props) {
  return (
    <div
      className="flex flex-col gap-1 bg-black/55 rounded-md px-3 py-2 pointer-events-none z-[998]"
      style={{ ...layoutStyle, userSelect: "none" }}
    >
      <Bar value={status.currentHp} max={status.maxHp} color="#c0392b" label="HP" />
      {status.maxMana > 0 && (
        <Bar value={status.currentMana} max={status.maxMana} color="#2980b9" label="MP" />
      )}
      {status.maxRage > 0 && (
        <Bar value={status.currentRage} max={status.maxRage} color="#e67e22" label="RP" />
      )}
      <GcdBar remainingMs={status.globalCooldownRemainingMs} />
    </div>
  );
}
