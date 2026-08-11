export function ObjectiveRow({ label, current, required }: { label: string; current: number; required: number }) {
  const pct = Math.min(100, (current / required) * 100);
  return (
    <div className="mb-1">
      <div className="flex justify-between text-xs text-white/70 mb-0.5">
        <span>{label}</span>
        <span>
          {current}/{required}
        </span>
      </div>
      <div className="h-1.5 bg-white/10 rounded-full overflow-hidden">
        <div className="h-full bg-yellow-500 rounded-full transition-all" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
