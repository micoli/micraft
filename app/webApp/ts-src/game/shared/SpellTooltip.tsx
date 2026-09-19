import { SpellMeta } from "../types";
import { Stat } from "./Stat";
import { formatCooldown } from "./tooltipFormat";
import { TooltipShell } from "./TooltipShell";

export function SpellTooltip({ id, meta, isLocked }: { id: string; meta: SpellMeta | null; isLocked?: boolean }) {
  if (!meta) return <TooltipShell>{id}</TooltipShell>;
  return (
    <TooltipShell>
      <div className="font-bold text-[11px] text-white">{id}</div>
      <div className="text-white/50 text-[9px]">
        {meta.type} · rang {meta.rank}
      </div>
      {isLocked && <div className="text-red-400 text-[9px]">Niveau requis trop bas</div>}
      <Stat label="Puissance" value={meta.power} />
      <Stat label="Cooldown" value={formatCooldown(meta.cooldownMs)} />
      {meta.maxRange > 0 && <Stat label="Portée" value={meta.maxRange} />}
      {meta.aoeRadius > 0 && <Stat label="Rayon" value={meta.aoeRadius} />}
      {meta.manaCost > 0 && <Stat label="Mana" value={meta.manaCost} />}
      {meta.rageCost > 0 && <Stat label="Rage" value={meta.rageCost} />}
      {meta.tokenCost > 0 && <Stat label="Token" value={meta.tokenCost} />}
    </TooltipShell>
  );
}
