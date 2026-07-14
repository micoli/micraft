import { useState, useEffect, useRef } from "react";
import { PlayerModelPreview } from "../shared/PlayerModelPreview";
import { Dialog, DialogContent, DialogTitle } from "../../primitives/Dialog";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "../../primitives/Tabs";
import { Button } from "../../primitives/Button";
import { cn } from "../../primitives/cn";
import { CharacterSyncData } from "../types";

interface ArmorSlots {
  head: boolean;
  body: boolean;
  rightArm: boolean;
  leftArm: boolean;
  rightLeg: boolean;
  leftLeg: boolean;
}

interface ArmorStatBonus {
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
  { key: "rightArm", label: "R.ARM" },
  { key: "leftArm", label: "L.ARM" },
  { key: "rightLeg", label: "R.LEG" },
  { key: "leftLeg", label: "L.LEG" },
];

function StatRow({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex justify-between items-center py-1 border-b border-white/5">
      <span className="text-white/50 text-xs">{label}</span>
      <span className="text-white text-xs font-mono">{value}</span>
    </div>
  );
}

const CLASS_LABELS: Record<string, string> = {
  WARRIOR: "Warrior",
  MAGE: "Mage",
  RANGER: "Ranger",
  ROGUE: "Rogue",
  CLERIC: "Cleric",
};

function BaseStatRow({ label, base, effective }: { label: string; base: number; effective: number }) {
  const bonus = effective - base;
  return (
    <div className="flex justify-between items-center py-1 border-b border-white/5">
      <span className="text-white/50 text-xs">{label}</span>
      <span className="text-xs font-mono">
        <span className="text-white/60">{base}</span>
        {bonus !== 0 && (
          <span className={cn("ml-1", bonus > 0 ? "text-green-400" : "text-red-400")}>
            {bonus > 0 ? `+${bonus}` : `${bonus}`}
          </span>
        )}
        {bonus !== 0 && <span className="text-white ml-1">= {effective}</span>}
        {bonus === 0 && <span className="text-white ml-1">{base}</span>}
      </span>
    </div>
  );
}

function CharacterStatsPanel({ data }: { data: CharacterSyncData }) {
  const { character: c, derived: d, effectiveBaseStats: e } = data;
  return (
    <div className="flex gap-6">
      <div className="flex-1">
        <div className="text-blue-300 text-xs font-mono mb-3 tracking-widest">IDENTITY</div>
        <StatRow label="Name" value={c.name} />
        <StatRow label="Class" value={CLASS_LABELS[c.characterClass] ?? c.characterClass} />
        <StatRow label="Level" value={c.level} />
        <StatRow label="XP" value={c.xp.toLocaleString()} />
        <StatRow label="HP" value={`${c.currentHp} / ${d.maxHp}`} />
        <StatRow label="Mana" value={`${c.currentMana} / ${d.maxMana}`} />

        <div className="text-blue-300 text-xs font-mono mt-5 mb-3 tracking-widest">BASE STATS</div>
        <BaseStatRow label="STR" base={c.baseStats.str} effective={e.str} />
        <BaseStatRow label="DEX" base={c.baseStats.dex} effective={e.dex} />
        <BaseStatRow label="INT" base={c.baseStats.intel} effective={e.intel} />
        <BaseStatRow label="WIS" base={c.baseStats.wis} effective={e.wis} />
        <BaseStatRow label="CON" base={c.baseStats.con} effective={e.con} />
        <BaseStatRow label="CHA" base={c.baseStats.cha} effective={e.cha} />
      </div>
      <div className="flex-1">
        <div className="text-blue-300 text-xs font-mono mb-3 tracking-widest">COMBAT</div>
        <StatRow label="Melee dmg" value={`+${d.meleeDmg}`} />
        <StatRow label="Ranged dmg" value={`+${d.rangedDmg}`} />
        <StatRow label="Spell dmg" value={`+${d.spellDmg}`} />
        <StatRow label="Crit chance" value={`${d.critChancePct.toFixed(1)}%`} />
        <StatRow label="Crit mult" value={`×${d.critDmgMult}`} />
        <StatRow label="Dodge" value={`${d.dodgePct.toFixed(1)}%`} />
        <StatRow label="Magic resist" value={`${d.magicResistPct.toFixed(0)}%`} />
        <StatRow label="Initiative" value={d.initiative >= 0 ? `+${d.initiative}` : `${d.initiative}`} />

        <div className="text-blue-300 text-xs font-mono mt-5 mb-3 tracking-widest">REGEN</div>
        <StatRow label="HP/s" value={d.hpRegenPerSec.toFixed(1)} />
        <StatRow label="Mana/s" value={d.manaRegenPerSec.toFixed(1)} />
      </div>
    </div>
  );
}

