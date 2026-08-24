import { BaseStats, PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { useEffect, useState } from "react";
import { SaveButton } from "../../../primitives/SaveButton";
import { StatRow } from "./StatRow";
import { getApiArmors, getApiWeapons, getApiTools } from "../../../generated/api/requests";
import { EquipmentTab, type EquipmentPayload } from "./EquipmentTab";
import { CharacterStatsPanel } from "../../../game/components/character/CharacterStatsPanel";
import type { CharacterSyncData, DerivedStats } from "../../../game/types";

const CHARACTER_CLASSES = ["WARRIOR", "MAGE", "RANGER", "ROGUE", "CLERIC"] as const;

interface StatBonusDto {
  str?: number;
  dex?: number;
  intel?: number;
  wis?: number;
  con?: number;
  cha?: number;
  acBonus?: number;
}
type EquipmentDef = { statBonus?: StatBonusDto };
const ZERO_BONUS: Required<StatBonusDto> = { str: 0, dex: 0, intel: 0, wis: 0, con: 0, cha: 0, acBonus: 0 };

function sumBonus(defs: Record<string, EquipmentDef>, names: (string | null | undefined)[]) {
  return names.reduce((acc, n) => {
    const b = (n && defs[n]?.statBonus) || undefined;
    if (!b) return acc;
    return {
      str: acc.str + (b.str ?? 0),
      dex: acc.dex + (b.dex ?? 0),
      intel: acc.intel + (b.intel ?? 0),
      wis: acc.wis + (b.wis ?? 0),
      con: acc.con + (b.con ?? 0),
      cha: acc.cha + (b.cha ?? 0),
      acBonus: acc.acBonus + (b.acBonus ?? 0),
    };
  }, ZERO_BONUS);
}

function computeDerived(s: BaseStats, level: number): DerivedStats {
  const f = (v: number) => Math.floor((v - 10) / 2);
  return {
    maxHp: Math.max(1, Math.floor(f(s.con) * level + 10)),
    maxMana: s.wis * 5,
    meleeDmg: f(s.str),
    rangedDmg: f(s.dex),
    spellDmg: f(s.intel),
    critChancePct: 5 + s.dex * 0.2,
    critDmgMult: 2,
    dodgePct: Math.min(s.dex * 2.5, 75),
    magicResistPct: Math.max((s.wis - 10) * 2, 0),
    initiative: f(s.dex),
    hpRegenPerSec: s.con / 10,
    manaRegenPerSec: s.wis / 20,
  };
}

export function RpgTab({
  file,
  onSave,
  onSaveEquipment,
  onGive,
}: {
  file: PlayerFile;
  onSave: (rpg: Record<string, unknown>) => Promise<void>;
  onSaveEquipment: (payload: EquipmentPayload) => Promise<void>;
  onGive: (name: string, count: number) => Promise<void>;
}) {
  const t = useT();
  const cd = file.state.characterData!;
  const [cls, setCls] = useState(cd.characterClass);
  const [stats, setStats] = useState({ ...cd.baseStats });
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [armorDefs, setArmorDefs] = useState<Record<string, EquipmentDef>>({});
  const [weaponDefs, setWeaponDefs] = useState<Record<string, EquipmentDef>>({});
  const [toolDefs, setToolDefs] = useState<Record<string, EquipmentDef>>({});

  useEffect(() => {
    Promise.all([
      getApiArmors({ throwOnError: true }).then((r) => r.data),
      getApiWeapons({ throwOnError: true }).then((r) => r.data),
      getApiTools({ throwOnError: true }).then((r) => r.data),
    ]).then(([a, w, tl]) => {
      setArmorDefs(a as Record<string, EquipmentDef>);
      setWeaponDefs(w as Record<string, EquipmentDef>);
      setToolDefs(tl as Record<string, EquipmentDef>);
    });
  }, []);

  const save = async () => {
    setSaving(true);
    await onSave({ characterClass: cls, ...stats });
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  const handItems = [file.state.rightHandItem, file.state.leftHandItem];
  const armorBonus = sumBonus(armorDefs, file.state.armors);
  const weaponBonus = sumBonus(weaponDefs, handItems);
  const toolBonus = sumBonus(toolDefs, handItems);
  const effectiveStats: BaseStats = {
    str: stats.str + armorBonus.str + weaponBonus.str + toolBonus.str,
    dex: stats.dex + armorBonus.dex + weaponBonus.dex + toolBonus.dex,
    intel: stats.intel + armorBonus.intel + weaponBonus.intel + toolBonus.intel,
    wis: stats.wis + armorBonus.wis + weaponBonus.wis + toolBonus.wis,
    con: stats.con + armorBonus.con + weaponBonus.con + toolBonus.con,
    cha: stats.cha + armorBonus.cha + weaponBonus.cha + toolBonus.cha,
  };
  const derived = computeDerived(effectiveStats, cd.level);
  const characterSyncData: CharacterSyncData = {
    character: {
      id: cd.id,
      name: cd.name,
      characterClass: cls,
      level: cd.level,
      xp: cd.xp,
      baseStats: stats,
      currentHp: cd.currentHp,
      currentMana: cd.currentMana,
    },
    derived,
    effectiveBaseStats: effectiveStats,
  };

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
        <CharacterStatsPanel data={characterSyncData} />
      </div>
      <div>
        <button
          onClick={() => setEditOpen((o) => !o)}
          className="text-xs font-medium text-[#8A99AF] mb-2 hover:text-white transition-colors"
        >
          {editOpen ? "▾" : "▸"} {t("players.baseStatsEdit")}
        </button>
        {editOpen && (
          <>
            <div className="grid grid-cols-2 gap-x-4">
              <StatRow
                name="str"
                label={t("players.strength")}
                stats={stats}
                setStats={setStats}
                bonus={effectiveStats.str - stats.str}
              />
              <StatRow
                name="dex"
                label={t("players.dexterity")}
                stats={stats}
                setStats={setStats}
                bonus={effectiveStats.dex - stats.dex}
              />
              <StatRow
                name="intel"
                label={t("players.intellect")}
                stats={stats}
                setStats={setStats}
                bonus={effectiveStats.intel - stats.intel}
              />
              <StatRow
                name="wis"
                label={t("players.wisdom")}
                stats={stats}
                setStats={setStats}
                bonus={effectiveStats.wis - stats.wis}
              />
              <StatRow
                name="con"
                label={t("players.constitution")}
                stats={stats}
                setStats={setStats}
                bonus={effectiveStats.con - stats.con}
              />
              <StatRow
                name="cha"
                label={t("players.charisma")}
                stats={stats}
                setStats={setStats}
                bonus={effectiveStats.cha - stats.cha}
              />
            </div>
            <SaveButton saving={saving} saved={saved} onClick={save} />
          </>
        )}
      </div>
      <div className="pt-5 border-t border-[#2E3A4E]">
        <EquipmentTab file={file} onSave={onSaveEquipment} onGive={onGive} />
      </div>
    </div>
  );
}
