import { useEffect, useState } from "react";
import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { SaveButton } from "../../../primitives/SaveButton";
import { getApiArmors, getApiWeapons, getApiTools } from "../../../generated/api/requests";
import { EquipmentToggleList } from "./EquipmentToggleList";
import { GiveItemForm } from "./GiveItemForm";

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
  // Names shown in each toggle list — fixed at load (plus anything granted via /give below),
  // independent of the toggle selection, so unchecking an item grays it out instead of
  // removing its row from the list.
  const [armorNames, setArmorNames] = useState<string[]>(file.state.ownedArmors ?? []);
  const [weaponNames, setWeaponNames] = useState<string[]>(file.state.ownedWeapons ?? []);
  const [toolNames, setToolNames] = useState<string[]>(file.state.ownedTools ?? []);
  const [worn, setWorn] = useState<string[]>(file.state.armors ?? []);
  const [rightHandItem, setRightHandItem] = useState(file.state.rightHandItem ?? "");
  const [leftHandItem, setLeftHandItem] = useState(file.state.leftHandItem ?? "");
  const [dominantHand, setDominantHand] = useState<string>(file.state.dominantHand ?? "RIGHT");

  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

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

  const onGiven = (name: string) => {
    if (name in armorDefs && !ownedArmors.includes(name)) {
      setOwnedArmors((p) => [...p, name]);
      setArmorNames((p) => (p.includes(name) ? p : [...p, name]));
    }
    if (name in weaponDefs && !ownedWeapons.includes(name)) {
      setOwnedWeapons((p) => [...p, name]);
      setWeaponNames((p) => (p.includes(name) ? p : [...p, name]));
    }
    if (name in toolDefs && !ownedTools.includes(name)) {
      setOwnedTools((p) => [...p, name]);
      setToolNames((p) => (p.includes(name) ? p : [...p, name]));
    }
  };

  const handOptions = ["", ...ownedWeapons, ...ownedTools];
  const giveNames = [...Object.keys(armorDefs), ...Object.keys(weaponDefs), ...Object.keys(toolDefs)];

  return (
    <div className="p-5 space-y-5">
      <GiveItemForm
        names={giveNames}
        placeholder={t("players.giveNamePlaceholder")}
        datalistId="equipment-give-names"
        onGive={onGive}
        onGiven={onGiven}
      />

      <EquipmentToggleList
        title={t("players.ownedArmors")}
        names={[...armorNames].sort()}
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
        names={[...weaponNames].sort()}
        selected={ownedWeapons}
        onToggle={(n) => toggle(ownedWeapons, setOwnedWeapons, n)}
      />
      <EquipmentToggleList
        title={t("players.ownedTools")}
        names={[...toolNames].sort()}
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
