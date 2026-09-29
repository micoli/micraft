import { ClassProtectionDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { ProtectionRankCell } from "./ProtectionRankCell";

const RANKS = [1, 2, 3, 4, 5];

/**
 * Protections table (spec: Protection Spells, Admin API/UI) — each Class's Protection Spell and
 * its per-Rank values, read live from config (SkillsConfig/ClassesConfig). A Class with none shows
 * as such rather than being omitted.
 */
export function ProtectionsSection({
  protections,
  classNames,
}: {
  protections: Record<string, ClassProtectionDto>;
  classNames: string[];
}) {
  const t = useT();
  return (
    <div className="p-5 border-t border-[#2E3A4E]">
      <p className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-4">
        {t("classes.protections")}
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="text-left">
              <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-[#8A99AF] text-[11px] uppercase tracking-wider font-semibold w-36">
                {t("classes.classes")}
              </th>
              <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-[#8A99AF] text-[11px] uppercase tracking-wider font-semibold w-32">
                {t("classes.protectionSpell")}
              </th>
              {RANKS.map((rank) => (
                <th
                  key={rank}
                  className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-white text-[11px] uppercase tracking-wider font-semibold"
                >
                  {t("classes.protectionRank")} {rank}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {classNames.map((className, i) => {
              const protection = protections[className];
              const ranksByNumber = new Map(protection?.ranks.map((r) => [r.rank, r]));
              return (
                <tr key={className} className={i % 2 === 0 ? "bg-[#0E1726]" : "bg-[#111827]"}>
                  <td className="border border-[#2E3A4E] px-3 py-2 font-semibold text-white text-xs">{className}</td>
                  <td className="border border-[#2E3A4E] px-3 py-2 font-mono text-xs">
                    {protection?.spellId ? (
                      <span className="text-white">{protection.spellId}</span>
                    ) : (
                      <span className="text-[#8A99AF] italic">{t("classes.noProtection")}</span>
                    )}
                  </td>
                  {RANKS.map((rank) => (
                    <ProtectionRankCell key={rank} rank={ranksByNumber.get(rank)} />
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
