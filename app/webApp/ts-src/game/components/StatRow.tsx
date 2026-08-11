export function StatRow({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex justify-between items-center py-1 border-b border-white/5">
      <span className="text-white/50 text-xs">{label}</span>
      <span className="text-white text-xs font-mono">{value}</span>
    </div>
  );
}
