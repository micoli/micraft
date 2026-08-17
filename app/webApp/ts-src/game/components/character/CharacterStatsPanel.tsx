import { CharacterSyncData } from "../../types";
import { StatRow } from "./StatRow";
import { BaseStatRow } from "./BaseStatRow";

const CLASS_LABELS: Record<string, string> = {
  WARRIOR: "Warrior",
  MAGE: "Mage",
  RANGER: "Ranger",
  ROGUE: "Rogue",
  CLERIC: "Cleric",
};

export function CharacterStatsPanel({ data }: { data: CharacterSyncData }) {
  const { character: c, derived: d, effectiveBaseStats: e } = data;
  return (
    <div className="flex gap-6">
      <div className="flex-1">
        <div className="text-blue-300 text-xs font-mono mb-3 tracking-widest">IDENTITY</div>
        <StatRow label="Name" value={c.name} />
        <StatRow label="Class" value={CLASS_LABELS[c.characterClass] ?? c.characterClass} />
        <StatRow label="Level" value={c.level} />
        <StatRow label="XP" value={c.xp.toLocaleString()} />
        <StatRow label="HP" value={`${c.currentHp} / ${d.maxHp}`} />
        <StatRow label="Mana" value={`${c.currentMana} / ${d.maxMana}`} />

        <div className="text-blue-300 text-xs font-mono mt-5 mb-3 tracking-widest">BASE STATS</div>
        <BaseStatRow label="STR" base={c.baseStats.str} effective={e.str} />
        <BaseStatRow label="DEX" base={c.baseStats.dex} effective={e.dex} />
        <BaseStatRow label="INT" base={c.baseStats.intel} effective={e.intel} />
        <BaseStatRow label="WIS" base={c.baseStats.wis} effective={e.wis} />
        <BaseStatRow label="CON" base={c.baseStats.con} effective={e.con} />
        <BaseStatRow label="CHA" base={c.baseStats.cha} effective={e.cha} />
      </div>
      <div className="flex-1">
        <div className="text-blue-300 text-xs font-mono mb-3 tracking-widest">COMBAT</div>
        <StatRow label="Melee dmg" value={`+${d.meleeDmg}`} />
        <StatRow label="Ranged dmg" value={`+${d.rangedDmg}`} />
        <StatRow label="Spell dmg" value={`+${d.spellDmg}`} />
        <StatRow label="Crit chance" value={`${d.critChancePct.toFixed(1)}%`} />
        <StatRow label="Crit mult" value={`×${d.critDmgMult}`} />
        <StatRow label="Dodge" value={`${d.dodgePct.toFixed(1)}%`} />
        <StatRow label="Magic resist" value={`${d.magicResistPct.toFixed(0)}%`} />
        <StatRow label="Initiative" value={d.initiative >= 0 ? `+${d.initiative}` : `${d.initiative}`} />

        <div className="text-blue-300 text-xs font-mono mt-5 mb-3 tracking-widest">REGEN</div>
        <StatRow label="HP/s" value={d.hpRegenPerSec.toFixed(1)} />
        <StatRow label="Mana/s" value={d.manaRegenPerSec.toFixed(1)} />
      </div>
    </div>
  );
}