function slotsOverlap(a: ArmorSlots | undefined, b: ArmorSlots | undefined): boolean {
  if (!a || !b) return false;
  return (
    (a.head && b.head) ||
    (a.body && b.body) ||
    (a.rightArm && b.rightArm) ||
    (a.leftArm && b.leftArm) ||
    (a.rightLeg && b.rightLeg) ||
    (a.leftLeg && b.leftLeg)
  );
}

function formatBonus(v: number): string {
  return v === 0 ? "" : v > 0 ? `+${v}` : `${v}`;
}

function ArmorBonusLine({ bonus }: { bonus: ArmorStatBonus | undefined }) {
  if (!bonus) return null;
  const parts = (["str", "dex", "intel", "wis", "con", "cha"] as const)
    .map((k) => ({ k, v: bonus[k] ?? 0 }))
    .filter(({ v }) => v !== 0)
    .map(({ k, v }) => `${k.toUpperCase()} ${formatBonus(v)}`);
  if (parts.length === 0) return null;
  return <span className="text-green-400 text-xs ml-1">{parts.join("  ")}</span>;
}

interface Props {
  open: boolean;
  onClose: () => void;
  onCommand: (cmd: string) => void;
  characterSyncData?: CharacterSyncData | null;
}

export function Character({ open, onClose, onCommand, characterSyncData }: Props) {
  const [available, setAvailable] = useState<Record<string, ArmorDefinition>>({});
  const [equipped, setEquipped] = useState<string[]>([]);
  const [skin, setSkin] = useState("player");
  const [walking, setWalking] = useState(true);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const playerId = window.mcState?.playerId || "";
    Promise.all([
      fetch("/api/armors").then((r) => r.json()),
      fetch(`/api/player/${encodeURIComponent(playerId)}/armors`)
        .then((r) => r.json())
        .catch(() => []),
      fetch(`/api/player/${encodeURIComponent(playerId)}/skin`)
        .then((r) => r.json())
        .catch(() => ({ skin: "player" })),
    ]).then(([armors, equippedArmors, skinData]) => {
      setAvailable(armors as Record<string, ArmorDefinition>);
      setEquipped(Array.isArray(equippedArmors) ? equippedArmors : []);
      setSkin((skinData as { skin: string }).skin ?? "player");
    });
  }, [open]);

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

  const sortedArmors = Object.keys(available).sort();

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent movable className="min-w-[520px] font-mono p-9">
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
          </TabsList>

          {characterSyncData && (
            <TabsContent value="stats">
              <CharacterStatsPanel data={characterSyncData} />
            </TabsContent>
          )}

          <TabsContent value="equipment">
            <div className="flex gap-9 items-start">
              {/* Armor list */}
              <div className="flex-1 min-w-[240px]">
                {sortedArmors.length === 0 && <div className="text-white/30 text-xs">No armor available.</div>}
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
                          {armorDef && <ArmorBonusLine bonus={armorDef.statBonus} />}
                        </div>
                        <div className="flex gap-1 flex-wrap">
                          {SLOT_LABELS.filter((s) => armorDef?.wearable[s.key]).map((s) => (
                            <span
                              key={s.key}
                              className="text-[9px] px-1 py-px bg-blue-950/60 border border-blue-700/40 rounded-sm text-blue-300"
                            >
                              {s.label}
                            </span>
                          ))}
                        </div>
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

                <Button variant="ghost" onClick={onClose} className="mt-6 w-full font-mono text-xs text-white/50">
                  Close
                </Button>
              </div>

              {/* Model preview */}
              <div className="flex flex-col items-center gap-2">
                <PlayerModelPreview key={skin + equipped.join(",")} skin={skin} armors={equipped} walking={walking} />
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
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
