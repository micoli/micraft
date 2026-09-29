import { useState } from "react";
import { getApiAdminProtectionsSimulate } from "../../../generated/api/requests";
import { BaseStats, ProtectionSimulationDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { ProtectionSimulatorForm } from "./ProtectionSimulatorForm";
import { ProtectionSimulatorResults } from "./ProtectionSimulatorResults";

const DEFAULT_BASE_STATS: BaseStats = { str: 10, dex: 10, intel: 10, wis: 10, con: 10, cha: 10 };

/**
 * Survival simulator (spec: Protection Spells §Admin API/UI) — an admin picks a Level and Danger
 * tier, optionally edits the simulated Character's Base stats and equipment AC bonus, and gets a
 * per-Class survival report computed server-side with the real combat formulas.
 */
export function ProtectionSimulator() {
  const t = useT();
  const [level, setLevel] = useState(1);
  const [dangerTier, setDangerTier] = useState(1);
  const [baseStats, setBaseStats] = useState<BaseStats>(DEFAULT_BASE_STATS);
  const [equipmentAcBonus, setEquipmentAcBonus] = useState(0);
  const [report, setReport] = useState<ProtectionSimulationDto | null>(null);
  const [running, setRunning] = useState(false);
  const [failed, setFailed] = useState(false);

  const run = async () => {
    setRunning(true);
    setFailed(false);
    try {
      const { data } = await getApiAdminProtectionsSimulate({
        query: { level, dangerTier, ...baseStats, equipmentAcBonus },
        throwOnError: true,
      });
      setReport(data);
    } catch {
      setFailed(true);
    }
    setRunning(false);
  };

  return (
    <div className="p-5 border-t border-[#2E3A4E]">
      <p className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-4">
        {t("classes.simulator")}
      </p>
      <ProtectionSimulatorForm
        level={level}
        dangerTier={dangerTier}
        baseStats={baseStats}
        equipmentAcBonus={equipmentAcBonus}
        running={running}
        onLevelChange={setLevel}
        onDangerTierChange={setDangerTier}
        onBaseStatsChange={setBaseStats}
        onEquipmentAcBonusChange={setEquipmentAcBonus}
        onRun={run}
      />
      {failed && <p className="text-xs text-red-400 mt-3">{t("classes.simulatorFailed")}</p>}
      {report && <ProtectionSimulatorResults report={report} />}
    </div>
  );
}
