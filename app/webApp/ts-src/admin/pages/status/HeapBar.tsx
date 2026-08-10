// ── Heap bar ──────────────────────────────────────────────────────────────────
export function HeapBar({ used, max, label }: { used: number; max: number; label: string }) {
  const pct = max > 0 ? Math.round((used / max) * 100) : 0;
  const color = pct > 85 ? "bg-red-500" : pct > 65 ? "bg-amber-400" : "bg-[#3C50E0]";
  return (
    <div>
      <div className="flex justify-between text-xs text-[#8A99AF] mb-2">
        <span>{label}</span>
        <span>
          {used} MB / {max} MB ({pct}%)
        </span>
      </div>
      <div className="w-full bg-[#2E3A4E] rounded-full h-2">
        <div className={`h-2 rounded-full transition-all ${color}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
