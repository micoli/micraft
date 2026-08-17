import { cn } from "../../../primitives/cn";

export function BaseStatRow({ label, base, effective }: { label: string; base: number; effective: number }) {
  const bonus = effective - base;
  return (
    <div className="flex justify-between items-center py-1 border-b border-white/5">
      <span className="text-white/50 text-xs">{label}</span>
      <span className="text-xs font-mono">
        <span className="text-white/60">{base}</span>
        {bonus !== 0 && (
          <span className={cn("ml-1", bonus > 0 ? "text-green-400" : "text-red-400")}>
            {bonus > 0 ? `+${bonus}` : `${bonus}`}
          </span>
        )}
        {bonus !== 0 && <span className="text-white ml-1">= {effective}</span>}
        {bonus === 0 && <span className="text-white ml-1">{base}</span>}
      </span>
    </div>
  );
}
