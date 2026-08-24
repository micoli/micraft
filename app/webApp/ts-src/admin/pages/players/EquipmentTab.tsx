import { useEffect, useState } from "react";
import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { SaveButton } from "../../../primitives/SaveButton";
import { getApiArmors, getApiWeapons, getApiTools } from "../../../generated/api/requests";
import { EquipmentToggleList } from "./EquipmentToggleList";
import { GiveItemForm } from "./GiveItemForm";
import { ArmorBonusLine } from "../../../game/components/character/ArmorBonusLine";
import { cn } from "../../../primitives/cn";
import { slotsOverlap, type ArmorSlots, type ArmorStatBonus } from "../../../game/components/character/Character";

interface ArmorDefinition {
  wearable: ArmorSlots;
  statBonus: ArmorStatBonus;
}

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
  const [armorDefs, setArmorDefs] = useState<Record<string, ArmorDefinition>>({});
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
      setArmorDefs(a as Record<string, ArmorDefinition>);
      setWeaponDefs(w as Record<string, unknown>);
      setToolDefs(tl as Record<string, unknown>);
    });
  }, []);

  const toggle = (list: string[], setList: (v: string[]) => void, name: string) =>
    setList(list.includes(name) ? list.filter((n) => n !== name) : [...list, name]);

  const toggleWorn = (name: string) => {
    if (worn.includes(name)) {
      setWorn((prev) => prev.filter((a) => a !== name));
      return;
    }
    const slots = armorDefs[name]?.wearable;
    const conflicting = worn.filter((a) => slotsOverlap(armorDefs[a]?.wearable, slots));
    setWorn((prev) => prev.filter((a) => !conflicting.includes(a)).concat(name));
  };

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
    <div>
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
      <div>
        <p className="text-xs font-medium text-[#8A99AF] mb-2">{t("players.wornArmors")}</p>
        {ownedArmors.length === 0 ? (
          <p className="text-xs text-[#4A5568]">—</p>
        ) : (
          <div className="space-y-2">
            {[...ownedArmors].sort().map((name) => {
              const armorDef = armorDefs[name];
              const isWorn = worn.includes(name);
              return (
                <div
                  key={name}
                  className={cn(
                    "flex items-center gap-3 px-3 py-2 rounded border",
                    isWorn ? "bg-green-950/40 border-green-700/50" : "bg-[#0E1726] border-[#2E3A4E]",
                  )}
                >
                  <div className="flex-1">
                    <div className={cn("text-xs mb-1", isWorn ? "text-green-400" : "text-[#8A99AF]")}>{name}</div>
                    {armorDef && <ArmorBonusLine bonus={armorDef.statBonus} wearable={armorDef.wearable} />}
                  </div>
                  <button
                    onClick={() => toggleWorn(name)}
                    className={cn(
                      "px-2 py-1 rounded-lg text-xs font-medium border transition-colors whitespace-nowrap",
                      isWorn
                        ? "border-[#2E3A4E] text-[#8A99AF] hover:border-red-500/50 hover:text-red-400"
                        : "bg-[#3C50E0]/20 border-[#3C50E0] text-[#818CF8]",
                    )}
                  >
                    {isWorn ? t("players.unequip") : t("players.equip")}
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>
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
