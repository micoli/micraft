export function Bar({ value, max, color, label }: { value: number; max: number; color: string; label: string }) {
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
