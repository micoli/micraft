import { useEffect, useState } from "react";
import { api, ClassDefinitionEntry } from "../../api";
import { useT, type TranslationKey } from "../../i18n";
import { ProgressionCell } from "./ProgressionCell";

// ── Types ─────────────────────────────────────────────────────────────────────

export interface SkillProgression {
  playerLevel: number;
  skillLevel: number;
}

interface SkillRow {
  name: string;
  type: "attack" | "spell";
  byClass: Record<string, SkillProgression[]>;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function buildMatrix(
  classes: Record<string, ClassDefinitionEntry>,
  allAttacks: string[],
  allSpells: string[],
): { skills: SkillRow[]; classNames: string[] } {
  const classNames = Object.keys(classes).sort();
  const skillMap = new Map<string, SkillRow>();

  // Seed with every known skill so unassociated ones appear as empty rows
  for (const name of allAttacks) {
    skillMap.set(name, { name, type: "attack", byClass: {} });
  }
  for (const name of allSpells) {
    skillMap.set(name, { name, type: "spell", byClass: {} });
  }

  for (const className of classNames) {
    const def = classes[className];
    const playerLevels = Object.entries(def.levels)
      .map(([lvl, entry]) => ({ playerLevel: parseInt(lvl, 10), entry }))
      .sort((a, b) => a.playerLevel - b.playerLevel);

    for (const { playerLevel, entry } of playerLevels) {
      for (const atk of entry.attacks) {
        const key = atk.attack;
        if (!skillMap.has(key)) skillMap.set(key, { name: key, type: "attack", byClass: {} });
        const row = skillMap.get(key)!;
        if (!row.byClass[className]) row.byClass[className] = [];
        const prog = row.byClass[className];
        const last = prog[prog.length - 1];
        if (!last || last.skillLevel !== atk.level) {
          prog.push({ playerLevel, skillLevel: atk.level });
        }
      }
      for (const spell of entry.spells) {
        if (!skillMap.has(spell)) skillMap.set(spell, { name: spell, type: "spell", byClass: {} });
        const row = skillMap.get(spell)!;
        if (!row.byClass[className]) row.byClass[className] = [];
        if (row.byClass[className].length === 0) {
          row.byClass[className].push({ playerLevel, skillLevel: 1 });
        }
      }
    }
  }

  const skills = Array.from(skillMap.values()).sort((a, b) => {
    if (a.type !== b.type) return a.type === "attack" ? -1 : 1;
    return a.name.localeCompare(b.name);
  });

  return { skills, classNames };
}

export function ClassesPage() {
  const t = useT();
  const [classes, setClasses] = useState<Record<string, ClassDefinitionEntry> | null>(null);
  const [allSkills, setAllSkills] = useState<{ attacks: string[]; spells: string[] } | null>(null);
  const [errorKey, setErrorKey] = useState<TranslationKey | null>(null);

  useEffect(() => {
    Promise.all([api.classes.get(), api.classes.skills()])
      .then(([c, s]) => {
        setClasses(c);
        setAllSkills(s);
      })
      .catch(() => setErrorKey("classes.failedToLoad"));
  }, []);

  if (errorKey) {
    return <p className="text-red-400 text-sm">{t(errorKey)}</p>;
  }

  if (!classes || !allSkills) {
    return (
      <div className="flex items-center justify-center h-40 text-[#8A99AF] text-sm animate-pulse">
        {t("common.loading")}
      </div>
    );
  }

  const { skills, classNames } = buildMatrix(classes, allSkills.attacks, allSkills.spells);

  return (
    <div className="space-y-6">
      <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] overflow-hidden">
        {/* Class summary cards */}
        <div className="p-5 border-b border-[#2E3A4E]">
          <p className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-4">
            {t("classes.classes")}
          </p>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {classNames.map((name) => {
              const def = classes[name];
              return (
                <div key={name} className="rounded-lg border border-[#2E3A4E] bg-[#0E1726] px-4 py-3">
                  <p className="text-white font-semibold text-sm">{name}</p>
                  <p className="text-[#8A99AF] text-[10px] mt-1">{def.classResource}</p>
                  <div className="mt-2 grid grid-cols-3 gap-x-2 gap-y-0.5 text-[10px] text-[#8A99AF]">
                    {(
                      [
                        ["STR", def.strBonus],
                        ["DEX", def.dexBonus],
                        ["INT", def.intelBonus],
                        ["WIS", def.wisBonus],
                        ["CON", def.conBonus],
                        ["CHA", def.chaBonus],
                      ] as [string, number][]
                    ).map(([stat, val]) => (
                      <span key={stat}>
                        <span className={val > 0 ? "text-emerald-400" : ""}>{stat}</span>
                        {val > 0 && <span className="text-emerald-400 ml-0.5">+{val}</span>}
                      </span>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Progression matrix */}
        <div className="p-5">
          <p className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-4">
            {t("classes.skillProgression")}
          </p>
          <div className="overflow-x-auto">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="text-left">
                  <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-[#8A99AF] text-[11px] uppercase tracking-wider font-semibold w-36">
                    {t("classes.skill")}
                  </th>
                  <th className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-[#8A99AF] text-[11px] uppercase tracking-wider font-semibold w-16 text-center">
                    {t("classes.type")}
                  </th>
                  {classNames.map((name) => (
                    <th
                      key={name}
                      className="border border-[#2E3A4E] bg-[#0E1726] px-3 py-2 text-white text-[11px] uppercase tracking-wider font-semibold"
                    >
                      {name}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {skills.map((skill, i) => (
                  <tr key={skill.name} className={i % 2 === 0 ? "bg-[#0E1726]" : "bg-[#111827]"}>
                    <td className="border border-[#2E3A4E] px-3 py-2 font-mono text-xs">
                      <span className={Object.keys(skill.byClass).length === 0 ? "text-[#8A99AF]" : "text-white"}>
                        {skill.name}
                      </span>
                      {Object.keys(skill.byClass).length === 0 && (
                        <span className="ml-2 text-[9px] text-[#8A99AF] italic">{t("classes.unassigned")}</span>
                      )}
                    </td>
                    <td className="border border-[#2E3A4E] px-3 py-2 text-center">
                      <span
                        className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${
                          skill.type === "attack" ? "bg-red-900/40 text-red-300" : "bg-purple-900/40 text-purple-300"
                        }`}
                      >
                        {skill.type}
                      </span>
                    </td>
                    {classNames.map((className) => (
                      <ProgressionCell key={className} progression={skill.byClass[className]} />
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-3 text-[10px] text-[#8A99AF]">
            <span className="text-[#3C50E0] font-semibold">Lv</span> {t("classes.legendLevel")} &nbsp;·&nbsp;
            <span className="text-emerald-400">sk</span> {t("classes.legendSkill")}
          </p>
        </div>
      </div>
    </div>
  );
}
