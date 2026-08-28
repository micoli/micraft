import { BaseStats } from "../../apiTypes";
import { Dispatch, SetStateAction } from "react";
import { cn } from "../../../primitives/cn";
import { RawNumberInput } from "../../../primitives/RawNumberInput";

export const StatRow = ({
  stats,
  name,
  label,
  setStats,
  bonus = 0,
}: {
  label: string;
  stats: BaseStats;
  name: keyof typeof stats;
  setStats: Dispatch<SetStateAction<BaseStats>>;
  bonus?: number;
}) => (
  <div className="flex items-center justify-between gap-2 py-1 border-b border-[#2E3A4E] last:border-0">
    <span className="text-xs text-[#8A99AF]">{label}</span>
    <div className="flex items-center gap-1 shrink-0">
      <RawNumberInput
        min={1}
        max={20}
        value={stats[name]}
        onChange={(e) => setStats((p) => ({ ...p, [name]: Number(e.target.value) }))}
        className="w-14 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white text-right tabular-nums focus:outline-none focus:border-[#3C50E0]"
      />
      <span
        className={cn(
          "w-7 shrink-0 text-xs tabular-nums text-right whitespace-nowrap",
          bonus > 0 ? "text-green-400" : bonus < 0 ? "text-red-400" : "text-[#4A5568]",
        )}
      >
        {bonus > 0 ? `+${bonus}` : bonus}
      </span>
      <span className="w-9 shrink-0 text-xs text-white tabular-nums text-right whitespace-nowrap">
        = {stats[name] + bonus}
      </span>
    </div>
  </div>
);
