import { cn } from "../../../primitives/cn";

export function DerivedStatRow({
  label,
  base,
  effective,
  decimals = 0,
  suffix = "",
}: {
  label: string;
  base: number;
  effective: number;
  decimals?: number;
  suffix?: string;
}) {
  const fmt = (v: number) => v.toFixed(decimals) + suffix;
  const bonus = Number((effective - base).toFixed(decimals));
  return (
    <div className="flex justify-between items-center py-1 border-b border-white/5">
      <span className="text-white/50 text-xs">{label}</span>
      <span className="text-xs font-mono">
        <span className="text-white/60">{fmt(base)}</span>
        {bonus !== 0 && (
          <span className={cn("ml-1", bonus > 0 ? "text-green-400" : "text-red-400")}>
            {bonus > 0 ? `+${fmt(bonus)}` : `-${fmt(-bonus)}`}
          </span>
        )}
        {bonus !== 0 && <span className="text-white ml-1">= {fmt(effective)}</span>}
        {bonus === 0 && <span className="text-white ml-1">{fmt(base)}</span>}
      </span>
    </div>
  );
}
