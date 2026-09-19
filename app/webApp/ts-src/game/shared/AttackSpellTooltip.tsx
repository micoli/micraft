import { AttackMeta, SpellMeta } from "../types";
import { damageTypeColor } from "../components/AttackPanel";

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="text-white/60 text-[10px]">
      <span className="text-white/40">{label}</span> {value}
    </div>
  );
}

function formatCooldown(ms: number): string {
  return `${(ms / 1000).toFixed(1)}s`;
}

function TooltipShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 z-[9999] bg-black/90 border border-white/30 rounded-md px-3 py-2 text-white font-mono text-xs whitespace-nowrap pointer-events-none flex flex-col items-center gap-0.5 min-w-[130px]">
      {children}
    </div>
  );
}

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

export function MacroTooltip({ id, icon, script, keybind }: { id: string; icon?: string; script?: string; keybind?: string }) {
  return (
    <TooltipShell>
      <div className="font-bold text-[11px] text-white">
        {icon ?? "⚡"} {id}
      </div>
      {keybind && (
        <div className="text-white/50 text-[9px]">
          <span className="text-white/40">Touche</span> {keybind}
        </div>
      )}
      {script && (
        <div className="text-white/60 text-[9px] whitespace-pre-wrap break-all max-w-[220px] mt-0.5 text-left">
          {script}
        </div>
      )}
    </TooltipShell>
  );
}
