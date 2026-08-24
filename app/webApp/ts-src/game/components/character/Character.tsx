import { useState, useEffect, useLayoutEffect, useRef } from "react";
import {
  getApiArmors,
  getApiPlayerByIdArmors,
  getApiPlayerByIdSkin,
  getApiWeapons,
  getApiTools,
  getApiPlayerByIdHands,
  getApiPlayerByIdOwned,
} from "../../../generated/api/requests";
import { PlayerModelPreview } from "../../shared/PlayerModelPreview";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "../../../primitives/Tabs";
import { Button } from "../../../primitives/Button";
import { cn } from "../../../primitives/cn";
import { CharacterSyncData, AttackMeta, SpellMeta } from "../../types";
import type { GetApiPlayerByIdHandsResponse } from "../../../generated/api/requests";
import { CharacterStatsPanel } from "./CharacterStatsPanel";
import { ArmorBonusLine } from "./ArmorBonusLine";
import { AttacksTab } from "./AttacksTab";

export interface ArmorSlots {
  head: boolean;
  body: boolean;
  cape: boolean;
  rightBiceps: boolean;
  rightForearm: boolean;
  rightHand: boolean;
  leftBiceps: boolean;
  leftForearm: boolean;
  leftHand: boolean;
  rightThigh: boolean;
  rightCalf: boolean;
  rightFoot: boolean;
  leftThigh: boolean;
  leftCalf: boolean;
  leftFoot: boolean;
}

export interface ArmorStatBonus {
  str?: number;
  dex?: number;
  intel?: number;
  wis?: number;
  con?: number;
  cha?: number;
}

interface ArmorDefinition {
  wearable: ArmorSlots;
  statBonus: ArmorStatBonus;
}

const SLOT_LABELS: { key: keyof ArmorSlots; label: string }[] = [
  { key: "head", label: "HEAD" },
  { key: "body", label: "BODY" },
  { key: "cape", label: "CAPE" },
  { key: "rightBiceps", label: "R.BICEPS" },
  { key: "rightForearm", label: "R.FOREARM" },
  { key: "rightHand", label: "R.HAND" },
  { key: "leftBiceps", label: "L.BICEPS" },
  { key: "leftForearm", label: "L.FOREARM" },
  { key: "leftHand", label: "L.HAND" },
  { key: "rightThigh", label: "R.THIGH" },
  { key: "rightCalf", label: "R.CALF" },
  { key: "rightFoot", label: "R.FOOT" },
  { key: "leftThigh", label: "L.THIGH" },
  { key: "leftCalf", label: "L.CALF" },
  { key: "leftFoot", label: "L.FOOT" },
];

export function slotsOverlap(a: ArmorSlots | undefined, b: ArmorSlots | undefined): boolean {
  if (!a || !b) return false;
  return SLOT_LABELS.some(({ key }) => a[key] && b[key]);
}

type HandItemDefinition = { category: string };
type PlayerHands = GetApiPlayerByIdHandsResponse;

interface Props {
  open: boolean;
  onClose: () => void;
  onCommand: (cmd: string) => void;
  characterSyncData?: CharacterSyncData | null;
  attackMeta?: Record<string, AttackMeta>;
  spellMeta?: Record<string, SpellMeta>;
}

