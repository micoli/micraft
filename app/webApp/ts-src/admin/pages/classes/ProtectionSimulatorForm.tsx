import { BaseStats } from "../../apiTypes";
import { useT } from "../../i18n";
import { RawNumberInput } from "../../../primitives/RawNumberInput";

const STAT_FIELDS: (keyof BaseStats)[] = ["str", "dex", "intel", "wis", "con", "cha"];

const inputClass =
  "w-16 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]";

/**
 * Simulator inputs (spec: Protection Spells §Admin UI) — Level, Danger tier, the simulated
 * Character's Base stats (default 10 everywhere) and an equipment AC bonus (default 0).
 */
export function ProtectionSimulatorForm({
  level,
  dangerTier,
  baseStats,
  equipmentAcBonus,
  running,
  onLevelChange,
  onDangerTierChange,
  onBaseStatsChange,
  onEquipmentAcBonusChange,
  onRun,
}: {
  level: number;
  dangerTier: number;
  baseStats: BaseStats;
  equipmentAcBonus: number;
  running: boolean;
  onLevelChange: (level: number) => void;
  onDangerTierChange: (tier: number) => void;
  onBaseStatsChange: (stats: BaseStats) => void;
  onEquipmentAcBonusChange: (bonus: number) => void;
  onRun: () => void;
}) {
  const t = useT();
  return (
    <div className="flex flex-wrap items-end gap-4">
      <div>
        <label className="block text-[10px] uppercase tracking-wider text-[#8A99AF] mb-1">
          {t("classes.simulatorLevel")}
        </label>
        <RawNumberInput
          min={1}
          max={30}
          value={level}
          onChange={(e) => onLevelChange(Math.min(30, Math.max(1, Number(e.target.value) || 1)))}
          className={inputClass}
        />
      </div>
      <div>
        <label className="block text-[10px] uppercase tracking-wider text-[#8A99AF] mb-1">
          {t("classes.simulatorDangerTier")}
        </label>
        <select value={dangerTier} onChange={(e) => onDangerTierChange(Number(e.target.value))} className={inputClass}>
          {[1, 2, 3, 4, 5].map((tier) => (
            <option key={tier} value={tier}>
              {tier}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block text-[10px] uppercase tracking-wider text-[#8A99AF] mb-1">
          {t("classes.simulatorBaseStats")}
        </label>
        <div className="flex gap-1">
          {STAT_FIELDS.map((stat) => (
            <RawNumberInput
              key={stat}
              min={1}
              value={baseStats[stat]}
              onChange={(e) => onBaseStatsChange({ ...baseStats, [stat]: Math.max(1, Number(e.target.value) || 1) })}
              title={stat.toUpperCase()}
              className={`${inputClass} w-12`}
            />
          ))}
        </div>
      </div>
      <div>
        <label className="block text-[10px] uppercase tracking-wider text-[#8A99AF] mb-1">
          {t("classes.simulatorEquipmentAc")}
        </label>
        <RawNumberInput
          value={equipmentAcBonus}
          onChange={(e) => onEquipmentAcBonusChange(Number(e.target.value) || 0)}
          className={inputClass}
        />
      </div>
      <button
        onClick={onRun}
        disabled={running}
        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
      >
        {t("classes.simulatorRun")}
      </button>
    </div>
  );
}
