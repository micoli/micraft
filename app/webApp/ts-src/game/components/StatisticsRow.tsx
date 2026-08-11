export function StatisticsRow({ label, value }: { label: string; value: string | number }) {
  return (
    <div key={label} className="flex items-center gap-1.5">
      <span className="text-[11px] text-white/60 w-14 shrink-0 truncate">{label}</span>
      <span className="text-white/90 truncate">{value}</span>
    </div>
  );
}
