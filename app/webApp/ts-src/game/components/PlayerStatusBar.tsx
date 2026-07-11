import { useEffect, useRef, useState } from "react";
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
  const [display, setDisplay] = useState(remainingMs);
  const rafRef = useRef<number>(0);
  const currentRef = useRef<number>(remainingMs);
  const lastTimeRef = useRef<number>(0);

  useEffect(() => {
    // Only reset if server sends a value larger than local countdown (new GCD triggered)
    if (remainingMs > currentRef.current + 50) {
      cancelAnimationFrame(rafRef.current);
      currentRef.current = remainingMs;
      setDisplay(remainingMs);
      lastTimeRef.current = performance.now();

      const tick = (now: number) => {
        const delta = now - lastTimeRef.current;
        lastTimeRef.current = now;
        currentRef.current = Math.max(0, currentRef.current - delta);
        setDisplay(currentRef.current);
        if (currentRef.current > 0) {
          rafRef.current = requestAnimationFrame(tick);
        }
      };
      rafRef.current = requestAnimationFrame(tick);
    }
    // No cleanup here — tick self-terminates at 0; canceling on every remainingMs update
    // would kill the countdown when server sends intermediate updates (e.g. 800ms while local is at 900ms).
  }, [remainingMs]);

  useEffect(() => {
    return () => cancelAnimationFrame(rafRef.current);
  }, []);

  const pct = display > 0 ? Math.min(100, (display / 1500) * 100) : 0;
  const label = display > 0 ? `${(display / 1000).toFixed(1)}s` : "ready";
  return (
    <div className="flex items-center gap-2">
      <span className="text-[11px] text-white/60 w-6 shrink-0">GCD</span>
      <div className="relative flex-1 h-4 bg-black/60 rounded overflow-hidden border border-white/10">
        <div
          className="h-full rounded"
          style={{
            width: `${pct}%`,
            background: "rgba(255,255,255,0.5)",
          }}
        />
        <span className="absolute inset-0 flex items-center justify-center text-[10px] text-white font-mono leading-none">
          {label}
        </span>
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
      {status.maxMana > 0 && <Bar value={status.currentMana} max={status.maxMana} color="#2980b9" label="MP" />}
      {status.maxRage > 0 && <Bar value={status.currentRage} max={status.maxRage} color="#e67e22" label="RP" />}
      {status.maxTokens > 0 && (
        <div className="flex items-center gap-1">
          <span className="text-orange-300/70 font-mono text-[10px] w-5 shrink-0">TK</span>
          <div className="flex gap-0.5">
            {Array.from({ length: status.maxTokens }).map((_, i) => (
              <div
                key={i}
                className="w-3 h-3 rounded-sm border border-orange-400/60"
                style={{ background: i < (status.currentTokens ?? 0) ? "#e67e22" : "rgba(0,0,0,0.4)" }}
              />
            ))}
          </div>
        </div>
      )}
      <GcdBar remainingMs={status.globalCooldownRemainingMs} />
    </div>
  );
}
