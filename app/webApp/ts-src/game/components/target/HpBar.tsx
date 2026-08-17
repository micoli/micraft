export function HpBar({ current, max, height = "h-4" }: { current: number; max: number; height?: string }) {
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