export function Character({ open, onClose, onCommand, characterSyncData, attackMeta = {}, spellMeta = {} }: Props) {
  const [available, setAvailable] = useState<Record<string, ArmorDefinition>>({});
  const [equipped, setEquipped] = useState<string[]>([]);
  const [skin, setSkin] = useState("articulated");
  const [walking, setWalking] = useState(true);
  const [weapons, setWeapons] = useState<Record<string, HandItemDefinition>>({});
  const [tools, setTools] = useState<Record<string, HandItemDefinition>>({});
  const [hands, setHands] = useState<PlayerHands>({ dominantHand: "RIGHT", rightHandItem: null, leftHandItem: null });
  const [owned, setOwned] = useState<{ armors: string[]; weapons: string[]; tools: string[] }>({
    armors: [],
    weapons: [],
    tools: [],
  });
  const [loading, setLoading] = useState(true);
  const closeRef = useRef(onClose);
  useLayoutEffect(() => {
    closeRef.current = onClose;
  });

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    const playerId = window.mcState?.playerId || "";
    Promise.all([
      getApiArmors({ throwOnError: true }).then((r) => r.data),
      getApiPlayerByIdArmors({ path: { id: playerId }, throwOnError: true })
        .then((r) => r.data)
        .catch(() => []),
      getApiPlayerByIdSkin({ path: { id: playerId }, throwOnError: true })
        .then((r) => r.data)
        .catch(() => ({ skin: "articulated" })),
      getApiWeapons({ throwOnError: true })
        .then((r) => r.data)
        .catch(() => ({})),
      getApiTools({ throwOnError: true })
        .then((r) => r.data)
        .catch(() => ({})),
      getApiPlayerByIdHands({ path: { id: playerId }, throwOnError: true })
        .then((r) => r.data)
        .catch(() => ({ dominantHand: "RIGHT" as const, rightHandItem: null, leftHandItem: null })),
      getApiPlayerByIdOwned({ path: { id: playerId }, throwOnError: true })
        .then((r) => r.data)
        .catch(() => ({ armors: [], weapons: [], tools: [] })),
    ]).then(([armors, equippedArmors, skinData, weaponDefs, toolDefs, handsData, ownedData]) => {
      setAvailable(armors as unknown as Record<string, ArmorDefinition>);
      setEquipped(Array.isArray(equippedArmors) ? equippedArmors : []);
      setSkin(skinData.skin ?? "articulated");
      setWeapons(weaponDefs as Record<string, HandItemDefinition>);
      setTools(toolDefs as Record<string, HandItemDefinition>);
      setHands(handsData as PlayerHands);
      setOwned(ownedData as { armors: string[]; weapons: string[]; tools: string[] });
      setLoading(false);
    });
  }, [open]);

  const sortedWeapons = Object.keys(weapons)
    .filter((name) => owned.weapons.includes(name))
    .sort();
  const sortedTools = Object.keys(tools)
    .filter((name) => owned.tools.includes(name))
    .sort();
  const handItems = { ...weapons, ...tools };
  const sortedHandItems = [...sortedWeapons, ...sortedTools];

  function setHand(hand: "right" | "left", name: string) {
    const key = hand === "right" ? "rightHandItem" : "leftHandItem";
    if (name === "") {
      onCommand(`/unwield ${hand}`);
      setHands((prev) => ({ ...prev, [key]: null }));
    } else {
      onCommand(`/wield ${name} ${hand}`);
      setHands((prev) => ({ ...prev, [key]: name }));
    }
  }

  function toggleArmor(name: string) {
    if (equipped.includes(name)) {
      onCommand(`/unequip ${name}`);
      setEquipped((prev) => prev.filter((a) => a !== name));
    } else {
      const slots = available[name]?.wearable;
      const conflicting = equipped.filter((a) => slotsOverlap(available[a]?.wearable, slots));
      conflicting.forEach((c) => onCommand(`/unequip ${c}`));
      onCommand(`/equip ${name}`);
      setEquipped((prev) => prev.filter((a) => !conflicting.includes(a)).concat(name));
    }
  }

  const sortedArmors = Object.keys(available)
    .filter((name) => owned.armors.includes(name))
    .sort();

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent movable className="min-w-[520px] font-mono p-9" onEscapeKeyDown={(e) => e.preventDefault()}>
        <DialogTitle className="text-blue-300 tracking-widest mb-5">CHARACTER</DialogTitle>

        <Tabs defaultValue={characterSyncData ? "stats" : "equipment"}>
          <TabsList className="mb-5">
            {characterSyncData && (
              <TabsTrigger value="stats" className="font-mono text-xs">
                Stats
              </TabsTrigger>
            )}
            <TabsTrigger value="equipment" className="font-mono text-xs">
              Equipment
            </TabsTrigger>
            {(Object.keys(attackMeta).length > 0 || Object.keys(spellMeta).length > 0) && (
              <TabsTrigger value="attacks" className="font-mono text-xs">
                Attaques
              </TabsTrigger>
            )}
          </TabsList>

          {characterSyncData && (
            <TabsContent value="stats">
              <CharacterStatsPanel data={characterSyncData} />
            </TabsContent>
          )}

          <TabsContent value="equipment">
            {loading ? (
              <div className="text-xs text-white/50 py-6 text-center">Loading...</div>
            ) : (
              <div className="flex gap-9 items-start">
                {/* Armor list */}
                <div className="flex-1 min-w-[240px]">
                  {sortedArmors.map((name) => {
                    const armorDef = available[name];
                    const isEquipped = equipped.includes(name);
                    return (
                      <div
                        key={name}
                        className={cn(
                          "flex items-center gap-3 px-3 py-3 mb-2.5 rounded border",
                          isEquipped ? "bg-green-950/60 border-green-700/60" : "bg-black/40 border-white/15",
                        )}
                      >
                        <div className="flex-1">
                          <div className={cn("text-xs mb-1.5", isEquipped ? "text-green-400" : "text-white/80")}>
                            {name}
                          </div>
                          {armorDef && <ArmorBonusLine bonus={armorDef.statBonus} wearable={armorDef.wearable} />}
                        </div>
                        <Button
                          variant={isEquipped ? "ghost" : "secondary"}
                          size="sm"
                          onClick={() => toggleArmor(name)}
                          className={cn(
                            "font-mono text-xs whitespace-nowrap",
                            isEquipped && "text-green-400 hover:text-red-400",
                          )}
                        >
                          {isEquipped ? "Unequip" : "Equip"}
                        </Button>
                      </div>
                    );
                  })}

                  {sortedHandItems.length > 0 && (
                    <div className="mt-6 pt-4 border-t border-white/10">
                      <div className="text-xs text-white/50 mb-2 tracking-widest">HANDS</div>
                      {(["right", "left"] as const).map((hand) => {
                        const current = hand === "right" ? hands.rightHandItem : hands.leftHandItem;
                        return (
                          <div key={hand} className="flex items-center gap-2 mb-2">
                            <span className="text-xs text-white/60 w-16 capitalize">{hand} hand</span>
                            <select
                              className="flex-1 bg-black/40 border border-white/15 rounded text-xs px-2 py-1 text-white/80"
                              value={current ?? ""}
                              onChange={(e) => setHand(hand, e.target.value)}
                            >
                              <option value="">(empty)</option>
                              {sortedWeapons.length > 0 && (
                                <optgroup label="Weapons">
                                  {sortedWeapons.map((name) => (
                                    <option key={name} value={name}>
                                      {name} — {handItems[name].category}
                                    </option>
                                  ))}
                                </optgroup>
                              )}
                              {sortedTools.length > 0 && (
                                <optgroup label="Tools">
                                  {sortedTools.map((name) => (
                                    <option key={name} value={name}>
                                      {name} — {handItems[name].category}
                                    </option>
                                  ))}
                                </optgroup>
                              )}
                            </select>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  <Button variant="ghost" onClick={onClose} className="mt-6 w-full font-mono text-xs text-white/50">
                    Close
                  </Button>
                </div>

                {/* Model preview */}
                <div className="flex flex-col items-center gap-2">
                  <PlayerModelPreview
                    key={skin + equipped.join(",") + hands.rightHandItem + hands.leftHandItem}
                    skin={skin}
                    armors={equipped}
                    rightHandItem={hands.rightHandItem}
                    leftHandItem={hands.leftHandItem}
                    walking={walking}
                  />
                  <div className="flex gap-1 w-40">
                    <button
                      onClick={() => setWalking(false)}
                      className={cn(
                        "flex-1 font-mono text-[11px] py-1 rounded border transition-colors",
                        !walking
                          ? "bg-green-950/60 border-green-700/60 text-green-400"
                          : "bg-black/20 border-white/15 text-white/40",
                      )}
                    >
                      Statique
                    </button>
                    <button
                      onClick={() => setWalking(true)}
                      className={cn(
                        "flex-1 font-mono text-[11px] py-1 rounded border transition-colors",
                        walking
                          ? "bg-green-950/60 border-green-700/60 text-green-400"
                          : "bg-black/20 border-white/15 text-white/40",
                      )}
                    >
                      Marche
                    </button>
                  </div>
                </div>
              </div>
            )}
          </TabsContent>

          <TabsContent value="attacks">
            <AttacksTab attackMeta={attackMeta} spellMeta={spellMeta} />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
