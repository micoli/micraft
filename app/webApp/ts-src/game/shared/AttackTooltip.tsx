import { AttackMeta } from "../types";
import { damageTypeColor } from "../components/AttackPanel";
import { Stat } from "./Stat";
import { formatCooldown } from "./tooltipFormat";
import { TooltipShell } from "./TooltipShell";

export function AttackTooltip({ id, meta }: { id: string; meta: AttackMeta | null }) {
  if (!meta) return <TooltipShell>{id}</TooltipShell>;
  return (
    <TooltipShell>
      <div
        className="w-3 h-3 rounded-full mb-0.5"
        style={{ background: damageTypeColor(meta.damageType) }}
      />
      <div className="font-bold text-[11px] text-white">{id}</div>
      <div className="text-white/50 text-[9px]">
        {meta.damageType} · rang {meta.rank}
      </div>
      <Stat label="Dégâts" value={`${meta.weaponDice} (+${meta.power})`} />
      <Stat label="Cooldown" value={formatCooldown(meta.cooldownMs)} />
      {meta.manaCost > 0 && <Stat label="Mana" value={meta.manaCost} />}
      {meta.rageCost > 0 && <Stat label="Rage" value={meta.rageCost} />}
    </TooltipShell>
  );
}
