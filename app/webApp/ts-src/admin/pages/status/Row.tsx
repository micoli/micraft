export function Row({ label, value, accent }: { label: string; value: string | number; accent?: boolean }) {
  return (
    <div className="flex justify-between items-center py-1.5 border-b border-[#2E3A4E] last:border-0 text-sm">
      <span className="text-[#8A99AF]">{label}</span>
      <span className={accent ? "text-[#3C50E0] font-semibold" : "text-white tabular-nums"}>{value}</span>
    </div>
  );
}
