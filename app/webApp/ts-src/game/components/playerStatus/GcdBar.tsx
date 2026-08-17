import { useEffect, useRef, useState } from "react";

export function GcdBar({ remainingMs }: { remainingMs: number }) {
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
      <span className="text-[11px] text-white/60 w-6 shrink-0 ml-2">GCD</span>
      <div
        className="relative flex-1 h-4 bg-black/60 rounded overflow-hidden border border-white/10"
        style={{
          minWidth: "50px",
        }}
      >
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
