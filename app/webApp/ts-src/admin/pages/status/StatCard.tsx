import { Svg } from "./Svg";

export function StatCard({
  label,
  value,
  sub,
  icon,
  color,
}: {
  label: string;
  value: string | number;
  sub?: string;
  icon: string;
  color: string; // tailwind bg class
}) {
  return (
    <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] p-5 flex items-start justify-between">
      <div>
        <p className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-1">{label}</p>
        <p className="text-3xl font-bold text-white tabular-nums leading-none">{value}</p>
        {sub && <p className="text-xs text-[#8A99AF] mt-1.5">{sub}</p>}
      </div>
      <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${color}`}>
        <Svg d={icon} size={20} />
      </div>
    </div>
  );
}
