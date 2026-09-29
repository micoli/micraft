import { ProtectionSimulationDto } from "../../apiTypes";
import { useT } from "../../i18n";

function pct(value: number): string {
  return `${value.toFixed(1)}%`;
}

function attacksSurvived(value: number | null | undefined, unlimitedLabel: string): string {
  return value == null ? unlimitedLabel : value.toFixed(1);
}

/**
 * Per-Class survival report (spec: Protection Spells §Admin UI): the active Protection Rank, hit
 * chance and mean damage / attacks survived with and without it, against the tier's real NPC
 * Abilities' physical/magical share.
 */
export function ProtectionSimulatorResults({ report }: { report: ProtectionSimulationDto }) {
  const t = useT();

  if (report.abilityCount === 0) {
    return <p className="text-xs text-[#8A99AF] italic mt-4">{t("classes.simulatorNoAbilities")}</p>;
  }

  return (
    <div className="mt-4">
      <p className="text-xs text-[#8A99AF] mb-3">
        {report.abilityCount} {t("classes.simulatorAbilitySummary")} —{" "}
        <span className="text-white">{pct(report.physicalSharePct)}</span> {t("classes.simulatorPhysical")},{" "}
        <span className="text-white">{pct(report.magicalSharePct)}</span> {t("classes.simulatorMagical")}
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="text-left">
              <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-[#8A99AF] text-[11px] uppercase tracking-wider font-semibold w-32">
                {t("classes.classes")}
              </th>
              <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-[#8A99AF] text-[11px] uppercase tracking-wider font-semibold w-20">
                {t("classes.simulatorProtectionRank")}
              </th>
              <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-white text-[11px] uppercase tracking-wider font-semibold">
                {t("classes.simulatorHitChance")}
              </th>
              <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-white text-[11px] uppercase tracking-wider font-semibold">
                {t("classes.simulatorMeanDamage")}
              </th>
              <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-white text-[11px] uppercase tracking-wider font-semibold">
                {t("classes.simulatorAttacksSurvived")}
              </th>
            </tr>
          </thead>
          <tbody>
            {report.classes.map((cls, i) => (
              <tr key={cls.className} className={i % 2 === 0 ? "bg-[#0E1726]" : "bg-[#111827]"}>
                <td className="border border-[#2E3A4E] px-3 py-2 font-semibold text-white text-xs">{cls.className}</td>
                <td className="border border-[#2E3A4E] px-3 py-2 font-mono text-xs text-center">
                  {cls.protectionRank ?? "—"}
                </td>
                <td className="border border-[#2E3A4E] px-3 py-2 font-mono text-xs">
                  {t("classes.simulatorWithout")} {pct(cls.hitChanceWithoutPct)} · {t("classes.simulatorWith")}{" "}
                  <span className="text-emerald-400">{pct(cls.hitChanceWithPct)}</span>
                </td>
                <td className="border border-[#2E3A4E] px-3 py-2 font-mono text-xs">
                  {t("classes.simulatorWithout")} {cls.meanDamageWithoutPerAttack.toFixed(1)} ·{" "}
                  {t("classes.simulatorWith")}{" "}
                  <span className="text-emerald-400">{cls.meanDamageWithPerAttack.toFixed(1)}</span>
                </td>
                <td className="border border-[#2E3A4E] px-3 py-2 font-mono text-xs">
                  {t("classes.simulatorWithout")}{" "}
                  {attacksSurvived(cls.meanAttacksSurvivedWithout, t("classes.simulatorUnlimited"))} ·{" "}
                  {t("classes.simulatorWith")}{" "}
                  <span className="text-emerald-400">
                    {attacksSurvived(cls.meanAttacksSurvivedWith, t("classes.simulatorUnlimited"))}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
