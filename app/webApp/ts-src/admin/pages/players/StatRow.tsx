import { BaseStats } from "../../api";
import { Dispatch, SetStateAction } from "react";

export const StatRow = ({
  stats,
  name,
  label,
  setStats,
}: {
  label: string;
  stats: BaseStats;
  name: keyof typeof stats;
  setStats: Dispatch<SetStateAction<BaseStats>>;
}) => (
  // const statRow = (name: keyof typeof stats, label: string) => (
  <div className="flex items-center gap-3 py-2 border-b border-[#2E3A4E] last:border-0">
    <span className="text-xs text-[#8A99AF] w-24">{label}</span>
    <input
      type="range"
      min={1}
      max={20}
      value={stats[name]}
      onChange={(e) => setStats((p) => ({ ...p, [name]: Number(e.target.value) }))}
      className="flex-1 accent-[#3C50E0]"
    />
    <span className="text-sm text-[#3C50E0] w-6 text-right tabular-nums font-semibold">{stats[name]}</span>
  </div>
);
