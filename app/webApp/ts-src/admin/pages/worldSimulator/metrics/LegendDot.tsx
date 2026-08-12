export function LegendDot({ color, label, value }: { color: string; label: string; value?: number }) {
  // Faded when the newest slice has nothing for this entry: a busy arena carries a dozen legend
  // entries and the ones still moving should be the ones that read.
  const idle = value === 0;
  return (
    <span className={"flex items-center gap-1 text-[10px] text-[#8A99AF]" + (idle ? " opacity-20" : "")}>
      <span className="inline-block h-2 w-2 rounded-sm" style={{ background: color }} />
      {label}
      {value !== undefined && <span className="text-white">{value}</span>}
    </span>
  );
}
