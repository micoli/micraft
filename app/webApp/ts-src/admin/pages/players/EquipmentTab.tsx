import { useEffect, useState } from "react";
import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { SaveButton } from "../../../primitives/SaveButton";
import { getApiArmors, getApiWeapons, getApiTools } from "../../../generated/api/requests";
import { EquipmentToggleList } from "./EquipmentToggleList";

export interface EquipmentPayload {
  ownedArmors: string[];
  ownedWeapons: string[];
  ownedTools: string[];
  armors: string[];
  rightHandItem: string;
  leftHandItem: string;
  dominantHand: string;
}

export function EquipmentTab({
  file,
  onSave,
  onGive,
}: {
  file: PlayerFile;
  onSave: (payload: EquipmentPayload) => Promise<void>;
  onGive: (name: string, count: number) => Promise<void>;
}) {
  const t = useT();
  const [armorDefs, setArmorDefs] = useState<Record<string, unknown>>({});
  const [weaponDefs, setWeaponDefs] = useState<Record<string, unknown>>({});
  const [toolDefs, setToolDefs] = useState<Record<string, unknown>>({});

  const [ownedArmors, setOwnedArmors] = useState<string[]>(file.state.ownedArmors ?? []);
  const [ownedWeapons, setOwnedWeapons] = useState<string[]>(file.state.ownedWeapons ?? []);
  const [ownedTools, setOwnedTools] = useState<string[]>(file.state.ownedTools ?? []);
  const [worn, setWorn] = useState<string[]>(file.state.armors ?? []);
  const [rightHandItem, setRightHandItem] = useState(file.state.rightHandItem ?? "");
  const [leftHandItem, setLeftHandItem] = useState(file.state.leftHandItem ?? "");
  const [dominantHand, setDominantHand] = useState<string>(file.state.dominantHand ?? "RIGHT");

  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const [giveName, setGiveName] = useState("");
  const [giveCount, setGiveCount] = useState(1);
  const [giving, setGiving] = useState(false);
  const [giveMsg, setGiveMsg] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      getApiArmors({ throwOnError: true }).then((r) => r.data),
      getApiWeapons({ throwOnError: true }).then((r) => r.data),
      getApiTools({ throwOnError: true }).then((r) => r.data),
    ]).then(([a, w, tl]) => {
      setArmorDefs(a as Record<string, unknown>);
      setWeaponDefs(w as Record<string, unknown>);
      setToolDefs(tl as Record<string, unknown>);
    });
  }, []);

  const toggle = (list: string[], setList: (v: string[]) => void, name: string) =>
    setList(list.includes(name) ? list.filter((n) => n !== name) : [...list, name]);

  const save = async () => {
    setSaving(true);
    await onSave({
      ownedArmors,
      ownedWeapons,
      ownedTools,
      armors: worn.filter((n) => ownedArmors.includes(n)),
      rightHandItem,
      leftHandItem,
      dominantHand,
    });
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  const give = async () => {
    const name = giveName.trim();
    if (!name) return;
    setGiving(true);
    setGiveMsg(null);
    try {
      await onGive(name, giveCount);
      setGiveMsg(t("players.giveDone"));
      if (name in armorDefs && !ownedArmors.includes(name)) setOwnedArmors((p) => [...p, name]);
      if (name in weaponDefs && !ownedWeapons.includes(name)) setOwnedWeapons((p) => [...p, name]);
      if (name in toolDefs && !ownedTools.includes(name)) setOwnedTools((p) => [...p, name]);
      setGiveName("");
    } catch {
      setGiveMsg(t("players.giveFailed"));
    }
    setGiving(false);
  };

  const handOptions = ["", ...ownedWeapons, ...ownedTools];
  const giveNames = [...Object.keys(armorDefs), ...Object.keys(weaponDefs), ...Object.keys(toolDefs)];

  return (
    <div className="p-5 space-y-5">
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.give")}</p>
        <div className="flex gap-2 items-center">
          <input
            value={giveName}
            onChange={(e) => setGiveName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && give()}
            placeholder={t("players.giveNamePlaceholder")}
            list="equipment-give-names"
            className="flex-1 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]"
          />
          <datalist id="equipment-give-names">
            {giveNames.map((n) => (
              <option key={n} value={n} />
            ))}
          </datalist>
          <input
            type="number"
            min={1}
            value={giveCount}
            onChange={(e) => setGiveCount(Math.max(1, Number(e.target.value) || 1))}
            className="w-16 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]"
          />
          <button
            onClick={give}
            disabled={giving || !giveName.trim()}
            className="px-3 py-1 rounded-lg text-xs font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
          >
            {t("players.giveButton")}
          </button>
        </div>
        {giveMsg && <p className="text-xs text-[#8A99AF] mt-1">{giveMsg}</p>}
      </div>

      <EquipmentToggleList
        title={t("players.ownedArmors")}
        names={[...ownedArmors].sort()}
        selected={ownedArmors}
        onToggle={(n) => toggle(ownedArmors, setOwnedArmors, n)}
      />
      <EquipmentToggleList
        title={t("players.wornArmors")}
        names={[...ownedArmors].sort()}
        selected={worn}
        onToggle={(n) => toggle(worn, setWorn, n)}
      />
      <EquipmentToggleList
        title={t("players.ownedWeapons")}
        names={[...ownedWeapons].sort()}
        selected={ownedWeapons}
        onToggle={(n) => toggle(ownedWeapons, setOwnedWeapons, n)}
      />
      <EquipmentToggleList
        title={t("players.ownedTools")}
        names={[...ownedTools].sort()}
        selected={ownedTools}
        onToggle={(n) => toggle(ownedTools, setOwnedTools, n)}
      />

      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.dominantHand")}</p>
        <div className="flex gap-2">
          {(["RIGHT", "LEFT"] as const).map((h) => (
            <button
              key={h}
              onClick={() => setDominantHand(h)}
              className={`px-3 py-1 rounded-lg text-xs font-medium border transition-colors ${
                dominantHand === h
                  ? "bg-[#3C50E0]/20 border-[#3C50E0] text-[#818CF8]"
                  : "border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0]/50 hover:text-white"
              }`}
            >
              {h}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.rightHand")}</p>
          <select
            value={rightHandItem}
            onChange={(e) => setRightHandItem(e.target.value)}
            className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]"
          >
            {handOptions.map((n) => (
              <option key={n || "none"} value={n}>
                {n || t("players.noneOption")}
              </option>
            ))}
          </select>
        </div>
        <div>
          <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.leftHand")}</p>
          <select
            value={leftHandItem}
            onChange={(e) => setLeftHandItem(e.target.value)}
            className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]"
          >
            {handOptions.map((n) => (
              <option key={n || "none"} value={n}>
                {n || t("players.noneOption")}
              </option>
            ))}
          </select>
        </div>
      </div>

      <SaveButton saving={saving} saved={saved} onClick={save} />
    </div>
  );
}
