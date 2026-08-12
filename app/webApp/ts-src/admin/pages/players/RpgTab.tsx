import { BaseStats, PlayerFile } from "../../api";
import { useT } from "../../i18n";
import { useState } from "react";
import { SaveButton } from "../../../primitives/SaveButton";
import { StatRow } from "./StatRow";

const CHARACTER_CLASSES = ["WARRIOR", "MAGE", "RANGER", "ROGUE", "CLERIC"];
function computeDerived(s: BaseStats, level: number) {
  const f = (v: number) => Math.floor((v - 10) / 2);
  return {
    maxHp: Math.max(1, Math.floor(f(s.con) * level + 10)),
    maxMana: s.wis * 5,
    meleeDmg: f(s.str),
    rangedDmg: f(s.dex),
    spellDmg: f(s.intel),
    critChancePct: (5 + s.dex * 0.2).toFixed(1),
    dodgePct: Math.min(s.dex * 2.5, 75).toFixed(1),
    magicResistPct: Math.max((s.wis - 10) * 2, 0).toFixed(1),
    armorClass: 10 + f(s.dex),
    hpRegenPerSec: (s.con / 10).toFixed(2),
    manaRegenPerSec: (s.wis / 20).toFixed(2),
    maxTokens: Math.floor(level / 4) + 1,
  };
}

export function RpgTab({
  file,
  onSave,
}: {
  file: PlayerFile;
  onSave: (rpg: Record<string, unknown>) => Promise<void>;
}) {
  const t = useT();
  const cd = file.state.characterData!;
  const [cls, setCls] = useState(cd.characterClass);
  const [stats, setStats] = useState({ ...cd.baseStats });
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const save = async () => {
    setSaving(true);
    await onSave({ characterClass: cls, ...stats });
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  const derived = computeDerived(stats, cd.level);

  return (
    <div className="p-5 space-y-5">
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.class")}</p>
        <div className="flex flex-wrap gap-2">
          {CHARACTER_CLASSES.map((c) => (
            <button
              key={c}
              onClick={() => setCls(c)}
              className={`px-3 py-1 rounded-lg text-xs font-medium border transition-colors ${
                cls === c
                  ? "bg-[#3C50E0]/20 border-[#3C50E0] text-[#818CF8]"
                  : "border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0]/50 hover:text-white"
              }`}
            >
              {c}
            </button>
          ))}
        </div>
      </div>
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.baseStats")}</p>
        <StatRow name="str" label={t("players.strength")} stats={stats} setStats={setStats} />
        <StatRow name="dex" label={t("players.dexterity")} stats={stats} setStats={setStats} />
        <StatRow name="intel" label={t("players.intellect")} stats={stats} setStats={setStats} />
        <StatRow name="wis" label={t("players.wisdom")} stats={stats} setStats={setStats} />
        <StatRow name="con" label={t("players.constitution")} stats={stats} setStats={setStats} />
        <StatRow name="cha" label={t("players.charisma")} stats={stats} setStats={setStats} />
      </div>
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">
          {t("players.derivedStats")}{" "}
          <span className="text-[#4A5568] normal-case font-normal">{t("players.derivedLevel", cd.level)}</span>
        </p>
        <div className="grid grid-cols-2 gap-x-4">
          {[
            [t("players.maxHp"), derived.maxHp],
            [t("players.maxMana"), derived.maxMana],
            [t("players.meleeDmg"), `+${derived.meleeDmg}`],
            [t("players.rangedDmg"), `+${derived.rangedDmg}`],
            [t("players.spellDmg"), `+${derived.spellDmg}`],
            [t("players.critChance"), `${derived.critChancePct}%`],
            [t("players.dodge"), `${derived.dodgePct}%`],
            [t("players.magicResist"), `${derived.magicResistPct}%`],
            [t("players.armorClass"), derived.armorClass],
            [t("players.hpRegen"), derived.hpRegenPerSec],
            [t("players.manaRegen"), derived.manaRegenPerSec],
            [t("players.maxTokens"), derived.maxTokens],
          ].map(([label, value]) => (
            <div key={String(label)} className="flex justify-between py-1 border-b border-[#2E3A4E] text-xs">
              <span className="text-[#8A99AF]">{label}</span>
              <span className="text-white tabular-nums font-medium">{value}</span>
            </div>
          ))}
        </div>
      </div>
      <p className="text-xs text-[#4A5568]">
        {t("players.hpManaSummary", cd.currentHp, derived.maxHp, cd.currentMana, derived.maxMana)}
      </p>
      <SaveButton saving={saving} saved={saved} onClick={save} />
    </div>
  );
}
