export function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="text-white/60 text-[10px]">
      <span className="text-white/40">{label}</span> {value}
    </div>
  );
}
